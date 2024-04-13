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

import java.util.List;
import java.util.Map;

public class DefaultOffsetManagementServiceImpl implements OffsetManagementService {

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public void persist() {

    }

    @Override
    public void load() {

    }

    @Override
    public void synchronize() {

    }

    @Override
    public Map<ConnectorRecordPartition, RecordOffset> getPositionMap() {
        return null;
    }

    @Override
    public RecordOffset getPosition(ConnectorRecordPartition partition) {
        return null;
    }

    @Override
    public void putPosition(Map<ConnectorRecordPartition, RecordOffset> positions) {

    }

    @Override
    public void putPosition(ConnectorRecordPartition partition, RecordOffset position) {

    }

    @Override
    public void removePosition(List<ConnectorRecordPartition> partitions) {

    }

    @Override
    public void initialize(OffsetStorageConfig connectorConfig) {

    }
}
