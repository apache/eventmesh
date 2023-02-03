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

import org.apache.eventmesh.api.factory.ConnectorPluginFactory;
import org.apache.eventmesh.connector.rabbitmq.consumer.RabbitmqConsumer;
import org.apache.eventmesh.connector.rabbitmq.producer.RabbitmqProducer;

import org.junit.Assert;
import org.junit.Test;

import com.rabbitmq.client.BuiltinExchangeType;

public class ConfigurationHolderTest {

    @Test
    public void getConfigWhenRabbitmqConsumerInit() {
        RabbitmqConsumer consumer =
                (RabbitmqConsumer) ConnectorPluginFactory.getMeshMQPushConsumer("rabbitmq");

        ConfigurationHolder config = consumer.getClientConfiguration();
        assertConfig(config);
    }

    @Test
    public void getConfigWhenRabbitmqProducerInit() {
        RabbitmqProducer producer =
                (RabbitmqProducer) ConnectorPluginFactory.getMeshMQProducer("rabbitmq");

        ConfigurationHolder config = producer.getClientConfiguration();
        assertConfig(config);
    }

    private void assertConfig(ConfigurationHolder config) {
        Assert.assertEquals(config.getHost(), "127.0.0.1");
        Assert.assertEquals(config.getPort(), 5672);
        Assert.assertEquals(config.getUsername(), "username-success!!!");
        Assert.assertEquals(config.getPasswd(), "passwd-success!!!");
        Assert.assertEquals(config.getVirtualHost(), "virtualHost-success!!!");

        Assert.assertEquals(config.getExchangeType(), BuiltinExchangeType.TOPIC);
        Assert.assertEquals(config.getExchangeName(), "exchangeName-success!!!");
        Assert.assertEquals(config.getRoutingKey(), "routingKey-success!!!");
        Assert.assertEquals(config.getQueueName(), "queueName-success!!!");
        Assert.assertTrue(config.isAutoAck());
    }
}