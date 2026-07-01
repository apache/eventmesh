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

package org.apache.eventmesh.connector.jdbc.event;

import org.apache.eventmesh.connector.jdbc.JdbcConnectData;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;

public abstract class AbstractEvent implements Event {

    private final TableId tableId;

    private JdbcConnectData data;

    public AbstractEvent(TableId tableId) {
        this.tableId = tableId;
        this.data = new JdbcConnectData();
    }

    public AbstractEvent(TableId tableId, JdbcConnectData data) {
        this.tableId = tableId;
        this.data = data;
    }

    /**
     * Gets the table ID of the event.
     *
     * @return The table ID.
     */
    @Override
    public TableId getTableId() {
        return tableId;
    }

    @Override
    public JdbcConnectData getJdbcConnectData() {
        return data;
    }
}
