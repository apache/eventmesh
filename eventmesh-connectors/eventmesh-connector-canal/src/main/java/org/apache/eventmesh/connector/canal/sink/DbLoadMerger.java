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

package org.apache.eventmesh.connector.canal.sink;

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

import org.springframework.util.CollectionUtils;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;


/**
 * <pre>
 * merge the same schema-table change record.
 * The result of merging multiple change data with the same primary key (pk) is:
 * 1, I
 * 2, U
 * 3, D
 * If there is one "I" (Insert) and multiple "U" (Update), merge into "I";
 * If there are multiple "U" (Update), take the latest one;
 * </pre>
 */
@Slf4j
public class DbLoadMerger {

    /**
     * Merge a batch of data based on table and primary key information,
     * ensuring that there is only one record for each primary key in a table
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
        RowKey rowKey = new RowKey(record.getSchemaName(), record.getTableName(),
            record.getKeys());
        if (!result.containsKey(rowKey)) {
            result.put(rowKey, record);
        } else {
            CanalConnectRecord oldRecord = result.get(rowKey);
            record.setSize(oldRecord.getSize() + record.getSize());
            if (oldRecord.getEventType() == EventType.DELETE) {
                result.put(rowKey, record);
            } else if (record.getEventType() == EventType.UPDATE
                || record.getEventType() == EventType.INSERT) {
                log.warn("update-insert/insert-insert happend. before[{}] , after[{}]", oldRecord, record);
                CanalConnectRecord mergeEventData = replaceColumnValue(record, oldRecord);
                mergeEventData.getOldKeys().clear();
                result.put(rowKey, mergeEventData);
            }
        }
    }

    private static void mergeUpdate(CanalConnectRecord record, Map<RowKey, CanalConnectRecord> result) {
        RowKey rowKey = new RowKey(record.getSchemaName(), record.getTableName(), record.getKeys());
        if (!CollectionUtils.isEmpty(record.getOldKeys())) {
            RowKey oldKey = new RowKey(record.getSchemaName(), record.getTableName(),
                record.getOldKeys());
            if (!result.containsKey(oldKey)) {
                result.put(rowKey, record);
            } else {
                CanalConnectRecord oldRecord = result.get(oldKey);
                record.setSize(oldRecord.getSize() + record.getSize());
                if (oldRecord.getEventType() == EventType.INSERT) {
                    record.setEventType(EventType.INSERT);
                    result.remove(oldKey);

                    CanalConnectRecord mergeEventData = replaceColumnValue(record, oldRecord);
                    mergeEventData.getOldKeys().clear();
                    result.put(rowKey, mergeEventData);
                } else if (oldRecord.getEventType() == EventType.UPDATE) {
                    result.remove(oldKey);
                    CanalConnectRecord mergeEventData = replaceColumnValue(record, oldRecord);
                    result.put(rowKey, mergeEventData);
                } else {
                    throw new RuntimeException("delete(has old pks) + update impossible happed!");
                }
            }
        } else {
            if (!result.containsKey(rowKey)) {
                result.put(rowKey, record);
            } else {
                CanalConnectRecord oldRecord = result.get(rowKey);
                if (oldRecord.getEventType() == EventType.INSERT) {
                    oldRecord.setEventType(EventType.INSERT);

                    CanalConnectRecord mergeEventData = replaceColumnValue(record, oldRecord);
                    result.put(rowKey, mergeEventData);
                } else if (oldRecord.getEventType() == EventType.UPDATE) {
                    CanalConnectRecord mergeEventData = replaceColumnValue(record, oldRecord);
                    result.put(rowKey, mergeEventData);
                } else if (oldRecord.getEventType() == EventType.DELETE) {
                    result.put(rowKey, record);
                }
            }
        }
    }

    private static void mergeDelete(CanalConnectRecord record, Map<RowKey, CanalConnectRecord> result) {
        RowKey rowKey = new RowKey(record.getSchemaName(), record.getTableName(),
            record.getKeys());
        if (!result.containsKey(rowKey)) {
            result.put(rowKey, record);
        } else {
            CanalConnectRecord oldRecord = result.get(rowKey);
            record.setSize(oldRecord.getSize() + record.getSize());
            if (!CollectionUtils.isEmpty(oldRecord.getOldKeys())) {
                record.setKeys(oldRecord.getOldKeys());
                record.getOldKeys().clear();

                result.remove(rowKey);
                result.put(new RowKey(record.getSchemaName(), record.getTableName(),
                    record.getKeys()), record);
            } else {
                record.getOldKeys().clear();
                result.put(rowKey, record);
            }

        }
    }

    /**
     * Merge the old value that exists in the old record and does not exist in the new record into the new record,
     * and save the old primary key of the last change to the old primary key of this change.
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
                    newColumn.setUpdate(newColumn.isUpdate() || oldColumn.isUpdate());
                    contain = true;
                }
            }

            if (!contain) {
                temp.add(oldColumn);
            }
        }
        newColumns.addAll(temp);
        Collections.sort(newColumns, new EventColumnIndexComparable());
        newRecord.setOldKeys(oldRecord.getOldKeys());
        if (oldRecord.getSyncConsistency() != null) {
            newRecord.setSyncConsistency(oldRecord.getSyncConsistency());
        }
        if (oldRecord.getSyncMode() != null) {
            newRecord.setSyncMode(oldRecord.getSyncMode());
        }

        if (oldRecord.isRemedy()) {
            newRecord.setRemedy(true);
        }
        newRecord.setSize(oldRecord.getSize() + newRecord.getSize());
        return newRecord;
    }

    @Setter
    @Getter
    public static class RowKey implements Serializable {

        private String schemaName;
        private String tableName;

        public RowKey(String schemaName, String tableName, List<EventColumn> keys) {
            this.schemaName = schemaName;
            this.tableName = tableName;
            this.keys = keys;
        }

        public RowKey(List<EventColumn> keys) {
            this.keys = keys;
        }

        private List<EventColumn> keys = new ArrayList<EventColumn>();

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((keys == null) ? 0 : keys.hashCode());
            result = prime * result + ((schemaName == null) ? 0 : schemaName.hashCode());
            result = prime * result + ((tableName == null) ? 0 : tableName.hashCode());
            return result;
        }

        @SuppressWarnings("checkstyle:NeedBraces")
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
                return other.tableName == null;
            } else {
                return tableName.equals(other.tableName);
            }
        }

    }
}
