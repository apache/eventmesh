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
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractJdbcTaskManager<Task extends EventMeshJdbcTask> implements JdbcTaskManager {

    protected Map<TableId, Task> tableIdJdbcTaskMap = new ConcurrentHashMap<>();

    protected Set<TableId> includeDatabaseTable;

    protected JdbcSourceConfig jdbcSourceConfig;

    protected List<Task> taskList = new ArrayList<>(128);

    protected List<TaskManagerListener> listeners = new ArrayList<>(16);

    @Override
    public void start() {
        taskList.forEach(EventMeshJdbcTask::start);
    }

    @Override
    public void shutdown() {
        taskList.forEach(EventMeshJdbcTask::shutdown);
    }

    @Override
    public void close() throws Exception {
        shutdown();
    }

    @Override
    public void registerListener(TaskManagerListener listener) {
        if (!Objects.isNull(listener)) {
            listeners.add(listener);
        }
    }

    public abstract Task select(TableId tableId);
}