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

package org.apache.eventmesh.connector.redis.sink.connector;

import org.apache.eventmesh.connector.redis.AbstractRedisServer;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordPartition;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RedisSinkConnectorTest extends AbstractRedisServer {

    private RedisSinkConnector connector;

    @BeforeEach
    public void setUp() throws Exception {
        connector = new RedisSinkConnector();
        Config config = ConfigUtil.parse(connector.configClass());
        connector.init(config);
        connector.start();
    }

    @Test
    public void testPutConnectRecords() {
        final int count = 5;
        final String message = "testRedisMessage";
        List<ConnectRecord> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RecordPartition partition = new RecordPartition();
            RecordOffset offset = new RecordOffset();
            ConnectRecord connectRecord = new ConnectRecord(partition, offset, System.currentTimeMillis(), (message + i).getBytes());
            connectRecord.addExtension("id", String.valueOf(UUID.randomUUID()));
            connectRecord.addExtension("source", "testSource");
            connectRecord.addExtension("type", "testType");
            records.add(connectRecord);
        }
        connector.put(records);
    }

    @AfterEach
    public void tearDown() throws Exception {
        connector.stop();
    }
}
