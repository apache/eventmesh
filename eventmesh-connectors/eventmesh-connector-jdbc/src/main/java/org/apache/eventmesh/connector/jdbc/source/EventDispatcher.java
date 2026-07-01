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

import org.apache.eventmesh.connector.jdbc.event.Event;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventDispatcher {

    private SourceJdbcTaskManager sourceJdbcTaskManager;

    public EventDispatcher(SourceJdbcTaskManager sourceJdbcTaskManager) {
        this.sourceJdbcTaskManager = sourceJdbcTaskManager;
    }

    /**
     * Dispatch CDC events.
     *
     * @param event The CDC event to be dispatched.
     */
    public void dispatch(Event event) {
        TableId tableId = event.getTableId();
        SourceEventMeshJdbcEventTask task = sourceJdbcTaskManager.select(tableId);
        try {
            // Put the CDC event into the selected JDBC task.
            task.put(event);
        } catch (InterruptedException e) {
            log.warn("Dispatch CdcEvent error", e);
        }
    }

}
