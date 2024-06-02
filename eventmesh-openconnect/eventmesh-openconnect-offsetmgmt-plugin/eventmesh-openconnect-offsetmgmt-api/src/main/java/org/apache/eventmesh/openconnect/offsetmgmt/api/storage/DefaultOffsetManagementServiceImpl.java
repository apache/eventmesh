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

import org.apache.eventmesh.common.config.connector.offset.OffsetStorageConfig;
import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;

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
    public Map<RecordPartition, RecordOffset> getPositionMap() {
        return null;
    }

    @Override
    public RecordOffset getPosition(RecordPartition partition) {
        return null;
    }

    @Override
    public void putPosition(Map<RecordPartition, RecordOffset> positions) {

    }

    @Override
    public void putPosition(RecordPartition partition, RecordOffset position) {

    }

    @Override
    public void removePosition(List<RecordPartition> partitions) {

    }

    @Override
    public void initialize(OffsetStorageConfig connectorConfig) {

    }
}
