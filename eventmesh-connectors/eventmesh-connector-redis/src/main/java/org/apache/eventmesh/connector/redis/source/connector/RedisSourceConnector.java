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

import org.apache.eventmesh.connector.redis.source.config.RedisSourceConfig;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.api.source.Source;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.redisson.Redisson;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.config.TransportMode;

public class RedisSourceConnector implements Source {

    private static final int DEFAULT_BATCH_SIZE = 10;

    private RTopic topic;

    private RedisSourceConfig sourceConfig;

    private RedissonClient redissonClient;

    private BlockingQueue<ConnectRecord> queue;

    @Override
    public Class<? extends Config> configClass() {
        return RedisSourceConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        this.sourceConfig = (RedisSourceConfig) config;
        org.redisson.config.Config redisConfig = new org.redisson.config.Config();
        redisConfig.useSingleServer().setAddress(sourceConfig.connectorConfig.getServer());
        this.redissonClient = Redisson.create(redisConfig);
        this.queue = new LinkedBlockingQueue<>(1000);
    }

    @Override
    public void start() throws Exception {
        this.topic = redissonClient.getTopic(sourceConfig.connectorConfig.getTopic());
        this.topic.addListener(ConnectRecord.class, (channel, msg) -> {
            queue.add(msg);
        });
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sourceConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() throws Exception {
        this.topic.removeAllListeners();
        this.redissonClient.shutdown();
    }

    @Override
    public List<ConnectRecord> poll() {
        List<ConnectRecord> connectRecords = new ArrayList<>(DEFAULT_BATCH_SIZE);
        for (int count = 0; count < DEFAULT_BATCH_SIZE; ++count) {
            try {
                ConnectRecord connectRecord = queue.poll(3, TimeUnit.SECONDS);
                if (connectRecord == null) {
                    break;
                }
                connectRecords.add(connectRecord);
            } catch (InterruptedException e) {
                break;
            }
        }
        return connectRecords;
    }
}
