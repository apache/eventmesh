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

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
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
        String host = ConfigurationWrapper.getProperty(ConfigKey.HOST);
        Preconditions.checkState(StringUtils.isNotEmpty(host), String.format("%s error", ConfigKey.HOST));
        this.host = host;

        String port = ConfigurationWrapper.getProperty(ConfigKey.PORT);
        Preconditions.checkState(StringUtils.isNotEmpty(port), String.format("%s error", ConfigKey.PORT));
        this.port = Integer.parseInt(port);

        String username = ConfigurationWrapper.getProperty(ConfigKey.USER_NAME);
        Preconditions.checkState(StringUtils.isNotEmpty(username), String.format("%s error", ConfigKey.USER_NAME));
        this.username = username;

        String passwd = ConfigurationWrapper.getProperty(ConfigKey.PASSWD);
        Preconditions.checkState(StringUtils.isNotEmpty(passwd), String.format("%s error", ConfigKey.PASSWD));
        this.passwd = passwd;

        this.virtualHost = ConfigurationWrapper.getProperty(ConfigKey.VIRTUAL_HOST);

        String exchangeType = ConfigurationWrapper.getProperty(ConfigKey.EXCHANGE_TYPE);
        Preconditions.checkState(StringUtils.isNotEmpty(exchangeType), String.format("%s error", ConfigKey.EXCHANGE_TYPE));
        this.exchangeType = BuiltinExchangeType.valueOf(exchangeType);

        String exchangeName = ConfigurationWrapper.getProperty(ConfigKey.EXCHANGE_NAME);
        Preconditions.checkState(StringUtils.isNotEmpty(host), String.format("%s error", ConfigKey.EXCHANGE_NAME));
        this.exchangeName = exchangeName;

        String routingKey = ConfigurationWrapper.getProperty(ConfigKey.ROUTING_KEY);
        Preconditions.checkState(StringUtils.isNotEmpty(routingKey), String.format("%s error", ConfigKey.ROUTING_KEY));
        this.routingKey = routingKey;

        String queueName = ConfigurationWrapper.getProperty(ConfigKey.QUEUE_NAME);
        Preconditions.checkState(StringUtils.isNotEmpty(queueName), String.format("%s error", ConfigKey.QUEUE_NAME));
        this.queueName = queueName;

        String autoAck = ConfigurationWrapper.getProperty(ConfigKey.AUTO_ACK);
        this.autoAck = StringUtils.isNotEmpty(autoAck) && Boolean.parseBoolean(autoAck);
    }
}
