from datetime import date, timedelta
import json
import logging
import time
from uuid import uuid4

from app.core.llm import ask_llm
from app.db import save_agent_call
from app.models.goal import GoalPlanStructure, GoalRequest, GoalTopic
from app.models.plan import LearningPhase, PlanDay, PlanResponse, WeeklyPlan

from app.config.llm_runtime import get_effective_llm_config

logger = logging.getLogger(__name__)


DAY_TYPE_ORDER = [
    "kickoff",
    "study",
    "practice",
    "study",
    "practice",
    "review",
    "recap",
]


class PlanAgent:
    """将周计划展开成按天的粗粒度计划。"""

    def run(
        self,
        goal: GoalRequest,
        goal_structure: GoalPlanStructure | None = None,
        phases: list[LearningPhase] | None = None,
        weeks: list[WeeklyPlan] | None = None,
        trace_id: str | None = None,
    ) -> PlanResponse:
        start = time.perf_counter()
        request_payload = json.dumps(
            {
                "goal": goal.model_dump(),
                "goal_blueprint": goal_structure.model_dump() if goal_structure is not None else None,
                "phases": [phase.model_dump() for phase in (phases or [])],
                "weeks": [week.model_dump() for week in (weeks or [])],
            },
            ensure_ascii=False,
        )

        if weeks:
            if self._should_use_llm():
                plan = self._build_plan_from_schedule_with_llm(goal, goal_structure, phases or [], weeks)
                if plan is not None:
                    self._log_call(
                        trace_id,
                        request_payload,
                        plan,
                        start,
                        model_name=self._resolve_model_name(),
                    )
                    return plan

            plan = self._build_plan_from_schedule(goal, goal_structure, phases or [], weeks)
            self._log_call(trace_id, request_payload, plan, start, model_name="-")
            return plan

        if self._should_use_llm():
            plan = self._try_llm(goal, goal_structure)
            if plan is not None:
                self._log_call(trace_id, request_payload, plan, start, model_name=self._resolve_model_name())
                return plan

        plan = self._fallback_plan(goal, goal_structure)
        self._log_call(trace_id, request_payload, plan, start, model_name="-")
        return plan

    def _build_plan_from_schedule(
        self,
        goal: GoalRequest,
        goal_structure: GoalPlanStructure | None,
        phases: list[LearningPhase],
        weeks: list[WeeklyPlan],
    ) -> PlanResponse:
        days = self._build_rule_based_days(goal, goal_structure, phases, weeks)
        return self._build_plan_response(goal, days)

    def _build_rule_based_days(
        self,
        goal: GoalRequest,
        goal_structure: GoalPlanStructure | None,
        phases: list[LearningPhase],
        weeks: list[WeeklyPlan],
    ) -> list[PlanDay]:
        topic_by_name = {topic.name: topic for topic in (goal_structure.topics if goal_structure else [])}
        today = date.today()
        days: list[PlanDay] = []
        day_index = 1
        phase_map = {phase.phase_id: phase for phase in phases}

        for week in weeks:
            phase = phase_map.get(week.phase_id)
            for local_day, day_type in enumerate(DAY_TYPE_ORDER, start=1):
                day_date = today + timedelta(days=day_index - 1)
                primary_topic = week.focus_topics[(local_day - 1) % max(len(week.focus_topics), 1)] if week.focus_topics else week.theme
                topic = topic_by_name.get(primary_topic)
                title, tasks, review_of = self._build_day_content(
                    day_type=day_type,
                    topic_name=primary_topic,
                    topic=topic,
                    week=week,
                    phase=phase,
                    goal=goal,
                    local_day=local_day,
                )
                estimated_minutes = min(180, max(30, goal.hours_per_day * 60))
                days.append(
                    PlanDay(
                        date=day_date,
                        title=title,
                        tasks=tasks,
                        status="not_started",
                        day_index=day_index,
                        week_index=week.week_index,
                        phase_id=week.phase_id,
                        goal=self._build_daily_goal(primary_topic, week, topic),
                        estimated_minutes=estimated_minutes,
                        topic_ids=[topic.id] if topic and topic.id else [],
                        task_type=day_type,
                        difficulty=(topic.difficulty if topic and topic.difficulty else goal.level),
                        review_of=review_of,
                    )
                )
                day_index += 1

        return days

    def _build_plan_from_schedule_with_llm(
        self,
        goal: GoalRequest,
        goal_structure: GoalPlanStructure | None,
        phases: list[LearningPhase],
        weeks: list[WeeklyPlan],
    ) -> PlanResponse | None:
        baseline_days = self._build_rule_based_days(goal, goal_structure, phases, weeks)
        if not baseline_days:
            return None

        phase_map = {phase.phase_id: phase for phase in phases}
        day_groups = self._group_days_by_week(baseline_days)
        enhanced_days: list[PlanDay] = []
        llm_success_count = 0
        consecutive_failures = 0

        for index, week in enumerate(weeks):
            week_days = day_groups.get(week.week_index, [])
            if not week_days:
                continue
            phase = phase_map.get(week.phase_id)
            maybe_days = self._enhance_week_with_llm(goal, goal_structure, phase, week, week_days)
            if maybe_days is not None:
                enhanced_days.extend(maybe_days)
                llm_success_count += 1
                consecutive_failures = 0
            else:
                enhanced_days.extend(day.model_copy(deep=True) for day in week_days)
                consecutive_failures += 1
                if consecutive_failures >= 2:
                    remaining_weeks = weeks[index + 1 :]
                    if remaining_weeks:
                        logger.warning("PlanAgent 连续多周调用 LLM 失败，剩余周次将直接使用规则计划。")
                    for remaining_week in remaining_weeks:
                        remaining_days = day_groups.get(remaining_week.week_index, [])
                        enhanced_days.extend(day.model_copy(deep=True) for day in remaining_days)
                    break

        if not enhanced_days:
            return None

        if llm_success_count == 0:
            return None

        enhanced_days.sort(key=lambda item: item.day_index or 0)
        return self._build_plan_response(goal, enhanced_days)

    def _enhance_week_with_llm(
        self,
        goal: GoalRequest,
        goal_structure: GoalPlanStructure | None,
        phase: LearningPhase | None,
        week: WeeklyPlan,
        week_days: list[PlanDay],
    ) -> list[PlanDay] | None:
        prompt = self._build_week_plan_prompt(goal, goal_structure, phase, week, week_days)
        try:
            raw = ask_llm(prompt)
            if not raw or not raw.strip():
                return None

            payload = json.loads(self._extract_json_from_text(raw))
            raw_days = payload.get("days", [])
            if not isinstance(raw_days, list) or len(raw_days) != len(week_days):
                return None

            day_map = {day.day_index: day for day in week_days if day.day_index is not None}
            merged_days: list[PlanDay] = []
            for raw_day in raw_days:
                if not isinstance(raw_day, dict):
                    return None
                day_index = self._safe_int(raw_day.get("day_index"))
                if day_index is None:
                    return None
                base_day = day_map.get(day_index)
                if base_day is None:
                    return None

                title = str(raw_day.get("title") or "").strip()
                tasks = [str(task).strip() for task in raw_day.get("tasks", []) if str(task).strip()]
                if not title or len(tasks) < 2:
                    return None

                day = base_day.model_copy(deep=True)
                day.title = title
                day.tasks = tasks[:4]

                goal_text = str(raw_day.get("goal") or "").strip()
                if goal_text:
                    day.goal = goal_text

                task_type = str(raw_day.get("task_type") or "").strip()
                if task_type in {"kickoff", "study", "practice", "review", "recap", "project", "learn"}:
                    day.task_type = task_type

                difficulty = str(raw_day.get("difficulty") or "").strip()
                if difficulty:
                    day.difficulty = difficulty

                estimated_minutes = self._safe_int(raw_day.get("estimated_minutes"))
                if estimated_minutes is not None and estimated_minutes > 0:
                    day.estimated_minutes = estimated_minutes

                review_of = [str(item).strip() for item in raw_day.get("review_of", []) if str(item).strip()]
                if review_of:
                    day.review_of = review_of

                merged_days.append(day)

            merged_days.sort(key=lambda item: item.day_index or 0)
            return merged_days
        except Exception:  # noqa: BLE001
            logger.exception("PlanAgent 按周调用 LLM 失败，将对该周使用规则计划兜底。week_index=%s", week.week_index)
            return None

    def _try_llm(self, goal: GoalRequest, goal_structure: GoalPlanStructure | None) -> PlanResponse | None:
        topics_text = "\n".join(
            f"{topic.order}. {topic.name}: {topic.description}" for topic in (goal_structure.topics if goal_structure else [])
        )
        prompt = f"""
你是一名学习计划编排助手。请根据目标生成 7 天的示例周计划，严格输出 JSON。

格式：
{{
  "title": "计划标题",
  "days": [
    {{"day_offset": 0, "title": "标题", "tasks": ["任务1", "任务2"], "task_type": "study"}}
  ]
}}

要求：
- 输出 7 天。
- task_type 只能是 kickoff / study / practice / review / recap。
- 每天 2 到 4 条任务。

目标：{goal.goal_text}
学习周期：{goal.duration_weeks} 周
每天时长：{goal.hours_per_day} 小时
基础水平：{goal.level}
主题列表：
{topics_text}
"""
        try:
            raw = ask_llm(prompt)
            if not raw or not raw.strip():
                return None
            payload = json.loads(self._extract_json_from_text(raw))
            raw_days = payload.get("days", [])
            if not raw_days:
                return None
            today = date.today()
            days: list[PlanDay] = []
            for index, item in enumerate(raw_days, start=1):
                offset = int(item.get("day_offset") or (index - 1))
                tasks = [str(task).strip() for task in item.get("tasks", []) if str(task).strip()]
                if not tasks:
                    continue
                days.append(
                    PlanDay(
                        date=today + timedelta(days=offset),
                        title=str(item.get("title") or f"第 {index} 天学习任务").strip(),
                        tasks=tasks,
                        task_type=str(item.get("task_type") or "study"),
                        day_index=index,
                        week_index=1,
                        estimated_minutes=min(180, max(30, goal.hours_per_day * 60)),
                    )
                )
            if not days:
                return None
            return PlanResponse(
                plan_id=str(uuid4()),
                title=str(payload.get("title") or f"{goal.goal_text} 学习计划").strip(),
                start_date=days[0].date,
                end_date=days[-1].date,
                days=days,
            )
        except Exception:  # noqa: BLE001
            logger.exception("PlanAgent 调用 LLM 失败，将使用规则计划兜底。")
            return None

    def _fallback_plan(self, goal: GoalRequest, goal_structure: GoalPlanStructure | None) -> PlanResponse:
        topics = [topic.name for topic in (goal_structure.topics if goal_structure else [])]
        if not topics:
            topics = [
                f"{goal.goal_text} 基础理解",
                f"{goal.goal_text} 核心方法",
                f"{goal.goal_text} 练习巩固",
                f"{goal.goal_text} 复盘输出",
            ]
        weeks = [
            WeeklyPlan(
                week_index=1,
                phase_id="phase-1",
                theme=f"{goal.goal_text} 启动周",
                focus_topics=topics[: min(3, len(topics))],
                target_hours=max(1, goal.hours_per_day * 7),
                milestone=goal.final_deliverable or "完成一轮具备实际目标感的入门计划",
                review_strategy="第 6 天复习，第 7 天总结",
            )
        ]
        phases = [
            LearningPhase(
                phase_id="phase-1",
                title="启动阶段",
                goal=f"帮助学习者围绕“{goal.goal_text}”建立节奏并快速开始。",
                weeks=1,
                focus_topics=weeks[0].focus_topics,
                expected_outcome=goal.final_deliverable or "完成基础学习与一轮复盘。",
            )
        ]
        return self._build_plan_from_schedule(goal, goal_structure, phases, weeks)

    def _build_day_content(
        self,
        day_type: str,
        topic_name: str,
        topic: GoalTopic | None,
        week: WeeklyPlan,
        phase: LearningPhase | None,
        goal: GoalRequest,
        local_day: int,
    ) -> tuple[str, list[str], list[str]]:
        phase_title = phase.title if phase is not None else "当前阶段"
        practice_type = (topic.practice_type if topic and topic.practice_type else None) or self._infer_practice_type(goal, topic_name)
        milestone_hint = topic.milestone_hint if topic and topic.milestone_hint else f"能够把 {topic_name} 真正应用到学习目标中。"

        if day_type == "kickoff":
            return (
                f"{topic_name} 入门与准备",
                [
                    f"明确 {topic_name} 在整体目标“{goal.goal_text}”中的位置，写下今天要解决的 2 到 3 个问题。",
                    f"梳理学习 {topic_name} 需要的前置知识、资料或工具，并安排接下来 {goal.hours_per_day} 小时的学习时间块。",
                    f"快速浏览 {topic_name} 的核心概念与常见场景，建立今天的学习地图。",
                ],
                [],
            )
        if day_type == "study":
            return (
                f"系统学习：{topic_name}",
                self._build_study_tasks(topic_name, practice_type, goal),
                [],
            )
        if day_type == "practice":
            return (
                f"专项练习：{topic_name}",
                self._build_practice_tasks(topic_name, practice_type, phase_title, milestone_hint),
                [],
            )
        if day_type == "review":
            review_topics = week.focus_topics[: min(2, len(week.focus_topics))]
            review_text = "、".join(review_topics) if review_topics else topic_name
            return (
                f"本周复习：{week.theme}",
                [
                    f"回顾本周涉及的主题：{review_text}，补齐仍然模糊的概念。",
                    "整理本周最容易出错的点，并写出对应的修正办法或记忆线索。",
                    "脱离资料复述本周关键知识，确认自己已经能说清思路与步骤。",
                ],
                review_topics,
            )
        return (
            f"输出与复盘：{week.theme}",
            [
                f"总结本周前 {local_day} 天的学习收获，提炼 3 条最重要的结论。",
                self._build_output_task(topic_name, practice_type, goal),
                f"对照本周里程碑“{week.milestone or phase_title}”和主题目标“{milestone_hint}”做一次自查。",
            ],
            week.focus_topics[: min(2, len(week.focus_topics))],
        )

    @staticmethod
    def _build_daily_goal(topic_name: str, week: WeeklyPlan, topic: GoalTopic | None) -> str:
        if topic and topic.milestone_hint:
            return topic.milestone_hint
        return f"围绕 {topic_name} 推进本周主题“{week.theme}”。"

    @staticmethod
    def _build_study_tasks(topic_name: str, practice_type: str, goal: GoalRequest) -> list[str]:
        if practice_type in {"coding", "project"}:
            return [
                f"系统学习 {topic_name} 的核心概念、关键流程和常见用法，先建立完整认知框架。",
                f"结合官方文档或示例代码跑通一个最小案例，记录关键步骤与易错点。",
                f"把今天关于 {topic_name} 的学习内容整理成笔记，沉淀后续练习时要重点关注的问题。",
            ]
        if practice_type == "quiz":
            return [
                f"梳理 {topic_name} 的核心概念、判断标准和常见题型。",
                f"整理一页知识卡片，记录 {topic_name} 最容易混淆的点。",
                f"先完成一组基础例题，检查自己对 {topic_name} 是否已经真正理解。",
            ]
        return [
            f"系统学习 {topic_name} 的核心内容，优先理解关键概念和典型场景。",
            f"整理一页学习笔记，记录 {topic_name} 的定义、步骤或使用方法。",
            f"围绕 {topic_name} 做一个小型理解练习，确认学习内容已经吸收。",
        ]

    @staticmethod
    def _build_practice_tasks(topic_name: str, practice_type: str, phase_title: str, milestone_hint: str) -> list[str]:
        if practice_type == "project":
            return [
                f"围绕 {topic_name} 实现一个和当前目标直接相关的小模块或小项目片段。",
                "记录实现过程中遇到的问题、报错或卡点，并尝试独立定位原因。",
                f"把练习结果与阶段目标“{phase_title}”和主题目标“{milestone_hint}”对照，补齐薄弱点。",
            ]
        if practice_type == "coding":
            return [
                f"围绕 {topic_name} 写一个最小可运行示例，重点关注输入、输出和边界情况。",
                "把练习过程中出现的问题记录下来，并尝试自己定位和修复。",
                f"把练习结果与阶段目标“{phase_title}”对照，确认自己已经具备独立动手能力。",
            ]
        if practice_type == "quiz":
            return [
                f"围绕 {topic_name} 完成一组针对性练习题，优先验证自己对核心概念的掌握程度。",
                "统计错题和犹豫题，给每一类问题写下原因和改进方法。",
                f"根据“{milestone_hint}”回看今天的题目，确认知识点已经能迁移使用。",
            ]
        return [
            f"围绕 {topic_name} 完成一个应用练习，把今天学到的方法真正用起来。",
            "把练习过程中的问题记录下来，并尝试给出自己的修正方案。",
            f"将练习结果与阶段目标“{phase_title}”对照，补齐还不稳定的部分。",
        ]

    @staticmethod
    def _build_output_task(topic_name: str, practice_type: str, goal: GoalRequest) -> str:
        deliverable = goal.final_deliverable or "一份可展示的学习输出"
        if practice_type in {"coding", "project"}:
            return f"围绕 {topic_name} 完成一次小输出，例如代码演示、接口 demo 或阶段性项目进展，逐步靠近“{deliverable}”。"
        if practice_type == "quiz":
            return f"围绕 {topic_name} 做一次错题复盘和知识总结，形成一份可回看的题型笔记，服务于“{deliverable}”。"
        return f"围绕 {topic_name} 完成一次小输出，例如总结、思维导图或案例整理，逐步靠近“{deliverable}”。"

    @staticmethod
    def _infer_practice_type(goal: GoalRequest, topic_name: str) -> str:
        text = f"{goal.goal_text} {topic_name}".lower()
        if any(keyword in text for keyword in ["项目", "实战", "作品", "系统", "网站", "接口"]):
            return "project"
        if any(keyword in text for keyword in ["java", "python", "spring", "vue", "react", "编程", "开发"]):
            return "coding"
        if any(keyword in text for keyword in ["面试", "刷题", "练习题", "考试", "题"]):
            return "quiz"
        return "reading"

    def _build_week_plan_prompt(
        self,
        goal: GoalRequest,
        goal_structure: GoalPlanStructure | None,
        phase: LearningPhase | None,
        week: WeeklyPlan,
        week_days: list[PlanDay],
    ) -> str:
        focus_topics = set(week.focus_topics)
        related_topics = [
            topic for topic in (goal_structure.topics if goal_structure else [])
            if topic.name in focus_topics
        ]
        topics_text = "\n".join(
            f"- {topic.order}. {topic.name} | 难度={topic.difficulty or goal.level} | 练习方式={topic.practice_type or 'reading'} | 里程碑={topic.milestone_hint or '无'}"
            for topic in related_topics
        )
        phase_text = (
            f"阶段={phase.title} | 目标={phase.goal} | focus={','.join(phase.focus_topics)} | outcome={phase.expected_outcome or '无'}"
            if phase is not None
            else "未提供"
        )
        week_days_text = "\n".join(
            f"- day_index={day.day_index}, date={day.date}, task_type={day.task_type}, title={day.title}, goal={day.goal}, difficulty={day.difficulty}, estimated_minutes={day.estimated_minutes}"
            for day in week_days
        )
        return f"""
你是一名高级学习计划编排助手。请只针对当前这一周生成更像真实可执行计划的“日计划内容”。

请严格返回 JSON，不要输出任何解释。

返回格式：
{{
  "days": [
    {{
      "day_index": 1,
      "title": "当日标题",
      "tasks": ["任务1", "任务2", "任务3"],
      "goal": "当天目标",
      "task_type": "kickoff",
      "difficulty": "beginner",
      "estimated_minutes": 120,
      "review_of": []
    }}
  ]
}}

要求：
- 只输出当前这一周的 {len(week_days)} 天。
- day_index 必须与输入完全一致，不能新增或删除。
- title 必须具体，不要使用“预热与目标对齐”“深入学习”“应用练习”这类泛化模板句式。
- tasks 每天 2 到 4 条，必须具体可执行，尽量贴近目标场景。
- coding / project 类任务优先写代码实现、接口联调、项目片段、调试、总结。
- review / recap 天重点写复盘、总结、错题或问题归因、阶段输出。
- task_type 只能是 kickoff / study / practice / review / recap / project / learn。
- 全部用简体中文。

整体目标：{goal.goal_text}
学习周期：{goal.duration_weeks} 周
每天学习时长：{goal.hours_per_day} 小时
基础水平：{goal.level}
目标方向：{goal.target_role or '未提供'}
学习约束：{goal.constraints or []}
最终产出：{goal.final_deliverable or '未提供'}

当前阶段：{phase_text}
当前周次：第 {week.week_index} 周
本周主题：{week.theme}
本周聚焦主题：{week.focus_topics}
本周里程碑：{week.milestone or '未提供'}
本周复习策略：{week.review_strategy or '未提供'}

本周相关主题：
{topics_text or '- 无'}

本周日计划骨架：
{week_days_text}
"""

    @staticmethod
    def _group_days_by_week(days: list[PlanDay]) -> dict[int, list[PlanDay]]:
        grouped: dict[int, list[PlanDay]] = {}
        for day in days:
            if day.week_index is None:
                continue
            grouped.setdefault(day.week_index, []).append(day)
        return grouped

    @staticmethod
    def _build_plan_response(goal: GoalRequest, days: list[PlanDay]) -> PlanResponse:
        today = date.today()
        title = goal.target_role or f"{goal.goal_text} 学习计划"
        return PlanResponse(
            plan_id=str(uuid4()),
            title=title,
            start_date=days[0].date if days else today,
            end_date=days[-1].date if days else today,
            days=days,
        )

    @staticmethod
    def _should_use_llm() -> bool:
        config = get_effective_llm_config()
        return bool(config.get("enableLlmPlan", False))

    @staticmethod
    def _extract_json_from_text(text: str) -> str:
        if not text:
            return text
        start = text.find("{")
        end = text.rfind("}")
        if start == -1 or end == -1 or end <= start:
            return text
        return text[start : end + 1]

    @staticmethod
    def _safe_int(value: object) -> int | None:
        try:
            if value is None:
                return None
            return int(value)
        except Exception:  # noqa: BLE001
            return None

    @staticmethod
    def _resolve_model_name() -> str | None:
        config = get_effective_llm_config()
        configured = config.get("defaultModel")
        return str(configured) if configured else None

    def _log_call(
        self,
        trace_id: str | None,
        request_payload: str,
        plan: PlanResponse,
        start: float,
        model_name: str | None,
    ) -> None:
        try:
            save_agent_call(
                agent_name="PlanAgent",
                trace_id=trace_id,
                request_payload=request_payload,
                response_payload=json.dumps(plan.model_dump(), ensure_ascii=False),
                model_name=model_name,
                duration_ms=int((time.perf_counter() - start) * 1000),
            )
        except Exception:  # noqa: BLE001
            logger.debug("保存 PlanAgent 调用日志失败，但不影响主流程。")