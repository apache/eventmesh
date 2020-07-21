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

package cn.webank.defibus.client.consumer;

import cn.webank.defibus.client.common.DeFiBusClientConfig;
import cn.webank.defibus.client.impl.DeFiBusClientAPIImpl;
import cn.webank.defibus.client.impl.factory.DeFiBusClientInstance;
import cn.webank.defibus.common.DeFiBusConstant;
import cn.webank.defibus.consumer.DeFiBusMessageListenerConcurrentlyOnce;
import cn.webank.defibus.consumer.DeFiBusPushConsumer;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.client.consumer.PullCallback;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.impl.FindBrokerResult;
import org.apache.rocketmq.client.impl.consumer.DefaultMQPushConsumerImpl;
import org.apache.rocketmq.client.impl.consumer.ProcessQueue;
import org.apache.rocketmq.client.impl.consumer.PullAPIWrapper;
import org.apache.rocketmq.client.impl.consumer.PullMessageService;
import org.apache.rocketmq.client.impl.consumer.PullRequest;
import org.apache.rocketmq.client.impl.consumer.PullResultExt;
import org.apache.rocketmq.client.impl.consumer.RebalancePushImpl;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.common.message.MessageClientExt;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.header.PullMessageRequestHeader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DeFiBusMessageListenerConcurrentlyOnceTest {
    private String consumerGroup;
    private String topic = "FooBar";
    private String brokerName = "BrokerA";
    private DeFiBusClientInstance defiBusClientFactory;
    private int totalMsg = 5;
    private AtomicInteger consumeCount = new AtomicInteger(0);

    @Mock
    private DeFiBusClientAPIImpl deFiBusClientAPIImpl;

    private PullAPIWrapper pullAPIWrapper;
    private RebalancePushImpl rebalancePushImpl;
    private DeFiBusPushConsumer pushConsumer;

    private final CountDownLatch countDownLatch = new CountDownLatch(totalMsg);

    @Before
    public void init() throws Exception {
        DeFiBusClientConfig clientConfig = new DeFiBusClientConfig();
        consumerGroup = "FooBarGroup" + System.currentTimeMillis();
        clientConfig.setConsumerGroup(consumerGroup);
        pushConsumer = new DeFiBusPushConsumer(clientConfig);
        pushConsumer.setNamesrvAddr("127.0.0.1:9876");
        pushConsumer.getDefaultMQPushConsumer().setPullInterval(60 * 1000);

        pushConsumer.registerMessageListener(new DeFiBusMessageListenerConcurrentlyOnce() {
            @Override
            public ConsumeConcurrentlyStatus handleMessage(MessageExt msg, ConsumeConcurrentlyContext context) {
                countDownLatch.countDown();
                consumeCount.getAndIncrement();
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        DefaultMQPushConsumerImpl pushConsumerImpl = pushConsumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl();
        rebalancePushImpl = spy(new RebalancePushImpl(pushConsumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl()));
        Field field = DefaultMQPushConsumerImpl.class.getDeclaredField("rebalanceImpl");
        field.setAccessible(true);
        field.set(pushConsumerImpl, rebalancePushImpl);
        pushConsumer.subscribe(topic);
        pushConsumer.start();

        defiBusClientFactory = spy(pushConsumer.getDeFiBusClientInstance());
        field = DefaultMQPushConsumerImpl.class.getDeclaredField("mQClientFactory");
        field.setAccessible(true);
        field.set(pushConsumerImpl, defiBusClientFactory);

        field = MQClientInstance.class.getDeclaredField("mQClientAPIImpl");
        field.setAccessible(true);
        field.set(defiBusClientFactory, deFiBusClientAPIImpl);

        pullAPIWrapper = spy(new PullAPIWrapper(defiBusClientFactory, consumerGroup, false));
        field = DefaultMQPushConsumerImpl.class.getDeclaredField("pullAPIWrapper");
        field.setAccessible(true);
        field.set(pushConsumerImpl, pullAPIWrapper);

        pushConsumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().getRebalanceImpl().setmQClientFactory(defiBusClientFactory);
        defiBusClientFactory.registerConsumer(consumerGroup, pushConsumerImpl);

        when(defiBusClientFactory.getMQClientAPIImpl().pullMessage(anyString(), any(PullMessageRequestHeader.class),
            anyLong(), any(CommunicationMode.class), nullable(PullCallback.class)))
            .thenAnswer(new Answer<Object>() {
                @Override
                public Object answer(InvocationOnMock mock) throws Throwable {
                    PullMessageRequestHeader requestHeader = mock.getArgument(1);
                    PullResult pullResult = createPullResult(requestHeader, PullStatus.FOUND, createMessageList(totalMsg));
                    ((PullCallback) mock.getArgument(4)).onSuccess(pullResult);
                    return pullResult;
                }
            })
            .thenAnswer(new Answer<Object>() {
                @Override public Object answer(InvocationOnMock invocation) throws Throwable {
                    PullMessageRequestHeader requestHeader = invocation.getArgument(1);
                    PullResult pullResult = createPullResult(requestHeader, PullStatus.NO_NEW_MSG, new ArrayList<MessageExt>());
                    ((PullCallback) invocation.getArgument(4)).onSuccess(pullResult);
                    return pullResult;
                }
            });

        doReturn(new FindBrokerResult("127.0.0.1:10912", false)).when(defiBusClientFactory).findBrokerAddressInSubscribe(anyString(), anyLong(), anyBoolean());
        Set<MessageQueue> messageQueueSet = new HashSet<MessageQueue>();
        messageQueueSet.add(createPullRequest().getMessageQueue());
        pushConsumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().updateTopicSubscribeInfo(topic, messageQueueSet);
    }

    @Test
    public void testPullMessage_ConsumeSuccess() throws Exception {
        PullMessageService pullMessageService = defiBusClientFactory.getPullMessageService();
        pullMessageService.executePullRequestImmediately(createPullRequest());
        countDownLatch.await(5000, TimeUnit.MILLISECONDS);

        Thread.sleep(2000);
        assertThat(consumeCount.get()).isEqualTo(totalMsg);
    }

    @After
    public void terminate() {
        pushConsumer.shutdown();
    }

    private PullRequest createPullRequest() {
        PullRequest pullRequest = new PullRequest();
        pullRequest.setConsumerGroup(consumerGroup);
        pullRequest.setNextOffset(1024);

        MessageQueue messageQueue = new MessageQueue();
        messageQueue.setBrokerName(brokerName);
        messageQueue.setQueueId(0);
        messageQueue.setTopic(topic);
        pullRequest.setMessageQueue(messageQueue);
        ProcessQueue processQueue = new ProcessQueue();
        processQueue.setLocked(true);
        processQueue.setLastLockTimestamp(System.currentTimeMillis());
        pullRequest.setProcessQueue(processQueue);

        return pullRequest;
    }

    private PullResultExt createPullResult(PullMessageRequestHeader requestHeader, PullStatus pullStatus,
        List<MessageExt> messageExtList) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (MessageExt messageExt : messageExtList) {
            outputStream.write(MessageDecoder.encode(messageExt, false));
        }
        return new PullResultExt(pullStatus, requestHeader.getQueueOffset() + messageExtList.size(), 123, 2048, messageExtList, 0, outputStream.toByteArray());
    }

    private ArrayList<MessageExt> createMessageList(int size) {
        ArrayList<MessageExt> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            MessageClientExt messageClientExt = new MessageClientExt();
            messageClientExt.setTopic(topic);
            messageClientExt.setQueueId(0);
            messageClientExt.setMsgId("123");
            messageClientExt.setBody(new byte[] {'a'});
            messageClientExt.setOffsetMsgId("234");
            messageClientExt.setBornHost(new InetSocketAddress(8080));
            messageClientExt.setStoreHost(new InetSocketAddress(8080));
            messageClientExt.putUserProperty(DeFiBusConstant.PROPERTY_MESSAGE_TTL, "3000");
            list.add(messageClientExt);
        }
        return list;
    }
}
