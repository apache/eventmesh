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

package cn.webank.defibus.broker.client;

import cn.webank.defibus.broker.DeFiBrokerController;
import cn.webank.defibus.common.DeFiBusBrokerConfig;
import cn.webank.defibus.common.util.ReflectUtil;
import io.netty.channel.Channel;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.broker.client.ClientChannelInfo;
import org.apache.rocketmq.broker.client.ConsumerIdsChangeListener;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.protocol.heartbeat.ConsumeType;
import org.apache.rocketmq.common.protocol.heartbeat.ConsumerData;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.common.protocol.heartbeat.SubscriptionData;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.remoting.protocol.LanguageCode;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Spy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class DeFiConsumerManagerTest {
    @Spy
    private DeFiBrokerController deFiBrokerController = new DeFiBrokerController(new BrokerConfig(), new NettyServerConfig(), new NettyClientConfig(), new MessageStoreConfig(), new DeFiBusBrokerConfig());
    private DeFiConsumerManager deFiConsumerManager;
    ConsumerIdsChangeListener consumerIdsChangeListener = (ConsumerIdsChangeListener) ReflectUtil.getSimpleProperty(BrokerController.class, deFiBrokerController, "consumerIdsChangeListener");
    private AdjustQueueNumStrategy adjustQueueNumStrategy = new AdjustQueueNumStrategy(deFiBrokerController);
    private String topic = "FooBar";
    private String group = "FooBarGroup";
    private ClientChannelInfo clientChannelInfo;
    private String clientId = UUID.randomUUID().toString();
    private ConsumerData consumerData;
    private Channel mockChannel;

    @Before
    public void init() {
        deFiConsumerManager = new DeFiConsumerManager(consumerIdsChangeListener, adjustQueueNumStrategy);
        mockChannel = mock(Channel.class);
        clientChannelInfo = new ClientChannelInfo(mockChannel, clientId, LanguageCode.JAVA, 100);
        consumerData = createConsumerData(group, topic);
        deFiConsumerManager.registerConsumer(consumerData.getGroupName(),
            clientChannelInfo,
            consumerData.getConsumeType(),
            consumerData.getMessageModel(),
            consumerData.getConsumeFromWhere(),
            consumerData.getSubscriptionDataSet(),
            false);
    }

    @Test
    public void testRegisterAndUnregisterConsumer() {
        assertThat(deFiConsumerManager.getConsumerTable().size()).isEqualTo(1);
        deFiConsumerManager.unregisterConsumer(consumerData.getGroupName(), clientChannelInfo, false);
        assertThat(deFiConsumerManager.getConsumerTable().size()).isEqualTo(0);
    }

    @Test
    public void testDoChannelCloseEvent() {
        assertThat(deFiConsumerManager.getConsumerTable().size()).isEqualTo(1);
        deFiConsumerManager.doChannelCloseEvent("127.0.0.1", mockChannel);
        assertThat(deFiConsumerManager.getConsumerTable().size()).isEqualTo(0);
    }

    private ConsumerData createConsumerData(String group, String topic) {
        ConsumerData consumerData = new ConsumerData();
        consumerData.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumerData.setConsumeType(ConsumeType.CONSUME_PASSIVELY);
        consumerData.setGroupName(group);
        consumerData.setMessageModel(MessageModel.CLUSTERING);
        Set<SubscriptionData> subscriptionDataSet = new HashSet<>();
        SubscriptionData subscriptionData = new SubscriptionData();
        subscriptionData.setTopic(topic);
        subscriptionData.setSubString("*");
        subscriptionData.setSubVersion(100L);
        subscriptionDataSet.add(subscriptionData);
        consumerData.setSubscriptionDataSet(subscriptionDataSet);
        return consumerData;
    }

}
