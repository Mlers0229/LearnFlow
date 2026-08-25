-- Read-only staging qualification for Flyway V1-V18, pgvector, critical indexes, and runtime ACLs.
-- Run as the database owner. This script does not read business payloads or mutate data.

\set ON_ERROR_STOP on
\pset pager off
select 'flyway' as check_name, count(*)::text || ' successful, max V' || max(version::integer) as result
from flyway_schema_history where success = true;
select 'vector' as check_name, extversion as result from pg_extension where extname = 'vector';
select 'critical_indexes' as check_name,
       count(*)::text || '/6 present' as result
from (values
  ('idx_study_plan_day_plan_index'),
  ('idx_resource_chunk_embedding_hnsw'),
  ('idx_resource_chunk_search_vector_gin'),
  ('idx_agent_workflow_status_updated'),
  ('idx_learning_event_profile_replay'),
  ('idx_mastery_profile_user_confidence')
) expected(index_name)
where to_regclass('public.' || index_name) is not null;
select 'runtime_roles' as check_name,
       count(*)::text || '/3 present' as result
from pg_roles where rolname in ('learnflow_migrator', 'learnflow_backend', 'learnflow_agent');

DO $qualification$
BEGIN
  IF (select count(*) from flyway_schema_history where success = true) <> 18
     OR (select max(version::integer) from flyway_schema_history where success = true) <> 18 THEN
    RAISE EXCEPTION 'Flyway V1-V18 qualification failed';
  END IF;
  IF NOT EXISTS (select 1 from pg_extension where extname = 'vector') THEN
    RAISE EXCEPTION 'pgvector extension missing';
  END IF;
  IF NOT (
    to_regclass('public.idx_study_plan_day_plan_index') is not null AND
    to_regclass('public.idx_resource_chunk_embedding_hnsw') is not null AND
    to_regclass('public.idx_resource_chunk_search_vector_gin') is not null AND
    to_regclass('public.idx_agent_workflow_status_updated') is not null AND
    to_regclass('public.idx_learning_event_profile_replay') is not null AND
    to_regclass('public.idx_mastery_profile_user_confidence') is not null
  ) THEN
    RAISE EXCEPTION 'critical index qualification failed';
  END IF;
  IF NOT (
    has_table_privilege('learnflow_backend', 'app_user', 'SELECT') AND
    has_table_privilege('learnflow_backend', 'app_user', 'INSERT') AND
    has_table_privilege('learnflow_backend', 'async_task', 'SELECT') AND
    has_table_privilege('learnflow_backend', 'async_task', 'UPDATE') AND
    has_table_privilege('learnflow_backend', 'resource_chunk_embedding', 'INSERT') AND
    has_table_privilege('learnflow_backend', 'learning_event', 'INSERT') AND
    has_table_privilege('learnflow_backend', 'mastery_profile', 'UPDATE')
  ) THEN
    RAISE EXCEPTION 'backend ACL qualification failed';
  END IF;
  IF NOT (
    has_table_privilege('learnflow_agent', 'resource_bank', 'SELECT') AND
    has_table_privilege('learnflow_agent', 'agent_call_log', 'INSERT') AND
    has_table_privilege('learnflow_agent', 'agent_call_log', 'DELETE') AND
    has_table_privilege('learnflow_agent', 'agent_workflow', 'SELECT') AND
    has_table_privilege('learnflow_agent', 'agent_workflow', 'INSERT') AND
    has_table_privilege('learnflow_agent', 'agent_workflow', 'UPDATE') AND
    has_table_privilege('learnflow_agent', 'agent_workflow', 'DELETE') AND
    has_table_privilege('learnflow_agent', 'agent_workflow_checkpoint', 'SELECT') AND
    has_table_privilege('learnflow_agent', 'agent_workflow_checkpoint', 'INSERT') AND
    has_table_privilege('learnflow_agent', 'resource_chunk_embedding', 'SELECT') AND
    has_table_privilege('learnflow_agent', 'resource_chunk', 'SELECT')
  ) THEN
    RAISE EXCEPTION 'agent positive ACL qualification failed';
  END IF;
  IF (
    has_table_privilege('learnflow_agent', 'agent_workflow_checkpoint', 'UPDATE') OR
    has_table_privilege('learnflow_agent', 'app_user', 'SELECT') OR
    has_table_privilege('learnflow_agent', 'async_task', 'SELECT') OR
    has_column_privilege('learnflow_agent', 'async_task', 'request_payload', 'SELECT') OR
    has_table_privilege('learnflow_agent', 'resource_chunk_embedding', 'INSERT') OR
    has_table_privilege('learnflow_agent', 'embedding_model_version', 'UPDATE') OR
    has_table_privilege('learnflow_agent', 'resource_chunk', 'INSERT') OR
    has_table_privilege('learnflow_agent', 'learning_event', 'SELECT') OR
    has_table_privilege('learnflow_agent', 'mastery_profile', 'SELECT')
  ) THEN
    RAISE EXCEPTION 'agent negative ACL qualification failed';
  END IF;
  IF NOT (
    has_column_privilege('learnflow_agent', 'async_task', 'id', 'SELECT') AND
    has_column_privilege('learnflow_agent', 'async_task', 'status', 'SELECT')
  ) THEN
    RAISE EXCEPTION 'agent async_task column ACL qualification failed';
  END IF;
END $qualification$;

select 'database_acl' as check_name, 'PASS' as result;