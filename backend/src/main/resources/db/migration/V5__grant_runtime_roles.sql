DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'learnflow_backend') THEN
        GRANT USAGE ON SCHEMA public TO learnflow_backend;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO learnflow_backend;
        GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO learnflow_backend;
        EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE learnflow_migrator IN SCHEMA public '
                'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO learnflow_backend';
        EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE learnflow_migrator IN SCHEMA public '
                'GRANT USAGE, SELECT ON SEQUENCES TO learnflow_backend';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'learnflow_agent') THEN
        GRANT USAGE ON SCHEMA public TO learnflow_agent;
        GRANT SELECT ON resource_bank, user_resource_feedback TO learnflow_agent;
        GRANT SELECT, INSERT, DELETE ON agent_call_log TO learnflow_agent;
        GRANT USAGE, SELECT ON SEQUENCE agent_call_log_id_seq TO learnflow_agent;
    END IF;
END
$$;
