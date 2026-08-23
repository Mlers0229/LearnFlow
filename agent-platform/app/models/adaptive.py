from typing import Literal

from pydantic import BaseModel, Field


class AdaptiveKnowledgePoint(BaseModel):
    knowledge_key: str = Field(..., pattern=r"^[0-9a-f]{64}$")
    display_name: str = Field(..., min_length=1, max_length=80)
    mastery_score: float = Field(..., ge=0.0, le=1.0)
    confidence: float = Field(..., ge=0.0, le=0.95)
    mastery_band: Literal["foundation", "developing", "proficient"]


class AdaptiveContext(BaseModel):
    policy_version: str = Field(..., pattern=r"^[A-Za-z0-9._:-]{1,64}$")
    variant: Literal["CONTROL", "ADAPTIVE"]
    applied: bool = False
    reason: str = Field(..., pattern=r"^[a-z0-9_]{1,64}$")
    target_difficulty: Literal["beginner", "intermediate", "advanced"] | None = None
    review_interval_days: int | None = Field(default=None, ge=1, le=30)
    review_priority: Literal["high", "medium", "low"] | None = None
    exercise_focus: Literal[
        "recall_and_example", "application_and_correction", "transfer_and_synthesis"
    ] | None = None
    weak_points: list[AdaptiveKnowledgePoint] = Field(default_factory=list, max_length=3)

    def prompt_summary(self) -> str:
        if not self.applied:
            return f"策略={self.policy_version}; 变体={self.variant}; 未应用={self.reason}"
        points = "、".join(point.display_name for point in self.weak_points) or "无"
        return (
            f"策略={self.policy_version}; 难度={self.target_difficulty}; "
            f"复习优先级={self.review_priority}; 复习间隔={self.review_interval_days}天; "
            f"练习方式={self.exercise_focus}; 弱项={points}"
        )
