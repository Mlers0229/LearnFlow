from typing import List, Optional

from fastapi import APIRouter, Query

from app.db import AgentCallLog, SessionLocal, agent_log_retention_cutoff, sanitize_agent_payload
from app.models.log import AgentCallLogItem, AgentCallLogListResponse

router = APIRouter(tags=["agent-log"])


@router.get("/agent/logs", response_model=AgentCallLogListResponse)
async def list_agent_logs(
    trace_id: Optional[str] = Query(
        default=None,
        description="按 trace_id 过滤日志，不传则返回最近的所有日志",
    ),
    limit: int = Query(
        default=50,
        ge=1,
        le=500,
        description="返回的最大条数，默认 50",
    ),
) -> AgentCallLogListResponse:
    """
    查询多 Agent 调用日志，用于调试和论文展示。

    - 若提供 trace_id，则按 trace_id 过滤，并按时间倒序返回最近的若干条；
    - 若不提供 trace_id，则直接返回全局最近的日志（按时间倒序）。
    """
    with SessionLocal() as db:
        query = db.query(AgentCallLog).filter(AgentCallLog.created_at >= agent_log_retention_cutoff())
        if trace_id:
            query = query.filter(AgentCallLog.trace_id == trace_id)
        rows: List[AgentCallLog] = (
            query.order_by(AgentCallLog.created_at.desc(), AgentCallLog.id.desc())
            .limit(limit)
            .all()
        )

        items: List[AgentCallLogItem] = []
        for row in rows:
            if row.id is None or row.agent_name is None or row.created_at is None:
                continue
            items.append(
                AgentCallLogItem(
                    id=row.id,
                    trace_id=row.trace_id,
                    agent_name=row.agent_name,
                    # Read-time sanitization also protects legacy rows that predate
                    # the write-time redaction policy.
                    request_payload=sanitize_agent_payload(row.request_payload),
                    response_payload=sanitize_agent_payload(row.response_payload),
                    model_name=row.model_name,
                    duration_ms=row.duration_ms,
                    created_at=row.created_at,
                )
            )

        return AgentCallLogListResponse(items=items)



























