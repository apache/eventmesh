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

package org.apache.eventmesh.connector.rabbitmq.config;

import org.apache.eventmesh.common.utils.AssertUtils;

import com.rabbitmq.client.BuiltinExchangeType;

import lombok.Data;

@Data
public class ConfigurationHolder {
    public String host;
    public int port;
    public String username;
    public String passwd;
    public String virtualHost;

    public BuiltinExchangeType exchangeType;
    public String exchangeName;
    public String routingKey;
    public String queueName;
    public boolean autoAck;

    public void init() {
        this.host = getProperty(ConfigKey.HOST);
        this.port = Integer.parseInt(getProperty(ConfigKey.PORT));
        this.username = getProperty(ConfigKey.USER_NAME);
        this.passwd = getProperty(ConfigKey.PASSWD);
        this.virtualHost = ConfigurationWrapper.getProperty(ConfigKey.VIRTUAL_HOST);
        this.exchangeType = BuiltinExchangeType.valueOf(getProperty(ConfigKey.EXCHANGE_TYPE));
        this.exchangeName = getProperty(ConfigKey.EXCHANGE_NAME);
        this.routingKey = getProperty(ConfigKey.ROUTING_KEY);
        this.queueName = getProperty(ConfigKey.QUEUE_NAME);
        this.autoAck = Boolean.parseBoolean(getProperty(ConfigKey.AUTO_ACK));
    }

    /**
     * get property
     *
     * @param configKey config key
     * @return property
     */
    private String getProperty(String configKey) {
        String property = ConfigurationWrapper.getProperty(configKey);
        AssertUtils.notBlack(property, String.format("%s error", configKey));
        return property;

    }
}
