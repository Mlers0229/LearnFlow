from typing import List, Optional

from pydantic import BaseModel, Field


class ResourceItem(BaseModel):
    """学习资源条目。"""

    id: Optional[int] = Field(default=None, description="资源 ID")
    title: str = Field(..., description="资源标题")
    url: str = Field(..., description="资源链接")
    level: Optional[str] = Field(default=None, description="适用水平")
    domain: Optional[str] = Field(default=None, description="资源领域")
    duration_minutes: Optional[int] = Field(default=None, description="预计学习时长")
    tags: List[str] = Field(default_factory=list, description="标签列表")
    reason: Optional[str] = Field(default=None, description="推荐理由")
    score: Optional[float] = Field(default=None, description="推荐得分")
    matched_terms: List[str] = Field(default_factory=list, description="命中的查询词")
    source: Optional[str] = Field(default=None, description="资源来源")


class ResourceRecommendRequest(BaseModel):
    """旧版 RAG 入参。"""

    topic: str = Field(..., description="当前学习主题")
    level: Optional[str] = Field(default=None, description="学习者水平")


class ResourceQueryContext(BaseModel):
    """RagAgent v2 的增强查询上下文。"""

    topic: str = Field(..., description="当前学习主题")
    level: Optional[str] = Field(default=None, description="学习者水平")
    domain: Optional[str] = Field(default=None, description="学习主题领域")
    topic_ids: List[str] = Field(default_factory=list, description="关联主题 ID")
    task_texts: List[str] = Field(default_factory=list, description="任务文本列表")
    task_type: Optional[str] = Field(default=None, description="当前任务类型")
    estimated_minutes: Optional[int] = Field(default=None, description="当前可用时长")
    phase_title: Optional[str] = Field(default=None, description="所属阶段")
    week_theme: Optional[str] = Field(default=None, description="本周主题")
    goal_text: Optional[str] = Field(default=None, description="整体学习目标")
    preferred_style: Optional[str] = Field(default=None, description="偏好的资源形式")
    top_k: int = Field(default=5, ge=1, le=10, description="返回条数")


class ResourceRecommendResponse(BaseModel):
    """旧版 RAG 返回结构。"""

    resources: List[ResourceItem] = Field(default_factory=list, description="推荐资源列表")


class ResourceRecommendResponseV2(ResourceRecommendResponse):
    """增强版 RAG 返回结构。"""

    expanded_queries: List[str] = Field(default_factory=list, description="扩展查询词")
    rerank_strategy: str = Field(default="rule-based", description="重排策略")
    query_summary: Optional[str] = Field(default=None, description="查询摘要")
