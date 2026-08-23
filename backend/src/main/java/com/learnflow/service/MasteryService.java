package com.learnflow.service;

import com.learnflow.dto.MasteryEvidenceDto;
import com.learnflow.dto.MasteryProfileDto;
import com.learnflow.entity.ExerciseRecord;
import com.learnflow.entity.ResourceBank;
import com.learnflow.entity.StudyPlanDay;
import com.learnflow.entity.UserResourceFeedback;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class MasteryService {

    static final String DEFAULT_ALGORITHM_VERSION = "weighted-v1";
    private static final String UNCLASSIFIED = "未分类";

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;
    private final TelemetryContext telemetryContext;
    private final boolean enabled;
    private final String algorithmVersion;

    public MasteryService(JdbcTemplate jdbcTemplate,
                          MeterRegistry meterRegistry,
                          TelemetryContext telemetryContext,
                          @Value("${learnflow.mastery.enabled:true}") boolean enabled,
                          @Value("${learnflow.mastery.algorithm-version:weighted-v1}") String algorithmVersion) {
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;
        this.telemetryContext = telemetryContext;
        this.enabled = enabled;
        this.algorithmVersion = sanitizeAlgorithmVersion(algorithmVersion);
    }

    @Transactional
    public void recordPlanDayStatus(Long userId,
                                    StudyPlanDay day,
                                    String previousStatus,
                                    String newStatus,
                                    String transitionVersion) {
        if (!enabled || day == null || day.getId() == null || newStatus == null) {
            return;
        }
        Signal signal = switch (newStatus) {
            case "COMPLETED" -> new Signal("PLAN_DAY_COMPLETED", 0.75, 0.35);
            case "DELAYED" -> new Signal("PLAN_DAY_DELAYED", 0.25, 0.20);
            case "IN_PROGRESS" -> new Signal("PLAN_DAY_STARTED", 0.55, 0.10);
            default -> new Signal("PLAN_DAY_RESET", null, 0);
        };
        String rawKey = "plan-day:" + day.getId() + ":" + previousStatus + ":" + newStatus + ":" + transitionVersion;
        recordEvent(userId, knowledgeLabel(day.getTitle(), null), signal.eventType(), "PLAN_DAY", day.getId(),
                rawKey, signal.value(), signal.weight(), "status=" + newStatus.toLowerCase(Locale.ROOT), null);
    }

    @Transactional
    public void recordExerciseAnswered(Long userId, ExerciseRecord record) {
        if (!enabled || record == null || record.getId() == null) {
            return;
        }
        Double value = record.getScore() == null
                ? (record.getIsCorrect() == null ? null : (record.getIsCorrect() ? 1.0 : 0.0))
                : Math.max(0, Math.min(100, record.getScore())) / 100.0;
        double weight = record.getScore() == null ? (record.getIsCorrect() == null ? 0 : 0.75) : 1.0;
        String fallback = record.getPlanDay() == null ? null : record.getPlanDay().getTitle();
        String summary = categoricalSummary(record.getDifficulty(), record.getMistakeType());
        recordEvent(userId, knowledgeLabel(record.getSkillFocus(), fallback), "EXERCISE_ANSWERED",
                "EXERCISE_RECORD", record.getId(), "exercise:" + record.getId() + ":answered",
                value, weight, summary, null);
    }

    @Transactional
    public void recordExerciseReviewed(Long userId, ExerciseRecord record) {
        if (!enabled || record == null || record.getId() == null) {
            return;
        }
        String fallback = record.getPlanDay() == null ? null : record.getPlanDay().getTitle();
        recordEvent(userId, knowledgeLabel(record.getSkillFocus(), fallback), "EXERCISE_REVIEWED",
                "EXERCISE_RECORD", record.getId(), "exercise:" + record.getId() + ":reviewed",
                null, 0, "reviewed", null);
    }

    @Transactional
    public void reverseExerciseAnswered(Long userId, ExerciseRecord record) {
        if (!enabled || record == null || record.getId() == null) {
            return;
        }
        List<OriginalEvent> originals = jdbcTemplate.query("""
                SELECT e.id, e.knowledge_point_id
                  FROM learning_event e
                 WHERE e.user_id = ?
                   AND e.source_type = 'EXERCISE_RECORD'
                   AND e.source_id = ?
                   AND e.event_type = 'EXERCISE_ANSWERED'
                   AND NOT EXISTS (SELECT 1 FROM learning_event r WHERE r.reverses_event_id = e.id)
                 ORDER BY e.id DESC
                 LIMIT 1
                """, (rs, rowNum) -> new OriginalEvent(rs.getLong(1), rs.getLong(2)), userId, record.getId());
        if (originals.isEmpty()) {
            return;
        }
        OriginalEvent original = originals.get(0);
        insertEvent(userId, original.knowledgePointId(), "EXERCISE_DELETED", "EXERCISE_RECORD", record.getId(),
                hash("exercise:" + record.getId() + ":deleted"), null, 0, "deleted", original.id());
        recomputeProfile(userId, original.knowledgePointId());
    }

    @Transactional
    public void recordResourceFeedback(Long userId, UserResourceFeedback feedback) {
        if (!enabled || feedback == null || feedback.getId() == null) {
            return;
        }
        ResourceBank resource = feedback.getResource();
        String label = resource == null ? UNCLASSIFIED : knowledgeLabel(resource.getDomain(), resource.getTitle());
        String state = "rating=" + (feedback.getRating() == null ? "none" : feedback.getRating())
                + ",invalid=" + Boolean.TRUE.equals(feedback.getReportedInvalid());
        recordEvent(userId, label, "RESOURCE_FEEDBACK_SUBMITTED", "RESOURCE_FEEDBACK", feedback.getId(),
                "resource-feedback:" + feedback.getId() + ":" + state, null, 0, state, null);
    }

    @Transactional(readOnly = true)
    public List<MasteryProfileDto> listProfiles(Long userId, Integer requestedLimit) {
        List<MasteryProfileDto> profiles = queryProfiles(userId, requestedLimit);
        for (MasteryProfileDto profile : profiles) {
            profile.setEvidence(findEvidence(userId, profile.getKnowledgePointId(), 5));
        }
        return profiles;
    }

    @Transactional(readOnly = true)
    public List<MasteryProfileDto> listProfilesForAdaptation(Long userId, Integer requestedLimit) {
        return queryProfiles(userId, requestedLimit);
    }

    private List<MasteryProfileDto> queryProfiles(Long userId, Integer requestedLimit) {
        if (!enabled) {
            return List.of();
        }
        int limit = requestedLimit == null || requestedLimit <= 0 ? 20 : Math.min(requestedLimit, 50);
        return jdbcTemplate.query("""
                SELECT p.knowledge_point_id, k.knowledge_key, k.display_name,
                       p.mastery_score, p.confidence, p.effective_weight, p.sample_count,
                       p.algorithm_version, p.calculated_at
                  FROM mastery_profile p
                  JOIN knowledge_point k ON k.id = p.knowledge_point_id
                 WHERE p.user_id = ? AND p.algorithm_version = ?
                 ORDER BY p.confidence DESC, p.mastery_score ASC, k.display_name ASC
                 LIMIT ?
                """, (rs, rowNum) -> {
            MasteryProfileDto dto = new MasteryProfileDto();
            dto.setKnowledgePointId(rs.getLong("knowledge_point_id"));
            dto.setKnowledgeKey(rs.getString("knowledge_key"));
            dto.setDisplayName(rs.getString("display_name"));
            dto.setMasteryScore(rs.getDouble("mastery_score"));
            dto.setConfidence(rs.getDouble("confidence"));
            dto.setEffectiveWeight(rs.getDouble("effective_weight"));
            dto.setSampleCount(rs.getInt("sample_count"));
            dto.setAlgorithmVersion(rs.getString("algorithm_version"));
            dto.setCalculatedAt(rs.getObject("calculated_at", OffsetDateTime.class));
            return dto;
        }, userId, algorithmVersion, limit);
    }

    @Transactional
    public List<MasteryProfileDto> recomputeAll(Long userId, Integer limit) {
        if (!enabled) {
            return List.of();
        }
        List<Long> knowledgePointIds = jdbcTemplate.queryForList("""
                SELECT DISTINCT knowledge_point_id
                  FROM learning_event
                 WHERE user_id = ?
                """, Long.class, userId);
        for (Long knowledgePointId : knowledgePointIds) {
            recomputeProfile(userId, knowledgePointId);
        }
        return listProfiles(userId, limit);
    }

    private void recordEvent(Long userId,
                             String knowledgeLabel,
                             String eventType,
                             String sourceType,
                             Long sourceId,
                             String rawEventKey,
                             Double signalValue,
                             double signalWeight,
                             String evidenceSummary,
                             Long reversesEventId) {
        String normalizedName = normalizeDisplayName(knowledgeLabel);
        Long knowledgePointId = upsertKnowledgePoint(normalizedName);
        Optional<Long> eventId = insertEvent(userId, knowledgePointId, eventType, sourceType, sourceId,
                hash(rawEventKey), signalValue, signalWeight, sanitizeSummary(evidenceSummary), reversesEventId);
        String outcome = eventId.isPresent() ? "inserted" : "duplicate";
        meterRegistry.counter("learnflow.mastery.events", "type", eventType.toLowerCase(Locale.ROOT), "outcome", outcome)
                .increment();
        if (eventId.isPresent() && signalWeight > 0) {
            recomputeProfile(userId, knowledgePointId);
        }
    }

    private Long upsertKnowledgePoint(String displayName) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO knowledge_point (knowledge_key, display_name, taxonomy_version, created_at, updated_at)
                VALUES (?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (knowledge_key) DO UPDATE
                    SET updated_at = CURRENT_TIMESTAMP
                RETURNING id
                """, Long.class, hash(displayName.toLowerCase(Locale.ROOT)), displayName);
    }

    private Optional<Long> insertEvent(Long userId,
                                       Long knowledgePointId,
                                       String eventType,
                                       String sourceType,
                                       Long sourceId,
                                       String eventKey,
                                       Double signalValue,
                                       double signalWeight,
                                       String evidenceSummary,
                                       Long reversesEventId) {
        Span span = telemetryContext.startInternalSpan("mastery.event.record");
        span.setAttribute("learnflow.mastery.event_type", eventType.toLowerCase(Locale.ROOT));
        try (Scope ignored = span.makeCurrent()) {
            List<Long> ids = jdbcTemplate.query(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO learning_event (
                            user_id, knowledge_point_id, event_type, source_type, source_id,
                            event_key, event_version, signal_value, signal_weight, evidence_summary,
                            reverses_event_id, occurred_at, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        ON CONFLICT (event_key) DO NOTHING
                        RETURNING id
                        """);
                statement.setLong(1, userId);
                statement.setLong(2, knowledgePointId);
                statement.setString(3, eventType);
                statement.setString(4, sourceType);
                statement.setLong(5, sourceId);
                statement.setString(6, eventKey);
                if (signalValue == null) statement.setNull(7, Types.NUMERIC); else statement.setDouble(7, signalValue);
                statement.setDouble(8, signalWeight);
                if (evidenceSummary == null) statement.setNull(9, Types.VARCHAR); else statement.setString(9, evidenceSummary);
                if (reversesEventId == null) statement.setNull(10, Types.BIGINT); else statement.setLong(10, reversesEventId);
                return statement;
            }, (rs, rowNum) -> rs.getLong(1));
            return ids.stream().findFirst();
        } catch (RuntimeException failure) {
            span.setStatus(StatusCode.ERROR, failure.getClass().getSimpleName());
            span.setAttribute("error.type", failure.getClass().getSimpleName());
            throw failure;
        } finally {
            span.end();
        }
    }

    private void recomputeProfile(Long userId, Long knowledgePointId) {
        Timer.Sample timer = Timer.start(meterRegistry);
        Span span = telemetryContext.startInternalSpan("mastery.profile.recompute");
        span.setAttribute("learnflow.mastery.algorithm_version", algorithmVersion);
        try (Scope ignored = span.makeCurrent()) {
            long lockKey = 31L * userId + knowledgePointId;
            jdbcTemplate.query(
                    "SELECT pg_advisory_xact_lock(?)",
                    preparedStatement -> preparedStatement.setLong(1, lockKey),
                    resultSet -> null);
            List<SignalRow> rows = jdbcTemplate.query("""
                    SELECT e.id, e.signal_value, e.signal_weight
                      FROM learning_event e
                     WHERE e.user_id = ?
                       AND e.knowledge_point_id = ?
                       AND e.signal_weight > 0
                       AND e.signal_value IS NOT NULL
                       AND NOT EXISTS (SELECT 1 FROM learning_event r WHERE r.reverses_event_id = e.id)
                     ORDER BY e.id
                    """, (rs, rowNum) -> new SignalRow(
                    rs.getLong("id"), rs.getDouble("signal_value"), rs.getDouble("signal_weight")
            ), userId, knowledgePointId);
            MasteryCalculator.Result result = MasteryCalculator.calculate(rows.stream()
                    .map(row -> new MasteryCalculator.WeightedSignal(row.value(), row.weight()))
                    .toList());
            if (result.sampleCount() == 0) {
                jdbcTemplate.update("""
                        DELETE FROM mastery_profile
                         WHERE user_id = ? AND knowledge_point_id = ? AND algorithm_version = ?
                        """, userId, knowledgePointId, algorithmVersion);
                return;
            }
            long lastEventId = rows.get(rows.size() - 1).id();
            jdbcTemplate.update("""
                    INSERT INTO mastery_profile (
                        user_id, knowledge_point_id, algorithm_version, mastery_score, confidence,
                        effective_weight, sample_count, last_event_id, calculated_at, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (user_id, knowledge_point_id, algorithm_version) DO UPDATE
                       SET mastery_score = EXCLUDED.mastery_score,
                           confidence = EXCLUDED.confidence,
                           effective_weight = EXCLUDED.effective_weight,
                           sample_count = EXCLUDED.sample_count,
                           last_event_id = EXCLUDED.last_event_id,
                           calculated_at = CURRENT_TIMESTAMP,
                           updated_at = CURRENT_TIMESTAMP
                    """, userId, knowledgePointId, algorithmVersion, result.score(), result.confidence(),
                    result.effectiveWeight(), result.sampleCount(), lastEventId);
            meterRegistry.counter("learnflow.mastery.recomputations", "algorithm", algorithmVersion, "outcome", "success")
                    .increment();
        } catch (RuntimeException failure) {
            span.setStatus(StatusCode.ERROR, failure.getClass().getSimpleName());
            span.setAttribute("error.type", failure.getClass().getSimpleName());
            meterRegistry.counter("learnflow.mastery.recomputations", "algorithm", algorithmVersion, "outcome", "failure")
                    .increment();
            throw failure;
        } finally {
            timer.stop(meterRegistry.timer("learnflow.mastery.recompute.duration", "algorithm", algorithmVersion));
            span.end();
        }
    }

    private List<MasteryEvidenceDto> findEvidence(Long userId, Long knowledgePointId, int limit) {
        return jdbcTemplate.query("""
                SELECT e.id, e.event_type, e.source_type, e.source_id,
                       e.signal_value, e.signal_weight, e.evidence_summary, e.occurred_at
                  FROM learning_event e
                 WHERE e.user_id = ?
                   AND e.knowledge_point_id = ?
                   AND e.reverses_event_id IS NULL
                   AND NOT EXISTS (SELECT 1 FROM learning_event r WHERE r.reverses_event_id = e.id)
                 ORDER BY e.occurred_at DESC, e.id DESC
                 LIMIT ?
                """, (rs, rowNum) -> {
            MasteryEvidenceDto dto = new MasteryEvidenceDto();
            dto.setEventId(rs.getLong("id"));
            dto.setEventType(rs.getString("event_type"));
            dto.setSourceType(rs.getString("source_type"));
            dto.setSourceId(rs.getLong("source_id"));
            Number signalValue = (Number) rs.getObject("signal_value");
            dto.setSignalValue(signalValue == null ? null : signalValue.doubleValue());
            dto.setSignalWeight(rs.getDouble("signal_weight"));
            dto.setSummary(rs.getString("evidence_summary"));
            dto.setOccurredAt(rs.getObject("occurred_at", OffsetDateTime.class));
            return dto;
        }, userId, knowledgePointId, limit);
    }

    private static String knowledgeLabel(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) return preferred;
        if (fallback != null && !fallback.isBlank()) return fallback;
        return UNCLASSIFIED;
    }

    static String normalizeDisplayName(String value) {
        String normalized = value == null ? UNCLASSIFIED : value
                .replaceAll("(?i)\\bBearer\\s+\\S+", "[redacted]")
                .replaceAll("(?i)https?://\\S+", "[redacted]")
                .replaceAll("(?i)[\\w.%+-]+@[\\w.-]+\\.[a-z]{2,}", "[redacted]")
                .replaceAll("\\b\\d{6,}\\b", "[redacted]")
                .trim()
                .replaceAll("\\s+", " ");
        if (normalized.isBlank()) normalized = UNCLASSIFIED;
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }
    private static String categoricalSummary(String difficulty, String mistakeType) {
        String safeDifficulty = safeCategory(difficulty);
        String safeMistake = safeCategory(mistakeType);
        if (safeDifficulty == null && safeMistake == null) return null;
        return "difficulty=" + (safeDifficulty == null ? "unknown" : safeDifficulty)
                + ",mistake=" + (safeMistake == null ? "none" : safeMistake);
    }

    private static String safeCategory(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]{1,40}")) return "other";
        return normalized;
    }

    private static String sanitizeSummary(String value) {
        if (value == null || value.isBlank()) return null;
        String sanitized = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return sanitized.length() <= 255 ? sanitized : sanitized.substring(0, 255);
    }

    private static String sanitizeAlgorithmVersion(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9._-]{1,64}")) return DEFAULT_ALGORITHM_VERSION;
        return value;
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record Signal(String eventType, Double value, double weight) { }
    private record SignalRow(long id, double value, double weight) { }
    private record OriginalEvent(long id, long knowledgePointId) { }
}

