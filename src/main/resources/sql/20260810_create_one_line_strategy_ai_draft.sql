CREATE TABLE IF NOT EXISTS one_line_strategy_ai_daily_run (
    generation_date DATE NOT NULL,
    api_call_count INT NOT NULL DEFAULT 0,
    last_status VARCHAR(20) NULL,
    last_attempt_at DATETIME NULL,
    completed_at DATETIME NULL,
    last_error VARCHAR(500) NULL,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    search_query_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (generation_date)
);

CREATE TABLE IF NOT EXISTS one_line_strategy_ai_draft (
    draft_id BIGINT NOT NULL AUTO_INCREMENT,
    generation_date DATE NOT NULL,
    slot_no INT NOT NULL,
    category VARCHAR(32) NOT NULL,
    content VARCHAR(160) NOT NULL,
    evidence_summary VARCHAR(500) NOT NULL,
    source_board VARCHAR(32) NOT NULL,
    source_post_num INT NOT NULL,
    source_title VARCHAR(255) NOT NULL,
    source_excerpt VARCHAR(1200) NOT NULL,
    external_source_url VARCHAR(1000) NOT NULL,
    external_source_title VARCHAR(255) NOT NULL,
    external_evidence_summary VARCHAR(500) NOT NULL,
    model VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at DATETIME NULL,
    reviewed_by VARCHAR(50) NULL,
    published_tip_num INT NULL,
    PRIMARY KEY (draft_id),
    UNIQUE KEY uk_ols_ai_draft_generation_slot (generation_date, slot_no),
    KEY idx_ols_ai_draft_status_created (status, created_at),
    CONSTRAINT fk_ols_ai_draft_category
        FOREIGN KEY (category) REFERENCES one_line_strategy_category(code),
    CONSTRAINT fk_ols_ai_draft_published_tip
        FOREIGN KEY (published_tip_num) REFERENCES one_line_strategy(tip_num)
        ON DELETE SET NULL
);

-- Existing installations originally used TINYINT because only three daily slots existed.
-- Manual administrator batches can continue with higher slot numbers on the same date.
ALTER TABLE one_line_strategy_ai_draft
    MODIFY COLUMN slot_no INT NOT NULL;
