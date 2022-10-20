create database db_workflow;
create table if not exists t_workflow
(
    id            int auto_increment primary key,
    workflow_id   varchar(1024)                      not null,
    workflow_name varchar(1024)                      not null,
    definition    text                               not null,
    status        int                                not null,
    version       varchar(64)                        not null,
    create_time   datetime default CURRENT_TIMESTAMP not null,
    update_time   datetime default CURRENT_TIMESTAMP not null,
    unique (workflow_id)
    ) collate = utf8mb3_bin;

create table if not exists t_workflow_instance
(
    id                   int auto_increment
    primary key,
    workflow_id          varchar(1024)                      not null,
    workflow_instance_id varchar(1024)                      not null,
    workflow_status      int                                not null,
    create_time          datetime default CURRENT_TIMESTAMP not null,
    update_time          datetime default CURRENT_TIMESTAMP not null
    )
    collate = utf8mb3_bin;

create table if not exists t_workflow_task
(
    id          int auto_increment
    primary key,
    workflow_id varchar(1024)                      not null,
    task_id     varchar(1024)                      not null,
    task_name   varchar(1024)                      not null,
    task_type   varchar(64)                        not null,
    status      int                                not null,
    create_time datetime default CURRENT_TIMESTAMP not null,
    update_time datetime default CURRENT_TIMESTAMP not null
    )
    collate = utf8mb3_bin;

create table if not exists t_workflow_task_action
(
    id             int auto_increment
    primary key,
    workflow_id    varchar(1024)                      not null,
    task_id        varchar(1024)                      not null,
    operation_name varchar(1024)                      not null,
    operation_type varchar(1024)                      not null,
    status         int                                not null,
    create_time    datetime default CURRENT_TIMESTAMP not null,
    update_time    datetime default CURRENT_TIMESTAMP not null
    )
    collate = utf8mb3_bin;

create table if not exists t_workflow_task_instance
(
    id                   int auto_increment
    primary key,
    workflow_id          varchar(1024)                      not null,
    workflow_instance_id varchar(1024)                      not null,
    task_id              varchar(1024)                      not null,
    task_instance_id     varchar(1024)                      not null,
    status               int                                not null,
    input                text                               not null,
    retry_times          int                                not null,
    create_time          datetime default CURRENT_TIMESTAMP not null,
    update_time          datetime default CURRENT_TIMESTAMP not null
    )
    collate = utf8mb3_bin;

create table if not exists t_workflow_task_relation
(
    id           int auto_increment
    primary key,
    workflow_id  varchar(1024)                      not null,
    from_task_id varchar(1024)                      not null,
    to_task_id   varchar(1024)                      not null,
    `condition`  varchar(2048)                      not null,
    status       int                                not null,
    create_time  datetime default CURRENT_TIMESTAMP not null,
    update_time  datetime default CURRENT_TIMESTAMP not null
    )
    collate = utf8mb3_bin;

