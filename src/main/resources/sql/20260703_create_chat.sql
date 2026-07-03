-- 공개 채팅 메시지 (id는 애플리케이션에서 발급: 인메모리 버퍼 + 지연 저장 방식)
create table if not exists chat_message (
    id bigint primary key,
    member_id varchar(50) null,
    nickname varchar(40) not null,
    role varchar(10) not null default 'MEMBER',
    content varchar(1000) not null,
    ip varchar(45) null,
    deleted tinyint(1) not null default 0,
    reg_date datetime not null default current_timestamp,
    key idx_chat_message_reg_date (reg_date)
) default charset=utf8mb4;

-- 채팅 제재 (MUTE: 회원 채팅금지, BLOCK_IP: 게스트/IP 차단)
create table if not exists chat_sanction (
    id bigint auto_increment primary key,
    sanction_type varchar(10) not null,
    member_id varchar(50) null,
    ip varchar(45) null,
    nickname varchar(40) null,
    reason varchar(200) null,
    expires_at datetime null,
    revoked tinyint(1) not null default 0,
    created_by varchar(50) not null,
    reg_date datetime not null default current_timestamp,
    key idx_chat_sanction_member (member_id),
    key idx_chat_sanction_ip (ip)
) default charset=utf8mb4;
