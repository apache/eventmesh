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

package org.apache.eventmesh.connector.canal.source;

import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSourceConfig;
import org.apache.eventmesh.connector.canal.CanalConnectRecord;
import org.apache.eventmesh.connector.canal.model.EventColumn;
import org.apache.eventmesh.connector.canal.model.EventColumnIndexComparable;
import org.apache.eventmesh.connector.canal.model.EventType;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.crypto.dsig.TransformException;

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

    /**
     * 将对应canal送出来的Entry对象解析为ConnectRecord
     *
     * <pre>
     * 需要处理数据过滤：
     * 1. Transaction Begin/End过滤
     * 2. retl.retl_client/retl.retl_mark 回环标记处理以及后续的回环数据过滤
     * 3. retl.xdual canal心跳表数据过滤
     * </pre>
     */
    public List<CanalConnectRecord> parse(CanalSourceConfig sourceConfig, List<Entry> datas) {
        List<CanalConnectRecord> recordList = new ArrayList<>();
        List<Entry> transactionDataBuffer = new ArrayList<>();
        try {
            for (Entry entry : datas) {
                switch (entry.getEntryType()) {
                    case TRANSACTIONBEGIN:
                        break;
                    case ROWDATA:
                        transactionDataBuffer.add(entry);
                        break;
                    case TRANSACTIONEND:
                        // 添加数据解析
                        for (Entry bufferEntry : transactionDataBuffer) {
                            List<CanalConnectRecord> recordParsedList = internParse(sourceConfig, bufferEntry);
                            if (CollectionUtils.isEmpty(recordParsedList)) {// 可能为空，针对ddl返回时就为null
                                continue;
                            }
                            // 初步计算一下事件大小
                            long totalSize = bufferEntry.getHeader().getEventLength();
                            long eachSize = totalSize / recordParsedList.size();
                            for (CanalConnectRecord record : recordParsedList) {
                                if (record == null) {
                                    continue;
                                }
                                record.setSize(eachSize);// 记录一下大小
                                recordList.add(record);
                            }
                        }
                        transactionDataBuffer.clear();
                        break;
                    default:
                        break;
                }
            }

            // 添加最后一次的数据，可能没有TRANSACTIONEND
            // 添加数据解析
            for (Entry bufferEntry : transactionDataBuffer) {
                List<CanalConnectRecord> recordParsedList = internParse(sourceConfig, bufferEntry);
                if (CollectionUtils.isEmpty(recordParsedList)) {// 可能为空，针对ddl返回时就为null
                    continue;
                }

                // 初步计算一下事件大小
                long totalSize = bufferEntry.getHeader().getEventLength();
                long eachSize = totalSize / recordParsedList.size();
                for (CanalConnectRecord record : recordParsedList) {
                    if (record == null) {
                        continue;
                    }
                    record.setSize(eachSize);// 记录一下大小
                    recordList.add(record);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return recordList;
    }

    private List<CanalConnectRecord> internParse(CanalSourceConfig sourceConfig, Entry entry) {
        String schemaName = entry.getHeader().getSchemaName();
        String tableName = entry.getHeader().getTableName();
        if (!schemaName.equalsIgnoreCase(sourceConfig.getSourceConnectorConfig().getSchemaName()) ||
            !tableName.equalsIgnoreCase(sourceConfig.getSourceConnectorConfig().getTableName())) {
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

        // 处理下DDL操作
        if (eventType.isQuery()) {
            // 直接忽略query事件
            return null;
        }

        // 首先判断是否为系统表
        if (StringUtils.equalsIgnoreCase(sourceConfig.getSystemSchema(), schemaName)) {
            // do noting
            if (eventType.isDdl()) {
                return null;
            }

            if (StringUtils.equalsIgnoreCase(sourceConfig.getSystemDualTable(), tableName)) {
                // 心跳表数据直接忽略
                return null;
            }
        } else {
            if (eventType.isDdl()) {
                log.warn("unsupported ddl event type: {}", eventType);
                return null;
            }
        }

        List<CanalConnectRecord> recordList = new ArrayList<>();
        for (RowData rowData : rowChange.getRowDatasList()) {
            CanalConnectRecord record = internParse(sourceConfig, entry, rowChange, rowData);
            recordList.add(record);
        }

        return recordList;
    }

    /**
     * 解析出从canal中获取的Event事件<br> Oracle:有变更的列值. <br>
     * <i>insert:从afterColumns中获取所有的变更数据<br>
     * <i>delete:从beforeColumns中获取所有的变更数据<br>
     * <i>update:在before中存放所有的主键和变化前的非主键值，在after中存放变化后的主键和非主键值,如果是复合主键，只会存放变化的主键<br>
     * Mysql:可以得到所有变更前和变更后的数据.<br>
     * <i>insert:从afterColumns中获取所有的变更数据<br>
     * <i>delete:从beforeColumns中获取所有的变更数据<br>
     * <i>update:在beforeColumns中存放变更前的所有数据,在afterColumns中存放变更后的所有数据<br>
     */
    private CanalConnectRecord internParse(CanalSourceConfig canalSourceConfig, Entry entry, RowChange rowChange, RowData rowData) {
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
        String tableName = canalConnectRecord.getSchemaName() + "." + canalConnectRecord.getTableName();

        // 判断一下是否需要all columns
        boolean isRowMode = canalSourceConfig.getSyncMode().isRow(); // 如果是rowMode模式，所有字段都需要标记为updated

        // 变更后的主键
        Map<String, EventColumn> keyColumns = new LinkedHashMap<String, EventColumn>();
        // 变更前的主键
        Map<String, EventColumn> oldKeyColumns = new LinkedHashMap<String, EventColumn>();
        // 有变化的非主键
        Map<String, EventColumn> notKeyColumns = new LinkedHashMap<String, EventColumn>();

        if (eventType.isInsert()) {
            for (Column column : afterColumns) {
                if (column.getIsKey()) {
                    keyColumns.put(column.getName(), copyEventColumn(column, true));
                } else {
                    // mysql 有效
                    notKeyColumns.put(column.getName(), copyEventColumn(column, true));
                }
            }
        } else if (eventType.isDelete()) {
            for (Column column : beforeColumns) {
                if (column.getIsKey()) {
                    keyColumns.put(column.getName(), copyEventColumn(column, true));
                } else {
                    // mysql 有效
                    notKeyColumns.put(column.getName(), copyEventColumn(column, true));
                }
            }
        } else if (eventType.isUpdate()) {
            // 获取变更前的主键.
            for (Column column : beforeColumns) {
                if (column.getIsKey()) {
                    oldKeyColumns.put(column.getName(), copyEventColumn(column, true));
                    // 同时记录一下new
                    // key,因为mysql5.6之后出现了minimal模式,after里会没有主键信息,需要在before记录中找
                    keyColumns.put(column.getName(), copyEventColumn(column, true));
                } else {
                    if (isRowMode && entry.getHeader().getSourceType() == CanalEntry.Type.ORACLE) {
                        // 针对行记录同步时，针对oracle记录一下非主键的字段，因为update时针对未变更的字段在aftercolume里没有
                        notKeyColumns.put(column.getName(), copyEventColumn(column, isRowMode));
                    }
                }
            }
            for (Column column : afterColumns) {
                if (column.getIsKey()) {
                    // 获取变更后的主键
                    keyColumns.put(column.getName(), copyEventColumn(column, true));
                } else if (isRowMode || entry.getHeader().getSourceType() == CanalEntry.Type.ORACLE
                    || column.getUpdated()) {
                    // 在update操作时，oracle和mysql存放变更的非主键值的方式不同,oracle只有变更的字段;
                    // mysql会把变更前和变更后的字段都发出来，只需要取有变更的字段.
                    // 如果是oracle库，after里一定为对应的变更字段

                    boolean isUpdate = true;
                    if (entry.getHeader().getSourceType() == CanalEntry.Type.MYSQL) { // mysql的after里部分数据为未变更,oracle里after里为变更字段
                        isUpdate = column.getUpdated();
                    }

                    notKeyColumns.put(column.getName(), copyEventColumn(column, isUpdate));// 如果是rowMode，所有字段都为updated
                }
            }

            if (entry.getHeader().getSourceType() == CanalEntry.Type.ORACLE) { // 针对oracle进行特殊处理
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
            if (canalConnectRecord.getEventType().isUpdate() && !oldKeys.equals(keys)) { // update类型，如果存在主键不同,则记录下old
                // keys为变更前的主键
                canalConnectRecord.setOldKeys(oldKeys);
            }
            canalConnectRecord.setColumns(columns);
        } else {
            throw new RuntimeException("this row data has no pks , entry: " + entry.toString() + " and rowData: "
                + rowData);
        }

        return canalConnectRecord;
    }

    /**
     * 在oracle中，补充没有变更的主键<br> 如果变更后的主键为空，直接从old中拷贝<br> 如果变更前后的主键数目不相等，把old中存在而new中不存在的主键拷贝到new中.
     *
     * @param oldKeyColumns
     * @param keyColumns
     */
    private void checkUpdateKeyColumns(Map<String, EventColumn> oldKeyColumns, Map<String, EventColumn> keyColumns) {
        // 在变更前没有主键的情况
        if (oldKeyColumns.size() == 0) {
            return;
        }
        // 变更后的主键数据大于变更前的，不符合
        if (keyColumns.size() > oldKeyColumns.size()) {
            return;
        }
        // 主键没有变更，把所有变更前的主键拷贝到变更后的主键中.
        if (keyColumns.size() == 0) {
            keyColumns.putAll(oldKeyColumns);
            return;
        }

        // 把old中存在而new中不存在的主键拷贝到new中
        if (oldKeyColumns.size() != keyColumns.size()) {
            for (String oldKey : oldKeyColumns.keySet()) {
                if (keyColumns.get(oldKey) == null) {
                    keyColumns.put(oldKey, oldKeyColumns.get(oldKey));
                }
            }
        }
    }

    /**
     * 把 erosa-protocol's Column 转化成 otter's model EventColumn.
     *
     * @param column
     * @return
     */
    private EventColumn copyEventColumn(Column column, boolean isUpdate) {
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

//    private String buildName(String name, ModeValue sourceModeValue, ModeValue targetModeValue) {
//        if (targetModeValue.getMode().isWildCard()) {
//            return name; // 通配符，认为源和目标一定是一致的
//        } else if (targetModeValue.getMode().isMulti()) {
//            int index = ConfigHelper.indexIgnoreCase(sourceModeValue.getMultiValue(), name);
//            if (index == -1) {
//                throw new TransformException("can not found namespace or name in media:" + sourceModeValue.toString());
//            }
//
//            return targetModeValue.getMultiValue().get(index);
//        } else {
//            return targetModeValue.getSingleValue();
//        }
//    }

}
