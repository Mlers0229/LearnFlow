from fastapi.testclient import TestClient

from app.main import app
from app.security import is_valid_internal_authorization


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
