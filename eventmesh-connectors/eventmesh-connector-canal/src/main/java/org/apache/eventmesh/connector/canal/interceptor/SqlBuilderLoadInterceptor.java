/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.connector.canal.interceptor;

import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSinkConfig;
import org.apache.eventmesh.connector.canal.CanalConnectRecord;
import org.apache.eventmesh.connector.canal.dialect.DbDialect;
import org.apache.eventmesh.connector.canal.model.EventColumn;
import org.apache.eventmesh.connector.canal.model.EventType;
import org.apache.eventmesh.connector.canal.template.SqlTemplate;

import java.util.List;

import org.springframework.util.CollectionUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * compute latest sql
 */
public class SqlBuilderLoadInterceptor {

    @Getter
    @Setter
    private DbDialect dbDialect;

    public boolean before(CanalSinkConfig sinkConfig, CanalConnectRecord record) {
        // build sql
        SqlTemplate sqlTemplate = dbDialect.getSqlTemplate();
        EventType type = record.getEventType();
        String sql = null;

        String schemaName = (record.isWithoutSchema() ? null : record.getSchemaName());

        String shardColumns = null;

        if (type.isInsert()) {
            if (CollectionUtils.isEmpty(record.getColumns())
                && (dbDialect.isDRDS())) {
                // sql
                sql = sqlTemplate.getInsertSql(schemaName,
                    record.getTableName(),
                    buildColumnNames(record.getKeys()),
                    buildColumnNames(record.getColumns()));
            } else {
                sql = sqlTemplate.getMergeSql(schemaName,
                    record.getTableName(),
                    buildColumnNames(record.getKeys()),
                    buildColumnNames(record.getColumns()),
                    new String[] {},
                    !dbDialect.isDRDS(),
                    shardColumns);
            }
        } else if (type.isUpdate()) {

            boolean existOldKeys = !CollectionUtils.isEmpty(record.getOldKeys());
            boolean rowMode = sinkConfig.getSyncMode().isRow();
            String[] keyColumns = null;
            String[] otherColumns = null;
            if (existOldKeys) {
                keyColumns = buildColumnNames(record.getOldKeys());
                if (dbDialect.isDRDS()) {
                    otherColumns = buildColumnNames(record.getUpdatedColumns(), record.getUpdatedKeys());
                } else {
                    otherColumns = buildColumnNames(record.getUpdatedColumns(), record.getKeys());
                }
            } else {
                keyColumns = buildColumnNames(record.getKeys());
                otherColumns = buildColumnNames(record.getUpdatedColumns());
            }

            if (rowMode && !existOldKeys) {
                sql = sqlTemplate.getMergeSql(schemaName,
                    record.getTableName(),
                    keyColumns,
                    otherColumns,
                    new String[] {},
                    !dbDialect.isDRDS(),
                    shardColumns);
            } else {
                sql = sqlTemplate.getUpdateSql(schemaName, record.getTableName(), keyColumns, otherColumns, !dbDialect.isDRDS(), shardColumns);
            }
        } else if (type.isDelete()) {
            sql = sqlTemplate.getDeleteSql(schemaName,
                record.getTableName(),
                buildColumnNames(record.getKeys()));
        }

        if (record.getHint() != null) {
            record.setSql(record.getHint() + sql);
        } else {
            record.setSql(sql);
        }
        return false;
    }

    private String[] buildColumnNames(List<EventColumn> columns) {
        String[] result = new String[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            EventColumn column = columns.get(i);
            result[i] = column.getColumnName();
        }
        return result;
    }

    private String[] buildColumnNames(List<EventColumn> columns1, List<EventColumn> columns2) {
        String[] result = new String[columns1.size() + columns2.size()];
        int i = 0;
        for (i = 0; i < columns1.size(); i++) {
            EventColumn column = columns1.get(i);
            result[i] = column.getColumnName();
        }

        for (; i < columns1.size() + columns2.size(); i++) {
            EventColumn column = columns2.get(i - columns1.size());
            result[i] = column.getColumnName();
        }
        return result;
    }
}
