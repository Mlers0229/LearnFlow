package com.learnflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Service
public class ChatProxyService {

    private final AgentHttpClient agentHttpClient;

    public ChatProxyService(AgentHttpClient agentHttpClient) {
        this.agentHttpClient = agentHttpClient;
    }

    public JsonNode fetchChatModels(boolean refresh) {
        String url = "/api/chat/models" + (refresh ? "?refresh=true" : "");
        return getJson(url);
    }

    public JsonNode fetchAdminChatConfig(boolean refresh) {
        String url = "/api/chat/admin-config" + (refresh ? "?refresh=true" : "");
        return getJson(url);
    }

    public JsonNode updateAdminChatConfig(JsonNode payload) {
        return exchangeJson("/api/chat/admin-config", HttpMethod.PUT, payload);
    }

    public JsonNode refreshAdminChatModels() {
        return exchangeJson("/api/chat/admin-config/refresh-models", HttpMethod.POST, null);
    }

    public StreamingResponseBody streamChat(JsonNode payload) {
        return outputStream -> {
            try {
                agentHttpClient.streamJson(AgentOperation.STREAM, "/api/chat/stream", payload, outputStream);
            } catch (AgentCallException exception) {
                throw new ResponseStatusException(BAD_GATEWAY, "调用流式对话服务失败", exception);
            }
        };
    }

    private JsonNode getJson(String url) {
        try {
            return agentHttpClient.get(AgentOperation.ADMIN, url, JsonNode.class);
        } catch (AgentCallException e) {
            throw new ResponseStatusException(BAD_GATEWAY, "调用模型配置服务失败", e);
        }
    }

    private JsonNode exchangeJson(String url, HttpMethod method, JsonNode payload) {
        try {
            return agentHttpClient.exchangeJson(
                    AgentOperation.ADMIN,
                    url,
                    method.name(),
                    payload,
                    JsonNode.class
            );
        } catch (AgentCallException e) {
            throw new ResponseStatusException(BAD_GATEWAY, "调用模型配置服务失败", e);
        }
    }
}
