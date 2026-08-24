package com.learnflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnflow.config.LearnFlowPrivacyProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;

@Service
public class PrivacyDataExportService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final LearnFlowPrivacyProperties properties;

    public PrivacyDataExportService(JdbcTemplate jdbc, ObjectMapper objectMapper,
                                    LearnFlowPrivacyProperties properties) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public byte[] build(long userId, UUID requestId) throws JsonProcessingException {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("schemaVersion", 1);
        export.put("format", "learnflow-user-data");
        export.put("requestId", requestId);
        export.put("generatedAt", OffsetDateTime.now(ZoneOffset.UTC));
        export.put("account", one("""
                SELECT id, username, email, role, status, level, created_at, updated_at
                FROM app_user WHERE id = ?
                """, userId));
        export.put("studyPlans", rows("SELECT * FROM study_plan WHERE user_id = ? ORDER BY created_at, id", userId));
        export.put("studyPlanDays", rows("""
                SELECT day.* FROM study_plan_day day
                JOIN study_plan plan ON plan.id = day.plan_id
                WHERE plan.user_id = ? ORDER BY day.date, day.day_index, day.id
                """, userId));
        export.put("exerciseRecords", rows("""
                SELECT id, plan_day_id, question, answer_correct, explanation, difficulty, skill_focus,
                       answer_user, is_correct, score, mistake_type, feedback, next_recommendation, created_at
                FROM exercise_record WHERE user_id = ? ORDER BY created_at, id
                """, userId));
        export.put("resourceFeedback", rows("""
                SELECT id, resource_bank_id, rating, comment, is_reported_invalid, created_at
                FROM user_resource_feedback WHERE user_id = ? ORDER BY created_at, id
                """, userId));
        export.put("uploadedResources", rows("""
                SELECT id, title, url, source_type, ingestion_status, level, domain, duration_minutes,
                       tags, status, created_at, updated_at
                FROM resource_bank WHERE uploader_user_id = ? ORDER BY created_at, id
                """, userId));
        export.put("resourceIngestions", rows("""
                SELECT ingestion.id, ingestion.resource_id, ingestion.source_type, ingestion.source_locator,
                       ingestion.original_filename, ingestion.content_type, ingestion.content_length,
                       ingestion.content_sha256, ingestion.language, ingestion.parser_version,
                       ingestion.chunker_version, ingestion.status, ingestion.created_at, ingestion.finished_at
                FROM resource_ingestion ingestion
                JOIN resource_bank resource ON resource.id = ingestion.resource_id
                WHERE resource.uploader_user_id = ? ORDER BY ingestion.created_at, ingestion.id
                """, userId));
        export.put("learningEvents", rows("SELECT * FROM learning_event WHERE user_id = ? ORDER BY occurred_at, id", userId));
        export.put("masteryProfiles", rows("SELECT * FROM mastery_profile WHERE user_id = ? ORDER BY id", userId));
        export.put("adaptiveAssignments", rows("SELECT * FROM adaptive_policy_assignment WHERE user_id = ? ORDER BY id", userId));
        export.put("adaptiveDecisions", rows("SELECT * FROM adaptive_decision WHERE user_id = ? ORDER BY created_at, id", userId));
        export.put("asyncTasks", rows("""
                SELECT id, task_type, status, progress, attempt_count, max_attempts, result_resource_type,
                       result_resource_id, error_code, created_at, started_at, finished_at, updated_at
                FROM async_task WHERE owner_user_id = ? ORDER BY created_at, id
                """, userId));
        export.put("securityExclusions", List.of(
                "password hashes", "refresh and reset tokens", "internal object keys",
                "workflow checkpoints", "request payloads", "logs and telemetry without direct identity"
        ));
        byte[] bytes = objectMapper.writeValueAsBytes(export);
        if (bytes.length > properties.getMaxExportBytes()) {
            throw new ResponseStatusException(PAYLOAD_TOO_LARGE, "数据导出超过允许大小，请联系数据保护负责人");
        }
        return bytes;
    }

    private Map<String, Object> one(String sql, long userId) {
        List<Map<String, Object>> values = jdbc.queryForList(sql, userId);
        if (values.size() != 1) throw new IllegalStateException("Export subject no longer exists");
        return values.get(0);
    }

    private List<Map<String, Object>> rows(String sql, long userId) {
        return jdbc.queryForList(sql, userId);
    }
}
