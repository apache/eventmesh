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

package org.apache.eventmesh.connector.canal;

import org.apache.eventmesh.common.remote.job.SyncConsistency;
import org.apache.eventmesh.common.remote.job.SyncMode;
import org.apache.eventmesh.connector.canal.model.EventColumn;
import org.apache.eventmesh.connector.canal.model.EventType;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class CanalConnectRecord {

    private String schemaName;
    private String tableName;

    /**
     * 变更数据的业务类型(I/U/D/C/A/E),与canal中的EntryProtocol中定义的EventType一致.
     */
    private EventType eventType;

    /**
     * 变更数据的业务时间.
     */
    private long executeTime;

    /**
     * 变更前的主键值,如果是insert/delete变更前和变更后的主键值是一样的.
     */
    private List<EventColumn> oldKeys = new ArrayList<EventColumn>();

    /**
     * 变更后的主键值,如果是insert/delete变更前和变更后的主键值是一样的.
     */
    private List<EventColumn> keys = new ArrayList<EventColumn>();

    /**
     * 非主键的其他字段
     */
    private List<EventColumn> columns = new ArrayList<EventColumn>();

    // ====================== 运行过程中对数据的附加属性 =============================
    /**
     * 预计的size大小，基于binlog event的推算
     */
    private long size = 1024;

    /**
     * 同步映射关系的id
     */
    private long pairId = -1;

    /**
     * 当eventType = CREATE/ALTER/ERASE时，就是对应的sql语句，其他情况为动态生成的INSERT/UPDATE/DELETE sql
     */
    private String sql;

    /**
     * ddl/query的schemaName，会存在跨库ddl，需要保留执行ddl的当前schemaName
     */
    private String ddlSchemaName;

    /**
     * 自定义的同步模式, 允许覆盖默认的pipeline parameter，比如针对补救数据同步
     */
    private SyncMode syncMode;

    /**
     * 自定义的同步一致性，允许覆盖默认的pipeline parameter，比如针对字段组强制反查数据库
     */
    private SyncConsistency syncConsistency;

    /**
     * 是否为remedy补救数据，比如回环补救自动产生的数据，或者是freedom产生的手工订正数据
     */
    private boolean remedy = false;

    /**
     * 生成对应的hint内容
     */
    private String hint;

    /**
     * 生成sql是否忽略schema,比如针对tddl/drds,需要忽略schema
     */
    private boolean withoutSchema = false;

    private String journalName;

    private long binLogOffset;

    public CanalConnectRecord() {
        super();
    }

    // ======================== helper method =================

    /**
     * 返回所有待变更的字段
     */
    public List<EventColumn> getUpdatedColumns() {
        List<EventColumn> columns = new ArrayList<EventColumn>();
        for (EventColumn column : this.columns) {
            if (column.isUpdate()) {
                columns.add(column);
            }
        }

        return columns;
    }

    /**
     * 返回所有变更的主键字段
     */
    public List<EventColumn> getUpdatedKeys() {
        List<EventColumn> columns = new ArrayList<EventColumn>();
        for (EventColumn column : this.keys) {
            if (column.isUpdate()) {
                columns.add(column);
            }
        }

        return columns;
    }

    private List<EventColumn> cloneColumn(List<EventColumn> columns) {
        if (columns == null) {
            return null;
        }

        List<EventColumn> cloneColumns = new ArrayList<EventColumn>();
        for (EventColumn column : columns) {
            cloneColumns.add(column.clone());
        }

        return cloneColumns;
    }

    public CanalConnectRecord clone() {
        CanalConnectRecord record = new CanalConnectRecord();
        record.setTableName(tableName);
        record.setSchemaName(schemaName);
        record.setDdlSchemaName(ddlSchemaName);
        record.setEventType(eventType);
        record.setExecuteTime(executeTime);
        record.setKeys(cloneColumn(keys));
        record.setColumns(cloneColumn(columns));
        record.setOldKeys(cloneColumn(oldKeys));
        record.setSize(size);
        record.setPairId(pairId);
        record.setSql(sql);
        record.setSyncMode(syncMode);
        record.setSyncConsistency(syncConsistency);
        record.setRemedy(remedy);
        record.setHint(hint);
        record.setWithoutSchema(withoutSchema);
        return record;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((columns == null) ? 0 : columns.hashCode());
        result = prime * result + ((eventType == null) ? 0 : eventType.hashCode());
        result = prime * result + (int) (executeTime ^ (executeTime >>> 32));
        result = prime * result + ((keys == null) ? 0 : keys.hashCode());
        result = prime * result + ((oldKeys == null) ? 0 : oldKeys.hashCode());
        result = prime * result + (int) (pairId ^ (pairId >>> 32));
        result = prime * result + ((schemaName == null) ? 0 : schemaName.hashCode());
        result = prime * result + ((tableName == null) ? 0 : tableName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        CanalConnectRecord other = (CanalConnectRecord) obj;
        if (columns == null) {
            if (other.columns != null) {
                return false;
            }
        } else if (!columns.equals(other.columns)) {
            return false;
        }
        if (eventType != other.eventType) {
            return false;
        }
        if (executeTime != other.executeTime) {
            return false;
        }
        if (keys == null) {
            if (other.keys != null) {
                return false;
            }
        } else if (!keys.equals(other.keys)) {
            return false;
        }
        if (oldKeys == null) {
            if (other.oldKeys != null) {
                return false;
            }
        } else if (!oldKeys.equals(other.oldKeys)) {
            return false;
        }
        if (pairId != other.pairId) {
            return false;
        }
        if (schemaName == null) {
            if (other.schemaName != null) {
                return false;
            }
        } else if (!schemaName.equals(other.schemaName)) {
            return false;
        }
        if (tableName == null) {
            if (other.tableName != null) {
                return false;
            }
        } else if (!tableName.equals(other.tableName)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "CanalConnectRecord{" +
            "tableName='" + tableName + '\'' +
            ", schemaName='" + schemaName + '\'' +
            ", eventType=" + eventType +
            ", executeTime=" + executeTime +
            ", oldKeys=" + oldKeys +
            ", keys=" + keys +
            ", columns=" + columns +
            ", size=" + size +
            ", pairId=" + pairId +
            ", sql='" + sql + '\'' +
            ", ddlSchemaName='" + ddlSchemaName + '\'' +
            ", syncMode=" + syncMode +
            ", syncConsistency=" + syncConsistency +
            ", remedy=" + remedy +
            ", hint='" + hint + '\'' +
            ", withoutSchema=" + withoutSchema +
            '}';
    }
}
