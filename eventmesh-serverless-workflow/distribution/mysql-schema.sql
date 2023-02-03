/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

create database if not exists db_workflow;

use db_workflow;

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
    index `index_workflow_id` (workflow_id)
    ) collate = utf8mb3_bin;

create table if not exists t_workflow_instance
(
    id                   int auto_increment
    primary key,
    workflow_id          varchar(1024)                      not null,
    workflow_instance_id varchar(1024)                      not null,
    workflow_status      int                                not null,
    create_time          datetime default CURRENT_TIMESTAMP not null,
    update_time          datetime default CURRENT_TIMESTAMP not null,
    index `index_workflow_id` (workflow_id)
    )
    collate = utf8mb3_bin;

create table if not exists t_workflow_task
(
    id          int auto_increment
    primary key,
    workflow_id       varchar(1024)                      not null,
    task_id           varchar(1024)                      not null,
    task_name         varchar(1024)                      not null,
    task_type         varchar(64)                        not null,
    task_input_filter varchar(1024)                      not null default '',
    status            int                                not null,
    create_time datetime default CURRENT_TIMESTAMP not null,
    update_time datetime default CURRENT_TIMESTAMP not null,
    index `index_workflow_id` (workflow_id)
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
    update_time    datetime default CURRENT_TIMESTAMP not null,
    index `index_workflow_id` (workflow_id)
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
    update_time          datetime default CURRENT_TIMESTAMP not null,
    index `index_workflow_id` (workflow_id)
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
    update_time  datetime default CURRENT_TIMESTAMP not null,
    index `index_from_task_id` (from_task_id)
    index `index_workflow_id` (workflow_id)
    )
    collate = utf8mb3_bin;

