\set ON_ERROR_STOP on

GRANT USAGE ON SCHEMA public TO learnflow_backend, learnflow_agent;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO learnflow_backend;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO learnflow_backend;

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM learnflow_agent;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM learnflow_agent;
GRANT SELECT ON resource_bank, user_resource_feedback TO learnflow_agent;
GRANT SELECT, INSERT, DELETE ON agent_call_log TO learnflow_agent;
GRANT USAGE, SELECT ON SEQUENCE agent_call_log_id_seq TO learnflow_agent;

ALTER DEFAULT PRIVILEGES FOR ROLE learnflow_migrator IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO learnflow_backend;
ALTER DEFAULT PRIVILEGES FOR ROLE learnflow_migrator IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO learnflow_backend;
