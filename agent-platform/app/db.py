import logging
import os

from sqlalchemy import Boolean, Column, DateTime, Integer, String, Text, create_engine
from sqlalchemy.orm import declarative_base, sessionmaker
from sqlalchemy.sql import func

try:
    from app.config import db_settings_local as db_settings
except Exception:  # noqa: BLE001
    try:
        from app.config import db_settings
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
    # 1. 配置文件中的完整 URL
    if db_settings is not None:
        url = getattr(db_settings, "DATABASE_URL", None)
        if url:
            return url

    # 2. 环境变量
    env_url = os.getenv("LEARNFLOW_DB_URL")
    if env_url:
        return env_url

    # 3. 使用配置文件中的分字段信息拼接
    if db_settings is not None:
        driver = getattr(db_settings, "DB_DRIVER", "postgresql+psycopg2")
        user = getattr(db_settings, "DB_USER", "learnflow_user")
        password = getattr(db_settings, "DB_PASSWORD", "root")
        host = getattr(db_settings, "DB_HOST", "localhost")
        port = getattr(db_settings, "DB_PORT", 5432)
        name = getattr(db_settings, "DB_NAME", "learnflow")
        return f"{driver}://{user}:{password}@{host}:{port}/{name}"

    # 4. 最终兜底：本机 learnflow 数据库
    return "postgresql+psycopg2://learnflow_user:root@localhost:5432/learnflow"


DATABASE_URL = _build_database_url()

engine = create_engine(DATABASE_URL, echo=False, future=True)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base = declarative_base()

logger = logging.getLogger(__name__)


class ResourceBank(Base):
    """
    资源库表 resource_bank（简化版），用于存放可推荐的学习资源。

    字段与 docs/db-design.md 中的设计保持基本一致，但暂时只保留当前 RAG 所需字段。
    """

    __tablename__ = "resource_bank"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    title = Column(String(300), nullable=False)
    url = Column(Text, nullable=False)
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

    # 为方便查看和 debug，request / response 暂时都存为 JSON 字符串
    request_payload = Column(Text, nullable=True)
    response_payload = Column(Text, nullable=True)

    model_name = Column(String(64), nullable=True)  # 使用的大模型名称或 "-"（无模型）

    duration_ms = Column(Integer, nullable=True)  # 调用耗时（毫秒）

    created_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now())


def init_db() -> None:
    """
    初始化数据库模型（如果表不存在则创建）。
    """
    Base.metadata.create_all(bind=engine)


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
            log_row = AgentCallLog(
                agent_name=agent_name,
                trace_id=trace_id,
                request_payload=request_payload,
                response_payload=response_payload,
                model_name=model_name,
                duration_ms=duration_ms,
            )
            db.add(log_row)
            db.commit()
    except Exception as exc:  # noqa: BLE001
        logger.exception("保存 Agent 调用日志失败（不会影响主流程）。", exc_info=exc)


