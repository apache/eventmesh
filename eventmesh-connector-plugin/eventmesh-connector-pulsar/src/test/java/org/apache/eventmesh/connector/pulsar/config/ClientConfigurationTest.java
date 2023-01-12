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

package org.apache.eventmesh.connector.pulsar.config;

import org.apache.eventmesh.api.factory.ConnectorPluginFactory;
import org.apache.eventmesh.connector.pulsar.consumer.PulsarConsumerImpl;
import org.apache.eventmesh.connector.pulsar.producer.PulsarProducerImpl;

import org.junit.Assert;
import org.junit.Test;

public class ClientConfigurationTest {

    @Test
    public void getConfigWhenPulsarConsumerInit() {
        PulsarConsumerImpl consumer =
                (PulsarConsumerImpl) ConnectorPluginFactory.getMeshMQPushConsumer("pulsar");

        ClientConfiguration config = consumer.getClientConfiguration();
        assertConfig(config);
    }

    @Test
    public void getConfigWhenPulsarProducerInit() {
        PulsarProducerImpl producer =
                (PulsarProducerImpl) ConnectorPluginFactory.getMeshMQProducer("pulsar");

        ClientConfiguration config = producer.getClientConfiguration();
        assertConfig(config);
    }

    private void assertConfig(ClientConfiguration config) {
        Assert.assertEquals(config.getServiceAddr(), "127.0.0.1:6650");
        Assert.assertEquals(config.getAuthPlugin(), "authPlugin-success!!!");
        Assert.assertEquals(config.getAuthParams(), "authParams-success!!!");
    }
}