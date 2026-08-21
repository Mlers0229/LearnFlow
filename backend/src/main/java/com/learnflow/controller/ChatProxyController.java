package com.learnflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnflow.service.ChatProxyService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatProxyController {

    private final ChatProxyService chatProxyService;

    public ChatProxyController(ChatProxyService chatProxyService) {
        this.chatProxyService = chatProxyService;
    }

    @GetMapping("/models")
    public ResponseEntity<JsonNode> getModels(@RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {
        return ResponseEntity.ok(chatProxyService.fetchChatModels(refresh));
    }

    @GetMapping("/admin-config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JsonNode> getAdminConfig(@RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {
        return ResponseEntity.ok(chatProxyService.fetchAdminChatConfig(refresh));
    }

    @PutMapping("/admin-config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JsonNode> updateAdminConfig(@RequestBody(required = false) JsonNode payload) {
        return ResponseEntity.ok(chatProxyService.updateAdminChatConfig(payload));
    }

    @PostMapping("/admin-config/refresh-models")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JsonNode> refreshAdminModels() {
        return ResponseEntity.ok(chatProxyService.refreshAdminChatModels());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamChat(@RequestBody JsonNode payload) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(chatProxyService.streamChat(payload));
    }
}
