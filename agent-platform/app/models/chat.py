from pydantic import BaseModel, Field
from typing import List, Optional


class ChatMessage(BaseModel):
    role: str = Field(..., description="对话角色：user / assistant / system")
    content: str = Field(..., description="消息内容")


class ChatRequest(BaseModel):
    messages: List[ChatMessage] = Field(
        default_factory=list,
        description="对话历史，至少包含一条 user 消息",
    )
    system_prompt: Optional[str] = Field(
        default=None, description="可选的系统提示词，用于设定助手语气/身份"
    )
    model: Optional[str] = Field(
        default=None,
        description="可选的模型名称，留空则使用全局默认模型",
    )


