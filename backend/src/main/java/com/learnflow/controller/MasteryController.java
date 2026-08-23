package com.learnflow.controller;

import com.learnflow.dto.MasteryProfileDto;
import com.learnflow.service.CurrentUserService;
import com.learnflow.service.MasteryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mastery")
public class MasteryController {

    private final MasteryService masteryService;
    private final CurrentUserService currentUserService;

    public MasteryController(MasteryService masteryService, CurrentUserService currentUserService) {
        this.masteryService = masteryService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<List<MasteryProfileDto>> list(
            @RequestParam(name = "limit", defaultValue = "20") Integer limit) {
        return ResponseEntity.ok(masteryService.listProfiles(currentUserService.requireUserId(), limit));
    }

    @PostMapping("/recompute")
    public ResponseEntity<List<MasteryProfileDto>> recompute(
            @RequestParam(name = "limit", defaultValue = "20") Integer limit) {
        return ResponseEntity.ok(masteryService.recomputeAll(currentUserService.requireUserId(), limit));
    }
}

