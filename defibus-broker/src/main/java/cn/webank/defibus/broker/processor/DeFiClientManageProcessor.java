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
import cn.webank.defibus.broker.client.DeFiConsumerGroupInfo;
import cn.webank.defibus.broker.client.DeFiConsumerManager;
import cn.webank.defibus.common.protocol.DeFiBusRequestCode;
import cn.webank.defibus.common.protocol.header.GetConsumerListByGroupAndTopicRequestHeader;
import io.netty.channel.ChannelHandlerContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.rocketmq.broker.client.ConsumerGroupInfo;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.header.GetConsumerListByGroupResponseBody;
import org.apache.rocketmq.common.protocol.header.GetConsumerListByGroupResponseHeader;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.exception.RemotingCommandException;
import org.apache.rocketmq.remoting.netty.NettyRequestProcessor;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeFiClientManageProcessor implements NettyRequestProcessor {
    private final DeFiBrokerController deFiBrokerController;
    private static final Logger LOG = LoggerFactory.getLogger(LoggerName.BROKER_LOGGER_NAME);

    public DeFiClientManageProcessor(DeFiBrokerController deFiBrokerController) {
        this.deFiBrokerController = deFiBrokerController;
    }

    @Override
    public RemotingCommand processRequest(ChannelHandlerContext ctx, RemotingCommand request) throws Exception {
        switch (request.getCode()) {
            case DeFiBusRequestCode.GET_CONSUMER_LIST_BY_GROUP_AND_TOPIC:
                return getConsumerListByGroupAndTopic(ctx, request);
            default:
                break;
        }
        return null;
    }

    private RemotingCommand getConsumerListByGroupAndTopic(ChannelHandlerContext ctx, RemotingCommand request)
        throws RemotingCommandException {
        final RemotingCommand response =
            RemotingCommand.createResponseCommand(GetConsumerListByGroupResponseHeader.class);
        final GetConsumerListByGroupAndTopicRequestHeader requestHeader =
            (GetConsumerListByGroupAndTopicRequestHeader) request
                .decodeCommandCustomHeader(GetConsumerListByGroupAndTopicRequestHeader.class);
        DeFiConsumerManager deFiConsumerManager = (DeFiConsumerManager) this.deFiBrokerController.getConsumerManager();
        ConsumerGroupInfo consumerGroupInfo = deFiConsumerManager.getConsumerGroupInfo(requestHeader.getConsumerGroup());

        if (consumerGroupInfo != null) {
            if (consumerGroupInfo instanceof DeFiConsumerGroupInfo) {
                DeFiConsumerGroupInfo wqCGInfo = (DeFiConsumerGroupInfo) consumerGroupInfo;
                List<String> cidList = new ArrayList<>();
                if (requestHeader.getTopic() != null) {
                    Set<String> cids = wqCGInfo.getClientIdBySubscription(requestHeader.getTopic());
                    if (cids != null) {
                        cidList.addAll(cids);
                    }
                    GetConsumerListByGroupResponseBody body = new GetConsumerListByGroupResponseBody();
                    body.setConsumerIdList(cidList);
                    response.setBody(body.encode());
                    response.setCode(ResponseCode.SUCCESS);
                    response.setRemark(null);
                    return response;
                }
            }

            //topic is null or consumerGroupInfo not an instance fo deFiConsumerGroupInfo
            List<String> clientIds = consumerGroupInfo.getAllClientId();
            if (!clientIds.isEmpty()) {
                GetConsumerListByGroupResponseBody body = new GetConsumerListByGroupResponseBody();
                body.setConsumerIdList(clientIds);
                response.setBody(body.encode());
                response.setCode(ResponseCode.SUCCESS);
                response.setRemark(null);
                return response;
            } else {
                LOG.warn("getAllClientId failed, {} {}", requestHeader.getConsumerGroup(),
                    RemotingHelper.parseChannelRemoteAddr(ctx.channel()));
            }
        } else {
            LOG.warn("getConsumerGroupInfo failed, {} {}", requestHeader.getConsumerGroup(),
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()));
        }

        response.setCode(ResponseCode.SYSTEM_ERROR);
        response.setRemark("no consumer for this group, " + requestHeader.getConsumerGroup());
        return response;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}

