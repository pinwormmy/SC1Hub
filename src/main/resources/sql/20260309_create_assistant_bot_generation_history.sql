CREATE TABLE assistant_bot_generation_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    persona_name VARCHAR(50) NOT NULL,
    board_title VARCHAR(50) NOT NULL,
    generation_mode VARCHAR(20) NOT NULL,
    target_post_num INT NULL,
    topic VARCHAR(255) NULL,
    draft_title VARCHAR(255) NULL,
    draft_body TEXT NOT NULL,
    raw_json MEDIUMTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    published_post_num INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_abgh_persona_board_created (persona_name, board_title, created_at),
    KEY idx_abgh_board_mode_created (board_title, generation_mode, created_at)
);
