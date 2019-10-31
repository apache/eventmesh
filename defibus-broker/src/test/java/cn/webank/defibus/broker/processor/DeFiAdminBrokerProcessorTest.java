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
import cn.webank.defibus.common.DeFiBusBrokerConfig;
import cn.webank.defibus.common.admin.DeFiBusConsumeStats;
import cn.webank.defibus.common.protocol.DeFiBusRequestCode;
import io.netty.channel.ChannelHandlerContext;
import java.nio.ByteBuffer;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.common.TopicFilterType;
import org.apache.rocketmq.common.protocol.RequestCode;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.header.CreateTopicRequestHeader;
import org.apache.rocketmq.common.protocol.header.GetConsumeStatsRequestHeader;
import org.apache.rocketmq.remoting.exception.RemotingCommandException;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.protocol.RemotingSerializable;
import org.apache.rocketmq.store.MappedFile;
import org.apache.rocketmq.store.MessageStore;
import org.apache.rocketmq.store.SelectMappedBufferResult;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;

@RunWith(MockitoJUnitRunner.class)
public class DeFiAdminBrokerProcessorTest {

    private DeFiAdminBrokerProcessor deFiAdminBrokerProcessor;

    @Mock
    private ChannelHandlerContext handlerContext;

    @Spy
    private DeFiBrokerController
        deFiBrokerController = new DeFiBrokerController(new BrokerConfig(), new NettyServerConfig(), new NettyClientConfig(),
        new MessageStoreConfig(), new DeFiBusBrokerConfig());

    @Mock
    private MessageStore messageStore;

    private String consumerGroup;
    private String topic;

    @Before
    public void init() {
        deFiBrokerController.setMessageStore(messageStore);
        deFiAdminBrokerProcessor = new DeFiAdminBrokerProcessor(deFiBrokerController);
        consumerGroup = "tempGroup";
        topic = "testTopic";
    }

    @Test
    public void testProcessRequestUpdateAndCreateTopic() throws RemotingCommandException {
        RemotingCommand request = createCommand(RequestCode.UPDATE_AND_CREATE_TOPIC);
        final RemotingCommand[] response = new RemotingCommand[1];
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                response[0] = (RemotingCommand) invocation.getArguments()[0];
                return null;
            }
        }).when(handlerContext).writeAndFlush(any(Object.class));
        RemotingCommand responseToReturn = deFiAdminBrokerProcessor.processRequest(handlerContext, request);
        if (responseToReturn != null) {
            Assert.assertNull(response[0]);
            response[0] = responseToReturn;
        }
        Assert.assertEquals(response[0].getCode(), ResponseCode.SUCCESS);
        Assert.assertEquals(response[0].getOpaque(), request.getOpaque());
    }

    @Test
    public void testProcessRequestUpdateAndCreateTopic_fail() throws RemotingCommandException {
        RemotingCommand request = createCommand(RequestCode.UPDATE_AND_CREATE_TOPIC);

        RemotingCommand response = deFiAdminBrokerProcessor.processRequest(handlerContext, request);
    }

    @Test
    public void testProcessRequest_GET_CONSUME_STATS_V2() throws RemotingCommandException {
        RemotingCommand request = createCommand(DeFiBusRequestCode.GET_CONSUME_STATS_V2);
        deFiBrokerController.getTopicConfigManager().createTopicInSendMessageBackMethod(topic, 1, 6, 0);
        RemotingCommand response = deFiAdminBrokerProcessor.processRequest(handlerContext, request);
        assertThat(response.getCode()).isEqualTo(ResponseCode.SUCCESS);
        DeFiBusConsumeStats consumerStats = RemotingSerializable.decode(response.getBody(), DeFiBusConsumeStats.class);
        assertThat(consumerStats).isNotNull();
    }

    private SelectMappedBufferResult createSelectMappedBufferResult() {
        SelectMappedBufferResult result = new SelectMappedBufferResult(0, ByteBuffer.allocate(1024), 0, new MappedFile());
        return result;
    }

    private RemotingCommand createCommand(int requestCode) {
        RemotingCommand request = null;
        if (requestCode == RequestCode.UPDATE_AND_CREATE_TOPIC) {
            request = RemotingCommand.createRequestCommand(requestCode, getCreateTopicRequestHeader());
        } else if (requestCode == DeFiBusRequestCode.GET_CONSUME_STATS_V2) {
            request = RemotingCommand.createRequestCommand(requestCode, getConsumeStatsRequestHeader());
        } else if (requestCode == RequestCode.GET_BROKER_RUNTIME_INFO) {
            request = RemotingCommand.createRequestCommand(requestCode, getConsumeStatsRequestHeader());
        }
        request.setOpaque(1);
        request.makeCustomHeaderToNet();
        return request;
    }

    private GetConsumeStatsRequestHeader getConsumeStatsRequestHeader() {
        GetConsumeStatsRequestHeader header = new GetConsumeStatsRequestHeader();
        header.setConsumerGroup(consumerGroup);
        header.setTopic(topic);
        return header;
    }

    private CreateTopicRequestHeader getCreateTopicRequestHeader() {
        CreateTopicRequestHeader header = new CreateTopicRequestHeader();
        header.setTopic(topic);
        header.setDefaultTopic("DefaultCluster");
        header.setReadQueueNums(1);
        header.setWriteQueueNums(1);
        header.setPerm(6);
        header.setTopicFilterType(TopicFilterType.SINGLE_TAG.toString());
        header.setTopicSysFlag(1);
        return header;
    }
}
