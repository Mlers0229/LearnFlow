import asyncio
import time

import httpx
from fastapi.testclient import TestClient

from app.core import http_client, llm
from app.core.request_budget import remaining_seconds, request_budget, timeout_from_header
from app.main import create_app


def test_timeout_header_reserves_time_for_upstream_fallback() -> None:
    assert timeout_from_header("10000") == 9.0
    assert timeout_from_header("90000") == 88.0
    assert timeout_from_header("invalid") is None


def test_request_budget_caps_nested_operations() -> None:
    async def scenario() -> None:
        async with request_budget(0.5):
            outer = remaining_seconds(5.0)
            async with request_budget(2.0):
                inner = remaining_seconds(5.0)
            assert 0.0 < inner <= outer <= 0.5

    asyncio.run(scenario())


def test_shared_http_client_is_reused_and_closed() -> None:
    async def scenario() -> None:
        await http_client.close_http_client()
        first = await http_client.open_http_client()
        second = await http_client.get_http_client()
        assert first is second
        await http_client.close_http_client()
        assert first.is_closed

    asyncio.run(scenario())


def test_llm_overall_budget_stops_slow_request(monkeypatch) -> None:
    async def handler(_request: httpx.Request) -> httpx.Response:
        await asyncio.sleep(0.25)
        return httpx.Response(200, json={"choices": [{"message": {"content": "late"}}]})

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))

    async def fake_client() -> httpx.AsyncClient:
        return client

    monkeypatch.setattr(llm, "get_http_client", fake_client)
    monkeypatch.setattr(
        llm,
        "get_effective_llm_config",
        lambda: {
            "apiBase": "https://llm.example.test",
            "apiKey": "secret",
            "defaultModel": "test-model",
        },
    )
    monkeypatch.setattr(llm, "validate_llm_url", lambda _url: None)

    async def scenario() -> None:
        started = time.perf_counter()
        async with request_budget(0.2):
            assert await llm.ask_llm("hello") == ""
        assert time.perf_counter() - started < 0.3
        await client.aclose()

    asyncio.run(scenario())


def test_llm_task_cancellation_reaches_http_transport(monkeypatch) -> None:
    transport_cancelled = asyncio.Event()

    async def handler(_request: httpx.Request) -> httpx.Response:
        try:
            await asyncio.sleep(10)
        finally:
            transport_cancelled.set()
        return httpx.Response(200)

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))

    async def fake_client() -> httpx.AsyncClient:
        return client

    monkeypatch.setattr(llm, "get_http_client", fake_client)
    monkeypatch.setattr(
        llm,
        "get_effective_llm_config",
        lambda: {
            "apiBase": "https://llm.example.test",
            "apiKey": "secret",
            "defaultModel": "test-model",
        },
    )
    monkeypatch.setattr(llm, "validate_llm_url", lambda _url: None)

    async def scenario() -> None:
        task = asyncio.create_task(llm.ask_llm("hello"))
        await asyncio.sleep(0)
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass
        else:
            raise AssertionError("LLM cancellation must not be swallowed")
        await asyncio.wait_for(transport_cancelled.wait(), timeout=0.5)
        await client.aclose()

    asyncio.run(scenario())


def test_fastapi_returns_explicit_overall_timeout() -> None:
    app = create_app()

    @app.get("/test/slow")
    async def slow_route() -> dict[str, bool]:
        await asyncio.sleep(0.3)
        return {"ok": True}

    with TestClient(app) as client:
        response = client.get(
            "/test/slow",
            headers={
                "Authorization": "Bearer dev-only-change-this-agent-token",
                "X-LearnFlow-Timeout-Ms": "100",
            },
        )

    assert response.status_code == 504
    assert response.json()["reason"] == "overall_timeout"


def test_async_plan_chain_keeps_rule_fallback_contract(monkeypatch) -> None:
    monkeypatch.setattr(
        llm,
        "get_effective_llm_config",
        lambda: {
            "apiBase": None,
            "apiKey": None,
            "defaultModel": "test-model",
        },
    )
    app = create_app()
    with TestClient(app) as client:
        response = client.post(
            "/api/v2/plan",
            headers={"Authorization": "Bearer dev-only-change-this-agent-token"},
            json={
                "goal_text": "学习 Java 并完成一个 REST API",
                "duration_weeks": 1,
                "hours_per_day": 1,
                "level": "beginner",
            },
        )

    assert response.status_code == 200
    payload = response.json()
    assert payload["days"]
    assert payload["validation_report"] is not None
