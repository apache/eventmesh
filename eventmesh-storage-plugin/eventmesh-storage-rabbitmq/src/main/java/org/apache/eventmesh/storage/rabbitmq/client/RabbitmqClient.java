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

package org.apache.eventmesh.storage.rabbitmq.client;

import org.apache.commons.lang3.StringUtils;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RabbitmqClient {

    private final RabbitmqConnectionFactory rabbitmqConnectionFactory;

    public RabbitmqClient(RabbitmqConnectionFactory rabbitmqConnectionFactory) {
        this.rabbitmqConnectionFactory = rabbitmqConnectionFactory;
    }

    /**
     * get rabbitmq connection
     *
     * @param host        host
     * @param username    username
     * @param passwd      password
     * @param port        port
     * @param virtualHost virtual host
     * @return connection
     * @throws Exception Exception
     */
    public Connection getConnection(String host, String username,
        String passwd, int port,
        String virtualHost) throws Exception {
        ConnectionFactory factory = rabbitmqConnectionFactory.createConnectionFactory();
        factory.setHost(host.trim());
        factory.setPort(port);
        if (StringUtils.isNotEmpty(virtualHost)) {
            factory.setVirtualHost(virtualHost.trim());
        }
        factory.setUsername(username);
        factory.setPassword(passwd.trim());

        return rabbitmqConnectionFactory.createConnection(factory);
    }

    /**
     * send message
     *
     * @param channel      channel
     * @param exchangeName exchange name
     * @param routingKey   routing key
     * @param message      message
     * @throws Exception Exception
     */
    public void publish(Channel channel, String exchangeName,
        String routingKey, byte[] message) throws Exception {
        channel.basicPublish(exchangeName, routingKey, null, message);
    }

    /**
     * binding queue
     *
     * @param channel             channel
     * @param builtinExchangeType exchange type
     * @param exchangeName        exchange name
     * @param routingKey          routing key
     * @param queueName           queue name
     */
    public void binding(Channel channel, BuiltinExchangeType builtinExchangeType,
        String exchangeName, String routingKey, String queueName) {
        try {
            channel.exchangeDeclare(exchangeName, builtinExchangeType.getType(), true,
                false, false, null);
            channel.queueDeclare(queueName, false, false,
                false, null);
            routingKey = builtinExchangeType.getType().equals(BuiltinExchangeType.FANOUT.getType()) ? "" : routingKey;
            channel.queueBind(queueName, exchangeName, routingKey);
        } catch (Exception ex) {
            log.error("[RabbitmqClient] binding happen exception.", ex);
        }
    }

    /**
     * unbinding queue
     *
     * @param channel      channel
     * @param exchangeName exchange name
     * @param routingKey   routing key
     * @param queueName    queue name
     */
    public void unbinding(Channel channel, String exchangeName, String routingKey, String queueName) {
        try {
            channel.queueUnbind(queueName, exchangeName, routingKey);
        } catch (Exception ex) {
            log.error("[RabbitmqClient] unbinding happen exception.", ex);
        }
    }

    /**
     * close connection
     *
     * @param connection connection
     */
    public void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ex) {
                log.error("[RabbitmqClient] connection close happen exception.", ex);
            }
        }
    }

    /**
     * close channel
     *
     * @param channel channel
     */
    public void closeChannel(Channel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (Exception ex) {
                log.error("[RabbitmqClient] channel close happen exception.", ex);
            }
        }
    }
}
