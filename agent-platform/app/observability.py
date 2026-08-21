import os
import re
from contextlib import contextmanager
from contextvars import ContextVar
from typing import Any, Iterator

from fastapi import FastAPI
from opentelemetry import metrics, trace
from opentelemetry.exporter.otlp.proto.http.metric_exporter import OTLPMetricExporter
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
from opentelemetry.instrumentation.sqlalchemy import SQLAlchemyInstrumentor
from opentelemetry.metrics import Observation
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.sdk.trace.sampling import ParentBased, TraceIdRatioBased
from opentelemetry.trace import Span, Status, StatusCode

_REQUEST_ID_RE = re.compile(r"^[A-Za-z0-9._-]{8,64}$")
_MODEL_NAME_RE = re.compile(r"^[A-Za-z0-9._:/-]{1,80}$")
_request_id: ContextVar[str | None] = ContextVar("learnflow_request_id", default=None)
_prompt_version: ContextVar[str] = ContextVar("learnflow_prompt_version", default="unspecified")
_configured = False
_database_pool_metrics_configured = False


def _enabled() -> bool:
    return os.getenv("LEARNFLOW_OTEL_ENABLED", "false").lower() in {"1", "true", "yes"}


def _bounded_probability() -> float:
    try:
        value = float(os.getenv("LEARNFLOW_TRACE_SAMPLE_PROBABILITY", "0.10"))
    except ValueError:
        return 0.10
    return min(1.0, max(0.0, value))


def _signal_endpoint(signal: str) -> str:
    explicit = os.getenv(f"OTEL_EXPORTER_OTLP_{signal.upper()}_ENDPOINT")
    if explicit:
        return explicit
    base = os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318").rstrip("/")
    return f"{base}/v1/{signal}"


def configure_telemetry(app: FastAPI, sqlalchemy_engine: Any) -> None:
    """Configure once per process; exporters remain disabled unless explicitly enabled."""
    global _configured
    if _configured:
        FastAPIInstrumentor.instrument_app(app, excluded_urls="/health,/api/health")
        return

    resource = Resource.create(
        {
            "service.name": os.getenv("OTEL_SERVICE_NAME", "learnflow-agent"),
            "service.version": os.getenv("LEARNFLOW_RELEASE_VERSION", "development"),
            "deployment.environment.name": os.getenv("LEARNFLOW_ENV", "development"),
        }
    )
    tracer_provider = TracerProvider(
        resource=resource,
        sampler=ParentBased(TraceIdRatioBased(_bounded_probability())),
    )
    metric_readers = []
    if _enabled():
        tracer_provider.add_span_processor(
            BatchSpanProcessor(OTLPSpanExporter(endpoint=_signal_endpoint("traces")))
        )
        metric_readers.append(
            PeriodicExportingMetricReader(
                OTLPMetricExporter(endpoint=_signal_endpoint("metrics")),
                export_interval_millis=max(
                    5_000,
                    int(os.getenv("LEARNFLOW_METRICS_EXPORT_INTERVAL_MS", "30000")),
                ),
            )
        )
    trace.set_tracer_provider(tracer_provider)
    metrics.set_meter_provider(MeterProvider(resource=resource, metric_readers=metric_readers))

    FastAPIInstrumentor.instrument_app(app, excluded_urls="/health,/api/health")
    HTTPXClientInstrumentor().instrument()
    SQLAlchemyInstrumentor().instrument(engine=sqlalchemy_engine)
    _register_database_pool_metrics(sqlalchemy_engine)
    _configured = True


def _register_database_pool_metrics(sqlalchemy_engine: Any) -> None:
    global _database_pool_metrics_configured
    if _database_pool_metrics_configured:
        return
    pool = sqlalchemy_engine.pool
    meter = metrics.get_meter("learnflow.database.pool")

    def observe(method_name: str):
        def callback(_options: Any):
            method = getattr(pool, method_name, None)
            try:
                value = method() if callable(method) else 0
                return [Observation(max(0, int(value)))]
            except (AttributeError, TypeError, ValueError):
                return [Observation(0)]

        return callback

    meter.create_observable_gauge(
        "learnflow.database.pool.connections",
        callbacks=[observe("size")],
        description="Configured SQLAlchemy database pool connections",
    )
    meter.create_observable_gauge(
        "learnflow.database.pool.checked_out",
        callbacks=[observe("checkedout")],
        description="Currently checked-out SQLAlchemy database connections",
    )
    meter.create_observable_gauge(
        "learnflow.database.pool.overflow",
        callbacks=[observe("overflow")],
        description="Current SQLAlchemy database pool overflow",
    )
    _database_pool_metrics_configured = True


def normalize_request_id(candidate: str | None) -> str | None:
    if candidate and _REQUEST_ID_RE.fullmatch(candidate):
        return candidate
    return None


def set_request_id(value: str | None):
    return _request_id.set(value)


def reset_request_id(token: Any) -> None:
    _request_id.reset(token)


def current_request_id() -> str | None:
    return _request_id.get()


def current_trace_id() -> str | None:
    context = trace.get_current_span().get_span_context()
    if not context.is_valid:
        return None
    return trace.format_trace_id(context.trace_id)


@contextmanager
def agent_span(
    agent_name: str,
    prompt_version: str,
    *,
    operation: str = "execute",
) -> Iterator[Span]:
    tracer = trace.get_tracer("learnflow.agent.nodes")
    token = _prompt_version.set(prompt_version)
    with tracer.start_as_current_span(f"agent.{agent_name}.{operation}") as span:
        span.set_attribute("learnflow.agent.name", agent_name)
        span.set_attribute("learnflow.agent.operation", operation)
        span.set_attribute("gen_ai.prompt.version", prompt_version)
        request_id = current_request_id()
        if request_id:
            span.set_attribute("learnflow.request.id", request_id)
        try:
            yield span
        except BaseException as exc:
            span.set_status(Status(StatusCode.ERROR, type(exc).__name__))
            span.set_attribute("error.type", type(exc).__name__)
            raise
        finally:
            _prompt_version.reset(token)


