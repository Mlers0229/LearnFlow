package com.learnflow.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MasteryPrivacyTest {

    @Test
    void knowledgePointDisplayNameRedactsCommonSensitiveIdentifiers() {
        String normalized = MasteryService.normalizeDisplayName(
                "Java user@example.com https://example.com/private Bearer secret-token 13800138000");

        assertThat(normalized).contains("Java").contains("[redacted]");
        assertThat(normalized).doesNotContain("user@example.com");
        assertThat(normalized).doesNotContain("https://");
        assertThat(normalized).doesNotContain("secret-token");
        assertThat(normalized).doesNotContain("13800138000");
    }

    @Test
    void eventKeysAreStableAndNeverContainSourceText() {
        String first = MasteryService.hash("exercise:42:answered");
        String second = MasteryService.hash("exercise:42:answered");

        assertThat(first).isEqualTo(second).matches("[0-9a-f]{64}");
        assertThat(first).doesNotContain("exercise");
    }
}

