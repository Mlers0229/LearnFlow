import json
from datetime import datetime, timedelta, timezone

from app.db import agent_log_retention_cutoff, sanitize_agent_payload


def test_sanitizer_redacts_credentials_answers_and_prompts() -> None:
    raw = json.dumps(
        {
            "user_id": 7,
            "authorization": "Bearer top-secret",
            "nested": {
                "user_answer": "the learner's complete answer",
                "prompt": "private system prompt",
            },
            "level": "beginner",
            "prompt_tokens": 123,
        }
    )

    sanitized = json.loads(sanitize_agent_payload(raw) or "{}")

    assert sanitized["user_id"] == 7
    assert sanitized["level"] == "beginner"
    assert sanitized["prompt_tokens"] == 123
    assert sanitized["authorization"]["redacted"] is True
    assert sanitized["nested"]["user_answer"]["redacted"] is True
    assert sanitized["nested"]["prompt"]["redacted"] is True
    assert "top-secret" not in json.dumps(sanitized)
    assert "complete answer" not in json.dumps(sanitized)


def test_non_json_payload_is_replaced_with_metadata() -> None:
    sanitized = json.loads(sanitize_agent_payload("raw model response with private content") or "{}")

    assert sanitized["summary"] == "non_json_payload"
    assert sanitized["length"] > 0
    assert "private content" not in json.dumps(sanitized)


def test_payload_is_bounded_by_configured_limit(monkeypatch) -> None:
    monkeypatch.setenv("LEARNFLOW_AGENT_LOG_MAX_PAYLOAD_BYTES", "256")
    raw = json.dumps({f"safe_{index}": index for index in range(100)})

    sanitized = sanitize_agent_payload(raw) or ""

    assert "payload_exceeded_log_limit" in sanitized
    assert len(sanitized.encode("utf-8")) <= 256


def test_retention_cutoff_uses_configured_days(monkeypatch) -> None:
    monkeypatch.setenv("LEARNFLOW_AGENT_LOG_RETENTION_DAYS", "7")
    before = datetime.now(timezone.utc) - timedelta(days=7, seconds=1)
    after = datetime.now(timezone.utc) - timedelta(days=7) + timedelta(seconds=1)

    cutoff = agent_log_retention_cutoff()

    assert before <= cutoff <= after
