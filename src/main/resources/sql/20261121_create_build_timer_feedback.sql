-- 빌드 타이머 (beta) 사용자 피드백 수집 테이블 (idempotent)
create table if not exists build_timer_feedback (
    id bigint auto_increment primary key,
    build_id varchar(80) null,
    build_label varchar(160) null,
    category varchar(30) not null default 'general',
    message varchar(1000) not null,
    contact varchar(100) null,
    member_id varchar(50) null,
    nickname varchar(50) null,
    ip varchar(45) null,
    user_agent varchar(255) null,
    status varchar(20) not null default 'NEW',
    reg_date datetime not null default current_timestamp,
    key idx_bt_feedback_reg_date (reg_date),
    key idx_bt_feedback_status (status)
) default charset=utf8mb4;
