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

package org.apache.eventmesh.openconnect.offsetmgmt.api.storage;

import org.apache.eventmesh.openconnect.offsetmgmt.api.config.OffsetStorageConfig;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffset;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

import java.util.List;
import java.util.Map;

/**
 * Interface for offset manager.
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.OFFSETMGMT)
public interface OffsetManagementService {

    /**
     * Start the manager.
     */
    void start();

    /**
     * Stop the manager.
     */
    void stop();

    /**
     * Configure class with the given key-value pairs
     */
    default void configure(OffsetStorageConfig config) {

    }

    /**
     * Persist position info in a persist store.
     */
    void persist();

    /**
     * load position info in a persist store.
     */
    void load();

    /**
     * Synchronize to other nodes.
     */
    void synchronize();

    /**
     * Get the current position table.
     *
     * @return
     */
    Map<ConnectorRecordPartition, RecordOffset> getPositionMap();

    RecordOffset getPosition(ConnectorRecordPartition partition);

    /**
     * Put a position info.
     */
    void putPosition(Map<ConnectorRecordPartition, RecordOffset> positions);

    void putPosition(ConnectorRecordPartition partition, RecordOffset position);

    /**
     * Remove a position info.
     *
     * @param partitions
     */
    void removePosition(List<ConnectorRecordPartition> partitions);

    void initialize(OffsetStorageConfig connectorConfig);

}
