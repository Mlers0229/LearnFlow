#!/usr/bin/env bash
set -Eeuo pipefail

: "${LEARNFLOW_MIGRATION_DB_PASSWORD:?LEARNFLOW_MIGRATION_DB_PASSWORD is required}"
: "${LEARNFLOW_BACKEND_DB_PASSWORD:?LEARNFLOW_BACKEND_DB_PASSWORD is required}"
: "${LEARNFLOW_AGENT_DB_PASSWORD:?LEARNFLOW_AGENT_DB_PASSWORD is required}"

psql --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=db_name="$POSTGRES_DB" \
  --set=migration_password="$LEARNFLOW_MIGRATION_DB_PASSWORD" \
  --set=backend_password="$LEARNFLOW_BACKEND_DB_PASSWORD" \
  --set=agent_password="$LEARNFLOW_AGENT_DB_PASSWORD" <<'SQL'
CREATE EXTENSION IF NOT EXISTS vector;

SELECT format('CREATE ROLE learnflow_migrator LOGIN PASSWORD %L', :'migration_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'learnflow_migrator') \gexec
SELECT format('CREATE ROLE learnflow_backend LOGIN PASSWORD %L', :'backend_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'learnflow_backend') \gexec
SELECT format('CREATE ROLE learnflow_agent LOGIN PASSWORD %L', :'agent_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'learnflow_agent') \gexec

ALTER ROLE learnflow_migrator PASSWORD :'migration_password';
ALTER ROLE learnflow_backend PASSWORD :'backend_password';
ALTER ROLE learnflow_agent PASSWORD :'agent_password';

GRANT CONNECT ON DATABASE :"db_name" TO learnflow_migrator, learnflow_backend, learnflow_agent;
GRANT USAGE, CREATE ON SCHEMA public TO learnflow_migrator;
GRANT USAGE ON SCHEMA public TO learnflow_backend, learnflow_agent;

ALTER DEFAULT PRIVILEGES FOR ROLE learnflow_migrator IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO learnflow_backend;
ALTER DEFAULT PRIVILEGES FOR ROLE learnflow_migrator IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO learnflow_backend;

SQL
