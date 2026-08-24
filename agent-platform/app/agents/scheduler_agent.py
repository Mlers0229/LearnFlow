import json
import logging
import math
import time

from app.db import save_agent_call
from app.models.goal import GoalPlanStructure, GoalRequest
from app.models.plan import LearningPhase, SchedulePlan, WeeklyPlan

logger = logging.getLogger(__name__)


PHASE_TITLES = [
    "入门建立期",
    "核心巩固期",
    "实践提升期",
    "复习收束期",
]


class SchedulerAgent:
    """把学习蓝图分配到阶段和周计划。"""

    def build_schedule(
        self,
        goal: GoalRequest,
        goal_structure: GoalPlanStructure,
        trace_id: str | None = None,
    ) -> SchedulePlan:
        start = time.perf_counter()
        request_payload = json.dumps(
            {
                "goal": goal.model_dump(mode="json"),
                "goal_blueprint": goal_structure.model_dump(mode="json"),
            },
            ensure_ascii=False,
        )

        phases = self._build_phases(goal, goal_structure)
        weeks = self._build_weeks(goal, phases)
        schedule = SchedulePlan(phases=phases, weeks=weeks)

        try:
            save_agent_call(
                agent_name="SchedulerAgent",
                trace_id=trace_id,
                request_payload=request_payload,
                response_payload=json.dumps(schedule.model_dump(mode="json"), ensure_ascii=False),
                model_name="-",
                duration_ms=int((time.perf_counter() - start) * 1000),
            )
        except Exception:  # noqa: BLE001
            logger.debug("保存 SchedulerAgent 调用日志失败，但不影响主流程。")
        return schedule

    def _build_phases(self, goal: GoalRequest, goal_structure: GoalPlanStructure) -> list[LearningPhase]:
        topics = goal_structure.topics
        if not topics:
            return [
                LearningPhase(
                    phase_id="phase-1",
                    title="基础推进期",
                    goal="完成基础学习与初步练习。",
                    weeks=max(1, goal.duration_weeks),
                    focus_topics=[goal.goal_text],
                    expected_outcome=goal.final_deliverable or "完成一轮系统学习。",
                )
            ]

        phase_count = min(4, max(2, math.ceil(goal.duration_weeks / 2)))
        phase_count = min(phase_count, len(topics), goal.duration_weeks)
        chunk_size = math.ceil(len(topics) / phase_count)
        phase_weeks = self._split_count(goal.duration_weeks, phase_count)

        phases: list[LearningPhase] = []
        for index in range(phase_count):
            topic_chunk = topics[index * chunk_size : (index + 1) * chunk_size]
            if not topic_chunk:
                continue
            phase_title = PHASE_TITLES[min(index, len(PHASE_TITLES) - 1)]
            focus_topics = [topic.name for topic in topic_chunk]
            expected_outcome = topic_chunk[-1].milestone_hint or f"完成 {focus_topics[-1]} 的学习输出。"
            phases.append(
                LearningPhase(
                    phase_id=f"phase-{index + 1}",
                    title=phase_title,
                    goal=f"围绕 {'、'.join(focus_topics)} 建立连续学习节奏。",
                    weeks=phase_weeks[index],
                    focus_topics=focus_topics,
                    expected_outcome=expected_outcome,
                )
            )
        return phases

    def _build_weeks(self, goal: GoalRequest, phases: list[LearningPhase]) -> list[WeeklyPlan]:
        weeks: list[WeeklyPlan] = []
        week_index = 1
        for phase in phases:
            for local_week in range(phase.weeks):
                focus_topics = self._rotate_topics(phase.focus_topics, local_week)
                is_last_week = local_week == phase.weeks - 1
                milestone = phase.expected_outcome if is_last_week else None
                weeks.append(
                    WeeklyPlan(
                        week_index=week_index,
                        phase_id=phase.phase_id,
                        theme=f"{phase.title} 第 {local_week + 1} 周",
                        focus_topics=focus_topics,
                        target_hours=max(1, goal.hours_per_day * 7),
                        milestone=milestone,
                        review_strategy="每周第 6 天进行复习，第 7 天进行总结或输出。",
                    )
                )
                week_index += 1
        return weeks

    @staticmethod
    def _split_count(total: int, parts: int) -> list[int]:
        base = total // parts
        remainder = total % parts
        values = []
        for index in range(parts):
            values.append(base + (1 if index < remainder else 0))
        return [max(1, value) for value in values]

    @staticmethod
    def _rotate_topics(topics: list[str], offset: int) -> list[str]:
        if not topics:
            return []
        if len(topics) == 1:
            return topics
        start = offset % len(topics)
        return topics[start:] + topics[:start]
