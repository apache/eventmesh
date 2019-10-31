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
import cn.webank.defibus.common.protocol.DeFiBusRequestCode;
import cn.webank.defibus.common.protocol.header.GetConsumerListByGroupAndTopicRequestHeader;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.rocketmq.broker.client.ClientChannelInfo;
import org.apache.rocketmq.broker.client.ConsumerGroupInfo;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.header.GetConsumerListByGroupResponseBody;
import org.apache.rocketmq.common.protocol.heartbeat.ConsumeType;
import org.apache.rocketmq.common.protocol.heartbeat.ConsumerData;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.common.protocol.heartbeat.SubscriptionData;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.remoting.protocol.LanguageCode;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.protocol.RemotingSerializable;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class DeFiClientManageProcessorTest {
    private DeFiClientManageProcessor deFiClientManageProcessor;
    @Spy
    private DeFiBrokerController deFiBrokerController = new DeFiBrokerController(new BrokerConfig(), new NettyServerConfig(), new NettyClientConfig(), new MessageStoreConfig(), new DeFiBusBrokerConfig());
    @Mock
    private ChannelHandlerContext handlerContext;
    @Mock
    private Channel channel;

    private ClientChannelInfo clientChannelInfo;
    private String clientId = UUID.randomUUID().toString();
    private String group = "FooBarGroup";
    private String topic = "FooBar";

    @Before
    public void init() {
        // when(handlerContext.channel()).thenReturn(channel);
        deFiClientManageProcessor = new DeFiClientManageProcessor(deFiBrokerController);
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
    }

    @Test
    public void processRequest_GetConsumerListByGroupAndTopic() throws Exception {
        ConsumerGroupInfo consumerGroupInfo = deFiBrokerController.getConsumerManager().getConsumerGroupInfo(group);
        assertThat(consumerGroupInfo).isNotNull();

        RemotingCommand request = GetConsumerListCommand();
        RemotingCommand response = deFiClientManageProcessor.processRequest(handlerContext, request);
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(ResponseCode.SUCCESS);
        GetConsumerListByGroupResponseBody body = RemotingSerializable.decode(response.getBody(), GetConsumerListByGroupResponseBody.class);
        List<String> clientIdList = body.getConsumerIdList();
        assertThat(clientIdList).isNotNull();
        assertThat(clientIdList.get(0)).isEqualTo(clientId);
    }

    @Test
    public void processRequest_GetConsumerListByGroupAndTopicIsNull() throws Exception {
        ConsumerGroupInfo consumerGroupInfo = deFiBrokerController.getConsumerManager().getConsumerGroupInfo(group);
        assertThat(consumerGroupInfo).isNotNull();

        RemotingCommand request = GetConsumerListCommandWithNoTopic();
        RemotingCommand response = response = deFiClientManageProcessor.processRequest(handlerContext, request);
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(ResponseCode.SUCCESS);
        GetConsumerListByGroupResponseBody body = RemotingSerializable.decode(response.getBody(), GetConsumerListByGroupResponseBody.class);
        List<String> clientIdList = body.getConsumerIdList();
        assertThat(clientIdList).isNotNull();
        assertThat(clientIdList.get(0)).isEqualTo(clientId);
    }

    private RemotingCommand GetConsumerListCommand() {
        GetConsumerListByGroupAndTopicRequestHeader requestHeader = new GetConsumerListByGroupAndTopicRequestHeader();
        requestHeader.setConsumerGroup(group);
        requestHeader.setTopic(topic);
        RemotingCommand request = RemotingCommand.createRequestCommand(DeFiBusRequestCode.GET_CONSUMER_LIST_BY_GROUP_AND_TOPIC, requestHeader);
        request.setLanguage(LanguageCode.JAVA);
        request.setVersion(100);
        request.makeCustomHeaderToNet();
        return request;
    }

    private RemotingCommand GetConsumerListCommandWithNoTopic() {
        GetConsumerListByGroupAndTopicRequestHeader requestHeader = new GetConsumerListByGroupAndTopicRequestHeader();
        requestHeader.setConsumerGroup(group);
        RemotingCommand request = RemotingCommand.createRequestCommand(DeFiBusRequestCode.GET_CONSUMER_LIST_BY_GROUP_AND_TOPIC, requestHeader);
        request.setLanguage(LanguageCode.JAVA);
        request.setVersion(100);
        request.makeCustomHeaderToNet();
        return request;
    }

    static ConsumerData createConsumerData(String group, String topic) {
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