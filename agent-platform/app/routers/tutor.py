from fastapi import APIRouter

from app.agents.tutor_agent import TutorAgent
from app.models.exercise import (
    TutorEvaluateRequest,
    TutorEvaluateResponse,
    TutorExerciseRequest,
    TutorExerciseResponse,
    TutorGenerateRequest,
    TutorSessionResponse,
)
from app.observability import agent_span, current_trace_id

router = APIRouter(tags=["tutor"])

tutor_agent = TutorAgent()


@router.post("/tutor/exercise", response_model=TutorExerciseResponse)
async def generate_exercises(payload: TutorExerciseRequest) -> TutorExerciseResponse:
    """兼容旧接口，仅返回题目列表。"""
    with agent_span("TutorAgent", "tutor-generate-v1", operation="generate"):
        return await tutor_agent.generate_exercises(payload, trace_id=current_trace_id())


@router.post("/v2/tutor/exercise", response_model=TutorSessionResponse)
async def generate_exercises_v2(payload: TutorGenerateRequest) -> TutorSessionResponse:
    """Tutor v2：返回题目和学习建议。"""
    with agent_span("TutorAgent", "tutor-generate-v1", operation="generate"):
        return await tutor_agent.generate_session(payload, trace_id=current_trace_id())


@router.post("/v2/tutor/evaluate", response_model=TutorEvaluateResponse)
async def evaluate_answer_v2(payload: TutorEvaluateRequest) -> TutorEvaluateResponse:
    """Tutor v2：评估学生作答并返回反馈。"""
    with agent_span("TutorAgent", "tutor-evaluate-v1", operation="evaluate"):
        return await tutor_agent.evaluate_answer(payload, trace_id=current_trace_id())
