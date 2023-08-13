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

package org.apache.eventmesh.storage.rabbitmq;

import org.apache.eventmesh.storage.rabbitmq.client.RabbitmqConnectionFactory;

import com.github.fridujo.rabbitmq.mock.MockConnectionFactory;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class RabbitmqMockConnectionFactory extends RabbitmqConnectionFactory {

    private final ConnectionFactory myConnectionFactory;

    private final Connection myConnection;

    private final Channel myChannel;

    public RabbitmqMockConnectionFactory() throws Exception {
        this.myConnectionFactory = new MockConnectionFactory();
        this.myConnectionFactory.setHost("127.0.0.1");
        this.myConnectionFactory.setPort(5672);
        this.myConnectionFactory.setUsername("root");
        this.myConnectionFactory.setPassword("123456");
        this.myConnection = this.myConnectionFactory.newConnection();
        this.myChannel = myConnection.createChannel();
    }

    @Override
    public ConnectionFactory createConnectionFactory() {
        return this.myConnectionFactory;
    }

    @Override
    public Connection createConnection(ConnectionFactory connectionFactory) {
        return this.myConnection;
    }

    @Override
    public Channel createChannel(Connection connection) {
        return this.myChannel;
    }
}
