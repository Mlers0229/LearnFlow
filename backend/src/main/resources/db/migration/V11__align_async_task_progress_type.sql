ALTER TABLE async_task
    ALTER COLUMN progress TYPE INTEGER
    USING progress::INTEGER;
