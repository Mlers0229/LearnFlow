import hashlib
import json
import logging
import os
from datetime import datetime, timedelta, timezone
from typing import Any
from urllib.parse import quote_plus

from sqlalchemy import Boolean, Column, DateTime, Integer, String, Text, create_engine
from sqlalchemy.engine import make_url
from sqlalchemy.orm import declarative_base, sessionmaker
from sqlalchemy.sql import func

db_settings: Any
try:
    from app.config import db_settings_local as local_db_settings

    db_settings = local_db_settings
except Exception:  # noqa: BLE001
    try:
        from app.config import db_settings as packaged_db_settings

        db_settings = packaged_db_settings
    except Exception:  # noqa: BLE001
        db_settings = None  # type: ignore[assignment]


def _build_database_url() -> str:
    """
    按优先级决定数据库连接 URL：
    1. db_settings.DATABASE_URL
    2. 环境变量 LEARNFLOW_DB_URL
    3. 根据 db_settings 中的分字段配置拼接
    4. 默认本机 Postgres learnflow 库
    """
    # 1. 部署环境中的完整 URL
    env_url = os.getenv("LEARNFLOW_DB_URL")
    if env_url:
        return env_url

    # 2. 部署环境中的独立 Agent 角色字段
    env_user = os.getenv("LEARNFLOW_DB_USER")
    if env_user:
        password = quote_plus(os.getenv("LEARNFLOW_DB_PASSWORD", ""))
        user = quote_plus(env_user)
        host = os.getenv("LEARNFLOW_DB_HOST", "localhost")
        port = os.getenv("LEARNFLOW_DB_PORT", "5432")
        name = os.getenv("LEARNFLOW_DB_NAME", "learnflow")
        return f"postgresql+psycopg2://{user}:{password}@{host}:{port}/{name}"

    # 3. 配置文件中的完整 URL
    if db_settings is not None:
        url = getattr(db_settings, "DATABASE_URL", None)
        if url:
            return url

    # 4. 使用配置文件中的分字段信息拼接
    if db_settings is not None:
        driver = getattr(db_settings, "DB_DRIVER", "postgresql+psycopg2")
        user = getattr(db_settings, "DB_USER", "learnflow_user")
        password = getattr(db_settings, "DB_PASSWORD", "root")
        host = getattr(db_settings, "DB_HOST", "localhost")
        port = str(getattr(db_settings, "DB_PORT", "5432"))
        name = getattr(db_settings, "DB_NAME", "learnflow")
        return f"{driver}://{user}:{password}@{host}:{port}/{name}"

    # 5. 最终兜底：本机 learnflow 数据库
    return "postgresql+psycopg2://learnflow_user:root@localhost:5432/learnflow"


DATABASE_URL = _build_database_url()
parsed_database_url = make_url(DATABASE_URL)

if os.getenv("LEARNFLOW_ENV", "development").lower() == "production":
    database_password = parsed_database_url.password or ""
    if len(database_password) < 16 or database_password.startswith("change_"):
        raise RuntimeError("Production Agent database password must be injected as a strong Secret")
    if not parsed_database_url.username or parsed_database_url.username in {"postgres", "learnflow_user"}:
        raise RuntimeError("Production Agent requires a dedicated least-privilege database role")


def _bounded_int(name: str, default: int, minimum: int) -> int:
    try:
        value = int(os.getenv(name, str(default)))
    except ValueError:
        return default
    return max(minimum, value)


engine_options: dict[str, Any] = {
    "echo": False,
    "future": True,
    "pool_pre_ping": True,
}
if parsed_database_url.get_backend_name() != "sqlite":
    engine_options.update(
        pool_size=_bounded_int("LEARNFLOW_AGENT_DB_POOL_SIZE", 5, 1),
        max_overflow=_bounded_int("LEARNFLOW_AGENT_DB_POOL_OVERFLOW", 0, 0),
        pool_timeout=_bounded_int("LEARNFLOW_AGENT_DB_POOL_ACQUIRE_TIMEOUT", 5, 1),
        pool_recycle=1800,
    )

engine = create_engine(DATABASE_URL, **engine_options)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base = declarative_base()

logger = logging.getLogger(__name__)

_SENSITIVE_KEY_PARTS = (
    "answer",
    "api_key",
    "authorization",
    "comment",
    "constraint",
    "content",
    "deliverable",
    "description",
    "explanation",
    "feedback",
    "goal_text",
    "message",
    "password",
    "prompt",
    "question",
    "query",
    "reason",
    "secret",
    "task",
    "text",
    "title",
    "token",
    "user_input",
)
_SAFE_METADATA_KEYS = {
    "completion_tokens",
    "input_tokens",
    "output_tokens",
    "prompt_tokens",
    "token_count",
    "total_tokens",
}


def _positive_int_from_env(name: str, default: int) -> int:
    try:
        return max(1, int(os.getenv(name, str(default))))
    except (TypeError, ValueError):
        return default


def agent_log_retention_cutoff() -> datetime:
    retention_days = _positive_int_from_env("LEARNFLOW_AGENT_LOG_RETENTION_DAYS", 30)
    return datetime.now(timezone.utc) - timedelta(days=retention_days)


def _redacted_metadata(value: Any) -> dict[str, Any]:
    serialized = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, default=str)
    encoded = serialized.encode("utf-8")
    return {
        "redacted": True,
        "type": type(value).__name__,
        "length": len(encoded),
        "sha256": hashlib.sha256(encoded).hexdigest()[:16],
    }


