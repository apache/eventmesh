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

package org.apache.eventmesh.connector.redis.source.connector;

import org.apache.eventmesh.common.config.connector.redis.RedisSourceConfig;
import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;
import org.apache.eventmesh.connector.redis.AbstractRedisServer;
import org.apache.eventmesh.connector.redis.cloudevent.CloudEventCodec;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.CloudEventUtil;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RTopic;
import org.redisson.config.Config;

public class RedisSourceConnectorTest extends AbstractRedisServer {

    private RedisSourceConnector connector;

    private final String expectedMessage = "testRedisMessage";

    private RTopic topic;

    private Redisson redisson;

    @BeforeEach
    public void setUp() throws Exception {
        connector = new RedisSourceConnector();
        RedisSourceConfig sourceConfig = (RedisSourceConfig) ConfigUtil.parse(connector.configClass());
        setupRedisServer(getPortFromUrl(sourceConfig.getConnectorConfig().getServer()));
        connector.init(sourceConfig);
        connector.start();
        Config config = new Config();
        config.setCodec(CloudEventCodec.getInstance());
        config.useSingleServer()
            .setAddress(sourceConfig.getConnectorConfig().getServer());
        redisson = (Redisson) Redisson.create(config);
        topic = redisson.getTopic(sourceConfig.connectorConfig.getTopic());
    }

    @Test
    public void testPollConnectRecords() throws Exception {
        publishMockEvents();
        List<ConnectRecord> connectRecords = connector.poll();
        for (ConnectRecord connectRecord : connectRecords) {
            String actualMessage = new String((byte[]) connectRecord.getData());
            Assertions.assertEquals(expectedMessage, actualMessage);
        }
    }

    private void publishMockEvents() {
        int mockCount = 5;
        for (int i = 0; i < mockCount; i++) {
            RecordPartition partition = new RecordPartition();
            RecordOffset offset = new RecordOffset();
            ConnectRecord connectRecord = new ConnectRecord(partition, offset, System.currentTimeMillis(),
                ("\"" + expectedMessage + "\"").getBytes(StandardCharsets.UTF_8));
            connectRecord.addExtension("id", String.valueOf(UUID.randomUUID()));
            connectRecord.addExtension("source", "testSource");
            connectRecord.addExtension("topic", "testTopic");
            connectRecord.addExtension("type", "testType");
            connectRecord.addExtension("datacontenttype", "testdatacontenttype");
            topic.publish(CloudEventUtil.convertRecordToEvent(connectRecord));
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        connector.stop();
        redisson.shutdown();
        shutdownRedisServer();
    }
}
