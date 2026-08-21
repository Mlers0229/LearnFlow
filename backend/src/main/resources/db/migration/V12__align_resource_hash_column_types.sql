ALTER TABLE resource_ingestion
    ALTER COLUMN content_sha256 TYPE VARCHAR(64)
    USING content_sha256::VARCHAR(64);

ALTER TABLE resource_chunk
    ALTER COLUMN content_hash TYPE VARCHAR(64)
    USING content_hash::VARCHAR(64);

ALTER TABLE resource_chunk_embedding
    ALTER COLUMN content_hash TYPE VARCHAR(64)
    USING content_hash::VARCHAR(64);
