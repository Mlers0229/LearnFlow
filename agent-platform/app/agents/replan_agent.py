from __future__ import annotations

import json
import logging
import time
from datetime import date, timedelta
from uuid import uuid4

from app.agents.plan_validator_agent import PlanValidatorAgent
from app.core.llm import ask_llm
from app.db import save_agent_call
from app.models.goal import GoalRequest
from app.models.plan import PlanDay, PlanReplanRequest, PlanResponse, PlanResponseV2

logger = logging.getLogger(__name__)


class ReplanAgent:
    """根据当前计划和扰动原因，对后续学习安排做 AI 重规划。"""

    def __init__(self) -> None:
        self.validator_agent = PlanValidatorAgent()

    async def replan(self, req: PlanReplanRequest, trace_id: str | None = None) -> PlanResponseV2:
        start = time.perf_counter()
        real_trace_id = trace_id or str(uuid4())
        request_payload = json.dumps(req.model_dump(mode="json"), ensure_ascii=False)

        ai_result = await self._try_llm(req)
        plan = self._build_replanned_plan(req, ai_result)
        validation_report = self.validator_agent.validate(
            PlanResponse(
                plan_id=plan.plan_id,
                title=plan.title,
                start_date=plan.start_date,
                end_date=plan.end_date,
                days=plan.days,
            ),
            goal=self._build_goal(req),
            goal_structure=req.current_plan.goal_blueprint,
            trace_id=real_trace_id,
        )

        response = PlanResponseV2(
            plan_id=plan.plan_id,
            title=plan.title,
            start_date=plan.start_date,
            end_date=plan.end_date,
            days=plan.days,
            trace_id=real_trace_id,
            goal_blueprint=req.current_plan.goal_blueprint,
            phases=req.current_plan.phases,
            weeks=req.current_plan.weeks,
            validation_report=validation_report,
        )

        try:
            save_agent_call(
                agent_name="ReplanAgent",
                trace_id=real_trace_id,
                request_payload=request_payload,
                response_payload=json.dumps(response.model_dump(mode="json"), ensure_ascii=False),
                model_name=None if ai_result is not None else "-",
                duration_ms=int((time.perf_counter() - start) * 1000),
            )
        except Exception:  # noqa: BLE001
            logger.debug("保存 ReplanAgent 调用日志失败，但不影响主流程。")

        return response

    def _build_goal(self, req: PlanReplanRequest) -> GoalRequest:
        return GoalRequest(
            goal_text=req.goal_text,
            duration_weeks=req.duration_weeks,
            hours_per_day=req.hours_per_day,
            level=req.level,
            target_role=req.target_role,
            preferred_style=req.preferred_style,
            constraints=req.constraints,
            final_deliverable=req.final_deliverable,
        )

    async def _try_llm(self, req: PlanReplanRequest) -> dict[int, dict] | None:
        current_days = sorted(req.current_plan.days, key=lambda item: item.day_index or 0)
        lines = []
        for day in current_days:
            tasks = "；".join(day.tasks)
            lines.append(
                f"day_index={day.day_index}, date={day.date}, status={day.status}, title={day.title}, tasks={tasks}"
            )

        prompt = f"""
你是学习计划重规划助手。请根据当前计划、触发原因和顺延天数，只为受影响的未完成学习日生成新的安排。
请严格返回 JSON，不要输出额外说明。

返回格式：
{{
  "title": "新的计划标题，可与原标题相同",
  "days": [
    {{
      "day_index": 3,
      "title": "新的主题标题",
      "tasks": ["任务1", "任务2", "任务3"],
      "goal": "当天目标",
      "task_type": "study",
      "difficulty": "intermediate",
      "review_of": ["主题A"],
      "estimated_minutes": 90
    }}
  ]
}}

规则：
- 只返回 day_index >= {req.trigger_day_index} 且 status != completed 的学习日。
- 不要返回已完成学习日。
- 保持总天数不变。
- tasks 必须是 2 到 4 条具体任务。
- task_type 只能是 kickoff / study / practice / review / recap / project / learn。
- 请结合原因重新安排后续内容，避免只是原样重复。

学习目标：{req.goal_text}
基础水平：{req.level}
每天时长：{req.hours_per_day} 小时
顺延天数：{req.delay_days}
重规划原因：{req.reason or '未提供'}
当前计划标题：{req.current_plan.title}
当前计划：
"""
        prompt += "\n".join(lines)

        try:
            raw = await ask_llm(prompt)
            payload = json.loads(self._extract_json(raw))
            raw_days = payload.get("days", [])
            if not isinstance(raw_days, list) or not raw_days:
                return None

            result: dict[int, dict] = {}
            for item in raw_days:
                day_index = item.get("day_index")
                if not isinstance(day_index, int):
                    continue
                item_tasks = [str(task).strip() for task in item.get("tasks", []) if str(task).strip()]
                if not item_tasks:
                    continue
                result[day_index] = {
                    "title": str(item.get("title") or "").strip(),
                    "tasks": item_tasks,
                    "goal": str(item.get("goal") or "").strip() or None,
                    "task_type": str(item.get("task_type") or "").strip() or None,
                    "difficulty": str(item.get("difficulty") or "").strip() or None,
                    "review_of": [str(v).strip() for v in item.get("review_of", []) if str(v).strip()],
                    "estimated_minutes": item.get("estimated_minutes"),
                }
            return result or None
        except Exception:  # noqa: BLE001
            logger.exception("ReplanAgent 调用 LLM 失败，将回退到规则重排。")
            return None

    def _build_replanned_plan(self, req: PlanReplanRequest, ai_result: dict[int, dict] | None) -> PlanResponse:
        current_days = sorted(req.current_plan.days, key=lambda item: item.day_index or 0)
        trigger_day = next(
            (day for day in current_days if day.day_index == req.trigger_day_index),
            None,
        )
        if trigger_day is None:
            raise ValueError("trigger_day_index 不存在")

        replanned_days: list[PlanDay] = []
        previous_date = None
        for day in current_days:
            if day.day_index is not None and day.day_index < req.trigger_day_index and day.date is not None:
                previous_date = day.date

        base_trigger_date = trigger_day.date or date.today()
        cursor = previous_date or (base_trigger_date - timedelta(days=1))
        next_date = base_trigger_date + timedelta(days=req.delay_days)

        for day in current_days:
            day_copy = day.model_copy(deep=True)
            day_index = day.day_index or 0

            if day_index < req.trigger_day_index:
                replanned_days.append(day_copy)
                if day_copy.date is not None and day_copy.date > cursor:
                    cursor = day_copy.date
                continue

            if (day.status or "").lower() == "completed":
                replanned_days.append(day_copy)
                if day_copy.date is not None and day_copy.date > cursor:
                    cursor = day_copy.date
                continue

            if next_date <= cursor:
                next_date = cursor + timedelta(days=1)

            update = ai_result.get(day_index) if ai_result else None
            if update:
                if update.get("title"):
                    day_copy.title = update["title"]
                if update.get("tasks"):
                    day_copy.tasks = update["tasks"]
                day_copy.goal = update.get("goal") or day_copy.goal
                day_copy.task_type = update.get("task_type") or day_copy.task_type
                day_copy.difficulty = update.get("difficulty") or day_copy.difficulty
                day_copy.review_of = update.get("review_of") or day_copy.review_of
                estimated = update.get("estimated_minutes")
                if isinstance(estimated, int) and estimated > 0:
                    day_copy.estimated_minutes = estimated

            day_copy.date = next_date
            day_copy.status = "delayed" if day_index == req.trigger_day_index else "not_started"
            replanned_days.append(day_copy)
            cursor = next_date
            next_date = next_date + timedelta(days=1)

        start_date = min((day.date for day in replanned_days if day.date is not None), default=base_trigger_date)
        end_date = max((day.date for day in replanned_days if day.date is not None), default=base_trigger_date)
        return PlanResponse(
            plan_id=req.current_plan.plan_id,
            title=req.current_plan.title,
            start_date=start_date,
            end_date=end_date,
            days=replanned_days,
        )

    @staticmethod
    def _extract_json(text: str) -> str:
        if not text:
            return text
        start = text.find("{")
        end = text.rfind("}")
        if start == -1 or end == -1 or end <= start:
            return text
        return text[start : end + 1]
