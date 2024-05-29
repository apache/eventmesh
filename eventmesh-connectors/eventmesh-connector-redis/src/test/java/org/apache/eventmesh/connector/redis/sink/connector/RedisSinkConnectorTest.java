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

import org.apache.eventmesh.common.config.connector.redis.RedisSinkConfig;
import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;
import org.apache.eventmesh.connector.redis.AbstractRedisServer;
import org.apache.eventmesh.connector.redis.cloudevent.CloudEventCodec;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RTopic;
import org.redisson.config.Config;

import io.cloudevents.CloudEvent;

public class RedisSinkConnectorTest extends AbstractRedisServer {

    private RedisSinkConnector connector;

    private Redisson redisson;

    private RedisSinkConfig sinkConfig;

    @BeforeEach
    public void setUp() throws Exception {
        connector = new RedisSinkConnector();
        sinkConfig = (RedisSinkConfig) ConfigUtil.parse(connector.configClass());
        setupRedisServer(getPortFromUrl(sinkConfig.getConnectorConfig().getServer()));
        connector.init(sinkConfig);
        connector.start();
        Config config = new Config();
        config.setCodec(CloudEventCodec.getInstance());
        config.useSingleServer()
            .setAddress(sinkConfig.getConnectorConfig().getServer());
        redisson = (Redisson) Redisson.create(config);
    }

    @Test
    public void testPutConnectRecords() throws InterruptedException {
        RTopic topic = redisson.getTopic(sinkConfig.connectorConfig.getTopic());

        final String expectedMessage = "\"testRedisMessage\"";
        final int expectedCount = 5;
        final CountDownLatch downLatch = new CountDownLatch(expectedCount);
        topic.addListener(CloudEvent.class, (channel, msg) -> {
            downLatch.countDown();
            Assertions.assertNotNull(msg.getData());
            Assertions.assertEquals(expectedMessage, new String(msg.getData().toBytes()));
        });

        List<ConnectRecord> records = new ArrayList<>();
        for (int i = 0; i < expectedCount; i++) {
            RecordPartition partition = new RecordPartition();
            RecordOffset offset = new RecordOffset();
            ConnectRecord connectRecord = new ConnectRecord(partition, offset, System.currentTimeMillis(),
                expectedMessage.getBytes(StandardCharsets.UTF_8));
            connectRecord.addExtension("id", String.valueOf(UUID.randomUUID()));
            connectRecord.addExtension("source", "testSource");
            connectRecord.addExtension("type", "testType");
            records.add(connectRecord);
        }
        connector.put(records);
        Assertions.assertTrue(downLatch.await(10, TimeUnit.SECONDS));
    }

    @AfterEach
    public void tearDown() throws Exception {
        connector.stop();
        redisson.shutdown();
        shutdownRedisServer();
    }
}
