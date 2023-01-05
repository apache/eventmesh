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

public class ConfigKey {
    public static final String HOST = "eventMesh.server.rabbitmq.host";
    public static final String PORT = "eventMesh.server.rabbitmq.port";
    public static final String USER_NAME = "eventMesh.server.rabbitmq.username";
    public static final String PASSWD = "eventMesh.server.rabbitmq.passwd";
    public static final String VIRTUAL_HOST = "eventMesh.server.rabbitmq.virtualHost";

    public static final String EXCHANGE_TYPE = "eventMesh.server.rabbitmq.exchangeType";
    public static final String EXCHANGE_NAME = "eventMesh.server.rabbitmq.exchangeName";
    public static final String ROUTING_KEY = "eventMesh.server.rabbitmq.routingKey";
    public static final String QUEUE_NAME = "eventMesh.server.rabbitmq.queueName";
    public static final String AUTO_ACK = "eventMesh.server.rabbitmq.autoAck";
}
