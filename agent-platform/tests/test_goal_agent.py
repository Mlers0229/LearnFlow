import asyncio
import json
from uuid import uuid4

from app.agents import goal_agent as goal_agent_module
from app.agents.goal_agent import GoalAgent
from app.models.goal import GoalRequest


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
