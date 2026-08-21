-- Databases adopted with baselineVersion=1 did not execute V1 and therefore
-- need the baseline indexes applied explicitly. IF NOT EXISTS keeps empty-db
-- V1 -> V6 migrations idempotent.
CREATE INDEX IF NOT EXISTS idx_study_plan_user_created
    ON study_plan (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_study_plan_day_plan_index
    ON study_plan_day (plan_id, day_index);
CREATE INDEX IF NOT EXISTS idx_exercise_record_user_created
    ON exercise_record (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_exercise_record_day
    ON exercise_record (plan_day_id);
CREATE INDEX IF NOT EXISTS idx_resource_bank_status_created
    ON resource_bank (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_resource_bank_uploader_created
    ON resource_bank (uploader_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_resource_feedback_user_resource
    ON user_resource_feedback (user_id, resource_bank_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_refresh_token_family
    ON refresh_token_session (family_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_expiry
    ON refresh_token_session (expires_at);
CREATE INDEX IF NOT EXISTS idx_agent_call_log_trace
    ON agent_call_log (trace_id);
CREATE INDEX IF NOT EXISTS idx_agent_call_log_created
    ON agent_call_log (created_at DESC);
