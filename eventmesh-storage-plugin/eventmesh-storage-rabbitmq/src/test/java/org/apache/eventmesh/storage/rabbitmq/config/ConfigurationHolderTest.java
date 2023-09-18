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

package org.apache.eventmesh.storage.rabbitmq.config;

import org.apache.eventmesh.api.factory.StoragePluginFactory;
import org.apache.eventmesh.storage.rabbitmq.consumer.RabbitmqConsumer;
import org.apache.eventmesh.storage.rabbitmq.producer.RabbitmqProducer;

import org.junit.Assert;
import org.junit.Test;

import com.rabbitmq.client.BuiltinExchangeType;

public class ConfigurationHolderTest {

    @Test
    public void getConfigWhenRabbitmqConsumerInit() {
        RabbitmqConsumer consumer =
                (RabbitmqConsumer) StoragePluginFactory.getMeshMQPushConsumer("rabbitmq");

        ConfigurationHolder config = consumer.getClientConfiguration();
        assertConfig(config);
    }

    @Test
    public void getConfigWhenRabbitmqProducerInit() {
        RabbitmqProducer producer =
                (RabbitmqProducer) StoragePluginFactory.getMeshMQProducer("rabbitmq");

        ConfigurationHolder config = producer.getClientConfiguration();
        assertConfig(config);
    }

    private void assertConfig(ConfigurationHolder config) {
        Assert.assertEquals("127.0.0.1", config.getHost());
        Assert.assertEquals(5672, config.getPort());
        Assert.assertEquals("username-success!!!", config.getUsername());
        Assert.assertEquals("passwd-success!!!", config.getPasswd());
        Assert.assertEquals("virtualHost-success!!!", config.getVirtualHost());

        Assert.assertEquals(BuiltinExchangeType.TOPIC, config.getExchangeType());
        Assert.assertEquals("exchangeName-success!!!", config.getExchangeName());
        Assert.assertEquals("routingKey-success!!!", config.getRoutingKey());
        Assert.assertEquals("queueName-success!!!", config.getQueueName());
        Assert.assertTrue(config.isAutoAck());
    }
}
