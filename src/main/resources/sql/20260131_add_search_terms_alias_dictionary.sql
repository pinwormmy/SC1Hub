-- Migration: add alias_dictionary and search_terms

CREATE TABLE IF NOT EXISTS alias_dictionary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alias VARCHAR(255) NOT NULL,
    canonical_terms TEXT,
    matchup_hint TEXT,
    boost_board_ids TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_alias_dictionary_alias (alias)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Add search_terms column and fulltext index to all boards in board_list.
-- This uses a stored procedure so it can run across many tables safely.
DELIMITER $$
CREATE PROCEDURE add_search_terms_to_boards()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE tbl VARCHAR(64);
    DECLARE col_exists INT DEFAULT 0;
    DECLARE idx_exists INT DEFAULT 0;

    DECLARE cur CURSOR FOR SELECT board_title FROM board_list;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO tbl;
        IF done THEN
            LEAVE read_loop;
        END IF;

        SELECT COUNT(*) INTO col_exists
        FROM information_schema.COLUMNS
        WHERE table_schema = DATABASE()
          AND table_name = tbl
          AND column_name = 'search_terms';

        IF col_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', tbl, ' ADD COLUMN search_terms TEXT NULL');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        SELECT COUNT(*) INTO idx_exists
        FROM information_schema.STATISTICS
        WHERE table_schema = DATABASE()
          AND table_name = tbl
          AND index_name = 'ft_title_content_search_terms';

        IF idx_exists = 0 THEN
            SET @sql = CONCAT('ALTER TABLE ', tbl,
                ' ADD FULLTEXT INDEX ft_title_content_search_terms (title, content, search_terms)');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END LOOP;
    CLOSE cur;
END$$
DELIMITER ;

CALL add_search_terms_to_boards();
DROP PROCEDURE add_search_terms_to_boards;
