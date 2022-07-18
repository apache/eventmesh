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

package org.apache.eventmesh.connector.redis.config;

import static org.apache.eventmesh.connector.redis.config.ClientConfiguration.ConfigOptions.CONF_FILE;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.PropertiesUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ClientConfiguration {

    public ClientConfiguration() {
        try (InputStream resourceAsStream = ClientConfiguration.class.getResourceAsStream(
            "/" + CONF_FILE)) {
            if (resourceAsStream != null) {
                properties.load(resourceAsStream);
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Load %s file from classpath error", CONF_FILE));
        }
        try {
            String configPath = Constants.EVENTMESH_CONF_HOME + File.separator + CONF_FILE;
            if (new File(configPath).exists()) {
                properties.load(new BufferedReader(new FileReader(configPath)));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Cannot load %s file from conf", CONF_FILE));
        }
    }


    private static final Properties properties = new Properties();

    /**
     * The redis server configuration to be used.
     */
    private ServerType serverType = ServerType.SINGLE;

    /**
     * The master server name used by Redis Sentinel servers and master change monitoring task.
     */
    private String serverMasterName = "master";

    /**
     * The address of the redis server following format -- host1:port1,host2:port2,……
     */
    private String serverAddress;

    /**
     * The password for redis authentication.
     */
    private String serverPassword;

    /**
     * The redisson options, redisson properties, please refer to the redisson manual.
     */
    private Properties redissonProperties;

    public ServerType getServerType() {
        return serverType;
    }

    public void setServerType(ServerType serverType) {
        this.serverType = serverType;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public String getServerPassword() {
        return serverPassword;
    }

    public void setServerPassword(String serverPassword) {
        this.serverPassword = serverPassword;
    }

    public String getServerMasterName() {
        return serverMasterName;
    }

    public void setServerMasterName(String serverMasterName) {
        this.serverMasterName = serverMasterName;
    }

    public Properties getRedissonProperties() {
        return redissonProperties;
    }

    public void setRedissonProperties(Properties redissonProperties) {
        this.redissonProperties = redissonProperties;
    }

    public static String getProperty(String key) {
        return StringUtils.isEmpty(key)
            ? null : properties.getProperty(key, null);
    }

    public static Properties getPropertiesByPrefix(String prefix) {
        if (StringUtils.isBlank(prefix)) {
            return null;
        }
        Properties to = new Properties();
        return PropertiesUtils.getPropertiesByPrefix(properties, to, prefix);
    }

    public enum ServerType {
        SINGLE,
        CLUSTER,
        SENTINEL
    }

    public interface ConfigOptions {

        /**
         * The config file used to get redis configuration.
         */
        String CONF_FILE = "redis-client.properties";

        /**
         * The redis server configuration to be used, default is SINGLE.
         */
        String SERVER_TYPE = "eventMesh.server.redis.serverType";

        /**
         * The master server name used by Redis Sentinel servers and master change monitoring task, default is master.
         */
        String SERVER_MASTER_NAME = "eventMesh.server.redis.serverMasterName";

        /**
         * The address of the redis server following format -- host1:port1,host2:port2,……
         */
        String SERVER_ADDRESS = "eventMesh.server.redis.serverAddress";

        /**
         * The password for redis authentication.
         */
        String SERVER_PASSWORD = "eventMesh.server.redis.serverPassword";

        /**
         * The redisson options, redisson properties, please refer to the redisson manual.
         * <p>
         * For example, the redisson timeout property is configured as eventMesh.server.redis.redisson.timeout
         */
        String REDISSON_PROPERTIES_PREFIX = "eventMesh.server.redis.redisson";
    }
}