@contextmanager
def model_call_span(model: str, operation: str = "chat") -> Iterator[Span]:
    tracer = trace.get_tracer("learnflow.model.calls")
    with tracer.start_as_current_span(f"gen_ai.{operation}") as span:
        span.set_attribute("gen_ai.operation.name", operation)
        span.set_attribute("gen_ai.request.model", _safe_model_label(model))
        span.set_attribute("gen_ai.prompt.version", _prompt_version.get())
        yield span


def mark_model_outcome(span: Span, outcome: str, reason: str = "none") -> None:
    span.set_attribute("learnflow.ai.outcome", outcome)
    span.set_attribute("learnflow.degradation.reason", reason)
    if outcome not in {"success", "fallback"}:
        span.set_status(Status(StatusCode.ERROR, reason))
    meter = metrics.get_meter("learnflow.model.calls")
    meter.create_counter("learnflow.ai.model.calls").add(
        1,
        {"outcome": outcome, "reason": reason},
    )


def record_rag_result(result_count: int) -> None:
    """Record retrieval availability without query or user-derived labels."""
    outcome = "empty" if result_count <= 0 else "success"
    metrics.get_meter("learnflow.rag").create_counter("learnflow.rag.requests").add(
        1,
        {"outcome": outcome},
    )


def record_dense_retrieval(
    outcome: str,
    reason: str,
    candidate_count: int,
    duration_seconds: float,
) -> None:
    """Record bounded Dense Retrieval metadata without query, Chunk, or user labels."""
    safe_outcome = outcome if outcome in {"success", "empty", "fallback"} else "failure"
    safe_reason = reason if re.fullmatch(r"[a-z0-9_]{1,48}", reason) else "other"
    meter = metrics.get_meter("learnflow.rag.dense")
    meter.create_counter("learnflow.rag.dense.requests").add(
        1,
        {"outcome": safe_outcome, "reason": safe_reason},
    )
    meter.create_histogram("learnflow.rag.dense.candidates").record(max(0, candidate_count))
    meter.create_histogram("learnflow.rag.dense.duration").record(max(0.0, duration_seconds))


def record_validator_result(is_valid: bool, issue_count: int, warning_count: int) -> None:
    """Record bounded plan quality outcomes; issue text never enters telemetry."""
    meter = metrics.get_meter("learnflow.plan.validator")
    meter.create_counter("learnflow.plan.validator.results").add(
        1,
        {"outcome": "valid" if is_valid else "invalid"},
    )
    meter.create_histogram("learnflow.plan.validator.issue.count").record(max(0, issue_count))
    meter.create_histogram("learnflow.plan.validator.warning.count").record(max(0, warning_count))


def record_model_usage(span: Span, model: str, usage: Any) -> None:
    if not isinstance(usage, dict):
        span.set_attribute("gen_ai.usage.available", False)
        return
    prompt_tokens = _safe_non_negative_int(usage.get("prompt_tokens") or usage.get("input_tokens"))
    completion_tokens = _safe_non_negative_int(
        usage.get("completion_tokens") or usage.get("output_tokens")
    )
    total_tokens = _safe_non_negative_int(usage.get("total_tokens"))
    if total_tokens is None and (prompt_tokens is not None or completion_tokens is not None):
        total_tokens = (prompt_tokens or 0) + (completion_tokens or 0)

    meter = metrics.get_meter("learnflow.model.usage")
    token_counter = meter.create_counter("learnflow.ai.tokens")
    model_label = _safe_model_label(model)
    if prompt_tokens is not None:
        span.set_attribute("gen_ai.usage.input_tokens", prompt_tokens)
        token_counter.add(prompt_tokens, {"model": model_label, "token.type": "input"})
    if completion_tokens is not None:
        span.set_attribute("gen_ai.usage.output_tokens", completion_tokens)
        token_counter.add(completion_tokens, {"model": model_label, "token.type": "output"})
    if total_tokens is not None:
        span.set_attribute("gen_ai.usage.total_tokens", total_tokens)
    span.set_attribute("gen_ai.usage.available", total_tokens is not None)

    estimated_cost = _estimated_cost(prompt_tokens, completion_tokens)
    if estimated_cost is not None:
        span.set_attribute("gen_ai.usage.cost_estimate_usd", estimated_cost)
        meter.create_counter("learnflow.ai.cost.estimated.usd").add(
            estimated_cost,
            {"model": model_label},
        )


def _safe_model_label(model: str) -> str:
    value = str(model).strip()
    return value if _MODEL_NAME_RE.fullmatch(value) else "other"


def _safe_non_negative_int(value: Any) -> int | None:
    try:
        result = int(value)
    except (TypeError, ValueError):
        return None
    return result if result >= 0 else None


def _estimated_cost(input_tokens: int | None, output_tokens: int | None) -> float | None:
    try:
        input_rate = float(os.getenv("LEARNFLOW_LLM_INPUT_USD_PER_1M_TOKENS", "0"))
        output_rate = float(os.getenv("LEARNFLOW_LLM_OUTPUT_USD_PER_1M_TOKENS", "0"))
    except ValueError:
        return None
    if input_rate <= 0 and output_rate <= 0:
        return None
    return round(((input_tokens or 0) * input_rate + (output_tokens or 0) * output_rate) / 1_000_000, 9)
