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

package cn.webank.defibus.client.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.impl.MQClientAPIImpl;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.header.GetConsumerListByGroupResponseBody;
import org.apache.rocketmq.common.protocol.header.SendMessageRequestHeader;
import org.apache.rocketmq.common.protocol.header.SendMessageResponseHeader;
import org.apache.rocketmq.remoting.InvokeCallback;
import org.apache.rocketmq.remoting.RemotingClient;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.ResponseFuture;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@RunWith(MockitoJUnitRunner.class)
public class DeFiBusClientAPIImplTest {
    private DeFiBusClientAPIImpl deFiBusClientAPI = new DeFiBusClientAPIImpl(new NettyClientConfig(), null, null, new ClientConfig());
    @Mock
    private RemotingClient remotingClient;
    @Mock
    private DefaultMQProducerImpl defaultMQProducerImpl;

    private String brokerAddr = "127.0.0.1";
    private String brokerName = "DefaultBroker";
    private static String group = "FooBarGroup";
    private static String topic = "FooBar";
    private Message msg = new Message("FooBar", new byte[] {});

    @Before
    public void init() throws Exception {
        Field field = MQClientAPIImpl.class.getDeclaredField("remotingClient");
        field.setAccessible(true);
        field.set(deFiBusClientAPI, remotingClient);
    }

    @Test
    public void testSendMessageTypeOfReply_Success() throws Exception {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock mock) throws Throwable {
                InvokeCallback callback = mock.getArgument(3);
                RemotingCommand request = mock.getArgument(1);
                ResponseFuture responseFuture = new ResponseFuture(null, request.getOpaque(), 3 * 1000, null, null);
                responseFuture.setResponseCommand(createSuccessResponse(request));
                callback.operationComplete(responseFuture);
                return null;
            }
        }).when(remotingClient).invokeAsync(anyString(), any(RemotingCommand.class), anyLong(), any(InvokeCallback.class));
        SendMessageContext sendMessageContext = new SendMessageContext();
        sendMessageContext.setProducer(new DefaultMQProducerImpl(new DefaultMQProducer()));
        msg.getProperties().put("msgType", "reply");
        deFiBusClientAPI.sendMessage(brokerAddr, brokerName, msg, new SendMessageRequestHeader(), 3 * 1000, CommunicationMode.ASYNC,
            new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    assertThat(sendResult.getSendStatus()).isEqualTo(SendStatus.SEND_OK);
                    assertThat(sendResult.getOffsetMsgId()).isEqualTo("123");
                    assertThat(sendResult.getQueueOffset()).isEqualTo(123L);
                    assertThat(sendResult.getMessageQueue().getQueueId()).isEqualTo(1);
                }

                @Override
                public void onException(Throwable e) {
                }
            }, null, null, 0, sendMessageContext, defaultMQProducerImpl);
    }

    @Test
    public void testSendMessagetypeOfnull_Success() throws RemotingException, InterruptedException, MQBrokerException {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock mock) throws Throwable {
                InvokeCallback callback = mock.getArgument(3);
                RemotingCommand request = mock.getArgument(1);
                ResponseFuture responseFuture = new ResponseFuture(null, request.getOpaque(), 3 * 1000, null, null);
                responseFuture.setResponseCommand(createSuccessResponse(request));
                callback.operationComplete(responseFuture);
                return null;
            }
        }).when(remotingClient).invokeAsync(anyString(), any(RemotingCommand.class), anyLong(), any(InvokeCallback.class));
        SendMessageContext sendMessageContext = new SendMessageContext();
        sendMessageContext.setProducer(new DefaultMQProducerImpl(new DefaultMQProducer()));
        deFiBusClientAPI.sendMessage(brokerAddr, brokerName, msg, new SendMessageRequestHeader(), 3 * 1000, CommunicationMode.ASYNC,
            new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    assertThat(sendResult.getSendStatus()).isEqualTo(SendStatus.SEND_OK);
                    assertThat(sendResult.getOffsetMsgId()).isEqualTo("123");
                    assertThat(sendResult.getQueueOffset()).isEqualTo(123L);
                    assertThat(sendResult.getMessageQueue().getQueueId()).isEqualTo(1);
                }

                @Override
                public void onException(Throwable e) {
                }
            },
            null, null, 0, sendMessageContext, defaultMQProducerImpl);
    }

    @Test
    public void testGetConsumerIdListByGroupAndTopic_Success() throws Exception {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock mock) throws Throwable {
                RemotingCommand request = mock.getArgument(1);
                GetConsumerListByGroupResponseBody body = new GetConsumerListByGroupResponseBody();
                List<String> cidList = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    cidList.add(i + "");
                }
                body.setConsumerIdList(cidList);
                request.setBody(body.encode());
                request.setCode(ResponseCode.SUCCESS);
                request.setOpaque(request.getOpaque());
                return request;
            }
        }).when(remotingClient).invokeSync(anyString(), any(RemotingCommand.class), anyLong());
        List<String> list = deFiBusClientAPI.getConsumerIdListByGroupAndTopic(brokerAddr, group, topic, 3 * 1000);
        assertThat(list.size()).isEqualTo(10);
    }

    @Test(expected = MQBrokerException.class)
    public void testGetConsumerIdListByGroupAndTopic_OnException() throws Exception {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock mock) throws Throwable {
                RemotingCommand request = mock.getArgument(1);
                request.setCode(ResponseCode.SYSTEM_ERROR);
                return request;
            }
        }).when(remotingClient).invokeSync(anyString(), any(RemotingCommand.class), anyLong());
        deFiBusClientAPI.getConsumerIdListByGroupAndTopic(brokerAddr, group, topic, 3 * 1000);
    }

    private RemotingCommand createSuccessResponse(RemotingCommand request) {
        RemotingCommand response = RemotingCommand.createResponseCommand(SendMessageResponseHeader.class);
        response.setCode(ResponseCode.SUCCESS);
        response.setOpaque(request.getOpaque());

        SendMessageResponseHeader responseHeader = (SendMessageResponseHeader) response.readCustomHeader();
        responseHeader.setMsgId("123");
        responseHeader.setQueueId(1);
        responseHeader.setQueueOffset(123L);

        response.addExtField(MessageConst.PROPERTY_MSG_REGION, "RegionHZ");
        response.addExtField(MessageConst.PROPERTY_TRACE_SWITCH, "true");
        response.addExtField("queueId", String.valueOf(responseHeader.getQueueId()));
        response.addExtField("msgId", responseHeader.getMsgId());
        response.addExtField("queueOffset", String.valueOf(responseHeader.getQueueOffset()));
        return response;
    }

}
