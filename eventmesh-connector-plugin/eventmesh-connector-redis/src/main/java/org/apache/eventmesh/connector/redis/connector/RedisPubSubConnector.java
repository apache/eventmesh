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

package org.apache.eventmesh.connector.redis.connector;

import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.pubsub.api.async.RedisClusterPubSubAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.api.LifeCycle;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.connector.redis.config.ClientConfiguration;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class RedisPubSubConnector implements LifeCycle {

    protected static RedisClient redisClient;

    protected RedisClusterPubSubAsyncCommands<String, String> redisCommands;

    protected final AtomicBoolean started = new AtomicBoolean(false);

    protected StatefulRedisConnection<String, String> redisConnection;

    protected ClientConfiguration clientConfiguration;

    private Properties properties;

    protected RedisPubSubConnector() {
        final ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.init();

        Properties properties = new Properties();
        properties.put("host", clientConfiguration.host);
        properties.put("port", clientConfiguration.port);
        properties.put("username", clientConfiguration.username);
        properties.put("password", clientConfiguration.password);
        properties.put("index", clientConfiguration.index);
        properties.put("masterId", clientConfiguration.masterId);
        properties.put("timeout", clientConfiguration.timeout);
        properties.put("retryTime", clientConfiguration.retryTime);
        this.properties = properties;
    }

    public void init(Properties properties) {
        RedisURI redisURI = RedisURI.Builder
                .sentinel(properties.getProperty("host"), Integer.parseInt(properties.getProperty("port")), properties.getProperty("masterId"))
                .withDatabase(Integer.parseInt(properties.getProperty("index")))
                .withAuthentication(properties.getProperty("username"), properties.getProperty("password"))
                .build();
        redisConnection = redisClient.connect(redisURI);
    }

    @Override
    public boolean isStarted() {
        return this.started.get();
    }

    @Override
    public boolean isClosed() {
        return !this.isStarted();
    }

    @Override
    public void start() {
        redisClient.setOptions(ClientOptions.builder().build());
        redisCommands = (RedisClusterPubSubAsyncCommands<String, String>) redisConnection.async();

        boolean scriptRunning = true;
        int retryTime = 0;
        while (scriptRunning && retryTime++ < clientConfiguration.retryTime) {
            scriptRunning = false;

            try {
                redisCommands.flushall();
                redisCommands.flushdb();
            } catch (RedisBusyException e) {
                scriptRunning = true;
                try {
                    redisCommands.scriptKill();
                } catch (RedisException e1) {
                    throw new ConnectorRuntimeException("script kill fail");
                }
            }
        }
    }

    @Override
    public void shutdown() {
        if (redisCommands != null) {
            redisCommands.getStatefulConnection().close();
        }
    }

    public void checkIsClosed() {
        if (isClosed()) {
            log.info("this connection is closed, and will reconnect");
            this.start();
        }
    }
    protected RedisClient getRedisClient() {
        return redisClient;
    }

    protected RedisClusterPubSubAsyncCommands<String, String> getRedisCommands() {
        return redisCommands;
    }
}
