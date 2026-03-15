from typing import List, Optional

from pydantic import BaseModel, Field


class GoalRequest(BaseModel):
    """
    学习目标请求。

    保持与现有 Java 后端兼容，并为后续更细的编排信息预留字段。
    """

    goal_text: str = Field(..., description="原始学习目标描述")
    duration_weeks: int = Field(..., ge=1, le=52, description="学习周期，单位周")
    hours_per_day: int = Field(..., ge=1, le=10, description="每天学习时长，单位小时")
    level: str = Field(..., description="当前基础水平")
    target_role: Optional[str] = Field(default=None, description="目标角色或方向")
    preferred_style: Optional[str] = Field(default=None, description="偏好的学习方式")
    constraints: List[str] = Field(default_factory=list, description="学习约束条件")
    final_deliverable: Optional[str] = Field(default=None, description="期望最终成果")


class GoalMilestone(BaseModel):
    """学习过程中的阶段性里程碑。"""

    title: str = Field(..., description="里程碑名称")
    description: str = Field(..., description="里程碑说明")
    suggested_week: Optional[int] = Field(default=None, ge=1, description="建议发生的周次")


class GoalTopic(BaseModel):
    """GoalAgent 拆解出的知识主题。"""

    id: Optional[str] = Field(default=None, description="主题唯一 ID")
    name: str = Field(..., description="知识主题名称")
    description: str = Field(..., description="主题说明")
    order: int = Field(..., ge=1, description="学习顺序")
    importance: Optional[str] = Field(default=None, description="重要程度")
    estimated_days: Optional[int] = Field(default=None, ge=1, description="预计投入天数")
    difficulty: Optional[str] = Field(default=None, description="主题难度")
    prerequisites: List[str] = Field(default_factory=list, description="前置依赖主题")
    practice_type: Optional[str] = Field(default=None, description="建议练习方式")
    milestone_hint: Optional[str] = Field(default=None, description="该主题完成后的成果提示")


class GoalPlanStructure(BaseModel):
    """GoalAgent 产出的结构化学习蓝图。"""

    summary: Optional[str] = Field(default=None, description="整体蓝图总结")
    target_role: Optional[str] = Field(default=None, description="目标角色或方向")
    topics: List[GoalTopic] = Field(default_factory=list, description="按顺序排列的主题列表")
    milestones: List[GoalMilestone] = Field(default_factory=list, description="阶段性里程碑")
