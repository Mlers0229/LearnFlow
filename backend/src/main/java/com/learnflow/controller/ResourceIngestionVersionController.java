package com.learnflow.controller;

import com.learnflow.dto.ResourceIngestionResponse;
import com.learnflow.dto.ResourceSourceVersionRequest;
import com.learnflow.service.CurrentUserService;
import com.learnflow.service.ResourceIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

@RestController
@RequestMapping("/api/resources/{resourceId}/ingestions")
@PreAuthorize("hasRole('ADMIN')")
public class ResourceIngestionVersionController {
    private final ResourceIngestionService ingestions;
    private final CurrentUserService currentUser;

    public ResourceIngestionVersionController(ResourceIngestionService ingestions, CurrentUserService currentUser) {
        this.ingestions = ingestions;
        this.currentUser = currentUser;
    }

    @PostMapping("/url")
    public ResponseEntity<ResourceIngestionResponse> url(@PathVariable long resourceId,
            @Valid @RequestBody ResourceSourceVersionRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return accepted(ingestions.submitUrlVersion(resourceId, request.content(), currentUser.requireUserId(), key));
    }

    @PostMapping("/text")
    public ResponseEntity<ResourceIngestionResponse> text(@PathVariable long resourceId,
            @Valid @RequestBody ResourceSourceVersionRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return accepted(ingestions.submitTextVersion(resourceId, request.content(), currentUser.requireUserId(), key));
    }

    @PostMapping(value = "/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResourceIngestionResponse> document(@PathVariable long resourceId,
            @RequestPart("file") MultipartFile file, @RequestParam boolean rightsConfirmed,
            @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        if (!rightsConfirmed) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "必须确认文档处理权限");
        return accepted(ingestions.submitDocumentVersion(resourceId, file, currentUser.requireUserId(), key));
    }

    private ResponseEntity<ResourceIngestionResponse> accepted(ResourceIngestionResponse response) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/resources/ingestions/" + response.ingestionId()))
                .header(HttpHeaders.RETRY_AFTER, "2").body(response);
    }
}
