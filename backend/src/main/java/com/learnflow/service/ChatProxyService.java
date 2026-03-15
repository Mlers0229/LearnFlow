package com.learnflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Service
public class ChatProxyService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String agentBaseUrl;

    public ChatProxyService(RestTemplate restTemplate,
                            ObjectMapper objectMapper,
                            @Value("${learnflow.ai-agent.base-url}") String agentBaseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.agentBaseUrl = agentBaseUrl;
    }

    public JsonNode fetchChatModels(boolean refresh) {
        String url = agentBaseUrl + "/api/chat/models" + (refresh ? "?refresh=true" : "");
        return getJson(url);
    }

    public JsonNode fetchAdminChatConfig(boolean refresh) {
        String url = agentBaseUrl + "/api/chat/admin-config" + (refresh ? "?refresh=true" : "");
        return getJson(url);
    }

    public JsonNode updateAdminChatConfig(JsonNode payload) {
        return exchangeJson(agentBaseUrl + "/api/chat/admin-config", HttpMethod.PUT, payload);
    }

    public JsonNode refreshAdminChatModels() {
        return exchangeJson(agentBaseUrl + "/api/chat/admin-config/refresh-models", HttpMethod.POST, null);
    }

    private JsonNode getJson(String url) {
        try {
            String response = restTemplate.getForObject(url, String.class);
            return parseJson(response);
        } catch (RestClientException e) {
            throw new ResponseStatusException(BAD_GATEWAY, "调用模型配置服务失败", e);
        }
    }

    private JsonNode exchangeJson(String url, HttpMethod method, JsonNode payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            String requestBody = payload == null ? "" : objectMapper.writeValueAsString(payload);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
            return parseJson(response.getBody());
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "序列化模型配置请求失败", e);
        } catch (RestClientException e) {
            throw new ResponseStatusException(BAD_GATEWAY, "调用模型配置服务失败", e);
        }
    }

    private JsonNode parseJson(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(rawResponse);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(BAD_GATEWAY, "解析模型配置服务响应失败", e);
        }
    }
}
