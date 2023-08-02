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
 *
 */

package org.apache.eventmesh.openconnect.offsetmgmt.api.storage;

import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordPartition;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;



public class OffsetStorageReaderImpl implements OffsetStorageReader {

    private final String namespace;

    private OffsetManagementService offsetManagementService;

    public OffsetStorageReaderImpl(String namespace, OffsetManagementService offsetManagementService) {
        this.namespace = namespace;
        this.offsetManagementService = offsetManagementService;
    }

    @Override
    public RecordOffset readOffset(RecordPartition partition) {
        ConnectorRecordPartition connectorRecordPartition = new ConnectorRecordPartition(namespace, partition.getPartition());
        return offsetManagementService.getPositionTable().get(connectorRecordPartition);
    }

    @Override
    public Map<RecordPartition, RecordOffset> readOffsets(Collection<RecordPartition> partitions) {
        Map<RecordPartition, RecordOffset> result = new HashMap<>();
        Map<ConnectorRecordPartition, RecordOffset> allData = offsetManagementService.getPositionTable();
        for (RecordPartition key : partitions) {
            ConnectorRecordPartition extendRecordPartition = new ConnectorRecordPartition(namespace, key.getPartition());
            if (allData.containsKey(extendRecordPartition)) {
                result.put(key, allData.get(extendRecordPartition));
            }
        }
        return result;
    }
}
