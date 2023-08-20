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

import org.apache.eventmesh.connector.redis.cloudevent.CloudEventCodec;
import org.apache.eventmesh.connector.redis.sink.config.RedisSinkConfig;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.redisson.Redisson;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RedisSinkConnector implements Sink {

    private RTopic topic;

    private RedisSinkConfig sinkConfig;

    private RedissonClient redissonClient;

    @Override
    public Class<? extends Config> configClass() {
        return RedisSinkConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        this.sinkConfig = (RedisSinkConfig) config;
        org.redisson.config.Config redisConfig = new org.redisson.config.Config();
        redisConfig.useSingleServer().setAddress(sinkConfig.connectorConfig.getServer());
        redisConfig.setCodec(CloudEventCodec.getInstance());
        this.redissonClient = Redisson.create(redisConfig);
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.sinkConfig = (RedisSinkConfig) sinkConnectorContext.getSinkConfig();
        org.redisson.config.Config redisConfig = new org.redisson.config.Config();
        redisConfig.useSingleServer().setAddress(sinkConfig.connectorConfig.getServer());
        redisConfig.setCodec(CloudEventCodec.getInstance());
        this.redissonClient = Redisson.create(redisConfig);
    }

    @Override
    public void start() throws Exception {
        this.topic = redissonClient.getTopic(sinkConfig.connectorConfig.getTopic());
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sinkConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() throws Exception {
        this.redissonClient.shutdown();
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        for (ConnectRecord connectRecord : sinkRecords) {
            CloudEvent event = convertRecordToEvent(connectRecord);
            try {
                long publish = topic.publish(event);
            } catch (Exception e) {
                log.error("[RedisSinkConnector] sendResult has error : ", e);
            }
        }
    }

    public CloudEvent convertRecordToEvent(ConnectRecord connectRecord) {
        CloudEventBuilder cloudEventBuilder = CloudEventBuilder.v1()
            .withData((byte[]) connectRecord.getData());
        connectRecord.getExtensions().keySet().forEach(s -> {
            switch (s) {
                case "id":
                    cloudEventBuilder.withId(connectRecord.getExtension(s));
                    break;
                case "topic":
                    cloudEventBuilder.withSubject(connectRecord.getExtension(s));
                    break;
                case "source":
                    try {
                        cloudEventBuilder.withSource(new URI(connectRecord.getExtension(s)));
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                    break;
                case "type":
                    cloudEventBuilder.withType(connectRecord.getExtension(s));
                    break;
                default:
                    cloudEventBuilder.withExtension(s, connectRecord.getExtension(s));
            }
        });
        return cloudEventBuilder.build();
    }
}
