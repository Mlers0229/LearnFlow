CREATE TABLE agent_workflow (
    workflow_id UUID PRIMARY KEY,
    schema_version SMALLINT NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    current_node VARCHAR(24) NOT NULL,
    checkpoint_sequence INTEGER NOT NULL DEFAULT 0,
    state_json JSONB NOT NULL,
    state_checksum CHAR(64) NOT NULL,
    result_resource_id BIGINT,
    last_error_code VARCHAR(64),
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '7 days'),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_agent_workflow_task
        FOREIGN KEY (workflow_id) REFERENCES async_task(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_workflow_result
        FOREIGN KEY (result_resource_id) REFERENCES study_plan(id),
    CONSTRAINT ck_agent_workflow_schema_version CHECK (schema_version = 1),
    CONSTRAINT ck_agent_workflow_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_agent_workflow_checksum CHECK (state_checksum ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_agent_workflow_status CHECK (
        status IN ('RUNNING', 'PAUSED', 'READY_TO_SAVE', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_agent_workflow_node CHECK (
        current_node IN ('GOAL', 'SCHEDULE', 'PLAN', 'VALIDATE', 'REPLAN', 'SAVE')
    ),
    CONSTRAINT ck_agent_workflow_sequence CHECK (checkpoint_sequence >= 0),
    CONSTRAINT ck_agent_workflow_result CHECK (
        (status = 'SUCCEEDED' AND result_resource_id IS NOT NULL)
        OR (status <> 'SUCCEEDED' AND result_resource_id IS NULL)
    )
);

CREATE TABLE agent_workflow_checkpoint (
    workflow_id UUID NOT NULL,
    sequence INTEGER NOT NULL,
    schema_version SMALLINT NOT NULL,
    node VARCHAR(24) NOT NULL,
    outcome VARCHAR(24) NOT NULL,
    node_attempt INTEGER NOT NULL,
    state_json JSONB NOT NULL,
    state_checksum CHAR(64) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (workflow_id, sequence),
    CONSTRAINT fk_agent_workflow_checkpoint_workflow
        FOREIGN KEY (workflow_id) REFERENCES agent_workflow(workflow_id) ON DELETE CASCADE,
    CONSTRAINT ck_agent_workflow_checkpoint_schema_version CHECK (schema_version = 1),
    CONSTRAINT ck_agent_workflow_checkpoint_node CHECK (
        node IN ('GOAL', 'SCHEDULE', 'PLAN', 'VALIDATE', 'REPLAN', 'SAVE')
    ),
    CONSTRAINT ck_agent_workflow_checkpoint_outcome CHECK (
        outcome IN ('STARTED', 'COMPLETED', 'SKIPPED', 'FAILED', 'PAUSED', 'CANCELLED')
    ),
    CONSTRAINT ck_agent_workflow_checkpoint_attempt CHECK (node_attempt >= 0),
    CONSTRAINT ck_agent_workflow_checkpoint_checksum CHECK (state_checksum ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_agent_workflow_status_updated
    ON agent_workflow (status, updated_at);
CREATE INDEX idx_agent_workflow_expiry
    ON agent_workflow (expires_at)
    WHERE status IN ('SUCCEEDED', 'FAILED', 'CANCELLED');
CREATE INDEX idx_agent_workflow_checkpoint_created
    ON agent_workflow_checkpoint (created_at);

COMMENT ON TABLE agent_workflow IS
    'Versioned resumable Agent state; access is restricted to Backend, Agent, and migrator roles';
COMMENT ON COLUMN agent_workflow.state_json IS
    'Operational workflow state required for resume; never copy this field into logs or telemetry';
COMMENT ON TABLE agent_workflow_checkpoint IS
    'Append-only node transition history used for deterministic resume and diagnosis';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'learnflow_agent') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON agent_workflow TO learnflow_agent;
        GRANT SELECT, INSERT ON agent_workflow_checkpoint TO learnflow_agent;
    END IF;
END
$$;
