CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE embedding_model_version (
    version VARCHAR(64) PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    dimensions INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMP(6) WITH TIME ZONE,
    retired_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT ck_embedding_model_dimensions CHECK (dimensions = 1536),
    CONSTRAINT ck_embedding_model_status CHECK (status IN ('BUILDING', 'ACTIVE', 'RETIRED', 'FAILED')),
    CONSTRAINT ck_embedding_model_activation CHECK (
        (status = 'ACTIVE' AND activated_at IS NOT NULL AND retired_at IS NULL)
        OR (status = 'RETIRED' AND activated_at IS NOT NULL AND retired_at IS NOT NULL)
        OR status IN ('BUILDING', 'FAILED')
    )
);

CREATE UNIQUE INDEX uq_embedding_model_active
    ON embedding_model_version ((status))
    WHERE status = 'ACTIVE';

CREATE TABLE resource_chunk_embedding (
    chunk_id UUID NOT NULL,
    embedding_version VARCHAR(64) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    embedding vector(1536) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (chunk_id, embedding_version),
    CONSTRAINT fk_resource_chunk_embedding_chunk
        FOREIGN KEY (chunk_id) REFERENCES resource_chunk(id) ON DELETE CASCADE,
    CONSTRAINT fk_resource_chunk_embedding_version
        FOREIGN KEY (embedding_version) REFERENCES embedding_model_version(version) ON DELETE RESTRICT
);

CREATE INDEX idx_resource_chunk_embedding_version
    ON resource_chunk_embedding (embedding_version, chunk_id);

CREATE INDEX idx_resource_chunk_embedding_hnsw
    ON resource_chunk_embedding
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

ALTER TABLE async_task DROP CONSTRAINT ck_async_task_type;
ALTER TABLE async_task
    ADD CONSTRAINT ck_async_task_type
        CHECK (task_type IN ('PLAN_GENERATION', 'RESOURCE_INGESTION', 'RESOURCE_EMBEDDING'));

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'learnflow_backend') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE
            ON embedding_model_version, resource_chunk_embedding
            TO learnflow_backend;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'learnflow_agent') THEN
        GRANT SELECT ON embedding_model_version, resource_chunk_embedding TO learnflow_agent;
    END IF;
END
$$;
