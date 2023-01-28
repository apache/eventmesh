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

package org.apache.eventmesh.connector.rocketmq.config;

import org.apache.eventmesh.api.factory.ConnectorPluginFactory;
import org.apache.eventmesh.connector.rocketmq.consumer.RocketMQConsumerImpl;
import org.apache.eventmesh.connector.rocketmq.producer.RocketMQProducerImpl;

import org.junit.Assert;
import org.junit.Test;

public class ClientConfigurationTest {

    @Test
    public void getConfigWhenRocketMQConsumerInit() {
        RocketMQConsumerImpl consumer =
                (RocketMQConsumerImpl) ConnectorPluginFactory.getMeshMQPushConsumer("rocketmq");

        ClientConfiguration config = consumer.getClientConfiguration();
        assertConfig(config);
    }

    @Test
    public void getConfigWhenRocketMQProducerInit() {
        RocketMQProducerImpl producer =
                (RocketMQProducerImpl) ConnectorPluginFactory.getMeshMQProducer("rocketmq");

        ClientConfiguration config = producer.getClientConfiguration();
        assertConfig(config);
    }

    private void assertConfig(ClientConfiguration config) {
        Assert.assertEquals(config.namesrvAddr, "127.0.0.1:9876;127.0.0.1:9876");
        Assert.assertEquals(config.clientUserName, "username-succeed!!!");
        Assert.assertEquals(config.clientPass, "password-succeed!!!");
        Assert.assertEquals(config.consumeThreadMin, Integer.valueOf(1816));
        Assert.assertEquals(config.consumeThreadMax, Integer.valueOf(2816));
        Assert.assertEquals(config.consumeQueueSize, Integer.valueOf(3816));
        Assert.assertEquals(config.pullBatchSize, Integer.valueOf(4816));
        Assert.assertEquals(config.ackWindow, Integer.valueOf(5816));
        Assert.assertEquals(config.pubWindow, Integer.valueOf(6816));
        Assert.assertEquals(config.consumeTimeout, 7816);
        Assert.assertEquals(config.pollNameServerInterval, Integer.valueOf(8816));
        Assert.assertEquals(config.heartbeatBrokerInterval, Integer.valueOf(9816));
        Assert.assertEquals(config.rebalanceInterval, Integer.valueOf(11816));
        Assert.assertEquals(config.clusterName, "cluster-succeed!!!");
        Assert.assertEquals(config.accessKey, "accessKey-succeed!!!");
        Assert.assertEquals(config.secretKey, "secretKey-succeed!!!");
    }
}