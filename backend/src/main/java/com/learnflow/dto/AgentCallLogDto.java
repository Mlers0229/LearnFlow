package com.learnflow.dto;

import java.time.OffsetDateTime;

/**
 * 多 Agent 调用日志 DTO，对应 FastAPI 侧 agent_call_log 表的精简视图。
 *
 * 主要用于前端调试页展示：
 * - 哪个 Agent 在什么时候被调用
 * - traceId 用于串联一次完整调用链
 * - 耗时 durationMs 便于做简单性能分析
 * - requestPayload / responsePayload 仅用于论文截图和调试
 */
public class AgentCallLogDto {

    private Long id;

    private String traceId;

    private String agentName;

    private String requestPayload;

    private String responsePayload;

    private String modelName;

    private Integer durationMs;

    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}




























