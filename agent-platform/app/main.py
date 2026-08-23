import asyncio
import os
from contextlib import asynccontextmanager
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from sqlalchemy import text

from app.core.embedding import embedding_config
from app.core.http_client import close_http_client, open_http_client
from app.core.request_budget import request_budget, timeout_from_header
from app.db import engine
from app.observability import (
    configure_telemetry,
    current_trace_id,
    normalize_request_id,
    reset_request_id,
    set_request_id,
)
from app.routers.chat import router as chat_router
from app.routers.log import router as log_router
from app.routers.plan import router as plan_router
from app.routers.rag import router as rag_router
from app.routers.tutor import router as tutor_router
from app.security import is_valid_internal_authorization


def _database_is_ready() -> bool:
    """Run the smallest possible database readiness query without leaking details."""
    with engine.connect() as connection:
        connection.execute(text("SELECT 1"))
    return True


def validate_production_runtime(environment: str, release_version: str, telemetry_enabled: str) -> None:
    if environment.lower() != "production":
        return
    if release_version.lower() in {"", "development", "latest"}:
        raise RuntimeError("Production requires an immutable LEARNFLOW_RELEASE_VERSION")
    if telemetry_enabled.lower() != "true":
        raise RuntimeError("Production requires LEARNFLOW_OTEL_ENABLED=true")


async def database_is_ready(timeout_seconds: float = 2.0) -> bool:
    try:
        return await asyncio.wait_for(asyncio.to_thread(_database_is_ready), timeout_seconds)
    except Exception:  # noqa: BLE001 - readiness intentionally collapses dependency details
        return False


@asynccontextmanager
async def lifespan(_app: FastAPI):
    await open_http_client()
    try:
        yield
    finally:
        await close_http_client()


def create_app() -> FastAPI:
    """
    FastAPI 应用工厂。
    后续如果需要挂更多 Router（goal / rag / tutor / supervisor / log），
    可以在这里统一注册，方便管理。
    """
    environment = os.getenv("LEARNFLOW_ENV", "development")
    validate_production_runtime(
        environment,
        os.getenv("LEARNFLOW_RELEASE_VERSION", "development"),
        os.getenv("LEARNFLOW_OTEL_ENABLED", "false"),
    )
    internal_token = os.getenv(
        "LEARNFLOW_INTERNAL_TOKEN",
        "dev-only-change-this-agent-token",
    )
    previous_internal_token = os.getenv("LEARNFLOW_INTERNAL_PREVIOUS_TOKEN", "")
    if environment.lower() == "production":
        if internal_token.startswith("dev-only-") or len(internal_token.encode("utf-8")) < 32:
            raise RuntimeError("Production requires a strong injected LEARNFLOW_INTERNAL_TOKEN")
        if previous_internal_token and len(previous_internal_token.encode("utf-8")) < 32:
            raise RuntimeError("LEARNFLOW_INTERNAL_PREVIOUS_TOKEN must contain at least 32 bytes")
        embedding = embedding_config()
        if embedding.enabled and (not embedding.api_base or not embedding.api_key):
            raise RuntimeError("Production Dense Retrieval requires an injected Embedding endpoint and key")
        if embedding.enabled and embedding.dimensions != 1536:
            raise RuntimeError("V10 Dense Retrieval requires 1536-dimensional embeddings")

    app = FastAPI(
        title="LearnFlow AI Agent Platform",
        description="多 Agent 学习规划与资源推荐平台（FastAPI）",
        version="0.1.0",
        lifespan=lifespan,
    )
    configure_telemetry(app, engine)

    @app.get("/health/live", include_in_schema=False)
    async def liveness():
        return {"status": "alive"}

    @app.get("/health/ready", include_in_schema=False)
    async def readiness():
        if not await database_is_ready():
            return JSONResponse(status_code=503, content={"status": "not_ready"})
        return {"status": "ready"}

    @app.middleware("http")
    async def require_internal_service_identity(request: Request, call_next):
        request_id = normalize_request_id(request.headers.get("x-request-id")) or str(uuid4())
        request_id_token = set_request_id(request_id)
        try:
            if request.url.path in {
                "/health",
                "/api/health",
                "/health/live",
                "/health/ready",
            }:
                response = await call_next(request)
            elif not is_valid_internal_authorization(
                request.headers.get("authorization"),
                (internal_token, previous_internal_token),
            ):
                response = JSONResponse(status_code=401, content={"detail": "invalid service credential"})
            else:
                timeout_seconds = timeout_from_header(request.headers.get("x-learnflow-timeout-ms"))
                try:
                    if timeout_seconds is None:
                        response = await call_next(request)
                    else:
                        async with request_budget(timeout_seconds):
                            response = await call_next(request)
                except TimeoutError:
                    response = JSONResponse(
                        status_code=504,
                        content={
                            "detail": "agent request exceeded overall budget",
                            "reason": "overall_timeout",
                        },
                    )
            response.headers["X-Request-Id"] = request_id
            trace_id = current_trace_id()
            if trace_id:
                response.headers["X-Trace-Id"] = trace_id
            return response
        finally:
            reset_request_id(request_id_token)

    app.include_router(plan_router, prefix="/api")
    app.include_router(rag_router, prefix="/api")
    app.include_router(tutor_router, prefix="/api")
    app.include_router(log_router, prefix="/api")
    app.include_router(chat_router, prefix="/api")
    return app


app = create_app()