def _sanitize_value(value: Any, key: str | None = None) -> Any:
    normalized_key = (key or "").lower()
    if normalized_key in _SAFE_METADATA_KEYS:
        return value
    if any(part in normalized_key for part in _SENSITIVE_KEY_PARTS):
        return _redacted_metadata(value)
    if isinstance(value, dict):
        return {str(child_key): _sanitize_value(child_value, str(child_key)) for child_key, child_value in value.items()}
    if isinstance(value, list):
        limited = [_sanitize_value(item) for item in value[:20]]
        if len(value) > 20:
            limited.append({"omitted_items": len(value) - 20})
        return limited
    if isinstance(value, str) and len(value.encode("utf-8")) > 160:
        return _redacted_metadata(value)
    return value


def sanitize_agent_payload(payload: str | None) -> str | None:
    """Return a bounded, deterministic summary suitable for persisted logs."""
    if payload is None:
        return None

    encoded = payload.encode("utf-8")
    try:
        parsed = json.loads(payload)
    except (TypeError, json.JSONDecodeError):
        sanitized: Any = {
            "summary": "non_json_payload",
            "length": len(encoded),
            "sha256": hashlib.sha256(encoded).hexdigest()[:16],
        }
    else:
        sanitized = _sanitize_value(parsed)

    result = json.dumps(sanitized, ensure_ascii=False, separators=(",", ":"), default=str)
    max_bytes = max(256, _positive_int_from_env("LEARNFLOW_AGENT_LOG_MAX_PAYLOAD_BYTES", 4096))
    if len(result.encode("utf-8")) > max_bytes:
        result = json.dumps(
            {
                "summary": "payload_exceeded_log_limit",
                "original_length": len(encoded),
                "sanitized_length": len(result.encode("utf-8")),
                "sha256": hashlib.sha256(encoded).hexdigest()[:16],
            },
            separators=(",", ":"),
        )
    if len(result.encode("utf-8")) > max_bytes:
        result = '{"summary":"payload_exceeded_log_limit"}'
    return result


class ResourceBank(Base):
    """
    资源库表 resource_bank（简化版），用于存放可推荐的学习资源。

    字段与 docs/db-design.md 中的设计保持基本一致，但暂时只保留当前 RAG 所需字段。
    """

    __tablename__ = "resource_bank"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    title = Column(String(300), nullable=False)
    url = Column(Text, nullable=True)
    level = Column(String(20), nullable=True)  # beginner / intermediate / advanced
    domain = Column(String(50), nullable=True)
    duration_minutes = Column(Integer, nullable=True)
    tags = Column(Text, nullable=True)  # 使用逗号分隔的标签字符串，例如 "java,basic,intro"
    status = Column(String(20), nullable=False, default="ACTIVE")


class UserResourceFeedback(Base):
    """用户资源反馈表的轻量只读映射，用于 RAG 反馈感知重排。"""

    __tablename__ = "user_resource_feedback"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    user_id = Column(Integer, nullable=True)
    resource_bank_id = Column(Integer, nullable=False, index=True)
    rating = Column(Integer, nullable=True)
    comment = Column(Text, nullable=True)
    is_reported_invalid = Column(Boolean, nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=True)


class AgentCallLog(Base):
    """
    记录多 Agent 调用链中每次 Agent 调用的输入输出与耗时。

    设计目标：
    - 方便在论文 / 调试界面中展示“某次请求调用了哪些 Agent、顺序如何、耗时多少”；
    - 仅做轻量记录，不影响主流程稳定性（写日志失败时只打日志，不抛异常）。
    """

    __tablename__ = "agent_call_log"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)

    # 一次完整链路的追踪 ID，例如某次 /api/plan 请求，可以复用同一个 trace_id
    trace_id = Column(String(64), nullable=True, index=True)

    agent_name = Column(String(64), nullable=False)  # GoalAgent / PlanAgent / RagAgent / TutorAgent / DetailPlanAgent

    # 只保存经脱敏和限长后的结构化摘要，禁止写入完整请求、回答、Prompt 或凭证。
    request_payload = Column(Text, nullable=True)
    response_payload = Column(Text, nullable=True)

    model_name = Column(String(64), nullable=True)  # 使用的大模型名称或 "-"（无模型）

    duration_ms = Column(Integer, nullable=True)  # 调用耗时（毫秒）

    created_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now())


def save_agent_call(
    agent_name: str,
    trace_id: str | None,
    request_payload: str | None,
    response_payload: str | None,
    model_name: str | None,
    duration_ms: int | None,
) -> None:
    """
    将一次 Agent 调用记录到 agent_call_log 表中。

    - 任何异常都不会向外抛出，只会写入日志，避免影响主业务流程。
    """
    try:
        from app.db import AgentCallLog  # 避免类型检查循环导入

        with SessionLocal() as db:
            db.query(AgentCallLog).filter(
                AgentCallLog.created_at < agent_log_retention_cutoff()
            ).delete(synchronize_session=False)
            log_row = AgentCallLog(
                agent_name=agent_name,
                trace_id=trace_id,
                request_payload=sanitize_agent_payload(request_payload),
                response_payload=sanitize_agent_payload(response_payload),
                model_name=model_name,
                duration_ms=duration_ms,
            )
            db.add(log_row)
            db.commit()
    except Exception as exc:  # noqa: BLE001
        logger.exception("保存 Agent 调用日志失败（不会影响主流程）。", exc_info=exc)


