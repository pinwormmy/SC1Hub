CREATE TABLE IF NOT EXISTS visitor_daily_identity (
    visit_date DATE NOT NULL,
    visitor_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (visit_date, visitor_hash),
    INDEX idx_visitor_daily_identity_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Atomic daily upserts require one visitor_count row per date. Consolidate any
-- legacy duplicates before adding the unique key. This uses only ordinary SQL
-- because hosted database accounts may not have CREATE ROUTINE permission.
DROP TEMPORARY TABLE IF EXISTS visitor_count_daily_rollup;
CREATE TEMPORARY TABLE visitor_count_daily_rollup AS
SELECT MIN(id) AS keep_id,
       date AS visit_date,
       SUM(daily_count) AS daily_count
FROM visitor_count
GROUP BY date;

UPDATE visitor_count vc
INNER JOIN visitor_count_daily_rollup rollup ON vc.id = rollup.keep_id
SET vc.daily_count = rollup.daily_count;

DELETE vc
FROM visitor_count vc
LEFT JOIN visitor_count_daily_rollup rollup ON vc.id = rollup.keep_id
WHERE rollup.keep_id IS NULL;

DROP TEMPORARY TABLE visitor_count_daily_rollup;

SET @visitor_count_unique_date_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT index_name
        FROM information_schema.STATISTICS
        WHERE table_schema = DATABASE()
          AND table_name = 'visitor_count'
          AND non_unique = 0
        GROUP BY index_name
        HAVING COUNT(*) = 1
           AND MAX(column_name = 'date') = 1
    ) unique_date_indexes
);
SET @visitor_count_unique_date_sql = IF(
    @visitor_count_unique_date_exists = 0,
    'ALTER TABLE visitor_count ADD UNIQUE KEY uq_visitor_count_date (date)',
    'SELECT 1'
);
PREPARE visitor_count_unique_date_statement FROM @visitor_count_unique_date_sql;
EXECUTE visitor_count_unique_date_statement;
DEALLOCATE PREPARE visitor_count_unique_date_statement;

INSERT INTO total_visitor_count (total_count)
SELECT 0
WHERE NOT EXISTS (SELECT 1 FROM total_visitor_count);
