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

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.redis.RedisSourceConfig;
import org.apache.eventmesh.connector.redis.cloudevent.CloudEventCodec;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.CloudEventUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.redisson.Redisson;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import io.cloudevents.CloudEvent;

public class RedisSourceConnector implements Source {

    private RTopic topic;

    private RedisSourceConfig sourceConfig;

    private RedissonClient redissonClient;

    private BlockingQueue<CloudEvent> queue;

    private int maxBatchSize;

    private long maxPollWaitTime;

    @Override
    public Class<? extends Config> configClass() {
        return RedisSourceConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        this.sourceConfig = (RedisSourceConfig) config;
        doInit();
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.sourceConfig = (RedisSourceConfig) sourceConnectorContext.getSourceConfig();
        doInit();
    }

    private void doInit() {
        org.redisson.config.Config redisConfig = new org.redisson.config.Config();
        redisConfig.useSingleServer().setAddress(sourceConfig.connectorConfig.getServer());
        redisConfig.setCodec(CloudEventCodec.getInstance());
        this.redissonClient = Redisson.create(redisConfig);
        this.queue = new LinkedBlockingQueue<>(sourceConfig.getPollConfig().getCapacity());
        this.maxBatchSize = sourceConfig.getPollConfig().getMaxBatchSize();
        this.maxPollWaitTime = sourceConfig.getPollConfig().getMaxWaitTime();
    }

    @Override
    public void start() throws Exception {
        this.topic = redissonClient.getTopic(sourceConfig.connectorConfig.getTopic());
        this.topic.addListener(CloudEvent.class, (channel, msg) -> {
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
    public void onException(ConnectRecord record) {

    }

    @Override
    public void stop() throws Exception {
        this.topic.removeAllListeners();
        this.redissonClient.shutdown();
    }

    @Override
    public List<ConnectRecord> poll() {
        long startTime = System.currentTimeMillis();
        long remainingTime = maxPollWaitTime;

        List<ConnectRecord> connectRecords = new ArrayList<>(maxBatchSize);
        for (int count = 0; count < maxBatchSize; ++count) {
            try {
                CloudEvent event = queue.poll(remainingTime, TimeUnit.MILLISECONDS);
                if (event == null) {
                    break;
                }
                connectRecords.add(CloudEventUtil.convertEventToRecord(event));

                // calculate elapsed time and update remaining time for next poll
                long elapsedTime = System.currentTimeMillis() - startTime;
                remainingTime = maxPollWaitTime > elapsedTime ? maxPollWaitTime - elapsedTime : 0;
            } catch (InterruptedException e) {
                break;
            }
        }
        return connectRecords;
    }

}
