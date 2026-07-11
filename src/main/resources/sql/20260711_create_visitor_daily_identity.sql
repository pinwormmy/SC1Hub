CREATE TABLE IF NOT EXISTS visitor_daily_identity (
    visit_date DATE NOT NULL,
    visitor_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (visit_date, visitor_hash),
    INDEX idx_visitor_daily_identity_created_at (created_at)
);
