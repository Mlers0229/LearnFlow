from datetime import date as Date
from typing import List, Optional
from uuid import UUID

from pydantic import BaseModel, Field

from app.models.adaptive import AdaptiveContext
from app.models.goal import GoalPlanStructure


class PlanDay(BaseModel):
    """学习计划中“某一天”的任务概要。"""

    date: Date = Field(..., description="日期")
    title: str = Field(..., description="当日学习主题")
    tasks: List[str] = Field(default_factory=list, description="任务列表")
    status: str = Field(default="not_started", description="任务状态")
    day_index: Optional[int] = Field(default=None, ge=1, description="整份计划中的天序号")
    week_index: Optional[int] = Field(default=None, ge=1, description="所属周次")
    phase_id: Optional[str] = Field(default=None, description="所属阶段 ID")
    goal: Optional[str] = Field(default=None, description="当天目标")
    estimated_minutes: Optional[int] = Field(default=None, ge=1, description="预计学习分钟数")
    topic_ids: List[str] = Field(default_factory=list, description="关联主题 ID")
    task_type: Optional[str] = Field(default=None, description="当天任务类型")
    difficulty: Optional[str] = Field(default=None, description="当天任务难度")
    review_of: List[str] = Field(default_factory=list, description="当天复习的主题")


class LearningPhase(BaseModel):
    """学习阶段，如入门期、核心期、实战期。"""

    phase_id: str = Field(..., description="阶段 ID")
    title: str = Field(..., description="阶段标题")
    goal: str = Field(..., description="阶段目标")
    weeks: int = Field(..., ge=1, description="阶段持续周数")
    focus_topics: List[str] = Field(default_factory=list, description="阶段聚焦主题")
    expected_outcome: Optional[str] = Field(default=None, description="阶段产出")


class WeeklyPlan(BaseModel):
    """按周组织的计划结构。"""

    week_index: int = Field(..., ge=1, description="第几周")
    phase_id: str = Field(..., description="所属阶段 ID")
    theme: str = Field(..., description="本周主题")
    focus_topics: List[str] = Field(default_factory=list, description="本周聚焦主题")
    target_hours: int = Field(..., ge=1, description="本周目标学习时长")
    milestone: Optional[str] = Field(default=None, description="本周里程碑")
    review_strategy: Optional[str] = Field(default=None, description="本周复习策略")


class SchedulePlan(BaseModel):
    """SchedulerAgent 的输出。"""

    phases: List[LearningPhase] = Field(default_factory=list, description="阶段计划")
    weeks: List[WeeklyPlan] = Field(default_factory=list, description="周计划")


class PlanValidationIssue(BaseModel):
    """计划校验问题。"""

    code: str = Field(..., description="问题代码")
    message: str = Field(..., description="问题说明")
    severity: str = Field(..., description="严重程度")
    day_index: Optional[int] = Field(default=None, ge=1, description="关联天序号")


class PlanValidationReport(BaseModel):
    """计划校验报告。"""

    is_valid: bool = Field(..., description="计划是否通过基础校验")
    issues: List[PlanValidationIssue] = Field(default_factory=list, description="错误级问题")
    warnings: List[PlanValidationIssue] = Field(default_factory=list, description="警告级问题")
    coverage_score: int = Field(default=100, ge=0, le=100, description="主题覆盖率评分")
    repetition_score: int = Field(default=100, ge=0, le=100, description="重复度评分")
    load_balance_score: int = Field(default=100, ge=0, le=100, description="负载均衡评分")
    suggested_fixes: List[str] = Field(default_factory=list, description="建议修复项")


class PlanResponse(BaseModel):
    """学习计划生成接口的旧响应体。"""

    plan_id: str = Field(..., description="计划标识")
    title: str = Field(..., description="计划标题")
    start_date: Date = Field(..., description="计划开始日期")
    end_date: Date = Field(..., description="计划结束日期")
    days: List[PlanDay] = Field(default_factory=list, description="每日任务列表")


class PlanResponseV2(PlanResponse):
    """包含编排上下文的增强版计划结构。"""

    trace_id: Optional[str] = Field(default=None, description="一次完整编排链路的追踪 ID")
    goal_blueprint: Optional[GoalPlanStructure] = Field(default=None, description="结构化目标蓝图")
    phases: List[LearningPhase] = Field(default_factory=list, description="阶段计划")
    weeks: List[WeeklyPlan] = Field(default_factory=list, description="周计划")
    validation_report: Optional[PlanValidationReport] = Field(default=None, description="计划校验报告")
    workflow_id: Optional[UUID] = Field(default=None, description="持久工作流 ID")
    workflow_status: Optional[str] = Field(default=None, description="持久工作流状态")
    completed_node: Optional[str] = Field(default=None, description="当前工作流节点")
    state_schema_version: Optional[int] = Field(default=None, description="工作流状态 Schema 版本")
    adaptation: Optional[AdaptiveContext] = Field(default=None, description="本次计划采用的适应性策略")


class PlanReplanRequest(BaseModel):
    """动态重规划请求。"""

    goal_text: str = Field(..., description="整体学习目标")
    duration_weeks: int = Field(..., ge=1, le=52, description="学习周期，单位周")
    hours_per_day: int = Field(..., ge=1, le=10, description="每天学习时长，单位小时")
    level: str = Field(..., description="当前基础水平")
    current_plan: PlanResponseV2 = Field(..., description="当前计划快照")
    trigger_day_index: int = Field(..., ge=1, description="触发重规划的天序号")
    delay_days: int = Field(default=1, ge=1, le=14, description="需要顺延的天数")
    reason: Optional[str] = Field(default=None, description="触发重规划的原因")
    target_role: Optional[str] = Field(default=None, description="目标角色或方向")
    preferred_style: Optional[str] = Field(default=None, description="偏好的学习方式")
    constraints: List[str] = Field(default_factory=list, description="学习约束")
    final_deliverable: Optional[str] = Field(default=None, description="期望最终产出")
    adaptive_context: Optional[AdaptiveContext] = Field(default=None, description="服务端确定的适应性策略")


class DayRefineRequest(BaseModel):
    """细化某一天学习任务的入参。"""

    title: str = Field(..., description="当日学习主题")
    current_tasks: List[str] = Field(default_factory=list, description="当前的粗略任务列表")
    goal_text: Optional[str] = Field(default=None, description="整体学习目标描述")
    hours_per_day: Optional[int] = Field(default=None, description="当天可用学习时间")
    level: Optional[str] = Field(default=None, description="学习者当前基础水平")
    day_index: Optional[int] = Field(default=None, ge=1, description="整份计划中的天序号")
    week_index: Optional[int] = Field(default=None, ge=1, description="所属周次")
    phase_id: Optional[str] = Field(default=None, description="所属阶段 ID")
    phase_title: Optional[str] = Field(default=None, description="所属阶段标题")
    previous_days_summary: Optional[str] = Field(default=None, description="前置上下文摘要")
    next_day_title: Optional[str] = Field(default=None, description="下一天主题")


class DayRefineResponse(BaseModel):
    """细化后的当日任务列表。"""

    tasks: List[str] = Field(default_factory=list, description="细化后的任务列表")