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

package cn.webank.defibus.broker.processor;

import cn.webank.defibus.broker.DeFiBrokerController;
import cn.webank.defibus.broker.client.DeFiProducerManager;
import cn.webank.defibus.broker.net.DeFiBusBroker2Client;
import cn.webank.defibus.common.DeFiBusBrokerConfig;
import cn.webank.defibus.common.DeFiBusConstant;
import cn.webank.defibus.common.protocol.DeFiBusRequestCode;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.net.InetSocketAddress;
import java.util.UUID;
import org.apache.rocketmq.broker.client.ClientChannelInfo;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.header.SendMessageRequestHeader;
import org.apache.rocketmq.remoting.exception.RemotingCommandException;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.store.AppendMessageResult;
import org.apache.rocketmq.store.AppendMessageStatus;
import org.apache.rocketmq.store.MessageStore;
import org.apache.rocketmq.store.PutMessageResult;
import org.apache.rocketmq.store.PutMessageStatus;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class DeFiReplyMessageProcessorTest {
    private DeFiBusBrokerConfig deFiBusBrokerConfig = new DeFiBusBrokerConfig();
    private DeFiReplyMessageProcessor deFiReplyMessageProcessor;
    private ChannelHandlerContext channelHandlerContext;
    private DeFiBrokerController deFiBrokerController;
    private MessageStore messageStore;
    private String topic = "TestTopic";
    private String group = "TestGroup";
    private String clientId = UUID.randomUUID().toString();

    @Before
    public void init() {
        deFiBrokerController = spy(new DeFiBrokerController(new BrokerConfig(), new NettyServerConfig(), new NettyClientConfig(), new MessageStoreConfig(), deFiBusBrokerConfig));
        channelHandlerContext = mock(ChannelHandlerContext.class);
        messageStore = mock(MessageStore.class);
        DeFiBusBroker2Client broker2Client = mock(DeFiBusBroker2Client.class);
        when(this.deFiBrokerController.getDeFiBusBroker2Client()).thenReturn(broker2Client);
        when(broker2Client.pushRRReplyMessageToClient(any(), any(), any())).thenReturn(true);
        Channel channel = mock(Channel.class);
        when(channel.isActive()).thenReturn(true);
        ClientChannelInfo channelInfo = mock(ClientChannelInfo.class);
        when(channelInfo.getChannel()).thenReturn(channel);
        DeFiProducerManager mockProducer = mock(DeFiProducerManager.class);
        when(mockProducer.getClientChannel(anyString())).thenReturn(channelInfo);
        when(this.deFiBrokerController.getProducerManager()).thenReturn(mockProducer);
        this.deFiBrokerController.setMessageStore(this.messageStore);
        when(this.messageStore.now()).thenReturn(System.currentTimeMillis());
        AppendMessageResult appendMessageResult = new AppendMessageResult(AppendMessageStatus.PUT_OK, 0, 0, "00000000000000000000000000000000", messageStore.now(), 0L, 0);
        when(this.messageStore.putMessage(any())).thenReturn(new PutMessageResult(PutMessageStatus.PUT_OK, appendMessageResult));
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress(1024));
        when(channelHandlerContext.channel()).thenReturn(channel);
        deFiReplyMessageProcessor = new DeFiReplyMessageProcessor(this.deFiBrokerController);
    }

    @Test
    public void assertReplyResult() throws RemotingCommandException {
        final RemotingCommand request = createReplyMsgCommand(DeFiBusRequestCode.SEND_DIRECT_MESSAGE);
        final RemotingCommand[] response = new RemotingCommand[1];
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                response[0] = (RemotingCommand) invocation.getArguments()[0];
                return null;
            }
        }).when(channelHandlerContext).writeAndFlush(any(Object.class));
        RemotingCommand responseToReturn = deFiReplyMessageProcessor.processRequest(channelHandlerContext, request);
        if (responseToReturn != null) {
            Assert.assertNull(response[0]);
            response[0] = responseToReturn;
        }
        Assert.assertEquals(response[0].getCode(), ResponseCode.SUCCESS);
        Assert.assertEquals(response[0].getOpaque(), request.getOpaque());
    }

    private SendMessageRequestHeader createSendMsgRequestHeader() {
        SendMessageRequestHeader requestHeader = new SendMessageRequestHeader();
        requestHeader.setProducerGroup(group);
        requestHeader.setTopic(topic);
        requestHeader.setDefaultTopic(MixAll.AUTO_CREATE_TOPIC_KEY_TOPIC);
        requestHeader.setDefaultTopicQueueNums(3);
        requestHeader.setQueueId(1);
        requestHeader.setSysFlag(0);
        requestHeader.setBornTimestamp(System.currentTimeMillis());
        requestHeader.setFlag(124);
        Message msg = new Message();
        msg.putUserProperty(DeFiBusConstant.KEY, DeFiBusConstant.REPLY);
        msg.putUserProperty(DeFiBusConstant.PROPERTY_MESSAGE_REPLY_TO, clientId);
        msg.setBody(new String("abcd").getBytes());
        requestHeader.setProperties(MessageDecoder.messageProperties2String(msg.getProperties()));
        return requestHeader;
    }

    private RemotingCommand createReplyMsgCommand(int requestCode) {
        SendMessageRequestHeader requestHeader = createSendMsgRequestHeader();
        RemotingCommand request = RemotingCommand.createRequestCommand(requestCode, requestHeader);
        request.makeCustomHeaderToNet();
        return request;
    }
}
