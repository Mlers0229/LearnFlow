\set ON_ERROR_STOP on
BEGIN;
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

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM study_plan_day WHERE plan_id = 1 ORDER BY day_index;

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM study_plan WHERE user_id = 1 ORDER BY created_at DESC LIMIT 20;

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM resource_bank WHERE status = 'ACTIVE' ORDER BY created_at DESC LIMIT 50;
ROLLBACK;
