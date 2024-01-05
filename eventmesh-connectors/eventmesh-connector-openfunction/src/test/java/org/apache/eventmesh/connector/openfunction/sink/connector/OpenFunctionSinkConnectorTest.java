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

package org.apache.eventmesh.connector.openfunction.sink.connector;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.eventmesh.connector.openfunction.sink.config.OpenFunctionSinkConfig;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordPartition;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OpenFunctionSinkConnectorTest {

    private final OpenFunctionSinkConnector connector = new OpenFunctionSinkConnector();

    @BeforeEach
    public void setUp() throws Exception {
        OpenFunctionSinkConfig sinkConfig = new OpenFunctionSinkConfig();
        connector.init(sinkConfig);
        connector.start();
    }

    @Test
    public void testSinkConnectorRunning() {
        Assertions.assertTrue(connector.isRunning());
    }

    @Test
    public void testOpenFunctionSinkConnector() throws Exception {
        final int count = 5;
        final String message = "testMessage";
        writeMockedRecords(count, message);
        BlockingQueue<ConnectRecord> queue = connector.queue();
        Assertions.assertEquals(count, queue.size());
        for (int i = 0; i < count; i++) {
            ConnectRecord poll = queue.poll();
            assertNotNull(poll);
            String expectedMessage = message + i;
            Assertions.assertEquals(poll.getData(), expectedMessage);
        }
    }

    @AfterEach
    public void shutdownConnector() {
        connector.stop();
    }

    private void writeMockedRecords(int count, String message) throws Exception {
        List<ConnectRecord> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RecordPartition partition = new RecordPartition();
            RecordOffset offset = new RecordOffset();
            records.add(new ConnectRecord(partition, offset, System.currentTimeMillis(), message + i));
        }
        connector.put(records);
    }

}
