package com.learnflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnflow.service.ChatProxyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin
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
    public ResponseEntity<JsonNode> getAdminConfig(@RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {
        return ResponseEntity.ok(chatProxyService.fetchAdminChatConfig(refresh));
    }

    @PutMapping("/admin-config")
    public ResponseEntity<JsonNode> updateAdminConfig(@RequestBody(required = false) JsonNode payload) {
        return ResponseEntity.ok(chatProxyService.updateAdminChatConfig(payload));
    }

    @PostMapping("/admin-config/refresh-models")
    public ResponseEntity<JsonNode> refreshAdminModels() {
        return ResponseEntity.ok(chatProxyService.refreshAdminChatModels());
    }
}
