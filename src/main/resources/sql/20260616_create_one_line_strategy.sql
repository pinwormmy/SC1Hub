create table if not exists one_line_strategy_category (
    code varchar(32) primary key,
    name varchar(30) not null,
    sort_order int not null
);

insert into one_line_strategy_category(code, name, sort_order)
values
    ('t_vs_z', '테저전', 10),
    ('t_vs_p', '테프전', 20),
    ('t_vs_t', '테테전', 30),
    ('z_vs_t', '저테전', 40),
    ('z_vs_p', '저프전', 50),
    ('z_vs_z', '저저전', 60),
    ('p_vs_t', '프테전', 70),
    ('p_vs_z', '프저전', 80),
    ('p_vs_p', '프프전', 90),
    ('honey_tip', '꿀팁', 100),
    ('team_play', '팀플', 110)
on duplicate key update
    name = values(name),
    sort_order = values(sort_order);

create table if not exists one_line_strategy (
    tip_num int auto_increment primary key,
    category varchar(32) not null,
    content varchar(160) not null,
    writer varchar(40) not null,
    member_id varchar(50) null,
    guest_password varchar(100) null,
    reg_date datetime not null,
    recommend_count int not null default 0,
    constraint fk_one_line_strategy_category
        foreign key (category) references one_line_strategy_category(code)
);

create index idx_one_line_strategy_category_reg_date
    on one_line_strategy(category, reg_date);

create index idx_one_line_strategy_reg_date
    on one_line_strategy(reg_date);
