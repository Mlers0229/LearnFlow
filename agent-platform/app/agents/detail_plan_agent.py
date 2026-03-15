import json
import logging
import time
from typing import List

from app.core.llm import ask_llm
from app.db import save_agent_call
from app.models.plan import DayRefineRequest, DayRefineResponse

logger = logging.getLogger(__name__)


class DetailPlanAgent:
    """将单日粗粒度任务细化为更具体的可执行步骤。"""

    def refine_day(self, req: DayRefineRequest, trace_id: str | None = None) -> DayRefineResponse:
        prompt = f"""
你是一名学习规划教练，现在需要把某一天的粗略任务细化成更具体、可执行的步骤。

严格输出 JSON：
{{
  "tasks": [
    "任务 1",
    "任务 2",
    "任务 3"
  ]
}}

要求：
- 任务尽量具体，避免抽象描述。
- 结合所属阶段、当前周次、前一天上下文和下一天主题，保持连贯。
- 如果每天只有 1 小时，给出 3 到 5 条任务；如果超过 2 小时，可以给 5 到 8 条。
- 每条任务尽量是一句能直接执行的话。

整体目标：{req.goal_text or '未提供'}
当日主题：{req.title}
当前水平：{req.level or '未提供'}
每天学习时长：{req.hours_per_day or 1}
所属阶段：{req.phase_title or req.phase_id or '未提供'}
所属周次：{req.week_index or '未提供'}
当天序号：{req.day_index or '未提供'}
前置上下文：{req.previous_days_summary or '未提供'}
下一天主题：{req.next_day_title or '未提供'}
当前粗略任务：{req.current_tasks}
"""

        start = time.perf_counter()
        request_payload = json.dumps(req.model_dump(), ensure_ascii=False)

        try:
            content = ask_llm(prompt)
            raw = (content or "").strip()
            start_idx = raw.find("{")
            end_idx = raw.rfind("}")
            if start_idx == -1 or end_idx == -1 or end_idx <= start_idx:
                raise ValueError("llm response is not json")

            json_str = raw[start_idx : end_idx + 1]
            data = json.loads(json_str)
            raw_tasks = data.get("tasks", [])
            tasks: List[str] = [str(task).strip() for task in raw_tasks if str(task).strip()]
            if not tasks:
                raise ValueError("empty tasks from llm")

            self._log_call(trace_id, request_payload, json_str, start)
            return DayRefineResponse(tasks=tasks)
        except Exception as exc:  # noqa: BLE001
            logger.exception("细化当日任务失败，将使用规则兜底。", exc_info=exc)
            self._log_call(trace_id, request_payload, None, start)
            if req.current_tasks:
                return DayRefineResponse(tasks=self._expand_tasks(req))
            return DayRefineResponse(tasks=self._default_tasks(req))

    @staticmethod
    def _expand_tasks(req: DayRefineRequest) -> list[str]:
        refined: list[str] = []
        for task in req.current_tasks:
            refined.append(f"先明确“{task}”的完成标准，并准备对应材料。")
            refined.append(f"围绕“{task}”执行一次 20 到 30 分钟的专注学习或编码练习。")
        refined.append("把今天的关键收获记录成 3 条笔记，并标记仍不理解的点。")
        return refined[: max(3, min(8, len(refined)))]

    @staticmethod
    def _default_tasks(req: DayRefineRequest) -> list[str]:
        return [
            f"围绕主题“{req.title}”先快速回顾核心概念，列出今天要解决的 2 到 3 个问题。",
            f"选择一份与“{req.title}”直接相关的材料进行学习，并同步记录要点。",
            f"完成一个与“{req.title}”相关的小练习或示例。",
            "总结今天学到的内容，并写下明天继续推进时最需要关注的点。",
        ]

    @staticmethod
    def _log_call(
        trace_id: str | None,
        request_payload: str,
        response_payload: str | None,
        start: float,
    ) -> None:
        try:
            save_agent_call(
                agent_name="DetailPlanAgent",
                trace_id=trace_id,
                request_payload=request_payload,
                response_payload=response_payload,
                model_name=None,
                duration_ms=int((time.perf_counter() - start) * 1000),
            )
        except Exception:  # noqa: BLE001
            logger.debug("保存 DetailPlanAgent 调用日志失败，但不影响主流程。")
