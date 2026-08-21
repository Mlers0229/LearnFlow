\set ON_ERROR_STOP on

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM study_plan WHERE user_id = 1 ORDER BY created_at DESC LIMIT 20;

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM study_plan_day WHERE plan_id = 1 ORDER BY day_index;

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM exercise_record WHERE user_id = 1 ORDER BY created_at DESC LIMIT 50;

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM resource_bank WHERE status = 'ACTIVE' ORDER BY created_at DESC LIMIT 50;

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM user_resource_feedback
WHERE user_id = 1 AND resource_bank_id = 1
ORDER BY created_at DESC LIMIT 20;

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM admin_audit_log ORDER BY created_at DESC LIMIT 200;
