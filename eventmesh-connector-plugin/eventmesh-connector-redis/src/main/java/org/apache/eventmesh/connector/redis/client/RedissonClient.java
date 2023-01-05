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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.eventmesh.connector.redis.client;

import static org.apache.eventmesh.connector.redis.config.ConfigurationWrapper.getPropertiesByPrefix;
import static org.apache.eventmesh.connector.redis.config.ConfigurationWrapper.getProperty;

import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.connector.redis.cloudevent.CloudEventCodec;
import org.apache.eventmesh.connector.redis.config.ConfigOptions;
import org.apache.eventmesh.connector.redis.config.RedisProperties;

import java.util.Arrays;

import org.redisson.Redisson;
import org.redisson.config.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Within EventMesh's JVM, there is no multi-connector server, and redisson itself is pooled management,
 * so a single instance is fine, and it can save resources and improve performance.
 */
public final class RedissonClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final Redisson INSTANCE;

    static {
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        INSTANCE = create();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                INSTANCE.shutdown();
            } catch (Exception ignore) {
                //
            }
        }));
    }

    public static Redisson create() {
        RedisProperties properties = new RedisProperties();
        String serverTypeName = getProperty(ConfigOptions.SERVER_TYPE);
        if (serverTypeName != null) {
            try {
                properties.setServerType(RedisProperties.ServerType.valueOf(serverTypeName));
            } catch (Exception e) {
                final String message = "Invalid Redis server type: " + properties.getServerType()
                    + ", supported values are: "
                    + Arrays.toString(RedisProperties.ServerType.values());
                throw new ConnectorRuntimeException(message, e);
            }
        } else {
            properties.setServerType(RedisProperties.ServerType.SINGLE);
        }

        String serverAddress = getProperty(ConfigOptions.SERVER_ADDRESS);
        if (serverAddress != null) {
            properties.setServerAddress(serverAddress);
        } else {
            throw new ConnectorRuntimeException("Lack Redis server address");
        }

        String serverMasterName = getProperty(ConfigOptions.SERVER_MASTER_NAME);
        if (serverMasterName != null) {
            properties.setServerMasterName(serverMasterName);
        }

        String serverPassword = getProperty(ConfigOptions.SERVER_PASSWORD);
        if (serverPassword != null) {
            properties.setServerPassword(serverPassword);
        }

        properties.setRedissonProperties(getPropertiesByPrefix(ConfigOptions.REDISSON_PROPERTIES_PREFIX));

        return create(properties);
    }

    private static Redisson create(RedisProperties properties) {
        RedisProperties.ServerType serverType;
        try {
            serverType = properties.getServerType();
        } catch (IllegalArgumentException ie) {
            final String message = "Invalid Redis server type: " + properties.getServerType()
                + ", supported values are: "
                + Arrays.toString(RedisProperties.ServerType.values());
            throw new ConnectorRuntimeException(message, ie);
        }

        String serverAddress = properties.getServerAddress();
        String serverPassword = properties.getServerPassword();
        String masterName = properties.getServerMasterName();

        Config config = OBJECT_MAPPER.convertValue(properties.getRedissonProperties(), Config.class);

        if (config == null) {
            config = new Config();
        }

        config.setCodec(CloudEventCodec.INSTANCE);

        switch (serverType) {
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
                final String message = "Invalid Redis server type: " + properties.getServerType()
                    + ", supported values are: "
                    + Arrays.toString(RedisProperties.ServerType.values());
                throw new ConnectorRuntimeException(message);
        }

        return (Redisson) Redisson.create(config);
    }
}
