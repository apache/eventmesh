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

package org.apache.eventmesh.connector.openfunction.source.connector;

import org.apache.eventmesh.common.config.connector.openfunction.OpenFunctionSourceConfig;
import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.List;
import java.util.concurrent.BlockingQueue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OpenFunctionSourceConnectorTest {

    private final OpenFunctionSourceConnector connector = new OpenFunctionSourceConnector();

    @Test
    public void testSpringSourceConnector() throws Exception {
        OpenFunctionSourceConfig sourceConfig = new OpenFunctionSourceConfig();
        connector.init(sourceConfig);
        connector.start();
        final int count = 5;
        final String message = "testMessage";
        writeMockedRecords(count, message);
        List<ConnectRecord> connectRecords = connector.poll();
        Assertions.assertEquals(count, connectRecords.size());
        for (int i = 0; i < connectRecords.size(); i++) {
            Object actualMessage = String.valueOf(connectRecords.get(i).getData());
            String expectedMessage = "testMessage" + i;
            Assertions.assertEquals(expectedMessage, actualMessage);
        }
        connector.stop();
    }

    private void writeMockedRecords(int count, String message) {
        BlockingQueue<ConnectRecord> queue = connector.queue();
        for (int i = 0; i < count; i++) {
            RecordPartition partition = new RecordPartition();
            RecordOffset offset = new RecordOffset();
            ConnectRecord record = new ConnectRecord(partition, offset, System.currentTimeMillis(), message + i);
            queue.offer(record);
        }
    }

}
