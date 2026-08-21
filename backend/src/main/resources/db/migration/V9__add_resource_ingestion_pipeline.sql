ALTER TABLE resource_bank
    ALTER COLUMN url DROP NOT NULL,
    ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT 'URL',
    ADD COLUMN ingestion_status VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN current_ingestion_id UUID;

ALTER TABLE resource_bank
    ADD CONSTRAINT ck_resource_bank_source_type
        CHECK (source_type IN ('URL', 'TEXT', 'DOCUMENT')),
    ADD CONSTRAINT ck_resource_bank_ingestion_status
        CHECK (ingestion_status IN ('NOT_STARTED', 'PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED'));

CREATE TABLE resource_ingestion (
    id UUID PRIMARY KEY,
    resource_id BIGINT NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    source_locator TEXT,
    object_key VARCHAR(512),
    original_filename VARCHAR(255),
    content_type VARCHAR(128),
    content_length BIGINT,
    content_sha256 CHAR(64),
    language VARCHAR(16),
    parser_version VARCHAR(64) NOT NULL,
    chunker_version VARCHAR(64) NOT NULL,
    rights_confirmed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL,
    error_code VARCHAR(64),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP(6) WITH TIME ZONE,
    finished_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_resource_ingestion_resource FOREIGN KEY (resource_id) REFERENCES resource_bank(id) ON DELETE CASCADE,
    CONSTRAINT ck_resource_ingestion_source_type CHECK (source_type IN ('URL', 'TEXT', 'DOCUMENT')),
    CONSTRAINT ck_resource_ingestion_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_resource_ingestion_content_length CHECK (content_length IS NULL OR content_length >= 0)
);

CREATE TABLE resource_chunk (
    id UUID PRIMARY KEY,
    resource_id BIGINT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    content TEXT NOT NULL,
    language VARCHAR(16),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_resource_chunk_resource FOREIGN KEY (resource_id) REFERENCES resource_bank(id) ON DELETE CASCADE,
    CONSTRAINT uq_resource_chunk_content UNIQUE (resource_id, content_hash),
    CONSTRAINT ck_resource_chunk_content_not_blank CHECK (length(btrim(content)) > 0)
);

CREATE TABLE resource_ingestion_chunk (
    ingestion_id UUID NOT NULL,
    chunk_id UUID NOT NULL,
    ordinal INTEGER NOT NULL,
    char_start INTEGER NOT NULL,
    char_end INTEGER NOT NULL,
    PRIMARY KEY (ingestion_id, ordinal),
    CONSTRAINT fk_resource_ingestion_chunk_ingestion FOREIGN KEY (ingestion_id) REFERENCES resource_ingestion(id) ON DELETE CASCADE,
    CONSTRAINT fk_resource_ingestion_chunk_chunk FOREIGN KEY (chunk_id) REFERENCES resource_chunk(id) ON DELETE RESTRICT,
    CONSTRAINT ck_resource_ingestion_chunk_ordinal CHECK (ordinal >= 0),
    CONSTRAINT ck_resource_ingestion_chunk_offsets CHECK (char_start >= 0 AND char_end > char_start)
);

ALTER TABLE resource_bank
    ADD CONSTRAINT fk_resource_bank_current_ingestion
        FOREIGN KEY (current_ingestion_id) REFERENCES resource_ingestion(id) ON DELETE SET NULL;

ALTER TABLE async_task DROP CONSTRAINT ck_async_task_type;
ALTER TABLE async_task
    ADD CONSTRAINT ck_async_task_type CHECK (task_type IN ('PLAN_GENERATION', 'RESOURCE_INGESTION'));

CREATE INDEX idx_resource_ingestion_resource_created
    ON resource_ingestion (resource_id, created_at DESC);
CREATE INDEX idx_resource_ingestion_status_created
    ON resource_ingestion (status, created_at);
CREATE INDEX idx_resource_chunk_resource
    ON resource_chunk (resource_id, created_at DESC);
CREATE INDEX idx_resource_ingestion_chunk_chunk
    ON resource_ingestion_chunk (chunk_id);
CREATE INDEX idx_resource_bank_ingestion_status
    ON resource_bank (ingestion_status, updated_at DESC);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'learnflow_backend') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE
            ON resource_ingestion, resource_chunk, resource_ingestion_chunk
            TO learnflow_backend;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'learnflow_agent') THEN
        GRANT SELECT ON resource_ingestion, resource_chunk, resource_ingestion_chunk TO learnflow_agent;
    END IF;
END
$$;
