package com.learnflow.service;

import com.learnflow.dto.AdaptationMetadataDto;
import com.learnflow.dto.AdaptiveKnowledgePointDto;
import com.learnflow.dto.MasteryProfileDto;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AdaptiveLearningService {

    public static final String CONTROL = "CONTROL";
    public static final String ADAPTIVE = "ADAPTIVE";
    private static final double MIN_CONFIDENCE = 0.25;
    private static final int MIN_SAMPLES = 2;

    private final MasteryService masteryService;
    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;
    private final TelemetryContext telemetryContext;
    private final boolean enabled;
    private final String policyVersion;
    private final String experimentKey;
    private final int adaptivePercentage;
    private final int decisionRetentionDays;

    public AdaptiveLearningService(MasteryService masteryService,
                                   JdbcTemplate jdbcTemplate,
                                   MeterRegistry meterRegistry,
                                   TelemetryContext telemetryContext,
                                   @Value("${learnflow.adaptive.enabled:true}") boolean enabled,
                                   @Value("${learnflow.adaptive.policy-version:adaptive-v1}") String policyVersion,
                                   @Value("${learnflow.adaptive.experiment-key:mastery-plan-v1}") String experimentKey,
                                   @Value("${learnflow.adaptive.adaptive-percentage:100}") int adaptivePercentage,
                                   @Value("${learnflow.adaptive.decision-retention-days:90}") int decisionRetentionDays) {
        this.masteryService = masteryService;
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;
        this.telemetryContext = telemetryContext;
        this.enabled = enabled;
        this.policyVersion = safeToken(policyVersion, "adaptive-v1");
        this.experimentKey = safeToken(experimentKey, "mastery-plan-v1");
        this.adaptivePercentage = Math.max(0, Math.min(100, adaptivePercentage));
        this.decisionRetentionDays = Math.max(7, Math.min(365, decisionRetentionDays));
    }

    @Transactional
    public AdaptationMetadataDto decide(Long userId, String surface, String sourceRef, String topic) {
        String safeSurface = normalizeSurface(surface);
        Span span = telemetryContext.startInternalSpan("adaptive.policy.decide");
        span.setAttribute("learnflow.adaptive.policy_version", policyVersion);
        span.setAttribute("learnflow.adaptive.surface", safeSurface.toLowerCase(Locale.ROOT));
        try (Scope ignored = span.makeCurrent()) {
            if (!enabled || userId == null) {
                return finish(userId, safeSurface, sourceRef, disabledContext(), "disabled");
            }

            String variant = resolveVariant(userId);
            if (CONTROL.equals(variant)) {
                AdaptationMetadataDto control = baseContext(variant, false, "control");
                return finish(userId, safeSurface, sourceRef, control, "control");
            }

            List<MasteryProfileDto> candidates = masteryService.listProfilesForAdaptation(userId, 50).stream()
                    .filter(profile -> profile.getSampleCount() != null && profile.getSampleCount() >= MIN_SAMPLES)
                    .filter(profile -> profile.getConfidence() != null && profile.getConfidence() >= MIN_CONFIDENCE)
                    .sorted(Comparator.comparing(MasteryProfileDto::getMasteryScore)
                            .thenComparing(Comparator.comparing(MasteryProfileDto::getConfidence).reversed()))
                    .toList();
            List<MasteryProfileDto> relevant = relevantProfiles(candidates, topic);
            if (relevant.isEmpty()) {
                AdaptationMetadataDto insufficient = baseContext(variant, false, "insufficient_evidence");
                return finish(userId, safeSurface, sourceRef, insufficient, "insufficient_evidence");
            }

            double weightedScore = relevant.stream()
                    .mapToDouble(profile -> profile.getMasteryScore() * profile.getConfidence()).sum()
                    / relevant.stream().mapToDouble(MasteryProfileDto::getConfidence).sum();
            AdaptationMetadataDto context = baseContext(variant, true, "mastery_applied");
            if (weightedScore < 0.45) {
                context.setTargetDifficulty("beginner");
                context.setReviewIntervalDays(1);
                context.setReviewPriority("high");
                context.setExerciseFocus("recall_and_example");
            } else if (weightedScore < 0.75) {
                context.setTargetDifficulty("intermediate");
                context.setReviewIntervalDays(3);
                context.setReviewPriority("medium");
                context.setExerciseFocus("application_and_correction");
            } else {
                context.setTargetDifficulty("advanced");
                context.setReviewIntervalDays(7);
                context.setReviewPriority("low");
                context.setExerciseFocus("transfer_and_synthesis");
            }
            context.setWeakPoints(relevant.stream().limit(3).map(this::toAdaptivePoint).toList());
            return finish(userId, safeSurface, sourceRef, context, "applied");
        } catch (RuntimeException failure) {
            span.setStatus(StatusCode.ERROR, failure.getClass().getSimpleName());
            span.setAttribute("error.type", failure.getClass().getSimpleName());
            meterRegistry.counter("learnflow.adaptive.decisions",
                    "surface", safeSurface.toLowerCase(Locale.ROOT),
                    "variant", "control",
                    "outcome", "policy_unavailable").increment();
            return baseContext(CONTROL, false, "policy_unavailable");
        } finally {
            span.end();
        }
    }

    private AdaptationMetadataDto finish(Long userId,
                                         String surface,
                                         String sourceRef,
                                         AdaptationMetadataDto context,
                                         String outcome) {
        Span.current().setAttribute("learnflow.adaptive.variant", context.getVariant().toLowerCase(Locale.ROOT));
        Span.current().setAttribute("learnflow.adaptive.applied", Boolean.TRUE.equals(context.getApplied()));
        Span.current().setAttribute("learnflow.adaptive.reason", context.getReason());
        meterRegistry.counter("learnflow.adaptive.decisions",
                "surface", surface.toLowerCase(Locale.ROOT),
                "variant", context.getVariant().toLowerCase(Locale.ROOT),
                "outcome", outcome).increment();
        if (userId != null && enabled) {
            recordDecision(userId, surface, sourceRef, context);
        }
        return context;
    }

    private String resolveVariant(Long userId) {
        List<String> existing = jdbcTemplate.queryForList("""
                SELECT variant FROM adaptive_policy_assignment
                 WHERE user_id = ? AND experiment_key = ?
                """, String.class, userId, experimentKey);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        int bucket = Math.floorMod((int) (userId ^ experimentKey.hashCode()), 100);
        String proposed = bucket < adaptivePercentage ? ADAPTIVE : CONTROL;
        jdbcTemplate.update("""
                INSERT INTO adaptive_policy_assignment
                    (user_id, experiment_key, variant, policy_version, assigned_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id, experiment_key) DO NOTHING
                """, userId, experimentKey, proposed, policyVersion);
        return jdbcTemplate.queryForObject("""
                SELECT variant FROM adaptive_policy_assignment
                 WHERE user_id = ? AND experiment_key = ?
                """, String.class, userId, experimentKey);
    }

    private void recordDecision(Long userId, String surface, String sourceRef, AdaptationMetadataDto context) {
        String boundedSource = safeSourceRef(sourceRef);
        String summary = "difficulty=" + value(context.getTargetDifficulty())
                + ",review=" + value(context.getReviewIntervalDays())
                + ",priority=" + value(context.getReviewPriority())
                + ",points=" + context.getWeakPoints().size();
        String contextHash = sha256(context.getVariant() + '|' + context.getReason() + '|' + summary);
        String decisionKey = sha256(userId + "|" + experimentKey + "|" + policyVersion + "|" + surface
                + "|" + boundedSource + "|" + contextHash);
        jdbcTemplate.update("""
                INSERT INTO adaptive_decision
                    (user_id, experiment_key, policy_version, variant, surface, source_ref,
                     context_hash, decision_key, decision_summary, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (decision_key) DO NOTHING
                """, userId, experimentKey, policyVersion, context.getVariant(), surface, boundedSource,
                contextHash, decisionKey, summary);
    }

    @Scheduled(cron = "${learnflow.adaptive.cleanup-cron:0 35 3 * * *}")
    public void cleanupExpiredDecisions() {
        if (!enabled) return;
        jdbcTemplate.update("DELETE FROM adaptive_decision WHERE created_at < CURRENT_TIMESTAMP - make_interval(days => ?)",
                decisionRetentionDays);
    }

    private List<MasteryProfileDto> relevantProfiles(List<MasteryProfileDto> candidates, String topic) {
        if (candidates.isEmpty()) return List.of();
        String normalizedTopic = normalizeText(topic);
        if (!normalizedTopic.isBlank()) {
            List<MasteryProfileDto> matches = candidates.stream()
                    .filter(profile -> {
                        String name = normalizeText(profile.getDisplayName());
                        return !name.isBlank() && (normalizedTopic.contains(name) || name.contains(normalizedTopic));
                    }).limit(3).toList();
            if (!matches.isEmpty()) return matches;
        }
        return candidates.stream().limit(3).toList();
    }

    private AdaptiveKnowledgePointDto toAdaptivePoint(MasteryProfileDto profile) {
        AdaptiveKnowledgePointDto point = new AdaptiveKnowledgePointDto();
        point.setKnowledgeKey(profile.getKnowledgeKey());
        point.setDisplayName(boundedLabel(profile.getDisplayName()));
        point.setMasteryScore(round(profile.getMasteryScore()));
        point.setConfidence(round(profile.getConfidence()));
        point.setMasteryBand(profile.getMasteryScore() < 0.45 ? "foundation"
                : profile.getMasteryScore() < 0.75 ? "developing" : "proficient");
        return point;
    }

    private AdaptationMetadataDto disabledContext() {
        return baseContext(CONTROL, false, "disabled");
    }

    private AdaptationMetadataDto baseContext(String variant, boolean applied, String reason) {
        AdaptationMetadataDto dto = new AdaptationMetadataDto();
        dto.setPolicyVersion(policyVersion);
        dto.setVariant(variant);
        dto.setApplied(applied);
        dto.setReason(reason);
        return dto;
    }

    private static String normalizeSurface(String value) {
        String normalized = value == null ? "PLAN" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PLAN", "RESOURCE", "EXERCISE", "REPLAN" -> normalized;
            default -> "PLAN";
        };
    }

    private static String safeToken(String value, String fallback) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,64}")) return fallback;
        return value;
    }

    private static String safeSourceRef(String value) {
        if (value == null || value.isBlank()) return "none";
        String cleaned = value.replaceAll("[^A-Za-z0-9._:-]", "_");
        return cleaned.substring(0, Math.min(64, cleaned.length()));
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String boundedLabel(String value) {
        if (value == null) return "未分类";
        String clean = value.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return clean.substring(0, Math.min(80, clean.length()));
    }

    private static double round(Double value) {
        return value == null ? 0 : Math.round(value * 1000.0) / 1000.0;
    }

    private static String value(Object value) { return value == null ? "none" : value.toString(); }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
