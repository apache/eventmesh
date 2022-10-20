create database db_event_catalog;
create table if not exists t_event
(
    id          int auto_increment comment '主键、自增'
    primary key,
    title       varchar(1024)                      not null,
    file_name   varchar(1024)                      not null,
    definition  text                               not null,
    status      int                                not null,
    version     varchar(64)                        not null,
    create_time datetime default CURRENT_TIMESTAMP not null,
    update_time datetime default CURRENT_TIMESTAMP not null,
    constraint unique_title
    unique (title)
    )
    collate = utf8mb3_bin;

create table if not exists t_event_catalog
(
    id           int auto_increment comment '主键、自增'
    primary key,
    service_name varchar(256) default ''                not null comment '服务名称',
    operation_id varchar(1024)                          not null,
    channel_name varchar(1024)                          not null,
    type         varchar(1024)                          not null,
    `schema`     text                                   not null,
    status       int                                    not null,
    create_time  datetime     default CURRENT_TIMESTAMP not null,
    update_time  datetime     default CURRENT_TIMESTAMP not null,
    constraint unique_operation_id
    unique (operation_id)
    )
    collate = utf8mb3_bin;

