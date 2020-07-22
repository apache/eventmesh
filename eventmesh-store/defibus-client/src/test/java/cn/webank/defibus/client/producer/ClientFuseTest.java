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

package cn.webank.defibus.client.producer;

import cn.webank.defibus.client.common.DeFiBusClientConfig;
import cn.webank.defibus.client.impl.producer.DeFiBusProducerImpl;
import cn.webank.defibus.client.impl.producer.HealthyMessageQueueSelector;
import cn.webank.defibus.client.impl.producer.MessageQueueHealthManager;
import cn.webank.defibus.common.protocol.DeFiBusResponseCode;
import cn.webank.defibus.producer.DeFiBusProducer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.impl.MQClientAPIImpl;
import org.apache.rocketmq.client.impl.MQClientManager;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.impl.producer.TopicPublishInfo;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.protocol.header.SendMessageRequestHeader;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.common.protocol.route.QueueData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ClientFuseTest {
    private static DeFiBusProducer deFiBusProducer;
    private Message msg;
    private String topic = "test";
    private String producerGroup = "fuseTest";

    @Mock
    private MQClientAPIImpl mqClientAPIImpl;
    @Spy
    private MQClientInstance mQClientFactory = MQClientManager.getInstance().getAndCreateMQClientInstance(new ClientConfig());

    @Before
    public void init() throws Exception {
        DeFiBusClientConfig clientConfig = new DeFiBusClientConfig();
        clientConfig.setClusterPrefix("GL");
        clientConfig.setProducerGroup(producerGroup);
        clientConfig.setNamesrvAddr("127.0.0.1:9876");
        deFiBusProducer = new DeFiBusProducer(clientConfig);
        deFiBusProducer.start();

        msg = new Message(topic, new byte[] {'a'});
        Field field = DefaultMQProducerImpl.class.getDeclaredField("mQClientFactory");
        field.setAccessible(true);
        field.set(deFiBusProducer.getDefaultMQProducer().getDefaultMQProducerImpl(), mQClientFactory);

        field = MQClientInstance.class.getDeclaredField("mQClientAPIImpl");
        field.setAccessible(true);
        field.set(mQClientFactory, mqClientAPIImpl);

        deFiBusProducer.getDefaultMQProducer().getDefaultMQProducerImpl().getmQClientFactory().
            registerProducer(producerGroup, deFiBusProducer.getDefaultMQProducer().getDefaultMQProducerImpl());

        Exception e = new MQBrokerException(DeFiBusResponseCode.CONSUME_DIFF_SPAN_TOO_LONG, "CODE: " + DeFiBusResponseCode.CONSUME_DIFF_SPAN_TOO_LONG + " DESC: consume span too long, maybe has slow consumer, so send rejected\nFor more information, please visit the url, http://rocketmq.apache.org/docs/faq/");
        when(mqClientAPIImpl.sendMessage(anyString(), anyString(), any(Message.class), any(SendMessageRequestHeader.class), anyLong(), any(CommunicationMode.class),
            any(SendCallback.class), nullable(TopicPublishInfo.class), any(MQClientInstance.class), anyInt(), nullable(SendMessageContext.class), any(DefaultMQProducerImpl.class)))
            .thenAnswer(new Answer() {
                @Override
                public Object answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    SendCallback callback = (SendCallback) args[6];
                    callback.onException(e);
                    return new SendResult();
                }
            });
    }

    @After
    public void shutdown() {
        if (deFiBusProducer != null) {
            deFiBusProducer.shutdown();
        }
    }

    @Test
    public void testProcessResponseFuse() throws Exception {
        when(mqClientAPIImpl.getTopicRouteInfoFromNameServer(anyString(), anyLong())).thenReturn(createTopicRoute());
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Field fieldSelector = DeFiBusProducerImpl.class.getDeclaredField("messageQueueSelector");
        fieldSelector.setAccessible(true);
        Field fieldProducer = DeFiBusProducer.class.getDeclaredField("deFiBusProducerImpl");
        fieldProducer.setAccessible(true);
        MessageQueueHealthManager messageQueueHealthManager = ((HealthyMessageQueueSelector) fieldSelector.get(fieldProducer.get(deFiBusProducer))).getMessageQueueHealthManager();

        Assert.assertEquals(0, messageQueueHealthManager.faultMap.size());

        deFiBusProducer.publish(msg, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
            }

            @Override
            public void onException(Throwable e) {
                Assert.fail(e.getMessage());
                countDownLatch.countDown();
            }
        });
        countDownLatch.await(3000L, TimeUnit.MILLISECONDS);

        Assert.assertEquals(3, messageQueueHealthManager.faultMap.size());
    }

    public static TopicRouteData createTopicRoute() {
        TopicRouteData topicRouteData = new TopicRouteData();

        topicRouteData.setFilterServerTable(new HashMap<String, List<String>>());
        List<BrokerData> brokerDataList = new ArrayList<BrokerData>();
        BrokerData brokerData = new BrokerData();
        brokerData.setBrokerName("BrokerA");
        brokerData.setCluster("DefaultCluster");
        HashMap<Long, String> brokerAddrs = new HashMap<Long, String>();
        brokerAddrs.put(0L, "127.0.0.1:10911");
        brokerData.setBrokerAddrs(brokerAddrs);
        brokerDataList.add(brokerData);
        topicRouteData.setBrokerDatas(brokerDataList);

        List<QueueData> queueDataList = new ArrayList<QueueData>();
        QueueData queueData = new QueueData();
        queueData.setBrokerName("BrokerA");
        queueData.setPerm(6);
        queueData.setReadQueueNums(3);
        queueData.setWriteQueueNums(4);
        queueData.setTopicSynFlag(0);
        queueDataList.add(queueData);
        topicRouteData.setQueueDatas(queueDataList);
        return topicRouteData;
    }
}
