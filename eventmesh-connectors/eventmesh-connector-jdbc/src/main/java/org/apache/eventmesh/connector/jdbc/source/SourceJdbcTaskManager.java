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

package org.apache.eventmesh.connector.jdbc.source;

import org.apache.eventmesh.common.config.connector.rdb.jdbc.JdbcSourceConfig;
import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;
import org.apache.eventmesh.connector.jdbc.JdbcConnectData;
import org.apache.eventmesh.connector.jdbc.event.Event;
import org.apache.eventmesh.connector.jdbc.source.dialect.cdc.RandomTaskSelectStrategy;
import org.apache.eventmesh.connector.jdbc.source.dialect.cdc.TaskSelectStrategy;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SourceJdbcTaskManager extends AbstractJdbcTaskManager<SourceEventMeshJdbcEventTask> {

    private final Set<TableId> includeDatabaseTable;

    private final JdbcSourceConfig jdbcSourceConfig;

    private TaskSelectStrategy<SourceEventMeshJdbcEventTask> cdcTaskSelectStrategy;

    public SourceJdbcTaskManager(Set<TableId> includeDatabaseTable, JdbcSourceConfig jdbcSourceConfig) {
        this.jdbcSourceConfig = jdbcSourceConfig;
        this.includeDatabaseTable = includeDatabaseTable == null ? new HashSet<>() : includeDatabaseTable;
    }

    @SuppressWarnings("unchecked")
    public void init() {
        // init Jdbc Task
        int maxTaskNum = this.jdbcSourceConfig.getSourceConnectorConfig().getMaxTask();
        int taskNum = Math.min(maxTaskNum, this.includeDatabaseTable.size());
        log.info("Source jdbc task num {}", taskNum);
        for (int index = 0; index < taskNum; ++index) {
            SourceEventMeshJdbcEventTask eventTask = new SourceEventMeshJdbcEventTask("source-jdbc-task-" + (index + 1));
            eventTask.registerEventHandler(this::doHandleEvent);
            taskList.add(eventTask);
        }
        cdcTaskSelectStrategy = new RandomTaskSelectStrategy(taskList);

    }

    private void doHandleEvent(Event event) {
        if (event == null) {
            return;
        }
        JdbcConnectData jdbcConnectData = event.getJdbcConnectData();
        RecordPartition partition = new RecordPartition();
        RecordOffset offset = new RecordOffset();
        ConnectRecord record = new ConnectRecord(partition, offset, System.currentTimeMillis(), jdbcConnectData);
        List<ConnectRecord> records = Collections.singletonList(record);
        for (TaskManagerListener listener : listeners) {
            listener.listen(records);
        }
    }

    @Override
    public SourceEventMeshJdbcEventTask select(TableId tableId) {
        return tableIdJdbcTaskMap.computeIfAbsent(tableId, key -> cdcTaskSelectStrategy.select(tableId));
    }

    public int getTaskCount() {
        return tableIdJdbcTaskMap.size();
    }

}
