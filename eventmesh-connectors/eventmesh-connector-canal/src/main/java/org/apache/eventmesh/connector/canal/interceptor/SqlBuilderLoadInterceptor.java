/*
 * Copyright (C) 2010-2101 Alibaba Group Holding Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import org.apache.eventmesh.connector.canal.template.SqlTemplate;
import org.apache.eventmesh.connector.canal.dialect.DbDialect;
import org.apache.eventmesh.connector.canal.model.EventColumn;
import org.apache.eventmesh.connector.canal.model.EventType;

import java.util.List;

import org.springframework.util.CollectionUtils;

/**
 * 计算下最新的sql语句
 */
public class SqlBuilderLoadInterceptor {

    private DbDialect dbDialect;

    public boolean before(CanalSinkConfig sinkConfig, CanalConnectRecord record) {
        // 初步构建sql
        SqlTemplate sqlTemplate = dbDialect.getSqlTemplate();
        EventType type = record.getEventType();
        String sql = null;

        String schemaName = (record.isWithoutSchema() ? null : record.getSchemaName());

        /**
         * 针对DRDS数据库
         */
        String shardColumns = null;

        // 注意insert/update语句对应的字段数序都是将主键排在后面
        if (type.isInsert()) {
            if (CollectionUtils.isEmpty(record.getColumns())
                && (dbDialect.isDRDS())) { // 如果表为全主键，直接进行insert
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
                // 需要考虑主键变更的场景
                // 构造sql如下：update table xxx set pk = newPK where pk = oldPk
                keyColumns = buildColumnNames(record.getOldKeys());
                // 这里需要精确获取变更的主键,因为目标为DRDS时主键会包含拆分键,正常的原主键变更只更新对应的单主键列即可
                if (dbDialect.isDRDS()) {
                    otherColumns = buildColumnNames(record.getUpdatedColumns(), record.getUpdatedKeys());
                } else {
                    otherColumns = buildColumnNames(record.getUpdatedColumns(), record.getKeys());
                }
            } else {
                keyColumns = buildColumnNames(record.getKeys());
                otherColumns = buildColumnNames(record.getUpdatedColumns());
            }

            if (rowMode && !existOldKeys) {// 如果是行记录,并且不存在主键变更，考虑merge sql
                sql = sqlTemplate.getMergeSql(schemaName,
                    record.getTableName(),
                    keyColumns,
                    otherColumns,
                    new String[] {},
                    !dbDialect.isDRDS(),
                    shardColumns);
            } else {// 否则进行update sql
                sql = sqlTemplate.getUpdateSql(schemaName, record.getTableName(), keyColumns, otherColumns, !dbDialect.isDRDS(), shardColumns);
            }
        } else if (type.isDelete()) {
            sql = sqlTemplate.getDeleteSql(schemaName,
                record.getTableName(),
                buildColumnNames(record.getKeys()));
        }

        // 处理下hint sql
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

    public DbDialect getDbDialect() {
        return dbDialect;
    }

    public void setDbDialect(DbDialect dbDialect) {
        this.dbDialect = dbDialect;
    }
}
