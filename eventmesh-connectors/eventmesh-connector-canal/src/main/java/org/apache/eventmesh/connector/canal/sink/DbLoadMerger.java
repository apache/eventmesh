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

import javafx.fxml.LoadException;

import org.apache.eventmesh.connector.canal.CanalConnectRecord;
import org.apache.eventmesh.connector.canal.model.EventColumn;
import org.apache.eventmesh.connector.canal.model.EventColumnIndexComparable;
import org.apache.eventmesh.connector.canal.model.EventType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import lombok.extern.slf4j.Slf4j;


/**
 * <pre>
 * 合并相同schema-table的变更记录.
 * pk相同的多条变更数据合并后的结果是：
 * 1, I
 * 2, U
 * 3, D
 * 如果有一条I，多条U，merge成I;
 * 如果有多条U，取最晚的那条;
 * </pre>
 */
@Slf4j
public class DbLoadMerger {

    /**
     * 将一批数据进行根据table+主键信息进行合并，保证一个表的一个pk记录只有一条结果
     *
     * @param eventDatas
     * @return
     */
    public static List<CanalConnectRecord> merge(List<CanalConnectRecord> eventDatas) {
        Map<RowKey, CanalConnectRecord> result = new LinkedHashMap<RowKey, CanalConnectRecord>();
        for (CanalConnectRecord eventData : eventDatas) {
            merge(eventData, result);
        }
        return new LinkedList<>(result.values());
    }

    public static void merge(CanalConnectRecord record, Map<RowKey, CanalConnectRecord> result) {
        EventType eventType = record.getEventType();
        switch (eventType) {
            case INSERT:
                mergeInsert(record, result);
                break;
            case UPDATE:
                mergeUpdate(record, result);
                break;
            case DELETE:
                mergeDelete(record, result);
                break;
            default:
                break;
        }
    }

    private static void mergeInsert(CanalConnectRecord record, Map<RowKey, CanalConnectRecord> result) {
        // insert无主键变更的处理
        RowKey rowKey = new RowKey(record.getSchemaName(), record.getTableName(),
            record.getKeys());
        if (!result.containsKey(rowKey)) {
            result.put(rowKey, record);
        } else {
            CanalConnectRecord oldRecord = result.get(rowKey);
            record.setSize(oldRecord.getSize() + record.getSize());
            // 如果上一条变更是delete的，就直接用insert替换
            if (oldRecord.getEventType() == EventType.DELETE) {
                result.put(rowKey, record);
            } else if (record.getEventType() == EventType.UPDATE
                || record.getEventType() == EventType.INSERT) {
                // insert之前出现了update逻辑上不可能，唯一的可能性主要是Freedom的介入，人为的插入了一条Insert记录
                // 不过freedom一般不建议Insert操作，只建议执行update/delete操作. update默认会走merge
                // sql,不存在即插入
                log.warn("update-insert/insert-insert happend. before[{}] , after[{}]", oldRecord, record);
                // 如果上一条变更是update的，就用insert替换，并且把上一条存在而这一条不存在的字段值拷贝到这一条中
                CanalConnectRecord mergeEventData = replaceColumnValue(record, oldRecord);
                mergeEventData.getOldKeys().clear();// 清空oldkeys，insert记录不需要
                result.put(rowKey, mergeEventData);
            }
        }
    }

    private static void mergeUpdate(CanalConnectRecord record, Map<RowKey, CanalConnectRecord> result) {
        RowKey rowKey = new RowKey(record.getSchemaName(), record.getTableName(), record.getKeys());
        if (!CollectionUtils.isEmpty(record.getOldKeys())) {// 存在主键变更
            // 需要解决(1->2 , 2->3)级联主键变更的问题
            RowKey oldKey = new RowKey(record.getSchemaName(), record.getTableName(),
                record.getOldKeys());
            if (!result.containsKey(oldKey)) {// 不需要级联
                result.put(rowKey, record);
            } else {
                CanalConnectRecord oldRecord = result.get(oldKey);
                record.setSize(oldRecord.getSize() + record.getSize());
                // 如果上一条变更是insert的，就把这一条的eventType改成insert，并且把上一条存在而这一条不存在的字段值拷贝到这一条中
                if (oldRecord.getEventType() == EventType.INSERT) {
                    record.setEventType(EventType.INSERT);
                    // 删除当前变更数据老主键的记录.
                    result.remove(oldKey);

                    CanalConnectRecord mergeEventData = replaceColumnValue(record, oldRecord);
                    mergeEventData.getOldKeys().clear();// 清空oldkeys，insert记录不需要
                    result.put(rowKey, mergeEventData);
                } else if (oldRecord.getEventType() == EventType.UPDATE) {
                    // 删除当前变更数据老主键的记录.
                    result.remove(oldKey);

                    // 如果上一条变更是update的，把上一条存在而这一条不存在的数据拷贝到这一条中
                    CanalConnectRecord mergeEventData = replaceColumnValue(record, oldRecord);
                    result.put(rowKey, mergeEventData);
                } else {
                    throw new RuntimeException("delete(has old pks) + update impossible happed!");
                }
            }
        } else {
            if (!result.containsKey(rowKey)) {// 没有主键变更
                result.put(rowKey, record);
            } else {
                CanalConnectRecord oldRecord = result.get(rowKey);
                // 如果上一条变更是insert的，就把这一条的eventType改成insert，并且把上一条存在而这一条不存在的字段值拷贝到这一条中
                if (oldRecord.getEventType() == EventType.INSERT) {
                    oldRecord.setEventType(EventType.INSERT);

                    CanalConnectRecord mergeEventData = replaceColumnValue(record, oldRecord);
                    result.put(rowKey, mergeEventData);
                } else if (oldRecord.getEventType() == EventType.UPDATE) {// 可能存在
                    // 1->2
                    // ,
                    // 2update的问题

                    // 如果上一条变更是update的，把上一条存在而这一条不存在的数据拷贝到这一条中
                    CanalConnectRecord mergeEventData = replaceColumnValue(record, oldRecord);
                    result.put(rowKey, mergeEventData);
                } else if (oldRecord.getEventType() == EventType.DELETE) {
                    //异常情况，出现 delete + update，那就直接更新为update
                    result.put(rowKey, record);
                }
            }
        }
    }

