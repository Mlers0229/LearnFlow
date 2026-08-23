from uuid import uuid4

from fastapi import APIRouter

from app.agents.detail_plan_agent import DetailPlanAgent
from app.agents.plan_validator_agent import PlanValidatorAgent
from app.agents.replan_agent import ReplanAgent
from app.models.goal import GoalRequest
from app.models.plan import (
    DayRefineRequest,
    DayRefineResponse,
    PlanReplanRequest,
    PlanResponse,
    PlanResponseV2,
    PlanValidationReport,
)
from app.observability import agent_span, current_trace_id
from app.workflow.study_plan import StatefulStudyOrchestrator

router = APIRouter(tags=["plan"])

orchestrator = StatefulStudyOrchestrator()
detail_agent = DetailPlanAgent()
validator_agent = PlanValidatorAgent()
replan_agent = ReplanAgent()


@router.post("/plan", response_model=PlanResponse)
async def generate_plan(goal: GoalRequest) -> PlanResponse:
    """兼容旧接口，仅返回基础计划结构。"""
    trace_id = current_trace_id() or str(uuid4())
    return await orchestrator.run_study_plan(goal, trace_id=trace_id)


@router.post("/v2/plan", response_model=PlanResponseV2)
async def generate_plan_v2(goal: GoalRequest) -> PlanResponseV2:
    """返回包含蓝图、阶段、周计划和校验报告的完整计划。"""
    trace_id = current_trace_id() or str(uuid4())
    return await orchestrator.run_study_plan_v2(goal, trace_id=trace_id)


@router.post("/v2/plan/validate", response_model=PlanValidationReport)
async def validate_plan(payload: PlanResponseV2) -> PlanValidationReport:
    """对一份已有计划进行独立校验。"""
    trace_id = payload.trace_id or current_trace_id() or str(uuid4())
    with agent_span("PlanValidatorAgent", "plan-validator-rules-v1", operation="validate"):
        return validator_agent.validate(
            PlanResponse(
                plan_id=payload.plan_id,
                title=payload.title,
                start_date=payload.start_date,
                end_date=payload.end_date,
                days=payload.days,
            ),
            goal_structure=payload.goal_blueprint,
            trace_id=trace_id,
        )


@router.post("/v2/plan/replan", response_model=PlanResponseV2)
async def replan_plan_v2(payload: PlanReplanRequest) -> PlanResponseV2:
    """对当前计划做 AI 重规划。"""
    trace_id = current_trace_id() or str(uuid4())
    with agent_span("ReplanAgent", "replan-v1", operation="replan"):
        return await replan_agent.replan(payload, trace_id=trace_id)


@router.post("/plan/day/refine", response_model=DayRefineResponse)
async def refine_day(payload: DayRefineRequest) -> DayRefineResponse:
    """细化某一天的学习任务。"""
    trace_id = current_trace_id() or str(uuid4())
    with agent_span("DetailPlanAgent", "detail-plan-v1", operation="refine"):
        return await detail_agent.refine_day(payload, trace_id=trace_id)
