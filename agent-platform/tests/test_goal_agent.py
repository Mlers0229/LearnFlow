import asyncio
import json
from pathlib import Path
from uuid import uuid4

from app.agents import goal_agent as goal_agent_module
from app.agents import scheduler_agent as scheduler_agent_module
from app.agents.goal_agent import GoalAgent
from app.agents.scheduler_agent import SchedulerAgent
from app.models.goal import GoalPlanStructure, GoalRequest, GoalTopic


def test_goal_agent_serializes_workflow_uuid_for_audit_log(monkeypatch):
    workflow_id = uuid4()
    captured: dict[str, object] = {}

    async def fake_ask_llm(_prompt: str) -> str:
        return json.dumps(
            {
                "summary": "Java learning blueprint",
                "target_role": "Java developer",
                "topics": [
                    {
                        "id": "topic-1",
                        "name": "Java basics",
                        "description": "Learn core syntax",
                        "order": 1,
                        "importance": "core",
                        "estimated_days": 3,
                        "difficulty": "beginner",
                        "prerequisites": [],
                        "practice_type": "coding",
                    }
                ],
                "milestones": [],
            }
        )

    def capture_agent_call(**kwargs):
        captured.update(kwargs)

    monkeypatch.setattr(goal_agent_module, "ask_llm", fake_ask_llm)
    monkeypatch.setattr(goal_agent_module, "save_agent_call", capture_agent_call)

    request = GoalRequest(
        goal_text="Learn Java",
        duration_weeks=1,
        hours_per_day=1,
        level="beginner",
        workflow_id=workflow_id,
    )

    blueprint = asyncio.run(GoalAgent().run(request, trace_id="trace-test"))
    logged_request = json.loads(str(captured["request_payload"]))

    assert blueprint.topics[0].id == "topic-1"
    assert logged_request["workflow_id"] == str(workflow_id)


def test_scheduler_serializes_workflow_uuid_for_audit_log(monkeypatch):
    workflow_id = uuid4()
    captured: dict[str, object] = {}

    monkeypatch.setattr(
        scheduler_agent_module,
        "save_agent_call",
        lambda **kwargs: captured.update(kwargs),
    )
    request = GoalRequest(
        goal_text="Learn Java",
        duration_weeks=1,
        hours_per_day=1,
        level="beginner",
        workflow_id=workflow_id,
    )
    blueprint = GoalPlanStructure(
        topics=[
            GoalTopic(
                id="topic-1",
                name="Java basics",
                description="Learn core syntax",
                order=1,
            )
        ]
    )

    schedule = SchedulerAgent().build_schedule(request, blueprint, trace_id="trace-test")
    logged_request = json.loads(str(captured["request_payload"]))

    assert schedule.weeks
    assert logged_request["goal"]["workflow_id"] == str(workflow_id)


def test_agent_audit_serialization_uses_json_mode():
    agents_dir = Path(goal_agent_module.__file__).parent
    offenders = [
        path.name
        for path in agents_dir.glob("*.py")
        if ".model_dump()" in path.read_text(encoding="utf-8")
    ]

    assert offenders == []
