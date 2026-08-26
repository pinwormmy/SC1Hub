CREATE TABLE IF NOT EXISTS one_line_strategy_recommendation (
    tip_num INT NOT NULL,
    recommend_date DATE NOT NULL,
    user_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tip_num, recommend_date, user_hash),
    INDEX idx_one_line_strategy_recommendation_date (recommend_date),
    CONSTRAINT fk_one_line_strategy_recommendation_tip
        FOREIGN KEY (tip_num) REFERENCES one_line_strategy(tip_num) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
