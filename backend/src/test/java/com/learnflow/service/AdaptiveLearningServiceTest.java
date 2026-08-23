package com.learnflow.service;

import com.learnflow.dto.AdaptationMetadataDto;
import com.learnflow.dto.MasteryProfileDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptiveLearningServiceTest {

    private MasteryService masteryService;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        masteryService = mock(MasteryService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(List.of("ADAPTIVE"));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    }

    @Test
    void weakReliableMasteryDrivesFoundationReviewAndExercises() {
        when(masteryService.listProfilesForAdaptation(7L, 50)).thenReturn(List.of(
                profile("Java Stream", 0.32, 0.60, 3)
        ));

        AdaptationMetadataDto result = service(100).decide(7L, "PLAN", "task-1", "Java Stream");

        assertThat(result.getApplied()).isTrue();
        assertThat(result.getTargetDifficulty()).isEqualTo("beginner");
        assertThat(result.getReviewIntervalDays()).isEqualTo(1);
        assertThat(result.getReviewPriority()).isEqualTo("high");
        assertThat(result.getExerciseFocus()).isEqualTo("recall_and_example");
        assertThat(result.getWeakPoints()).extracting("displayName").containsExactly("Java Stream");
    }

    @Test
    void lowConfidenceEvidenceFallsBackWithoutOverclaiming() {
        when(masteryService.listProfilesForAdaptation(8L, 50)).thenReturn(List.of(
                profile("事务", 0.20, 0.10, 1)
        ));

        AdaptationMetadataDto result = service(100).decide(8L, "EXERCISE", "day:3", "事务");

        assertThat(result.getApplied()).isFalse();
        assertThat(result.getReason()).isEqualTo("insufficient_evidence");
        assertThat(result.getTargetDifficulty()).isNull();
    }

    @Test
    void policyDatabaseFailureDoesNotBreakTheLearningFlow() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        AdaptationMetadataDto result = service(100).decide(9L, "RESOURCE", "day:9", "事务");

        assertThat(result.getApplied()).isFalse();
        assertThat(result.getVariant()).isEqualTo("CONTROL");
        assertThat(result.getReason()).isEqualTo("policy_unavailable");
    }
    private AdaptiveLearningService service(int percentage) {
        return new AdaptiveLearningService(
                masteryService,
                jdbcTemplate,
                new SimpleMeterRegistry(),
                new TelemetryContext(OpenTelemetry.noop()),
                true,
                "adaptive-v1",
                "mastery-plan-v1",
                percentage,
                90
        );
    }

    private static MasteryProfileDto profile(String name, double score, double confidence, int samples) {
        MasteryProfileDto profile = new MasteryProfileDto();
        profile.setKnowledgeKey("a".repeat(64));
        profile.setDisplayName(name);
        profile.setMasteryScore(score);
        profile.setConfidence(confidence);
        profile.setSampleCount(samples);
        return profile;
    }
}
