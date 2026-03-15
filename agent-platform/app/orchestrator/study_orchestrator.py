from uuid import uuid4

from app.agents.goal_agent import GoalAgent
from app.agents.plan_agent import PlanAgent
from app.agents.plan_validator_agent import PlanValidatorAgent
from app.agents.scheduler_agent import SchedulerAgent
from app.models.goal import GoalRequest
from app.models.plan import PlanResponse, PlanResponseV2


class StudyOrchestrator:
    """学习计划编排器。"""

    def __init__(self) -> None:
        self.goal_agent = GoalAgent()
        self.scheduler_agent = SchedulerAgent()
        self.plan_agent = PlanAgent()
        self.plan_validator_agent = PlanValidatorAgent()

    def run_study_plan(self, goal: GoalRequest, trace_id: str | None = None) -> PlanResponse:
        """兼容旧接口，只返回基础计划结构。"""
        result = self.run_study_plan_v2(goal, trace_id=trace_id)
        return PlanResponse(
            plan_id=result.plan_id,
            title=result.title,
            start_date=result.start_date,
            end_date=result.end_date,
            days=result.days,
        )

    def run_study_plan_v2(self, goal: GoalRequest, trace_id: str | None = None) -> PlanResponseV2:
        """新的完整编排流程。"""
        real_trace_id = trace_id or str(uuid4())
        goal_blueprint = self.goal_agent.run(goal, trace_id=real_trace_id)
        schedule = self.scheduler_agent.build_schedule(goal, goal_blueprint, trace_id=real_trace_id)
        plan = self.plan_agent.run(
            goal,
            goal_structure=goal_blueprint,
            phases=schedule.phases,
            weeks=schedule.weeks,
            trace_id=real_trace_id,
        )
        validation_report = self.plan_validator_agent.validate(
            plan,
            goal=goal,
            goal_structure=goal_blueprint,
            trace_id=real_trace_id,
        )
        return PlanResponseV2(
            plan_id=plan.plan_id,
            title=plan.title,
            start_date=plan.start_date,
            end_date=plan.end_date,
            days=plan.days,
            trace_id=real_trace_id,
            goal_blueprint=goal_blueprint,
            phases=schedule.phases,
            weeks=schedule.weeks,
            validation_report=validation_report,
        )
