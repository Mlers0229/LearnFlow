CREATE TABLE async_task (
    id UUID PRIMARY KEY,
    task_type VARCHAR(64) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    progress SMALLINT NOT NULL DEFAULT 0,
    request_payload TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    next_attempt_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(128),
    lease_expires_at TIMESTAMP(6) WITH TIME ZONE,
    cancel_requested_at TIMESTAMP(6) WITH TIME ZONE,
    deadline_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    result_resource_type VARCHAR(32),
    result_resource_id BIGINT,
    error_code VARCHAR(64),
    error_summary VARCHAR(512),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP(6) WITH TIME ZONE,
    finished_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_async_task_owner FOREIGN KEY (owner_user_id) REFERENCES app_user(id),
    CONSTRAINT uq_async_task_idempotency UNIQUE (owner_user_id, task_type, idempotency_key),
    CONSTRAINT ck_async_task_type CHECK (task_type IN ('PLAN_GENERATION')),
    CONSTRAINT ck_async_task_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_async_task_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT ck_async_task_attempts CHECK (attempt_count >= 0 AND max_attempts BETWEEN 1 AND 10),
    CONSTRAINT ck_async_task_result CHECK (
        (status = 'SUCCEEDED' AND result_resource_type IS NOT NULL AND result_resource_id IS NOT NULL)
        OR status <> 'SUCCEEDED'
    )
);

CREATE INDEX idx_async_task_claim
    ON async_task (status, next_attempt_at, created_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_async_task_expired_lease
    ON async_task (lease_expires_at)
    WHERE status = 'RUNNING';
CREATE INDEX idx_async_task_owner_created
    ON async_task (owner_user_id, created_at DESC);
CREATE INDEX idx_async_task_failed_created
    ON async_task (created_at DESC)
    WHERE status = 'FAILED';

ALTER TABLE study_plan ADD COLUMN source_task_id UUID;
ALTER TABLE study_plan
    ADD CONSTRAINT fk_study_plan_source_task
    FOREIGN KEY (source_task_id) REFERENCES async_task(id);
CREATE UNIQUE INDEX uq_study_plan_source_task
    ON study_plan (source_task_id)
    WHERE source_task_id IS NOT NULL;
