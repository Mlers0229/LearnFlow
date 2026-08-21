package com.learnflow.service;

import com.learnflow.config.LearnFlowEmbeddingProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ResourceEmbeddingService {

    private static final long ACTIVATION_LOCK = 0x4c46564543544f52L;

    private final LearnFlowEmbeddingProperties properties;
    private final JdbcTemplate jdbc;
    private final AgentHttpClient agentHttpClient;
    private final AsyncTaskService tasks;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate transactionTemplate;

    public ResourceEmbeddingService(
            LearnFlowEmbeddingProperties properties,
            JdbcTemplate jdbc,
            AgentHttpClient agentHttpClient,
            AsyncTaskService tasks,
            MeterRegistry meterRegistry,
            PlatformTransactionManager transactionManager
    ) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.agentHttpClient = agentHttpClient;
        this.tasks = tasks;
        this.meterRegistry = meterRegistry;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public void enqueueForIngestion(UUID ingestionId, long resourceId, long ownerUserId) {
        if (!properties.isEnabled()) {
            return;
        }
        tasks.createResourceEmbeddingTask(
                ingestionId,
                resourceId,
                ownerUserId,
                properties.getVersion()
        );
    }

    public long process(UUID taskId, UUID ingestionId, String embeddingVersion) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Dense embedding is disabled");
        }
        if (!properties.getVersion().equals(embeddingVersion)) {
            throw new IllegalStateException("Embedding task targets a superseded model version");
        }
        long startedAt = System.nanoTime();
        String outcome = "success";
        String reason = "none";
        try {
            ensureConfiguredVersion();
            Long resourceId = jdbc.queryForObject(
                    "select resource_id from resource_ingestion where id = ? and status = 'SUCCEEDED'",
                    Long.class,
                    ingestionId
            );
            if (resourceId == null) {
                throw new IllegalStateException("Embedding ingestion has no resource reference");
            }
            List<ChunkInput> chunks = jdbc.query(
                    """
                    select c.id, c.content_hash, c.content
                    from resource_ingestion_chunk ic
                    join resource_chunk c on c.id = ic.chunk_id
                    join resource_bank r on r.id = c.resource_id and r.current_ingestion_id = ic.ingestion_id
                    left join resource_chunk_embedding e
                      on e.chunk_id = c.id and e.embedding_version = ?
                    where ic.ingestion_id = ? and e.chunk_id is null
                    order by ic.ordinal
                    """,
                    (rs, row) -> new ChunkInput(
                            rs.getObject("id", UUID.class),
                            rs.getString("content_hash"),
                            rs.getString("content")
                    ),
                    properties.getVersion(),
                    ingestionId
            );
            int total = chunks.size();
            for (int start = 0; start < total; start += properties.getBatchSize()) {
                int end = Math.min(total, start + properties.getBatchSize());
                persistBatch(taskId, chunks.subList(start, end));
                int progress = total == 0 ? 90 : 10 + (int) Math.floor(80.0 * end / total);
                tasks.updateProgress(taskId, progress);
                if (tasks.isCancellationRequested(taskId)) {
                    throw new EmbeddingTaskCancelledException();
                }
                if (tasks.isDeadlineExceeded(taskId)) {
                    throw new EmbeddingTaskTimeoutException();
                }
            }
            activateWhenComplete();
            return resourceId;
        } catch (RuntimeException failure) {
            outcome = "failure";
            reason = safeReason(failure);
            throw failure;
        } finally {
            Tags tags = Tags.of("outcome", outcome, "reason", reason, "version", safeVersionLabel());
            meterRegistry.counter("learnflow.resource.embedding.tasks", tags).increment();
            meterRegistry.timer("learnflow.resource.embedding.duration", tags)
                    .record(Duration.ofNanos(System.nanoTime() - startedAt));
        }
    }

    @Scheduled(fixedDelayString = "${learnflow.embedding.backfill-interval-ms:30000}")
    public void enqueueMissingCurrentIngestions() {
        if (!properties.isEnabled()) {
            return;
        }
        ensureConfiguredVersion();
        List<BackfillTarget> targets = jdbc.query(
                """
                select r.current_ingestion_id, r.id, r.uploader_user_id
                from resource_bank r
                join resource_ingestion i on i.id = r.current_ingestion_id and i.status = 'SUCCEEDED'
                where r.uploader_user_id is not null
                  and exists (
                    select 1
                    from resource_ingestion_chunk ic
                    left join resource_chunk_embedding e
                      on e.chunk_id = ic.chunk_id and e.embedding_version = ?
                    where ic.ingestion_id = r.current_ingestion_id and e.chunk_id is null
                  )
                order by r.updated_at asc nulls first, r.id
                limit ?
                """,
                (rs, row) -> new BackfillTarget(
                        rs.getObject("current_ingestion_id", UUID.class),
                        rs.getLong("id"),
                        rs.getLong("uploader_user_id")
                ),
                properties.getVersion(),
                properties.getBackfillPageSize()
        );
        for (BackfillTarget target : targets) {
            tasks.createResourceEmbeddingTask(
                    target.ingestionId(),
                    target.resourceId(),
                    target.ownerUserId(),
                    properties.getVersion()
            );
        }
    }

    private void ensureConfiguredVersion() {
        validateLabels();
        jdbc.update(
                """
                insert into embedding_model_version(version, provider, model_name, dimensions, status)
                values (?, ?, ?, ?, 'BUILDING')
                on conflict (version) do nothing
                """,
                properties.getVersion(),
                properties.getProvider(),
                properties.getModel(),
                properties.getDimensions()
        );
        VersionDefinition existing = jdbc.queryForObject(
                "select provider, model_name, dimensions from embedding_model_version where version = ?",
                (rs, row) -> new VersionDefinition(
                        rs.getString("provider"),
                        rs.getString("model_name"),
                        rs.getInt("dimensions")
                ),
                properties.getVersion()
        );
        if (existing == null
                || !properties.getProvider().equals(existing.provider())
                || !properties.getModel().equals(existing.model())
                || properties.getDimensions() != existing.dimensions()) {
            throw new IllegalStateException("Embedding version is already bound to a different model definition");
        }
    }

    private void persistBatch(UUID taskId, List<ChunkInput> chunks) {
        List<EmbeddingInput> inputs = chunks.stream()
                .map(chunk -> new EmbeddingInput(chunk.id(), chunk.content()))
                .toList();
        EmbeddingResponse response = agentHttpClient.postJsonForTask(
                taskId,
                AgentOperation.RAG,
                "/api/internal/embeddings",
                new EmbeddingRequest(
                        properties.getVersion(),
                        properties.getModel(),
                        properties.getDimensions(),
                        inputs
                ),
                EmbeddingResponse.class
        );
        if (response == null
                || !properties.getVersion().equals(response.version())
                || !properties.getModel().equals(response.model())
                || response.dimensions() != properties.getDimensions()
                || response.items() == null) {
            throw new IllegalStateException("Embedding provider returned an incompatible response");
        }
        Map<UUID, List<Double>> vectors = new HashMap<>();
        for (EmbeddingOutput item : response.items()) {
            if (item != null && item.chunkId() != null) {
                vectors.put(item.chunkId(), item.embedding());
            }
        }
        if (vectors.size() != chunks.size()) {
            throw new IllegalStateException("Embedding provider returned an incomplete batch");
        }
        for (ChunkInput chunk : chunks) {
            String vector = vectorLiteral(vectors.get(chunk.id()));
            jdbc.update(
                    """
                    insert into resource_chunk_embedding(
                        chunk_id, embedding_version, content_hash, embedding, created_at, updated_at
                    ) values (?, ?, ?, cast(? as vector), current_timestamp, current_timestamp)
                    on conflict (chunk_id, embedding_version) do update
                    set content_hash = excluded.content_hash,
                        embedding = excluded.embedding,
                        updated_at = current_timestamp
                    where resource_chunk_embedding.content_hash <> excluded.content_hash
                    """,
                    chunk.id(),
                    properties.getVersion(),
                    chunk.contentHash(),
                    vector
            );
        }
        meterRegistry.summary(
                "learnflow.resource.embedding.batch.size",
                Tags.of("version", safeVersionLabel())
        ).record(chunks.size());
    }

    private void activateWhenComplete() {
        transactionTemplate.executeWithoutResult(status -> {
            jdbc.query("select pg_advisory_xact_lock(?)", resultSet -> null, ACTIVATION_LOCK);
            Long missing = jdbc.queryForObject(
                    """
                    select count(*)
                    from resource_bank r
                    join resource_ingestion_chunk ic on ic.ingestion_id = r.current_ingestion_id
                    left join resource_chunk_embedding e
                      on e.chunk_id = ic.chunk_id and e.embedding_version = ?
                    where r.current_ingestion_id is not null
                      and r.ingestion_status = 'SUCCEEDED'
                      and e.chunk_id is null
                    """,
                    Long.class,
                    properties.getVersion()
            );
            if (missing != null && missing == 0) {
                jdbc.update(
                        """
                        update embedding_model_version
                        set status = 'RETIRED', retired_at = current_timestamp
                        where status = 'ACTIVE' and version <> ?
                        """,
                        properties.getVersion()
                );
                jdbc.update(
                        """
                        update embedding_model_version
                        set status = 'ACTIVE', activated_at = coalesce(activated_at, current_timestamp), retired_at = null
                        where version = ? and status <> 'ACTIVE'
                        """,
                        properties.getVersion()
                );
            }
        });
    }

    private String vectorLiteral(List<Double> values) {
        if (values == null || values.size() != properties.getDimensions()) {
            throw new IllegalStateException("Embedding vector has an invalid dimension");
        }
        List<String> normalized = new ArrayList<>(values.size());
        for (Double value : values) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalStateException("Embedding vector contains a non-finite value");
            }
            normalized.add(Double.toString(value));
        }
        return "[" + String.join(",", normalized) + "]";
    }

    private void validateLabels() {
        if (!properties.getVersion().matches("[A-Za-z0-9._:-]{1,64}")) {
            throw new IllegalStateException("Embedding version contains unsupported characters");
        }
        if (properties.getProvider().length() > 64 || properties.getModel().length() > 128) {
            throw new IllegalStateException("Embedding provider or model label is too long");
        }
    }

    private String safeVersionLabel() {
        return properties.getVersion().matches("[A-Za-z0-9._:-]{1,64}")
                ? properties.getVersion()
                : "other";
    }

    private static String safeReason(Throwable failure) {
        String value = failure.getClass().getSimpleName().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_");
        return value.substring(0, Math.min(48, value.length()));
    }

    private record ChunkInput(UUID id, String contentHash, String content) {}
    private record BackfillTarget(UUID ingestionId, long resourceId, long ownerUserId) {}
    private record VersionDefinition(String provider, String model, int dimensions) {}
    public record EmbeddingInput(UUID chunkId, String text) {}
    public record EmbeddingRequest(String version, String model, int dimensions, List<EmbeddingInput> items) {}
    public record EmbeddingOutput(UUID chunkId, List<Double> embedding) {}
    public record EmbeddingResponse(String version, String model, int dimensions, List<EmbeddingOutput> items) {}

    public static class EmbeddingTaskCancelledException extends RuntimeException {}
    public static class EmbeddingTaskTimeoutException extends RuntimeException {}
}
