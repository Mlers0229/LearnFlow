import json
import logging
import re
import time
from typing import Any

from app.core.llm import ask_llm
from app.db import save_agent_call
from app.models.goal import GoalMilestone, GoalPlanStructure, GoalRequest, GoalTopic

logger = logging.getLogger(__name__)


PRACTICE_KEYWORDS = {
    "project": "project",
    "实战": "project",
    "项目": "project",
    "code": "coding",
    "coding": "coding",
    "开发": "coding",
    "练习": "quiz",
    "面试": "quiz",
}

TOPIC_LIBRARY: list[tuple[list[str], list[str]]] = [
    (
        ["java", "后端"],
        [
            "Java 基础语法与面向对象",
            "集合、异常与常用工具类",
            "数据库与 SQL 基础",
            "Spring Boot Web 开发",
            "接口设计与项目实战",
            "复习与项目总结",
        ],
    ),
    (
        ["java", "spring"],
        [
            "Java 基础语法与面向对象",
            "集合、异常与常用工具类",
            "Spring Boot 核心机制",
            "接口开发与数据库整合",
            "综合项目实战与部署",
        ],
    ),
    (
        ["java"],
        [
            "Java 语法基础与开发环境",
            "面向对象与常用 API",
            "集合、异常与 IO",
            "项目练习与调试",
            "总结复盘与输出",
        ],
    ),
    (
        ["python"],
        [
            "Python 语法基础",
            "函数、模块与数据结构",
            "面向对象与常用库",
            "项目练习与调试",
            "复习与输出",
        ],
    ),
    (
        ["vue"],
        [
            "Vue 基础语法与组件思维",
            "组合式 API 与状态管理",
            "页面交互与接口联调",
            "项目实战与性能优化",
            "总结复盘与作品整理",
        ],
    ),
    (
        ["react"],
        [
            "React 基础与组件化",
            "状态管理与 Hooks",
            "路由、数据请求与表单",
            "项目实战与性能优化",
            "总结复盘与作品整理",
        ],
    ),
    (
        ["前端"],
        [
            "HTML / CSS / JavaScript 基础",
            "页面结构与交互逻辑",
            "组件化开发与接口联调",
            "项目实战与调优",
            "复盘总结与作品输出",
        ],
    ),
    (
        ["算法"],
        [
            "基础数据结构与复杂度",
            "常见算法思想与题型",
            "专项刷题与错误归因",
            "综合题训练与复盘",
        ],
    ),
]

GENERIC_TOPIC_STOP_WORDS = {
    "学习",
    "学习计划",
    "计划",
    "目标",
    "提升",
    "入门",
    "系统学习",
    "准备",
    "掌握",
    "熟悉",
    "了解",
    "完成",
    "实现",
    "能够",
    "学会",
    "重点掌握",
    "重点学习",
    "希望",
    "我想",
    "一个",
}

PREFIX_PATTERN = re.compile(
    r"^(我想|希望|计划|打算|准备|系统学习|学习|入门|掌握|熟悉|了解|提升|完成|实现|能够|学会|重点掌握|重点学习|目标是|希望能)\s*"
)


def _extract_json_from_text(text: str) -> str:
    if not text:
        return text

    start = text.find("{")
    end = text.rfind("}")
    if start == -1 or end == -1 or end <= start:
        return text
    return text[start : end + 1]


