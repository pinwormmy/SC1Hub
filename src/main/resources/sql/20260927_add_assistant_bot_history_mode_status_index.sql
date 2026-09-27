-- 2026-09-27: index for AssistantBotMapper.countPublishedSinceAllPersonas
-- (WHERE generation_mode = ? AND status = 'published' AND created_at >= ?).
-- The existing indexes lead with persona_name / board_title, so this cross-persona
-- chat min-gap check scanned the whole history table every minute. Idempotent.
SET @abgh_mode_status_created_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = 'assistant_bot_generation_history'
      AND index_name = 'idx_abgh_mode_status_created'
);
SET @abgh_mode_status_created_sql = IF(
    @abgh_mode_status_created_exists = 0,
    'ALTER TABLE assistant_bot_generation_history ADD KEY idx_abgh_mode_status_created (generation_mode, status, created_at)',
    'SELECT 1'
);
PREPARE abgh_mode_status_created_statement FROM @abgh_mode_status_created_sql;
EXECUTE abgh_mode_status_created_statement;
DEALLOCATE PREPARE abgh_mode_status_created_statement;
