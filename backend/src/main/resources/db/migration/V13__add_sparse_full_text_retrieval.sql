ALTER TABLE resource_chunk
    ADD COLUMN search_vector TSVECTOR
        GENERATED ALWAYS AS (to_tsvector('simple', coalesce(content, ''))) STORED;

CREATE INDEX idx_resource_chunk_search_vector_gin
    ON resource_chunk
    USING GIN (search_vector);

COMMENT ON COLUMN resource_chunk.search_vector IS
    'Versioned Chunk full-text document. The simple configuration avoids language-specific stemming.';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'learnflow_agent') THEN
        GRANT SELECT ON resource_bank, resource_ingestion, resource_chunk, resource_ingestion_chunk
            TO learnflow_agent;
    END IF;
END
$$;
