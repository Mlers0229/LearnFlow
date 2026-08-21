\set ON_ERROR_STOP on
BEGIN;
CREATE SCHEMA learnflow_queryplan_verify;
SET LOCAL search_path TO learnflow_queryplan_verify;

CREATE TABLE study_plan (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    title TEXT
);
CREATE INDEX idx_study_plan_user_created ON study_plan (user_id, created_at DESC);

CREATE TABLE study_plan_day (
    id BIGINT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    day_index INTEGER NOT NULL,
    title TEXT
);
CREATE INDEX idx_study_plan_day_plan_index ON study_plan_day (plan_id, day_index);

CREATE TABLE resource_bank (
    id BIGINT PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    title TEXT
);
CREATE INDEX idx_resource_bank_status_created ON resource_bank (status, created_at DESC);

INSERT INTO study_plan
SELECT value, (value % 100) + 1, clock_timestamp() - (value || ' minutes')::interval, 'plan-' || value
FROM generate_series(1, 10000) value;

INSERT INTO study_plan_day
SELECT value, ((value - 1) / 100) + 1, ((value - 1) % 100) + 1, 'day-' || value
FROM generate_series(1, 100000) value;

INSERT INTO resource_bank
SELECT value,
       CASE WHEN value % 5 = 0 THEN 'INACTIVE' ELSE 'ACTIVE' END,
       clock_timestamp() - (value || ' minutes')::interval,
       'resource-' || value
FROM generate_series(1, 10000) value;

ANALYZE study_plan;
ANALYZE study_plan_day;
ANALYZE resource_bank;

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM study_plan WHERE user_id = 1 ORDER BY created_at DESC LIMIT 20;

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM study_plan_day WHERE plan_id = 1 ORDER BY day_index;

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM resource_bank WHERE status = 'ACTIVE' ORDER BY created_at DESC LIMIT 50;

ROLLBACK;
