ALTER TABLE async_task
    ADD COLUMN pause_requested_at TIMESTAMP(6) WITH TIME ZONE;

ALTER TABLE async_task
    DROP CONSTRAINT ck_async_task_status;
ALTER TABLE async_task
    ADD CONSTRAINT ck_async_task_status
        CHECK (status IN ('PENDING', 'RUNNING', 'PAUSED', 'SUCCEEDED', 'FAILED', 'CANCELLED'));

ALTER TABLE agent_workflow_checkpoint
    DROP CONSTRAINT ck_agent_workflow_checkpoint_outcome;
ALTER TABLE agent_workflow_checkpoint
    ADD CONSTRAINT ck_agent_workflow_checkpoint_outcome
        CHECK (outcome IN ('STARTED', 'COMPLETED', 'SKIPPED', 'FAILED', 'PAUSED', 'RESUMED', 'CANCELLED'));

CREATE INDEX idx_async_task_paused_owner
    ON async_task (owner_user_id, updated_at DESC)
    WHERE status = 'PAUSED';

COMMENT ON COLUMN async_task.pause_requested_at IS
    'User-requested cooperative pause marker; unlike cancellation it preserves payload and checkpoints';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'learnflow_agent') THEN
        GRANT SELECT (id, status) ON async_task TO learnflow_agent;
    END IF;
END
$$;
