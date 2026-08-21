package com.learnflow.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResourceSourceVersionRequest(
        @NotBlank @Size(max = 2_000_000) String content,
        @AssertTrue(message = "必须确认拥有摄取和索引该内容的权限") Boolean rightsConfirmed
) {}
