package com.learnflow.controller;

import com.learnflow.dto.ResourceIngestionResponse;
import com.learnflow.dto.ResourceIngestionStatusResponse;
import com.learnflow.dto.TextResourceIngestionRequest;
import com.learnflow.dto.UrlResourceIngestionRequest;
import com.learnflow.service.CurrentUserService;
import com.learnflow.service.ResourceIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/resources/ingestions")
public class ResourceIngestionController {
    private final ResourceIngestionService ingestions;
    private final CurrentUserService currentUser;

    public ResourceIngestionController(ResourceIngestionService ingestions, CurrentUserService currentUser) {
        this.ingestions = ingestions;
        this.currentUser = currentUser;
    }

    @PostMapping("/url")
    public ResponseEntity<ResourceIngestionResponse> submitUrl(
            @Valid @RequestBody UrlResourceIngestionRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return accepted(ingestions.submitUrl(request, currentUser.requireUserId(), currentUser.requireUsername(), idempotencyKey));
    }

    @PostMapping("/text")
    public ResponseEntity<ResourceIngestionResponse> submitText(
            @Valid @RequestBody TextResourceIngestionRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return accepted(ingestions.submitText(request, currentUser.requireUserId(), currentUser.requireUsername(), idempotencyKey));
    }

    @PostMapping(value = "/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResourceIngestionResponse> submitDocument(
            @RequestParam String title,
            @RequestParam String domain,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Integer durationMinutes,
            @RequestParam(required = false) String tags,
            @RequestParam boolean rightsConfirmed,
            @RequestPart("file") MultipartFile file,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        if (!rightsConfirmed) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "必须确认拥有摄取和索引该文档的权限");
        }
        return accepted(ingestions.submitDocument(title, domain, level, durationMinutes, tags, file,
                currentUser.requireUserId(), currentUser.requireUsername(), idempotencyKey));
    }

    @GetMapping("/{ingestionId}")
    public ResponseEntity<ResourceIngestionStatusResponse> status(@PathVariable UUID ingestionId) {
        return ResponseEntity.ok(ingestions.getStatus(ingestionId, currentUser.requireUserId(), currentUser.isAdmin()));
    }

    private ResponseEntity<ResourceIngestionResponse> accepted(ResourceIngestionResponse response) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/resources/ingestions/" + response.ingestionId()))
                .header(HttpHeaders.RETRY_AFTER, "2")
                .body(response);
    }
}
