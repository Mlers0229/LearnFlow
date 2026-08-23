from datetime import date

import pytest
from pydantic import ValidationError

from app.agents.plan_agent import PlanAgent
from app.agents.rag_agent import RagAgent
from app.agents.tutor_agent import TutorAgent
from app.models.adaptive import AdaptiveContext, AdaptiveKnowledgePoint
from app.models.exercise import TutorGenerateRequest
from app.models.goal import GoalRequest
from app.models.plan import PlanDay
from app.models.resource import ResourceQueryContext


def adaptive_context(score: float = 0.30) -> AdaptiveContext:
    return AdaptiveContext(
        policy_version="adaptive-v1",
        variant="ADAPTIVE",
        applied=True,
        reason="mastery_applied",
        target_difficulty="beginner",
        review_interval_days=1,
        review_priority="high",
        exercise_focus="recall_and_example",
        weak_points=[
            AdaptiveKnowledgePoint(
                knowledge_key="a" * 64,
                display_name="Java Stream",
                mastery_score=score,
                confidence=0.6,
                mastery_band="foundation",
            )
        ],
    )


def test_plan_policy_changes_difficulty_and_review_coverage() -> None:
    goal = GoalRequest(
        goal_text="学习 Java",
        duration_weeks=1,
        hours_per_day=1,
        level="advanced",
        adaptive_context=adaptive_context(),
    )
    days = [
        PlanDay(
            date=date(2026, 8, 23),
            title="Stream",
            tasks=["完成练习"],
            day_index=1,
            task_type="practice",
            difficulty="advanced",
        )
    ]

    adapted = PlanAgent._apply_adaptive_policy(goal, days)

    assert adapted[0].difficulty == "beginner"
    assert adapted[0].review_of == ["Java Stream"]
    assert adapted[0].tasks[0].startswith("优先复习弱项")


def test_rag_uses_adaptive_target_level_without_mutating_input() -> None:
    request = ResourceQueryContext(
        topic="Stream",
        level="advanced",
        adaptive_context=adaptive_context(),
    )

    effective = RagAgent._effective_adaptive_request(request)

    assert request.level == "advanced"
    assert effective.level == "beginner"
    assert effective.adaptive_context is not None


def test_tutor_fallback_changes_question_type_and_difficulty() -> None:
    request = TutorGenerateRequest(
        title="Stream",
        level="advanced",
        adaptive_context=adaptive_context(),
    )

    questions = TutorAgent._fallback_questions(request)

    assert questions[0].difficulty == "beginner"
    assert questions[0].skill_focus == "recall_and_example"
    assert "回忆" in questions[0].question


def test_adaptive_context_rejects_unbounded_or_identity_like_payload() -> None:
    with pytest.raises(ValidationError):
        AdaptiveContext(
            policy_version="adaptive-v1",
            variant="ADAPTIVE",
            applied=True,
            reason="mastery_applied",
            target_difficulty="beginner",
            weak_points=[
                {
                    "knowledge_key": "not-a-hash",
                    "display_name": "x" * 200,
                    "mastery_score": 0.2,
                    "confidence": 0.5,
                    "mastery_band": "foundation",
                    "user_id": 99,
                }
            ],
        )
