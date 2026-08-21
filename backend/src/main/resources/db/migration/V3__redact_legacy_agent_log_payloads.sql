-- Agent payloads created before the production logging policy may contain full
-- user answers, prompts, or model output. They are intentionally made
-- unrecoverable during the upgrade; operational tracing keeps metadata only.
UPDATE agent_call_log
SET request_payload = '{"summary":"redacted_legacy_payload"}'
WHERE request_payload IS NOT NULL;

UPDATE agent_call_log
SET response_payload = '{"summary":"redacted_legacy_payload"}'
WHERE response_payload IS NOT NULL;

COMMENT ON COLUMN agent_call_log.request_payload IS
    'Bounded redacted JSON metadata only; never store complete user or model content';
COMMENT ON COLUMN agent_call_log.response_payload IS
    'Bounded redacted JSON metadata only; never store complete user or model content';
