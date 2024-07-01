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

package org.apache.eventmesh.connector.canal.source;

import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSourceConfig;
import org.apache.eventmesh.connector.canal.CanalConnectRecord;
import org.apache.eventmesh.connector.canal.model.EventColumn;
import org.apache.eventmesh.connector.canal.model.EventColumnIndexComparable;
import org.apache.eventmesh.connector.canal.model.EventType;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.eventmesh.connector.canal.source.table.RdbTableMgr;
import org.springframework.util.CollectionUtils;

import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.CanalEntry.Column;
import com.alibaba.otter.canal.protocol.CanalEntry.Entry;
import com.alibaba.otter.canal.protocol.CanalEntry.RowChange;
import com.alibaba.otter.canal.protocol.CanalEntry.RowData;

import lombok.extern.slf4j.Slf4j;

/**
 * data object parse
 */
@Slf4j
public class EntryParser {

    public static Map<Long, List<CanalConnectRecord>> parse(CanalSourceConfig sourceConfig, List<Entry> datas) {
        List<CanalConnectRecord> recordList = new ArrayList<>();
        List<Entry> transactionDataBuffer = new ArrayList<>();
        // need check weather the entry is loopback
        boolean needSync;
        Map<Long, List<CanalConnectRecord>> recordMap = new HashMap<>();
        try {
            for (Entry entry : datas) {
                switch (entry.getEntryType()) {
                    case ROWDATA:
                        RowChange rowChange = RowChange.parseFrom(entry.getStoreValue());
                        needSync = checkNeedSync(sourceConfig, rowChange.getRowDatas(0));
                        if (needSync) {
                            transactionDataBuffer.add(entry);
                        }
                        break;
                    case TRANSACTIONEND:
                        parseRecordListWithEntryBuffer(sourceConfig, recordList, transactionDataBuffer);
                        if (!recordList.isEmpty()) {
                            recordMap.put(entry.getHeader().getLogfileOffset(), recordList);
                        }
                        transactionDataBuffer.clear();
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return recordMap;
    }

    private void parseRecordListWithEntryBuffer(CanalSourceConfig sourceConfig, List<CanalConnectRecord> recordList,
        List<Entry> transactionDataBuffer) {
        for (Entry bufferEntry : transactionDataBuffer) {
            List<CanalConnectRecord> recordParsedList = internParse(sourceConfig, bufferEntry);
            if (CollectionUtils.isEmpty(recordParsedList)) {
                continue;
            }
            long totalSize = bufferEntry.getHeader().getEventLength();
            long eachSize = totalSize / recordParsedList.size();
            for (CanalConnectRecord record : recordParsedList) {
                if (record == null) {
                    continue;
                }
                record.setSize(eachSize);
                recordList.add(record);
            }
        }
    }

    private boolean checkNeedSync(CanalSourceConfig sourceConfig, RowData rowData) {
        Column markedColumn = getColumnIgnoreCase(rowData.getAfterColumnsList(), sourceConfig.getNeedSyncMarkTableColumnName());
        if (markedColumn != null) {
            return StringUtils.equalsIgnoreCase(markedColumn.getValue(), sourceConfig.getNeedSyncMarkTableColumnValue());
        }
        return false;
    }

    private Column getColumnIgnoreCase(List<Column> columns, String columName) {
        for (Column column : columns) {
            if (column.getName().equalsIgnoreCase(columName)) {
                return column;
            }
        }
        return null;
    }

    private static List<CanalConnectRecord> internParse(CanalSourceConfig sourceConfig, Entry entry, RdbTableMgr tableMgr) {
        String schemaName = entry.getHeader().getSchemaName();
        String tableName = entry.getHeader().getTableName();
        if (tableMgr.getTable(schemaName, tableName) == null) {
            return null;
        }

        RowChange rowChange = null;
        try {
            rowChange = RowChange.parseFrom(entry.getStoreValue());
        } catch (Exception e) {
            throw new RuntimeException("parser of canal-event has an error , data:" + entry.toString(), e);
        }

        if (rowChange == null) {
            return null;
        }

        EventType eventType = EventType.valueOf(rowChange.getEventType().name());

        if (eventType.isQuery()) {
            return null;
        }

        if (eventType.isDdl()) {
            log.warn("unsupported ddl event type: {}", eventType);
            return null;
        }

        List<CanalConnectRecord> recordList = new ArrayList<>();
        for (RowData rowData : rowChange.getRowDatasList()) {
            CanalConnectRecord record = internParse(sourceConfig, entry, rowChange, rowData);
            recordList.add(record);
        }

        return recordList;
    }

    private static CanalConnectRecord internParse(CanalSourceConfig canalSourceConfig, Entry entry, RowChange rowChange, RowData rowData) {
        CanalConnectRecord canalConnectRecord = new CanalConnectRecord();
        canalConnectRecord.setTableName(entry.getHeader().getTableName());
        canalConnectRecord.setSchemaName(entry.getHeader().getSchemaName());
        canalConnectRecord.setEventType(EventType.valueOf(rowChange.getEventType().name()));
        canalConnectRecord.setExecuteTime(entry.getHeader().getExecuteTime());
        canalConnectRecord.setJournalName(entry.getHeader().getLogfileName());
        canalConnectRecord.setBinLogOffset(entry.getHeader().getLogfileOffset());
        EventType eventType = canalConnectRecord.getEventType();

        List<Column> beforeColumns = rowData.getBeforeColumnsList();
        List<Column> afterColumns = rowData.getAfterColumnsList();

        boolean isRowMode = canalSourceConfig.getSyncMode().isRow();

        Map<String, EventColumn> keyColumns = new LinkedHashMap<>();
        Map<String, EventColumn> oldKeyColumns = new LinkedHashMap<>();
        Map<String, EventColumn> notKeyColumns = new LinkedHashMap<>();

        if (eventType.isInsert()) {
            for (Column column : afterColumns) {
                if (column.getIsKey()) {
                    keyColumns.put(column.getName(), copyEventColumn(column, true));
                } else {
                    notKeyColumns.put(column.getName(), copyEventColumn(column, true));
                }
            }
        } else if (eventType.isDelete()) {
            for (Column column : beforeColumns) {
                if (column.getIsKey()) {
                    keyColumns.put(column.getName(), copyEventColumn(column, true));
                } else {
                    notKeyColumns.put(column.getName(), copyEventColumn(column, true));
                }
            }
        } else if (eventType.isUpdate()) {
            for (Column column : beforeColumns) {
                if (column.getIsKey()) {
                    oldKeyColumns.put(column.getName(), copyEventColumn(column, true));
                    keyColumns.put(column.getName(), copyEventColumn(column, true));
                } else {
                    if (isRowMode && entry.getHeader().getSourceType() == CanalEntry.Type.ORACLE) {
                        notKeyColumns.put(column.getName(), copyEventColumn(column, true));
                    }
                }
            }
            for (Column column : afterColumns) {
                if (column.getIsKey()) {
                    keyColumns.put(column.getName(), copyEventColumn(column, true));
                } else if (isRowMode || entry.getHeader().getSourceType() == CanalEntry.Type.ORACLE
                    || column.getUpdated()) {

                    boolean isUpdate = true;
                    if (entry.getHeader().getSourceType() == CanalEntry.Type.MYSQL) {
                        isUpdate = column.getUpdated();
                    }

                    notKeyColumns.put(column.getName(), copyEventColumn(column, isUpdate));
                }
            }

            if (entry.getHeader().getSourceType() == CanalEntry.Type.ORACLE) {
                checkUpdateKeyColumns(oldKeyColumns, keyColumns);
            }
        }

        List<EventColumn> keys = new ArrayList<>(keyColumns.values());
        List<EventColumn> oldKeys = new ArrayList<>(oldKeyColumns.values());
        List<EventColumn> columns = new ArrayList<>(notKeyColumns.values());

        keys.sort(new EventColumnIndexComparable());
        oldKeys.sort(new EventColumnIndexComparable());
        columns.sort(new EventColumnIndexComparable());
        if (!keyColumns.isEmpty()) {
            canalConnectRecord.setKeys(keys);
            if (canalConnectRecord.getEventType().isUpdate() && !oldKeys.equals(keys)) {
                canalConnectRecord.setOldKeys(oldKeys);
            }
            canalConnectRecord.setColumns(columns);
        } else {
            throw new RuntimeException("this row data has no pks , entry: " + entry + " and rowData: "
                + rowData);
        }

        return canalConnectRecord;
    }

    private static void checkUpdateKeyColumns(Map<String, EventColumn> oldKeyColumns,
                                           Map<String, EventColumn> keyColumns) {
        if (oldKeyColumns.isEmpty()) {
            return;
        }
        if (keyColumns.size() > oldKeyColumns.size()) {
            return;
        }

        if (keyColumns.isEmpty()) {
            keyColumns.putAll(oldKeyColumns);
            return;
        }

        if (oldKeyColumns.size() != keyColumns.size()) {
            for (String oldKey : oldKeyColumns.keySet()) {
                if (keyColumns.get(oldKey) == null) {
                    keyColumns.put(oldKey, oldKeyColumns.get(oldKey));
                }
            }
        }
    }

    private static EventColumn copyEventColumn(Column column, boolean isUpdate) {
        EventColumn eventColumn = new EventColumn();
        eventColumn.setIndex(column.getIndex());
        eventColumn.setKey(column.getIsKey());
        eventColumn.setNull(column.getIsNull());
        eventColumn.setColumnName(column.getName());
        eventColumn.setColumnValue(column.getValue());
        eventColumn.setUpdate(isUpdate);
        eventColumn.setColumnType(column.getSqlType());

        return eventColumn;
    }

}
