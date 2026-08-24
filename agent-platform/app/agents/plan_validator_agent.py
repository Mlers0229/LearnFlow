import json
import logging
import re
import time
from difflib import SequenceMatcher

from app.db import save_agent_call
from app.models.goal import GoalPlanStructure, GoalRequest
from app.models.plan import PlanResponse, PlanValidationIssue, PlanValidationReport
from app.observability import record_validator_result

logger = logging.getLogger(__name__)


class PlanValidatorAgent:
    """对计划结果做规则化质量检查。"""

    def validate(
        self,
        plan: PlanResponse,
        goal: GoalRequest | None = None,
        goal_structure: GoalPlanStructure | None = None,
        trace_id: str | None = None,
    ) -> PlanValidationReport:
        start = time.perf_counter()
        issues: list[PlanValidationIssue] = []
        warnings: list[PlanValidationIssue] = []
        suggested_fixes: list[str] = []

        coverage_score, matched_topics, unmatched_topics = self._coverage_analysis(plan, goal_structure)
        repetition_score = self._repetition_score(plan)
        load_balance_score = self._load_balance_score(plan, goal)

        if coverage_score < 60:
            issues.append(
                PlanValidationIssue(
                    code="low_topic_coverage",
                    message="计划对目标主题的覆盖偏低，建议补足缺失主题。",
                    severity="error",
                )
            )
            suggested_fixes.append("补充未覆盖的主题，并在周计划中重新分配。")
            logger.warning(
                "PlanValidatorAgent 判定主题覆盖偏低。trace_id=%s matched=%s unmatched=%s score=%s",
                trace_id,
                matched_topics,
                unmatched_topics,
                coverage_score,
            )
        elif coverage_score < 85:
            warnings.append(
                PlanValidationIssue(
                    code="partial_topic_coverage",
                    message="计划对部分主题覆盖不足，建议增加对应学习日或复习日。",
                    severity="warning",
                )
            )
            logger.info(
                "PlanValidatorAgent 判定主题部分覆盖。trace_id=%s matched=%s unmatched=%s score=%s",
                trace_id,
                matched_topics,
                unmatched_topics,
                coverage_score,
            )

        if repetition_score < 60:
            issues.append(
                PlanValidationIssue(
                    code="high_repetition",
                    message="计划中重复标题或重复任务过多。",
                    severity="error",
                )
            )
            suggested_fixes.append("减少重复任务模板，按阶段或周次区分目标。")
        elif repetition_score < 80:
            warnings.append(
                PlanValidationIssue(
                    code="moderate_repetition",
                    message="计划有一定重复，建议增加差异化实践和复盘任务。",
                    severity="warning",
                )
            )

        if load_balance_score < 60:
            issues.append(
                PlanValidationIssue(
                    code="unbalanced_load",
                    message="每天负载不均衡，部分学习日任务过载或过轻。",
                    severity="error",
                )
            )
            suggested_fixes.append("按照每天可用时长调整单日任务数量和粒度。")
        elif load_balance_score < 85:
            warnings.append(
                PlanValidationIssue(
                    code="minor_load_imbalance",
                    message="计划存在轻微负载波动，可以继续优化单日任务粒度。",
                    severity="warning",
                )
            )

        if not self._has_task_type(plan, {"review", "recap"}):
            warnings.append(
                PlanValidationIssue(
                    code="missing_review_day",
                    message="计划中缺少明显的复习或总结日。",
                    severity="warning",
                )
            )
            suggested_fixes.append("每周至少安排一次复习或总结日。")

        if not self._has_task_type(plan, {"practice", "project"}):
            warnings.append(
                PlanValidationIssue(
                    code="missing_practice_day",
                    message="计划中缺少足够的练习或实战输出。",
                    severity="warning",
                )
            )
            suggested_fixes.append("在每个阶段增加练习日或项目日。")

        report = PlanValidationReport(
            is_valid=not issues,
            issues=issues,
            warnings=warnings,
            coverage_score=coverage_score,
            repetition_score=repetition_score,
            load_balance_score=load_balance_score,
            suggested_fixes=suggested_fixes,
        )
        record_validator_result(report.is_valid, len(report.issues), len(report.warnings))

        try:
            save_agent_call(
                agent_name="PlanValidatorAgent",
                trace_id=trace_id,
                request_payload=json.dumps(plan.model_dump(mode="json"), ensure_ascii=False),
                response_payload=json.dumps(report.model_dump(mode="json"), ensure_ascii=False),
                model_name="-",
                duration_ms=int((time.perf_counter() - start) * 1000),
            )
        except Exception:  # noqa: BLE001
            logger.debug("保存 PlanValidatorAgent 调用日志失败，但不影响主流程。")
        return report

    @staticmethod
    def _coverage_analysis(
        plan: PlanResponse,
        goal_structure: GoalPlanStructure | None,
    ) -> tuple[int, list[str], list[str]]:
        if goal_structure is None or not goal_structure.topics:
            return 100, [], []

        day_texts = [
            PlanValidatorAgent._normalize_text(
                f"{day.title} {' '.join(day.tasks)} {day.goal or ''} {' '.join(day.review_of)}"
            )
            for day in plan.days
        ]
        plan_text = "".join(day_texts)
        covered_topic_ids = {
            topic_id.strip().lower()
            for day in plan.days
            for topic_id in day.topic_ids
            if isinstance(topic_id, str) and topic_id.strip()
        }

        matched_topics: list[str] = []
        unmatched_topics: list[str] = []
        for topic in goal_structure.topics:
            if PlanValidatorAgent._topic_matches(topic, covered_topic_ids, plan_text, day_texts):
                matched_topics.append(topic.name)
            else:
                unmatched_topics.append(topic.name)

        score = max(0, min(100, int(round(len(matched_topics) * 100 / len(goal_structure.topics)))))
        return score, matched_topics, unmatched_topics

    @staticmethod
    def _topic_matches(
        topic,
        covered_topic_ids: set[str],
        plan_text: str,
        day_texts: list[str],
    ) -> bool:
        topic_id = (topic.id or "").strip().lower()
        if topic_id and topic_id in covered_topic_ids:
            return True

        keywords = PlanValidatorAgent._topic_keywords(topic.name)
        if getattr(topic, "description", None):
            keywords.extend(PlanValidatorAgent._topic_keywords(topic.description))
        if getattr(topic, "milestone_hint", None):
            keywords.extend(PlanValidatorAgent._topic_keywords(topic.milestone_hint))
        keywords = PlanValidatorAgent._dedupe_keywords(keywords)
        if not keywords:
            return False

        core_keywords = [keyword for keyword in PlanValidatorAgent._topic_keywords(topic.name) if len(keyword) >= 2]
        if any(keyword in plan_text for keyword in core_keywords):
            return True

        for day_text in day_texts:
            keyword_hits = sum(1 for keyword in core_keywords if keyword in day_text)
            if core_keywords and keyword_hits >= max(1, min(2, len(core_keywords))):
                return True

        topic_text = PlanValidatorAgent._normalize_text(topic.name)
        if not topic_text:
            return False

        best_similarity = 0.0
        best_bigram_overlap = 0.0
        for day_text in day_texts:
            if not day_text:
                continue
            best_similarity = max(best_similarity, SequenceMatcher(None, topic_text, day_text).ratio())
            best_bigram_overlap = max(best_bigram_overlap, PlanValidatorAgent._bigram_overlap(topic_text, day_text))

        return best_similarity >= 0.50 or best_bigram_overlap >= 0.55

    @staticmethod
    def _normalize_text(text: str) -> str:
        lowered = text.lower()
        lowered = lowered.replace("restful", "restful api")
        lowered = lowered.replace("springboot", "spring boot")
        lowered = lowered.replace("spring boot", "springboot")
        lowered = lowered.replace("mysql", "mysql sql")
        return re.sub(r"[\s\-_.:,，。；;：:、/|()（）\[\]【】]+", "", lowered)

    @staticmethod
    def _topic_keywords(topic_text: str) -> list[str]:
        if not topic_text:
            return []

        keywords: list[str] = []
        normalized_full = PlanValidatorAgent._normalize_text(topic_text)
        if len(normalized_full) >= 2:
            keywords.append(normalized_full)

        fragments = re.split(r"[、/|,，\s]+|与|和|及|并|以及|并且", topic_text)
        for fragment in fragments:
            normalized_fragment = PlanValidatorAgent._normalize_text(fragment)
            normalized_fragment = re.sub(
                r"(基础|入门|核心|进阶|综合|实战|项目|开发|应用|常用|常见|巩固|总结|复盘|学习|训练|搭建|实现|实践)$",
                "",
                normalized_fragment,
            )
            if len(normalized_fragment) >= 2:
                keywords.append(normalized_fragment)

        return PlanValidatorAgent._dedupe_keywords(keywords)

    @staticmethod
    def _dedupe_keywords(keywords: list[str]) -> list[str]:
        deduped: list[str] = []
        seen: set[str] = set()
        for keyword in keywords:
            if not keyword or keyword in seen:
                continue
            seen.add(keyword)
            deduped.append(keyword)
        return deduped

    @staticmethod
    def _bigram_overlap(source: str, target: str) -> float:
        if not source or not target:
            return 0.0
        if len(source) < 2:
            return 1.0 if source in target else 0.0

        grams = [source[index : index + 2] for index in range(len(source) - 1)]
        if not grams:
            return 0.0
        hits = sum(1 for gram in grams if gram in target)
        return hits / len(grams)

    @staticmethod
    def _repetition_score(plan: PlanResponse) -> int:
        if not plan.days:
            return 100
        signatures = []
        for day in plan.days:
            signature = (day.title.strip().lower(), tuple(task.strip().lower() for task in day.tasks))
            signatures.append(signature)
        unique = len(set(signatures))
        return max(0, min(100, int(round(unique * 100 / len(signatures)))))

    @staticmethod
    def _load_balance_score(plan: PlanResponse, goal: GoalRequest | None) -> int:
        if not plan.days:
            return 100
        target_minutes = goal.hours_per_day * 60 if goal is not None else None
        scores = []
        for day in plan.days:
            estimated = day.estimated_minutes or max(30, len(day.tasks) * 25)
            if target_minutes is None:
                scores.append(100)
                continue
            diff = abs(estimated - target_minutes)
            score = max(0, 100 - int(round(diff * 100 / max(target_minutes, 1))))
            scores.append(score)
        return max(0, min(100, int(round(sum(scores) / len(scores)))))

    @staticmethod
    def _has_task_type(plan: PlanResponse, expected_types: set[str]) -> bool:
        for day in plan.days:
            if (day.task_type or "").lower() in expected_types:
                return True
            title = day.title.lower()
            if any(keyword in title for keyword in expected_types):
                return True
        return False
