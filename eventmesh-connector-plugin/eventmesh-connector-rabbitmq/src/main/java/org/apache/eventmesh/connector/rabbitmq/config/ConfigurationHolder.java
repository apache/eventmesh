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

import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigFiled;

import com.rabbitmq.client.BuiltinExchangeType;

import lombok.Data;

@Data
@Config(prefix = "eventMesh.server.rabbitmq", path = "classPath://rabbitmq-client.properties")
public class ConfigurationHolder {

    @ConfigFiled(field = "host")
    public String host;

    @ConfigFiled(field = "port")
    public int port;

    @ConfigFiled(field = "username")
    public String username;

    @ConfigFiled(field = "passwd")
    public String passwd;

    @ConfigFiled(field = "virtualHost")
    public String virtualHost;

    @ConfigFiled(field = "exchangeType")
    public BuiltinExchangeType exchangeType;

    @ConfigFiled(field = "exchangeName")
    public String exchangeName;

    @ConfigFiled(field = "routingKey")
    public String routingKey;

    @ConfigFiled(field = "queueName")
    public String queueName;

    @ConfigFiled(field = "autoAck")
    public boolean autoAck;
}
