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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

//@RunWith(MockitoJUnitRunner.class)
public class DeFiBusProducerTest {
//    @Spy
//    private DeFiBusClientInstance deFiBusClientInstance = DeFiBusClientManager.getInstance().getAndCreateDeFiBusClientInstance(new ClientConfig(), null);
//    @Mock
//    private DeFiBusClientAPIImpl mqClientAPI;
//
//    private DeFiBusProducer producer;
//    private Message message;
//    private Message zeroMsg;
//    private Message bigMessage;
//    private String topic = "FooBar";
//    private String producerGroupPrefix = "FooBar_PID";
//    private String clusterName = "DefaultCluster";
//
//    @Before
//    public void init() throws Exception {
//        String producerGroupTemp = producerGroupPrefix + System.currentTimeMillis();
//        DeFiBusClientConfig clientConfig = new DeFiBusClientConfig();
//        clientConfig.setProducerGroup(producerGroupTemp);
//        producer = new DeFiBusProducer(clientConfig);
//        producer.setNamesrvAddr("127.0.0.1:9876");
//        producer.getDefaultMQProducer().setCompressMsgBodyOverHowmuch(16);
//        message = new Message(topic, new byte[] {'a'});
//        message.getUserProperty("");
//        zeroMsg = new Message(topic, new byte[] {});
//        zeroMsg.getUserProperty("");
//        bigMessage = new Message(topic, "This is a very huge message!".getBytes());
//        bigMessage.getUserProperty("");
//
//        producer.start();
//
//        Field field = DefaultMQProducerImpl.class.getDeclaredField("mQClientFactory");
//        field.setAccessible(true);
//        field.set(producer.getDefaultMQProducer().getDefaultMQProducerImpl(), deFiBusClientInstance);
//
//        field = MQClientInstance.class.getDeclaredField("mQClientAPIImpl");
//        field.setAccessible(true);
//        field.set(deFiBusClientInstance, mqClientAPI);
//
//        producer.getDefaultMQProducer().getDefaultMQProducerImpl().getmQClientFactory()
//            .registerProducer(producerGroupTemp, producer.getDefaultMQProducer().getDefaultMQProducerImpl());
//
//        when(mqClientAPI.sendMessage(anyString(), anyString(), any(Message.class), any(SendMessageRequestHeader.class), anyLong(), any(CommunicationMode.class),
//            nullable(SendCallback.class), nullable(TopicPublishInfo.class), nullable(MQClientInstance.class), anyInt(), nullable(SendMessageContext.class), any(DefaultMQProducerImpl.class)))
//            .thenAnswer(new Answer<Object>() {
//                @Override public Object answer(InvocationOnMock invocation) throws Throwable {
//                    String brokerName = invocation.getArgument(1);
//                    CommunicationMode communicationMode = invocation.getArgument(5);
//                    SendCallback callback = invocation.getArgument(6);
//                    SendResult sendResult = createSendResult(SendStatus.SEND_OK, brokerName);
//                    switch (communicationMode) {
//                        case SYNC:
//                            return sendResult;
//                        case ASYNC:
//                        case ONEWAY:
//                            if (callback != null) {
//                                callback.onSuccess(sendResult);
//                            }
//                    }
//                    return null;
//                }
//            });
//    }
//
//    @After
//    public void terminate() {
//        producer.shutdown();
//    }
//
//    @Test
//    public void testSendMessage_ZeroMessage() throws InterruptedException, RemotingException, MQBrokerException {
//        try {
//            producer.publish(zeroMsg);
//        } catch (MQClientException e) {
//            assertThat(e).hasMessageContaining("message body length is zero");
//        }
//    }
//
//    @Test
//    public void testSendMessage_NoNameSrv() throws RemotingException, InterruptedException {
//        try {
//            producer.publish(message);
//        } catch (MQClientException e) {
//            assertThat(e).hasMessageContaining("No name server address");
//        }
//    }
//
//    @Test
//    public void testSendMessage_NoRoute() throws RemotingException, InterruptedException {
//        try {
//            producer.publish(message);
//        } catch (MQClientException e) {
//            assertThat(e).hasMessageContaining("No route info of this topic");
//        }
//    }
//
//    @Test
//    public void testSendMessageAsync_Success() throws RemotingException, InterruptedException, MQBrokerException, MQClientException {
//        final CountDownLatch countDownLatch = new CountDownLatch(1);
//        final AtomicInteger success = new AtomicInteger(0);
//        when(mqClientAPI.getTopicRouteInfoFromNameServer(anyString(), anyLong())).thenReturn(createTopicRoute());
//        producer.publish(message, new SendCallback() {
//            @Override
//            public void onSuccess(SendResult sendResult) {
//                assertThat(sendResult.getSendStatus()).isEqualTo(SendStatus.SEND_OK);
//                assertThat(sendResult.getOffsetMsgId()).isEqualTo("123");
//                assertThat(sendResult.getQueueOffset()).isEqualTo(456L);
//                success.getAndIncrement();
//                countDownLatch.countDown();
//            }
//
//            @Override
//            public void onException(Throwable e) {
//                countDownLatch.countDown();
//            }
//        });
//        long timeout = producer.getDefaultMQProducer().getSendMsgTimeout();
//        countDownLatch.await(timeout, TimeUnit.MILLISECONDS);
//        assertThat(success.get()).isEqualTo(1);
//    }
//
//    @Test
//    public void testSendMessageAsync_Exception() throws RemotingException, MQClientException, InterruptedException, MQBrokerException {
//        final CountDownLatch countDownLatch = new CountDownLatch(1);
//        final AtomicBoolean success = new AtomicBoolean(true);
//        when(mqClientAPI.getTopicRouteInfoFromNameServer(anyString(), anyLong())).thenReturn(createTopicRoute());
//        when(mqClientAPI.sendMessage(anyString(), anyString(), any(Message.class), any(SendMessageRequestHeader.class), anyLong(), any(CommunicationMode.class),
//            nullable(SendCallback.class), nullable(TopicPublishInfo.class), nullable(MQClientInstance.class), anyInt(), nullable(SendMessageContext.class), any(DefaultMQProducerImpl.class)))
//            .thenAnswer(new Answer<Object>() {
//                @Override public Object answer(InvocationOnMock invocation) throws Throwable {
//                    SendCallback callback = invocation.getArgument(6);
//                    if (callback != null) {
//                        callback.onException(new Exception("test send exception"));
//                    }
//                    return null;
//                }
//            });
//        producer.publish(message, new SendCallback() {
//            @Override
//            public void onSuccess(SendResult sendResult) {
//                success.set(true);
//                countDownLatch.countDown();
//            }
//
//            @Override
//            public void onException(Throwable e) {
//                success.set(false);
//                countDownLatch.countDown();
//                assertThat(e).hasMessage("test send exception");
//            }
//        });
//        long timeout = producer.getDefaultMQProducer().getSendMsgTimeout();
//        countDownLatch.await(timeout, TimeUnit.MILLISECONDS);
//        assertThat(success.get()).isEqualTo(false);
//    }
//
//    @Test
//    public void testRequest_Timeout() throws RemotingException, MQClientException, InterruptedException, MQBrokerException {
//        when(mqClientAPI.getTopicRouteInfoFromNameServer(anyString(), anyLong())).thenReturn(createTopicRoute());
//        when(deFiBusClientInstance.getTopicRouteTable()).thenReturn(new ConcurrentHashMap<String, TopicRouteData>());
//        Message replyMsg = producer.request(createRequestMessage(topic, clusterName), 3000);
//        assertThat(replyMsg).isNull();
//    }
//
//    @Test
//    public void testRequest_Success() throws RemotingException, MQClientException, InterruptedException, MQBrokerException {
//        when(mqClientAPI.getTopicRouteInfoFromNameServer(anyString(), anyLong())).thenReturn(createTopicRoute());
//        when(deFiBusClientInstance.getTopicRouteTable()).thenReturn(new ConcurrentHashMap<String, TopicRouteData>());
//        final AtomicBoolean finish = new AtomicBoolean(false);
//        new Thread(new Runnable() {
//            @Override public void run() {
//                ConcurrentHashMap<String, RRResponseFuture> responseMap = ResponseTable.getRrResponseFurtureConcurrentHashMap();
//                assertThat(responseMap).isNotNull();
//                while (!finish.get()) {
//                    try {
//                        Thread.sleep(10);
//                    } catch (InterruptedException e) {
//                    }
//                    for (Map.Entry<String, RRResponseFuture> entry : responseMap.entrySet()) {
//                        RRResponseFuture future = entry.getValue();
//                        future.putResponse(createRequestMessage(topic, clusterName));
//                    }
//                }
//            }
//        }).start();
//        Message replyMsg = producer.request(createRequestMessage(topic, clusterName), 3000);
//        finish.getAndSet(true);
//        assertThat(replyMsg.getTopic()).isEqualTo(topic);
//        assertThat(replyMsg.getBody()).isEqualTo(new byte[] {'a'});
//    }
//
//    @Test
//    public void testRequestAsync_Success() throws RemotingException, MQClientException, InterruptedException, MQBrokerException {
//        when(mqClientAPI.getTopicRouteInfoFromNameServer(anyString(), anyLong())).thenReturn(createTopicRoute());
//        when(deFiBusClientInstance.getTopicRouteTable()).thenReturn(new ConcurrentHashMap<String, TopicRouteData>());
//        final AtomicBoolean finish = new AtomicBoolean(false);
//        new Thread(new Runnable() {
//            @Override public void run() {
//                ConcurrentHashMap<String, RRResponseFuture> responseMap = ResponseTable.getRrResponseFurtureConcurrentHashMap();
//                assertThat(responseMap).isNotNull();
//                while (!finish.get()) {
//                    try {
//                        Thread.sleep(10);
//                    } catch (InterruptedException e) {
//                    }
//                    for (Map.Entry<String, RRResponseFuture> entry : responseMap.entrySet()) {
//                        RRResponseFuture future = entry.getValue();
//                        future.putResponse(createRequestMessage(topic, clusterName));
//                    }
//                }
//            }
//        }).start();
//        producer.request(createRequestMessage(topic, clusterName), new RRCallback() {
//            @Override public void onSuccess(Message msg) {
//                finish.getAndSet(true);
//                assertThat(msg.getTopic()).isEqualTo(topic);
//                assertThat(msg.getBody()).isEqualTo(new byte[] {'a'});
//            }
//
//            @Override public void onException(Throwable e) {
//                finish.set(true);
//                assert false;
//            }
//        }, 3000);
//    }
//
//    public static TopicRouteData createTopicRoute() {
//        TopicRouteData topicRouteData = new TopicRouteData();
//
//        topicRouteData.setFilterServerTable(new HashMap<String, List<String>>());
//        List<BrokerData> brokerDataList = new ArrayList<BrokerData>();
//        BrokerData brokerData = new BrokerData();
//        brokerData.setBrokerName("BrokerA");
//        brokerData.setCluster("DefaultCluster");
//        HashMap<Long, String> brokerAddrs = new HashMap<Long, String>();
//        brokerAddrs.put(0L, "127.0.0.1:10911");
//        brokerData.setBrokerAddrs(brokerAddrs);
//        brokerDataList.add(brokerData);
//        topicRouteData.setBrokerDatas(brokerDataList);
//
//        List<QueueData> queueDataList = new ArrayList<QueueData>();
//        QueueData queueData = new QueueData();
//        queueData.setBrokerName("BrokerA");
//        queueData.setPerm(6);
//        queueData.setReadQueueNums(3);
//        queueData.setWriteQueueNums(4);
//        queueData.setTopicSynFlag(0);
//        queueDataList.add(queueData);
//        topicRouteData.setQueueDatas(queueDataList);
//        return topicRouteData;
//    }
//
//    private SendResult createSendResult(SendStatus sendStatus, String brokerName) {
//        SendResult sendResult = new SendResult();
//        sendResult.setMsgId("123");
//        sendResult.setOffsetMsgId("123");
//        sendResult.setQueueOffset(456);
//        sendResult.setSendStatus(sendStatus);
//        sendResult.setRegionId("HZ");
//        MessageQueue mq = new MessageQueue();
//        mq.setTopic(topic);
//        mq.setBrokerName(brokerName);
//        mq.setQueueId(0);
//        sendResult.setMessageQueue(mq);
//        return sendResult;
//    }
//
//    private Message createRequestMessage(String topic, String clusterName) {
//        Message requestMessage = new Message();
//        Map map = new HashMap<String, String>();
//        map.put(DeFiBusConstant.PROPERTY_MESSAGE_REPLY_TO, "127.0.0.1");
//        map.put(DeFiBusConstant.PROPERTY_MESSAGE_CLUSTER, clusterName);
//        map.put(DeFiBusConstant.PROPERTY_MESSAGE_TTL, "3000");
//        MessageAccessor.setProperties(requestMessage, map);
//        requestMessage.setTopic(topic);
//        requestMessage.setBody(new byte[] {'a'});
//        return requestMessage;
//    }
}
