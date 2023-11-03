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

import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.redis.AbstractRedisServer;
import org.apache.eventmesh.connector.redis.RedisProperties;
import org.apache.eventmesh.connector.redis.cloudevent.CloudEventCodec;
import org.apache.eventmesh.connector.redis.sink.config.RedisSinkConfig;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordPartition;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RTopic;

import io.cloudevents.CloudEvent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RedisSinkConnectorTest extends AbstractRedisServer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RedisSinkConnector connector;

    private Redisson redisson;

    private RedisSinkConfig sinkConfig;

    @BeforeEach
    public void setUp() throws Exception {
        ConfigService configService = ConfigService.getInstance();
        RedisProperties properties = configService.buildConfigInstance(RedisProperties.class);
        redisson = createRedisson(properties);
        connector = new RedisSinkConnector();
        sinkConfig = (RedisSinkConfig) ConfigUtil.parse(connector.configClass());
        connector.init(sinkConfig);
        connector.start();
    }

    private Redisson createRedisson(RedisProperties properties) {
        String serverAddress = properties.getServerAddress();
        String serverPassword = properties.getServerPassword();

        org.redisson.config.Config config =
            OBJECT_MAPPER.convertValue(properties.getRedissonProperties(), org.redisson.config.Config.class);

        if (config == null) {
            config = new org.redisson.config.Config();
        }
        config.setCodec(CloudEventCodec.getInstance());
        config.useSingleServer()
            .setAddress(serverAddress)
            .setPassword(serverPassword);

        return (Redisson) Redisson.create(config);
    }

    @Test
    public void testPutConnectRecords() throws InterruptedException {
        RTopic topic = redisson.getTopic(sinkConfig.connectorConfig.getTopic());

        final String expectedMessage = "testRedisMessage";
        final int expectedCount = 5;
        final CountDownLatch downLatch = new CountDownLatch(expectedCount);
        topic.addListener(CloudEvent.class, (channel, msg) -> {
            downLatch.countDown();
            Assertions.assertNotNull(msg.getData());
            HashMap<String, String> contentMap = JsonUtils.parseTypeReferenceObject(new String(msg.getData().toBytes()),
                new TypeReference<HashMap<String, String>>() {
                });
            Assertions.assertNotNull(contentMap);
            Assertions.assertEquals(expectedMessage, contentMap.get("content"));
        });

        List<ConnectRecord> records = new ArrayList<>();
        for (int i = 0; i < expectedCount; i++) {
            RecordPartition partition = new RecordPartition();
            RecordOffset offset = new RecordOffset();
            Map<String, String> content = new HashMap<>();
            content.put("content", expectedMessage);
            ConnectRecord connectRecord = new ConnectRecord(partition, offset, System.currentTimeMillis(),
                JsonUtils.toJSONString(content).getBytes(StandardCharsets.UTF_8));
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
    }
}
