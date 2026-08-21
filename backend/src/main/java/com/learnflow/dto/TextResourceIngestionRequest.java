package com.learnflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

public record TextResourceIngestionRequest(
        @NotBlank @Size(max = 300) String title,
        @NotBlank @Size(max = 2_000_000) String text,
        @NotBlank @Size(max = 50) String domain,
        @Size(max = 20) String level,
        Integer durationMinutes,
        @Size(max = 1000) String tags,
        @AssertTrue(message = "必须确认拥有摄取和索引该内容的权限") Boolean rightsConfirmed
) {}
