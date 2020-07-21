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

package cn.webank.defibus.broker.consumequeue;

import cn.webank.defibus.broker.DeFiBrokerController;
import cn.webank.defibus.common.DeFiBusBrokerConfig;
import io.netty.channel.Channel;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.apache.rocketmq.broker.client.ClientChannelInfo;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.protocol.heartbeat.ConsumeType;
import org.apache.rocketmq.common.protocol.heartbeat.ConsumerData;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.common.protocol.heartbeat.SubscriptionData;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.remoting.protocol.LanguageCode;
import org.apache.rocketmq.store.MessageStore;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConsumeQueueManagerTest {
    private ConsumeQueueManager consumeQueueManager = ConsumeQueueManager.onlyInstance();
    @Spy
    private DeFiBrokerController deFiBrokerController = new DeFiBrokerController(new BrokerConfig(), new NettyServerConfig(), new NettyClientConfig(), new MessageStoreConfig(), new DeFiBusBrokerConfig());
    @Mock
    private MessageStore messageStore;
    private final String group = "BarGroup";
    private final String topic = "BarTopic";
    private final int queueId = 0;
    private final long offSet = 1024;
    @Mock
    private Channel channel;
    private ClientChannelInfo clientChannelInfo;
    private String clientId = UUID.randomUUID().toString();

    @Before
    public void init() {
        deFiBrokerController.setMessageStore(messageStore);
        consumeQueueManager.setBrokerController(deFiBrokerController);
    }

    @Test
    public void testRecordAndScanUnsubscribedTopic() {
        consumeQueueManager.recordLastDeliverOffset(group, topic, queueId, offSet);
        long deliverOffset = consumeQueueManager.queryDeliverOffset(group, topic, queueId);
        assertThat(deliverOffset).isEqualTo(offSet);
        consumeQueueManager.scanUnsubscribedTopic();
        long deliverOffsetNew = consumeQueueManager.queryDeliverOffset(group, topic, queueId);
        assertThat(deliverOffsetNew).isEqualTo(-1);
    }

    @Test
    public void testGetMinAccumulated() throws Exception {
        clientChannelInfo = new ClientChannelInfo(channel, clientId, LanguageCode.JAVA, 100);
        ConsumerData consumerData = createConsumerData(group, topic);
        deFiBrokerController.getConsumerManager().registerConsumer(
            consumerData.getGroupName(),
            clientChannelInfo,
            consumerData.getConsumeType(),
            consumerData.getMessageModel(),
            consumerData.getConsumeFromWhere(),
            consumerData.getSubscriptionDataSet(),
            false);
        deFiBrokerController.getConsumerOffsetManager().commitOffset(clientId, group, topic, queueId, offSet);
        consumeQueueManager.setBrokerController(deFiBrokerController);
        when(messageStore.getMaxOffsetInQueue(topic, queueId)).thenReturn(1025L);
        ConsumeQueueWaterMark mark = consumeQueueManager.getMinAccumulated(topic, queueId);
        assertThat(mark).isNotNull();
        assertThat(mark.getTopic()).isEqualTo(topic);
        assertThat(mark.getConsumerGroup()).isEqualTo(group);
        assertThat(mark.getAccumulated()).isEqualTo(1);
    }

    private static ConsumerData createConsumerData(String group, String topic) {
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
