package com.learnflow.controller;

import com.learnflow.dto.AgentCallLogDto;
import com.learnflow.service.AiProxyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 调试与可视化相关接口。
 *
 * 当前仅提供一个简单的 Agent 调用日志查询接口，便于前端做“多 Agent 调用链”调试页和论文截图。
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class DebugController {

    private final AiProxyService aiProxyService;

    public DebugController(AiProxyService aiProxyService) {
        this.aiProxyService = aiProxyService;
    }

    /**
     * 查询多 Agent 调用日志。
     *
     * @param traceId 可选 traceId，用于按某一次完整调用链过滤；为空则返回全局最近的日志
     * @param limit   返回条数，默认 50
     */
    @GetMapping("/agent/logs")
    public ResponseEntity<List<AgentCallLogDto>> getAgentLogs(
            @RequestParam(name = "traceId", required = false) String traceId,
            @RequestParam(name = "limit", required = false, defaultValue = "50") Integer limit) {
        List<AgentCallLogDto> logs = aiProxyService.getAgentLogs(traceId, limit);
        return new ResponseEntity<>(logs, HttpStatus.OK);
    }
}




