    private static void mergeDelete(CanalConnectRecord record, Map<RowKey, CanalConnectRecord> result) {
        // 只保留pks，把columns去掉. 以后针对数据仓库可以开放delete columns记录
        RowKey rowKey = new RowKey(record.getSchemaName(), record.getTableName(),
            record.getKeys());
        if (!result.containsKey(rowKey)) {
            result.put(rowKey, record);
        } else {
            CanalConnectRecord oldRecord = result.get(rowKey);
            record.setSize(oldRecord.getSize() + record.getSize());
            if (!CollectionUtils.isEmpty(oldRecord.getOldKeys())) {// 存在主键变更
                // insert/update -> delete记录组合时，delete的对应的pk为上一条记录的pk
                record.setKeys(oldRecord.getOldKeys());
                record.getOldKeys().clear();// 清除oldKeys

                result.remove(rowKey);// 删除老的对象
                result.put(new RowKey(record.getSchemaName(), record.getTableName(),
                    record.getKeys()), record); // key发生变化，需要重新构造一个RowKey
            } else {
                record.getOldKeys().clear();// 清除oldKeys
                result.put(rowKey, record);
            }

        }
    }

    /**
     * 把old中的值存在而new中不存在的值合并到new中,并且把old中的变更前的主键保存到new中的变更前的主键.
     *
     * @param newRecord
     * @param oldRecord
     * @return
     */
    private static CanalConnectRecord replaceColumnValue(CanalConnectRecord newRecord, CanalConnectRecord oldRecord) {
        List<EventColumn> newColumns = newRecord.getColumns();
        List<EventColumn> oldColumns = oldRecord.getColumns();
        List<EventColumn> temp = new ArrayList<>();
        for (EventColumn oldColumn : oldColumns) {
            boolean contain = false;
            for (EventColumn newColumn : newColumns) {
                if (oldColumn.getColumnName().equalsIgnoreCase(newColumn.getColumnName())) {
                    newColumn.setUpdate(newColumn.isUpdate() || oldColumn.isUpdate());// 合并isUpdate字段
                    contain = true;
                }
            }

            if (!contain) {
                temp.add(oldColumn);
            }
        }
        newColumns.addAll(temp);
        Collections.sort(newColumns, new EventColumnIndexComparable()); // 排序
        // 把上一次变更的旧主键传递到这次变更的旧主键.
        newRecord.setOldKeys(oldRecord.getOldKeys());
        if (oldRecord.getSyncConsistency() != null) {
            newRecord.setSyncConsistency(oldRecord.getSyncConsistency());
        }
        if (oldRecord.getSyncMode() != null) {
            newRecord.setSyncMode(oldRecord.getSyncMode());
        }

        if (oldRecord.isRemedy()) {
            newRecord.setRemedy(oldRecord.isRemedy());
        }
        newRecord.setSize(oldRecord.getSize() + newRecord.getSize());
        return newRecord;
    }

    public static class RowKey implements Serializable {

        private static final long serialVersionUID = -7369951798499581038L;
        private String schemaName;                              // tableId代表统配符时，需要指定schemaName
        private String tableName;                               // tableId代表统配符时，需要指定tableName

        public RowKey(String schemaName, String tableName, List<EventColumn> keys) {
            this.schemaName = schemaName;
            this.tableName = tableName;
            this.keys = keys;
        }

        public RowKey(List<EventColumn> keys) {
            this.keys = keys;
        }

        private List<EventColumn> keys = new ArrayList<EventColumn>();

        public List<EventColumn> getKeys() {
            return keys;
        }

        public void setKeys(List<EventColumn> keys) {
            this.keys = keys;
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

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((keys == null) ? 0 : keys.hashCode());
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
            if (!(obj instanceof RowKey)) {
                return false;
            }
            RowKey other = (RowKey) obj;
            if (keys == null) {
                if (other.keys != null) {
                    return false;
                }
            } else if (!keys.equals(other.keys)) {
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

    }
}