class GoalAgent:
    """将自然语言目标拆解成结构化学习蓝图。"""

    def run(self, goal: GoalRequest, trace_id: str | None = None) -> GoalPlanStructure:
        blueprint = self._try_llm(goal, trace_id=trace_id)
        if blueprint is None:
            blueprint = self._fallback_blueprint(goal)
        return blueprint

    def _try_llm(self, goal: GoalRequest, trace_id: str | None = None) -> GoalPlanStructure | None:
        prompt = f"""
你是一名学习路径规划专家。请把用户的目标拆解成结构化学习蓝图，并且严格输出 JSON。

返回格式：
{{
  "summary": "一句话总结这份学习蓝图",
  "target_role": "目标角色或方向",
  "topics": [
    {{
      "id": "topic-1",
      "name": "主题名称",
      "description": "主题说明",
      "order": 1,
      "importance": "core",
      "estimated_days": 5,
      "difficulty": "beginner",
      "prerequisites": [],
      "practice_type": "coding",
      "milestone_hint": "完成后应该能做什么"
    }}
  ],
  "milestones": [
    {{
      "title": "里程碑名称",
      "description": "里程碑说明",
      "suggested_week": 2
    }}
  ]
}}

要求：
- topics 输出 4 到 8 个。
- order 从 1 开始递增。
- prerequisites 使用主题 id。
- importance 只能是 core / important / supporting。
- practice_type 只能是 reading / coding / project / quiz。
- 全部用简体中文。

用户目标：{goal.goal_text}
学习周期：{goal.duration_weeks} 周
每天学习时长：{goal.hours_per_day} 小时
当前水平：{goal.level}
目标角色：{goal.target_role or '未提供'}
偏好方式：{goal.preferred_style or '未提供'}
约束条件：{goal.constraints or []}
最终成果：{goal.final_deliverable or '未提供'}
"""
        start = time.perf_counter()
        request_payload = json.dumps(goal.model_dump(), ensure_ascii=False)
        try:
            content = ask_llm(prompt)
            if not content or not content.strip():
                return None
            duration_ms = int((time.perf_counter() - start) * 1000)
            payload = json.loads(_extract_json_from_text(content))

            topics: list[GoalTopic] = []
            raw_topics = payload.get("topics", [])
            for index, item in enumerate(raw_topics, start=1):
                if not isinstance(item, dict):
                    continue
                topic = GoalTopic(
                    id=str(item.get("id") or f"topic-{index}"),
                    name=str(item.get("name") or f"主题 {index}").strip(),
                    description=str(item.get("description") or "").strip(),
                    order=int(item.get("order") or index),
                    importance=str(item.get("importance") or "important").strip(),
                    estimated_days=self._safe_int(item.get("estimated_days"), default=3),
                    difficulty=str(item.get("difficulty") or goal.level).strip(),
                    prerequisites=[str(v).strip() for v in item.get("prerequisites", []) if str(v).strip()],
                    practice_type=str(item.get("practice_type") or "reading").strip(),
                    milestone_hint=str(item.get("milestone_hint") or "").strip() or None,
                )
                topics.append(topic)

            if not topics:
                return None

            milestones: list[GoalMilestone] = []
            raw_milestones = payload.get("milestones", [])
            for item in raw_milestones:
                if not isinstance(item, dict):
                    continue
                title = str(item.get("title") or "").strip()
                if not title:
                    continue
                milestones.append(
                    GoalMilestone(
                        title=title,
                        description=str(item.get("description") or title).strip(),
                        suggested_week=self._safe_int(item.get("suggested_week"), default=None),
                    )
                )

            blueprint = GoalPlanStructure(
                summary=str(payload.get("summary") or f"围绕“{goal.goal_text}”的学习蓝图").strip(),
                target_role=str(payload.get("target_role") or goal.target_role or "").strip() or None,
                topics=sorted(topics, key=lambda topic: topic.order),
                milestones=milestones,
            )
            self._log_call("GoalAgent", trace_id, request_payload, blueprint.model_dump(), duration_ms)
            return blueprint
        except Exception as exc:  # noqa: BLE001
            logger.exception("GoalAgent 调用 LLM 失败，将使用本地蓝图兜底。", exc_info=exc)
            duration_ms = int((time.perf_counter() - start) * 1000)
            self._log_call("GoalAgent", trace_id, request_payload, None, duration_ms)
            return None

    def _fallback_blueprint(self, goal: GoalRequest) -> GoalPlanStructure:
        topic_names = self._guess_topics(goal.goal_text, goal.target_role, goal.final_deliverable)
        estimated = max(2, (goal.duration_weeks * 7) // max(len(topic_names), 1))
        topics: list[GoalTopic] = []
        for index, name in enumerate(topic_names, start=1):
            topic_id = f"topic-{index}"
            prerequisites = [] if index == 1 else [f"topic-{index - 1}"]
            importance = "core" if index <= 2 else "important"
            practice_type = self._guess_practice_type(goal.goal_text, name)
            milestone_hint = self._build_milestone_hint(name, practice_type, goal.final_deliverable)
            topics.append(
                GoalTopic(
                    id=topic_id,
                    name=name,
                    description=self._build_topic_description(name, practice_type, goal.goal_text),
                    order=index,
                    importance=importance,
                    estimated_days=estimated,
                    difficulty=goal.level,
                    prerequisites=prerequisites,
                    practice_type=practice_type,
                    milestone_hint=milestone_hint,
                )
            )

        milestones = [
            GoalMilestone(
                title="完成基础阶段",
                description=f"在第 {min(goal.duration_weeks, 2)} 周前完成前两项核心主题的入门学习。",
                suggested_week=min(goal.duration_weeks, 2),
            )
        ]
        if goal.duration_weeks >= 4:
            deliverable_text = goal.final_deliverable or "一次综合练习、阶段总结或小项目"
            milestones.append(
                GoalMilestone(
                    title="完成一次综合输出",
                    description=f"至少完成 {deliverable_text}，并做一次自我复盘。",
                    suggested_week=min(goal.duration_weeks, 4),
                )
            )

        return GoalPlanStructure(
            summary=f"围绕“{goal.goal_text}”生成的结构化学习蓝图。",
            target_role=goal.target_role,
            topics=topics,
            milestones=milestones,
        )

    @staticmethod
    def _safe_int(value: Any, default: int | None) -> int | None:
        try:
            if value is None:
                return default
            parsed = int(value)
            return parsed if parsed > 0 else default
        except Exception:  # noqa: BLE001
            return default

    @staticmethod
    def _guess_practice_type(goal_text: str, topic_name: str) -> str:
        lower_text = f"{goal_text} {topic_name}".lower()
        for keyword, practice_type in PRACTICE_KEYWORDS.items():
            if keyword in lower_text:
                return practice_type
        return "coding" if re.search(r"java|python|spring|后端|编程|前端|react|vue", lower_text) else "reading"

    @classmethod
    def _guess_topics(
        cls,
        goal_text: str,
        target_role: str | None = None,
        final_deliverable: str | None = None,
    ) -> list[str]:
        lower_text = goal_text.lower()
        for keywords, topics in TOPIC_LIBRARY:
            if all(keyword.lower() in lower_text for keyword in keywords):
                return cls._append_deliverable_topic(topics, final_deliverable)

        fragments = cls._extract_named_fragments(goal_text)
        if fragments:
            return cls._complete_topics_from_fragments(fragments, target_role or goal_text, final_deliverable)

        subject = cls._extract_subject(goal_text, target_role)
        subject = subject or "该主题"
        return cls._append_deliverable_topic(
            [
                f"{subject} 基础与关键概念",
                f"{subject} 核心方法与常见场景",
                f"{subject} 练习巩固与问题定位",
                f"{subject} 综合输出与复盘",
            ],
            final_deliverable,
        )

    @classmethod
    def _extract_named_fragments(cls, text: str) -> list[str]:
        normalized = re.sub(r"[，,；;：:、/|]+", "、", text)
        normalized = re.sub(r"(以及|并且|并|和|及|还有|同时)", "、", normalized)
        parts = [part.strip() for part in normalized.split("、") if part.strip()]

        fragments: list[str] = []
        seen: set[str] = set()
        for part in parts:
            candidate = cls._clean_candidate(part)
            if not cls._looks_like_topic(candidate):
                continue
            token = re.sub(r"\s+", "", candidate.lower())
            if token in seen:
                continue
            seen.add(token)
            fragments.append(candidate)

        return fragments[:6]

    @staticmethod
    def _clean_candidate(candidate: str) -> str:
        value = candidate.strip()
        while True:
            updated = PREFIX_PATTERN.sub("", value).strip()
            if updated == value:
                break
            value = updated
        value = re.sub(r"^(重点|围绕|关于|针对|面向)\s*", "", value).strip()
        value = re.sub(r"[。！？!?,，；;：:]+$", "", value).strip()
        return value

    @classmethod
    def _looks_like_topic(cls, candidate: str) -> bool:
        if not candidate or len(candidate) < 2 or len(candidate) > 24:
            return False
        plain = re.sub(r"\s+", "", candidate)
        if plain in GENERIC_TOPIC_STOP_WORDS:
            return False
        if plain.endswith("学习计划") and len(plain) <= 6:
            return False
        return True

    @classmethod
    def _complete_topics_from_fragments(
        cls,
        fragments: list[str],
        fallback_subject: str,
        final_deliverable: str | None,
    ) -> list[str]:
        topics: list[str] = []
        seen: set[str] = set()
        for fragment in fragments:
            formatted = cls._format_topic_name(fragment)
            token = re.sub(r"\s+", "", formatted.lower())
            if token in seen:
                continue
            seen.add(token)
            topics.append(formatted)
            if len(topics) >= 4:
                break

        subject = cls._extract_subject(fallback_subject, None) or fragments[0]
        supplements = [
            f"{subject} 核心方法与应用",
            f"{subject} 练习巩固与问题定位",
            f"{subject} 综合输出与复盘",
        ]
        for item in supplements:
            token = re.sub(r"\s+", "", item.lower())
            if token in seen:
                continue
            seen.add(token)
            topics.append(item)
            if len(topics) >= 4:
                break

        return cls._append_deliverable_topic(topics[:6], final_deliverable)

    @staticmethod
    def _format_topic_name(value: str) -> str:
        cleaned = re.sub(r"\s+", " ", value).strip()
        cleaned = cleaned.replace("  ", " ")
        return cleaned

    @staticmethod
    def _extract_subject(goal_text: str, target_role: str | None) -> str | None:
        if target_role and target_role.strip():
            return target_role.strip()

        value = goal_text.strip()
        patterns = [
            r"学习(.{2,18})",
            r"掌握(.{2,18})",
            r"准备(.{2,18})",
            r"完成(.{2,18})",
        ]
        for pattern in patterns:
            match = re.search(pattern, value)
            if match:
                subject = GoalAgent._clean_candidate(match.group(1))
                if subject and len(subject) <= 18:
                    return subject
        return None

    @classmethod
    def _append_deliverable_topic(cls, topics: list[str], final_deliverable: str | None) -> list[str]:
        items = list(topics)
        if not final_deliverable or not final_deliverable.strip():
            return items
        deliverable = final_deliverable.strip()
        has_output_topic = any(any(keyword in topic for keyword in ["项目", "作品", "输出", "总结"]) for topic in items)
        if not has_output_topic:
            items.append(cls._build_deliverable_topic(deliverable))
        return items[:6]

    @staticmethod
    def _build_deliverable_topic(final_deliverable: str) -> str:
        deliverable = final_deliverable.strip()
        if any(keyword in deliverable for keyword in ["项目", "系统", "网站", "应用", "接口"]):
            return "综合项目实现与交付"
        if any(keyword in deliverable for keyword in ["作品", "海报", "作品集"]):
            return "作品打磨与成果整理"
        return "综合输出与成果复盘"

    @staticmethod
    def _build_topic_description(topic_name: str, practice_type: str, goal_text: str) -> str:
        if practice_type == "project":
            return f"围绕 {topic_name} 建立可落地的项目能力，并逐步贴近“{goal_text}”的最终目标。"
        if practice_type == "coding":
            return f"围绕 {topic_name} 建立概念理解、代码实现和问题定位能力。"
        if practice_type == "quiz":
            return f"围绕 {topic_name} 建立概念辨析、题目训练和复盘纠错能力。"
        return f"围绕 {topic_name} 建立基础理解，并逐步转向可执行练习。"

    @staticmethod
    def _build_milestone_hint(topic_name: str, practice_type: str, final_deliverable: str | None) -> str:
        if practice_type == "project":
            deliverable = final_deliverable or "一个小项目或阶段成果"
            return f"完成 {topic_name} 后，能够把它真正用到 {deliverable} 中。"
        if practice_type == "coding":
            return f"完成 {topic_name} 后，能够独立写出一个最小可运行示例并解释关键步骤。"
        if practice_type == "quiz":
            return f"完成 {topic_name} 后，能够稳定完成相关基础题，并说清常见错误原因。"
        return f"完成 {topic_name} 后，能够独立解释相关概念并完成一个小练习。"

    @staticmethod
    def _log_call(
        agent_name: str,
        trace_id: str | None,
        request_payload: str,
        response_payload: dict[str, Any] | None,
        duration_ms: int,
    ) -> None:
        try:
            save_agent_call(
                agent_name=agent_name,
                trace_id=trace_id,
                request_payload=request_payload,
                response_payload=json.dumps(response_payload, ensure_ascii=False) if response_payload is not None else None,
                model_name=None,
                duration_ms=duration_ms,
            )
        except Exception:  # noqa: BLE001
            logger.debug("保存 GoalAgent 调用日志失败，但不影响主流程。")