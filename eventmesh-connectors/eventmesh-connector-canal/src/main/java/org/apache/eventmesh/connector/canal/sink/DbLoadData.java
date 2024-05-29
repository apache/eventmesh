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

package org.apache.eventmesh.connector.canal.sink;

import org.apache.eventmesh.connector.canal.CanalConnectRecord;
import org.apache.eventmesh.connector.canal.model.EventType;

import java.util.ArrayList;
import java.util.List;

/**
 * 将数据归类,按表和insert/update/delete类型进行分类
 *
 * <pre>
 * 归类用途：对insert语句进行batch优化
 * 1. mysql索引的限制，需要避免insert并发执行
 * </pre>
 */
public class DbLoadData {

    private List<TableLoadData> tables = new ArrayList<TableLoadData>();

    public DbLoadData() {
        // nothing
    }

    public DbLoadData(List<CanalConnectRecord> records) {
        for (CanalConnectRecord record : records) {
            merge(record);
        }
    }

    public void merge(CanalConnectRecord record) {
        TableLoadData tableData = findTableData(record);

        EventType type = record.getEventType();
        if (type.isInsert()) {
            tableData.getInsertDatas().add(record);
        } else if (type.isUpdate()) {
            tableData.getUpdateDatas().add(record);
        } else if (type.isDelete()) {
            tableData.getDeleteDatas().add(record);
        }
    }

    public List<TableLoadData> getTables() {
        return tables;
    }

    private synchronized TableLoadData findTableData(CanalConnectRecord record) {
        for (TableLoadData table : tables) {
            if (table.getSchemaName().equals(record.getSchemaName()) &&
                table.getTableName().equals(record.getTableName())) {
                return table;
            }
        }

        TableLoadData data = new TableLoadData(record.getSchemaName(), record.getTableName());
        tables.add(data);
        return data;
    }

    /**
     * 按table进行分类
     */
    public static class TableLoadData {

        private String schemaName;

        private String tableName;
        private List<CanalConnectRecord> insertDatas = new ArrayList<>();
        private List<CanalConnectRecord> upadateDatas = new ArrayList<>();
        private List<CanalConnectRecord> deleteDatas = new ArrayList<>();

        public TableLoadData(String schemaName, String tableName) {
            this.schemaName = schemaName;
            this.tableName = tableName;
        }

        public List<CanalConnectRecord> getInsertDatas() {
            return insertDatas;
        }

        public void setInsertDatas(List<CanalConnectRecord> insertDatas) {
            this.insertDatas = insertDatas;
        }

        public List<CanalConnectRecord> getUpdateDatas() {
            return upadateDatas;
        }

        public void setUpdateDatas(List<CanalConnectRecord> upadateDatas) {
            this.upadateDatas = upadateDatas;
        }

        public List<CanalConnectRecord> getDeleteDatas() {
            return deleteDatas;
        }

        public void setDeleteDatas(List<CanalConnectRecord> deleteDatas) {
            this.deleteDatas = deleteDatas;
        }

        public String getSchemaName() {
            return schemaName;
        }

        public void setSchemaName(String schemaName) {
            this.schemaName = schemaName;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
    }
}
