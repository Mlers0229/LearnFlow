from typing import List, Literal, Optional

from pydantic import BaseModel, Field

from app.models.adaptive import AdaptiveContext


class ExerciseQuestion(BaseModel):
    """TutorAgent 生成的一道练习题。"""

    question: str = Field(..., description="题干")
    answer: str = Field(..., description="参考答案")
    explanation: Optional[str] = Field(default=None, description="解析")
    difficulty: Optional[str] = Field(default=None, description="题目难度")
    skill_focus: Optional[str] = Field(default=None, description="考察点")


class TutorExerciseRequest(BaseModel):
    """旧版出题入参。"""

    title: str = Field(..., description="当日学习主题")
    level: Optional[str] = Field(default=None, description="学习者水平")
    goal_text: Optional[str] = Field(default=None, description="整体学习目标")


class ExerciseAttempt(BaseModel):
    """一次学生作答。"""

    question: str = Field(..., description="题目")
    reference_answer: Optional[str] = Field(default=None, description="参考答案")
    user_answer: str = Field(..., description="学生答案")
    score: Optional[int] = Field(default=None, ge=0, le=100, description="评分")
    mistake_type: Optional[str] = Field(default=None, description="错误类型")
    feedback: Optional[str] = Field(default=None, description="反馈内容")
    next_recommendation: Optional[str] = Field(default=None, description="下一步建议")


class TutorGenerateRequest(BaseModel):
    """Tutor v2 出题请求。"""

    title: str = Field(..., description="当前学习主题")
    level: Optional[str] = Field(default=None, description="学习者水平")
    goal_text: Optional[str] = Field(default=None, description="整体学习目标")
    phase_title: Optional[str] = Field(default=None, description="所属阶段")
    week_index: Optional[int] = Field(default=None, ge=1, description="所属周次")
    day_index: Optional[int] = Field(default=None, ge=1, description="所属天序号")
    task_type: Optional[str] = Field(default=None, description="当前任务类型")
    question_count: int = Field(default=2, ge=1, le=5, description="题目数量")
    adaptive_context: Optional[AdaptiveContext] = Field(default=None, description="服务端确定的适应性策略")


class TutorEvaluateRequest(BaseModel):
    """Tutor v2 评估请求。"""

    title: str = Field(..., description="当前学习主题")
    level: Optional[str] = Field(default=None, description="学习者水平")
    goal_text: Optional[str] = Field(default=None, description="整体学习目标")
    phase_title: Optional[str] = Field(default=None, description="所属阶段")
    question: str = Field(..., description="题目")
    reference_answer: str = Field(..., description="参考答案")
    user_answer: str = Field(..., description="学生答案")


class TutorExerciseResponse(BaseModel):
    """旧版出题返回结构。"""

    questions: List[ExerciseQuestion] = Field(default_factory=list, description="题目列表")


class TutorEvaluateResponse(BaseModel):
    """Tutor v2 评估返回结构。"""

    attempt: ExerciseAttempt = Field(..., description="评估结果")


class TutorSessionResponse(BaseModel):
    """Tutor v2 出题返回结构。"""

    mode: Literal["generate"] = Field(default="generate")
    questions: List[ExerciseQuestion] = Field(default_factory=list, description="题目列表")
    learning_tip: Optional[str] = Field(default=None, description="学习建议")
