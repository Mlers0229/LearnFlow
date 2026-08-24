CREATE TABLE privacy_request (
    id UUID PRIMARY KEY,
    user_id BIGINT,
    subject_ref_hash CHAR(64) NOT NULL,
    request_type VARCHAR(16) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    next_attempt_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(128),
    lease_expires_at TIMESTAMP(6) WITH TIME ZONE,
    artifact_object_key VARCHAR(512),
    artifact_sha256 CHAR(64),
    artifact_size_bytes BIGINT,
    artifact_expires_at TIMESTAMP(6) WITH TIME ZONE,
    artifact_deleted_at TIMESTAMP(6) WITH TIME ZONE,
    error_code VARCHAR(64),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP(6) WITH TIME ZONE,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_privacy_request_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT uq_privacy_request_idempotency UNIQUE (user_id, request_type, request_fingerprint),
    CONSTRAINT ck_privacy_request_subject_hash CHECK (subject_ref_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_privacy_request_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_privacy_request_type CHECK (request_type IN ('EXPORT', 'ERASURE')),
    CONSTRAINT ck_privacy_request_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_privacy_request_attempts CHECK (attempt_count >= 0 AND max_attempts BETWEEN 1 AND 10),
    CONSTRAINT ck_privacy_request_artifact CHECK (
        (artifact_object_key IS NULL AND artifact_sha256 IS NULL AND artifact_size_bytes IS NULL AND artifact_expires_at IS NULL)
        OR
        (request_type = 'EXPORT' AND artifact_object_key IS NOT NULL
            AND artifact_sha256 ~ '^[0-9a-f]{64}$' AND artifact_size_bytes >= 0 AND artifact_expires_at IS NOT NULL)
    )
);

CREATE INDEX idx_privacy_request_claim ON privacy_request (status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'RUNNING');
CREATE INDEX idx_privacy_request_user_created ON privacy_request (user_id, created_at DESC)
    WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_privacy_request_active_erasure ON privacy_request (user_id)
    WHERE user_id IS NOT NULL AND request_type = 'ERASURE' AND status IN ('PENDING', 'RUNNING');

CREATE INDEX idx_privacy_request_artifact_expiry ON privacy_request (artifact_expires_at)
    WHERE artifact_object_key IS NOT NULL AND artifact_deleted_at IS NULL;

COMMENT ON TABLE privacy_request IS 'Durable, privacy-minimized data export and account-erasure workflow state';
COMMENT ON COLUMN privacy_request.subject_ref_hash IS 'HMAC-SHA256 subject reference; never store username or email in this table';
COMMENT ON COLUMN privacy_request.artifact_object_key IS 'Internal object key only; never expose it through an API, log, metric, or trace';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'learnflow_backend') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON privacy_request TO learnflow_backend;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'learnflow_agent') THEN
        REVOKE ALL ON privacy_request FROM learnflow_agent;
    END IF;
END
$$;
