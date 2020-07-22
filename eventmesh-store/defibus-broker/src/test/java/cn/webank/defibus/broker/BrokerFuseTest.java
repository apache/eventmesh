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

package cn.webank.defibus.broker;

import cn.webank.defibus.broker.client.DeFiConsumerGroupInfo;
import cn.webank.defibus.broker.consumequeue.ConsumeQueueManager;
import cn.webank.defibus.broker.consumequeue.ConsumeQueueWaterMark;
import cn.webank.defibus.broker.processor.DeFiSendMessageProcessor;
import cn.webank.defibus.common.DeFiBusBrokerConfig;
import cn.webank.defibus.common.protocol.DeFiBusResponseCode;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.rocketmq.broker.client.ConsumerGroupInfo;
import org.apache.rocketmq.broker.client.ConsumerManager;
import org.apache.rocketmq.broker.offset.ConsumerOffsetManager;
import org.apache.rocketmq.broker.topic.TopicConfigManager;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.RequestCode;
import org.apache.rocketmq.common.protocol.header.SendMessageRequestHeader;
import org.apache.rocketmq.common.protocol.heartbeat.ConsumeType;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.common.protocol.heartbeat.SubscriptionData;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.store.MessageStore;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ConsumeQueueManager.class)
public class BrokerFuseTest {
    private DeFiSendMessageProcessor deFiSendMessageProcessor;
    @Spy
    private DeFiBrokerController brokerController = new DeFiBrokerController(
        new BrokerConfig(),
        new NettyServerConfig(),
        new NettyClientConfig(),
        new MessageStoreConfig(),
        new DeFiBusBrokerConfig()
    );

    @Mock
    private MessageStore messageStore;
    @Mock
    private ChannelHandlerContext handlerContext;
    @Mock
    private ConsumeQueueManager deFiQueueManager;
    @Mock
    private TopicConfigManager topicConfigManager;

    private String topic = "FooBar";
    private String producerGroup = "FooBarGroup";
    private String consumeGroup = "ConGroup";
    private int queueId = 1;

    @Before
    public void init() {
        brokerController.setMessageStore(messageStore);
        when(messageStore.now()).thenReturn(System.currentTimeMillis());
        Channel mockChannel = mock(Channel.class);
        when(mockChannel.remoteAddress()).thenReturn(new InetSocketAddress(1024));
        when(handlerContext.channel()).thenReturn(mockChannel);
        when(messageStore.lookMessageByOffset(anyLong())).thenReturn(new MessageExt());
    }

    @Test
    public void testProcessRequestFuse() throws Exception {
        //fuse condition
        ConsumeQueueWaterMark minWaterMark = new ConsumeQueueWaterMark(consumeGroup, topic, 1, 1000, 800);
        PowerMockito.mockStatic(ConsumeQueueManager.class);
        when(ConsumeQueueManager.onlyInstance()).thenReturn(deFiQueueManager);
        when(deFiQueueManager.getMaxQueueDepth(topic)).thenReturn((long) 500);
        when(deFiQueueManager.getMinAccumulated(topic, queueId)).thenReturn(minWaterMark);
        when(deFiQueueManager.getBrokerController()).thenReturn(brokerController);
        deFiSendMessageProcessor = new DeFiSendMessageProcessor(brokerController);

        //send message request
        final RemotingCommand request = createSendMsgCommand(RequestCode.SEND_MESSAGE);
        RemotingCommand response = deFiSendMessageProcessor.processRequest(handlerContext, request);
        Assert.assertEquals(response.getCode(), DeFiBusResponseCode.CONSUME_DIFF_SPAN_TOO_LONG);
    }

    @Test
    public void testAutoUpdateDepth() throws Exception {
        ConcurrentHashMap<String/* Group */, ConsumerGroupInfo> consumerTable =
            new ConcurrentHashMap<String, ConsumerGroupInfo>(1024);
        DeFiConsumerGroupInfo deFiConsumerGroupInfo =
            new DeFiConsumerGroupInfo(consumeGroup,
                ConsumeType.CONSUME_ACTIVELY,
                MessageModel.CLUSTERING,
                ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumerTable.put(consumeGroup, deFiConsumerGroupInfo);
        SubscriptionData subscriptionData = new SubscriptionData(topic, "test");
        HashSet<SubscriptionData> hashSet = new HashSet<>();
        hashSet.add(subscriptionData);
        deFiConsumerGroupInfo.registerClientId(hashSet, "123");
        ConsumeQueueManager consumeQueueManager = ConsumeQueueManager.onlyInstance();
        consumeQueueManager.setBrokerController(brokerController);
        ConsumerManager consumerManager = brokerController.getConsumerManager();
        ConsumerOffsetManager consumerOffsetManager = brokerController.getConsumerOffsetManager();
        consumerOffsetManager.commitOffset("resetByBroker", consumeGroup, topic, queueId, 100);
        TopicConfig topicConfig = new TopicConfig(topic, 4, 4, 6);

        Assert.assertEquals(consumerOffsetManager.queryOffset(consumeGroup, topic, queueId), 100);
        when(brokerController.getTopicConfigManager()).thenReturn(topicConfigManager);
        when(topicConfigManager.selectTopicConfig(topic)).thenReturn(topicConfig);

        Field field = ConsumerManager.class.getDeclaredField("consumerTable");
        field.setAccessible(true);
        field.set(consumerManager, consumerTable);

        Method method = ConsumeQueueManager.class.getDeclaredMethod("autoUpdateDepth", String.class, String.class, int.class, long.class, long.class);
        method.setAccessible(true);
        method.invoke(consumeQueueManager, consumeGroup, topic, queueId, 500, 1500);
        Assert.assertEquals(consumerOffsetManager.queryOffset(consumeGroup, topic, queueId), 1500 - 500 * 0.65, 0);

    }

    private RemotingCommand createSendMsgCommand(int requestCode) {
        SendMessageRequestHeader requestHeader = createSendMsgRequestHeader();

        RemotingCommand request = RemotingCommand.createRequestCommand(requestCode, requestHeader);
        request.setBody(new byte[] {'a'});
        request.makeCustomHeaderToNet();
        return request;
    }

    private SendMessageRequestHeader createSendMsgRequestHeader() {
        SendMessageRequestHeader requestHeader = new SendMessageRequestHeader();
        requestHeader.setProducerGroup(producerGroup);
        requestHeader.setTopic(topic);
        requestHeader.setDefaultTopic(MixAll.AUTO_CREATE_TOPIC_KEY_TOPIC);
        requestHeader.setDefaultTopicQueueNums(3);
        requestHeader.setQueueId(queueId);
        requestHeader.setSysFlag(0);
        requestHeader.setBornTimestamp(System.currentTimeMillis());
        requestHeader.setFlag(124);
        requestHeader.setReconsumeTimes(0);
        return requestHeader;
    }
}
