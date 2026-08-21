ALTER TABLE async_task
    ADD COLUMN traceparent VARCHAR(55),
    ADD COLUMN request_id VARCHAR(64);

ALTER TABLE async_task
    ADD CONSTRAINT ck_async_task_traceparent CHECK (
        traceparent IS NULL
        OR traceparent ~ '^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$'
    ),
    ADD CONSTRAINT ck_async_task_request_id CHECK (
        request_id IS NULL
        OR request_id ~ '^[A-Za-z0-9._-]{8,64}$'
    );

COMMENT ON COLUMN async_task.traceparent IS
    'W3C trace context only; never stores authorization or user content';
COMMENT ON COLUMN async_task.request_id IS
    'Bounded request correlation identifier propagated from the trusted gateway';
