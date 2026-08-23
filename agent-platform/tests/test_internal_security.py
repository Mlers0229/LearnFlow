from unittest.mock import AsyncMock

from fastapi.testclient import TestClient

from app.main import app, validate_production_runtime
from app.security import is_valid_internal_authorization


def test_production_runtime_requires_immutable_observable_release():
    validate_production_runtime("development", "development", "false")

    for release, telemetry in (("development", "true"), ("latest", "true"), ("commit-123", "false")):
        try:
            validate_production_runtime("production", release, telemetry)
        except RuntimeError:
            pass
        else:
            raise AssertionError("unsafe production runtime was accepted")

    validate_production_runtime("production", "commit-0123456789abcdef", "true")


def test_accepts_exact_bearer_service_token():
    assert is_valid_internal_authorization("Bearer internal-secret", "internal-secret")
    assert is_valid_internal_authorization("bearer internal-secret", "internal-secret")


def test_rejects_missing_wrong_or_non_bearer_credentials():
    assert not is_valid_internal_authorization(None, "internal-secret")
    assert not is_valid_internal_authorization("Bearer wrong", "internal-secret")
    assert not is_valid_internal_authorization("Basic internal-secret", "internal-secret")


def test_accepts_previous_service_token_during_rotation():
    assert is_valid_internal_authorization(
        "Bearer previous-secret",
        ("current-secret", "previous-secret"),
    )


def test_rejects_unsafe_request_id_and_returns_bounded_correlation_id():
    with TestClient(app) as client:
        response = client.get(
            "/api/agent/logs",
            headers={"X-Request-Id": "unsafe secret-bearing value"},
        )

    assert response.status_code == 401
    request_id = response.headers["X-Request-Id"]
    assert request_id != "unsafe secret-bearing value"
    assert len(request_id) == 36


def test_extracts_w3c_parent_and_returns_same_trace_id():
    trace_id = "1234567890abcdef1234567890abcdef"
    traceparent = f"00-{trace_id}-1234567890abcdef-01"
    with TestClient(app) as client:
        response = client.get(
            "/api/agent/logs",
            headers={"traceparent": traceparent},
        )

    assert response.status_code == 401
    assert response.headers["X-Trace-Id"] == trace_id


def test_health_probes_are_public_and_do_not_expose_dependency_details(monkeypatch):
    readiness = AsyncMock(return_value=True)
    monkeypatch.setattr("app.main.database_is_ready", readiness)

    with TestClient(app) as client:
        live_response = client.get("/health/live")
        ready_response = client.get("/health/ready")

    assert live_response.status_code == 200
    assert live_response.json() == {"status": "alive"}
    assert ready_response.status_code == 200
    assert ready_response.json() == {"status": "ready"}
    readiness.assert_awaited_once()


def test_readiness_fails_closed_without_leaking_database_error(monkeypatch):
    monkeypatch.setattr("app.main.database_is_ready", AsyncMock(return_value=False))

    with TestClient(app) as client:
        response = client.get("/health/ready")

    assert response.status_code == 503
    assert response.json() == {"status": "not_ready"}
