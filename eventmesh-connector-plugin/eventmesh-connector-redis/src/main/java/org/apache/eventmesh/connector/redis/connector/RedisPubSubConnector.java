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

import static org.apache.eventmesh.connector.redis.config.ClientConfiguration.getPropertiesByPrefix;
import static org.apache.eventmesh.connector.redis.config.ClientConfiguration.getProperty;

import org.apache.eventmesh.api.LifeCycle;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.connector.redis.cloudevent.CloudEventCodec;
import org.apache.eventmesh.connector.redis.config.ClientConfiguration;

import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.Redisson;
import org.redisson.config.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public abstract class RedisPubSubConnector implements LifeCycle {

    protected Redisson redisson;

    protected final AtomicBoolean started = new AtomicBoolean(false);

    private static final ClientConfiguration clientConfiguration = new ClientConfiguration();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected RedisPubSubConnector() {

    }

    public void init(Properties properties) {
        String serverType = getProperty(ClientConfiguration.ConfigOptions.SERVER_TYPE);
        if (serverType != null) {
            try {
                clientConfiguration.setServerType(ClientConfiguration.ServerType.valueOf(serverType));
            } catch (Exception e) {
                final String message = "Invalid Redis server type: " + clientConfiguration.getServerType()
                    + ", supported values are: "
                    + Arrays.toString(ClientConfiguration.ServerType.values());
                throw new ConnectorRuntimeException(message, e);
            }
        } else {
            clientConfiguration.setServerType(ClientConfiguration.ServerType.SINGLE);
        }

        String serverAddress = getProperty(ClientConfiguration.ConfigOptions.SERVER_ADDRESS);
        if (serverAddress != null) {
            clientConfiguration.setServerAddress(serverAddress);
        } else {
            throw new ConnectorRuntimeException("Lack Redis server address");
        }

        String masterName = getProperty(ClientConfiguration.ConfigOptions.SERVER_MASTER_NAME);
        if (masterName != null) {
            clientConfiguration.setServerMasterName(masterName);
        }

        String serverPassword = getProperty(ClientConfiguration.ConfigOptions.SERVER_PASSWORD);
        if (serverPassword != null) {
            clientConfiguration.setServerPassword(serverPassword);
        }

        clientConfiguration.setRedissonProperties(getPropertiesByPrefix(ClientConfiguration.ConfigOptions.REDISSON_PROPERTIES_PREFIX));

        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, Boolean.FALSE);
        Config config = OBJECT_MAPPER.convertValue(clientConfiguration.getRedissonProperties(), Config.class);
        if (Objects.isNull(config)) {
            config = new Config();
        }
        config.setCodec(CloudEventCodec.INSTANCE);

        switch (clientConfiguration.getServerType()) {
            case SINGLE:
                config.useSingleServer()
                    .setAddress(serverAddress)
                    .setPassword(serverPassword);
                break;
            case CLUSTER:
                config.useClusterServers()
                    .addNodeAddress(serverAddress.split(Constants.COMMA))
                    .setPassword(serverPassword);
                break;
            case SENTINEL:
                config.useSentinelServers()
                    .setMasterName(masterName)
                    .addSentinelAddress(serverAddress)
                    .setPassword(serverPassword);
                break;
            default:
                final String message = "Invalid Redis server type: " + clientConfiguration.getServerType()
                    + ", supported values are: "
                    + Arrays.toString(ClientConfiguration.ServerType.values());
                throw new ConnectorRuntimeException(message);
        }

        redisson = (Redisson) Redisson.create(config);
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
        if (isClosed()) {
            this.started.compareAndSet(false, true);
        }
    }

    @Override
    public void shutdown() {
        if (isStarted()) {
            try {
                redisson = null;
            } finally {
                this.started.compareAndSet(true, false);
            }
        }
    }

    public Redisson getRedisson() {
        return this.redisson;
    }
}