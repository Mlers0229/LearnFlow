from datetime import datetime
from typing import List, Optional

from pydantic import BaseModel, ConfigDict, Field


class AgentCallLogItem(BaseModel):
    """单条 Agent 调用日志的返回结构。"""

    model_config = ConfigDict(protected_namespaces=())

    id: int = Field(..., description="日志主键 ID")
    trace_id: Optional[str] = Field(
        default=None,
        description="一次完整调用链的追踪 ID，可用于将多条日志串联起来",
    )
    agent_name: str = Field(..., description="Agent 名称，例如 GoalAgent / PlanAgent / RagAgent 等")
    request_payload: Optional[str] = Field(
        default=None,
        description="请求内容（JSON 字符串），仅用于调试与论文展示",
    )
    response_payload: Optional[str] = Field(
        default=None,
        description="响应内容（JSON 字符串或原始文本），仅用于调试与论文展示",
    )
    model_name: Optional[str] = Field(
        default=None,
        description="使用的大模型名称，若未使用模型则为 '-' 或 None",
    )
    duration_ms: Optional[int] = Field(
        default=None,
        description="本次 Agent 调用耗时（毫秒）",
    )
    created_at: datetime = Field(..., description="日志创建时间")


class AgentCallLogListResponse(BaseModel):
    """Agent 调用日志列表响应。"""

    items: List[AgentCallLogItem] = Field(default_factory=list)