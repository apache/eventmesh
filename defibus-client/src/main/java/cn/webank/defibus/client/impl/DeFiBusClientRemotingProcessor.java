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

import cn.webank.defibus.client.impl.factory.DeFiBusClientInstance;
import cn.webank.defibus.client.impl.producer.RRResponseFuture;
import cn.webank.defibus.client.impl.producer.ResponseTable;
import cn.webank.defibus.common.DeFiBusConstant;
import cn.webank.defibus.common.message.DeFiBusMessageConst;
import cn.webank.defibus.common.protocol.DeFiBusRequestCode;
import cn.webank.defibus.common.protocol.header.NotifyTopicChangedRequestHeader;
import cn.webank.defibus.common.protocol.header.ReplyMessageRequestHeader;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.message.MessageAccessor;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.sysflag.MessageSysFlag;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.exception.RemotingCommandException;
import org.apache.rocketmq.remoting.netty.NettyRequestProcessor;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeFiBusClientRemotingProcessor implements NettyRequestProcessor {
    public static final Logger LOGGER = LoggerFactory.getLogger(DeFiBusClientRemotingProcessor.class);
    private final DeFiBusClientInstance mqClientFactory;

    public DeFiBusClientRemotingProcessor(final DeFiBusClientInstance mqClientFactory) {
        this.mqClientFactory = mqClientFactory;
    }

    @Override
    public RemotingCommand processRequest(ChannelHandlerContext ctx, RemotingCommand request) throws Exception {
        switch (request.getCode()) {
            case DeFiBusRequestCode.PUSH_RR_REPLY_MSG_TO_CLIENT:
                return this.receiveRRReplyMsg(ctx, request);
            case DeFiBusRequestCode.NOTIFY_WHEN_TOPIC_CONFIG_CHANGE:
                return this.notifyWhenTopicConfigChange(ctx, request);
            default:
                break;
        }
        return null;
    }

    private RemotingCommand receiveRRReplyMsg(ChannelHandlerContext ctx,
        RemotingCommand request) throws RemotingCommandException {
        long receiveTime = System.currentTimeMillis();
        ReplyMessageRequestHeader requestHeader = (ReplyMessageRequestHeader) request.decodeCommandCustomHeader(ReplyMessageRequestHeader.class);

        try {
            MessageExt msg = new MessageExt();
            msg.setTopic(requestHeader.getTopic());
            msg.setQueueId(requestHeader.getQueueId());
            msg.setStoreTimestamp(requestHeader.getStoreTimestamp());

            if (requestHeader.getBornHost() != null) {
                String[] bornHostArr = requestHeader.getBornHost().split("/");
                String bornHost/*ip:port*/ = bornHostArr[bornHostArr.length - 1];
                String[] host = bornHost.split(":");
                if (host.length == 2)
                    msg.setBornHost(new InetSocketAddress(host[0], Integer.parseInt(host[1])));
            }

            if (requestHeader.getStoreHost() != null) {
                String[] storeHostArr = requestHeader.getStoreHost().split("/");
                String storeHost = storeHostArr[storeHostArr.length - 1];
                String[] host = storeHost.split(":");
                if (host.length == 2)
                    msg.setStoreHost(new InetSocketAddress(host[0], Integer.parseInt(host[1])));
            }

            byte[] body = request.getBody();
            if ((requestHeader.getSysFlag() & MessageSysFlag.COMPRESSED_FLAG) == MessageSysFlag.COMPRESSED_FLAG) {
                try {
                    body = UtilAll.uncompress(body);
                } catch (IOException e) {
                    LOGGER.warn("err when uncompress constant", e);
                }
            }
            msg.setBody(body);
            msg.setFlag(requestHeader.getFlag());
            MessageAccessor.setProperties(msg, MessageDecoder.string2messageProperties(requestHeader.getProperties()));

            MessageAccessor.putProperty(msg, DeFiBusMessageConst.ARRIVE_TIME, String.valueOf(receiveTime));
            msg.setBornTimestamp(requestHeader.getBornTimestamp());
            msg.setReconsumeTimes(requestHeader.getReconsumeTimes() == null ? 0 : requestHeader.getReconsumeTimes());
            processResponse(msg);

        } catch (Exception e) {
            LOGGER.warn("unknown err when receiveRRReplyMsg", e);
        }

        return null;
    }

    private void processResponse(MessageExt msg) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("receive reply message :{}", msg);
        }
        final String uniqueId = msg.getUserProperty(DeFiBusConstant.PROPERTY_RR_REQUEST_ID);

        RRResponseFuture rrResponseFuture = ResponseTable.getRrResponseFurtureConcurrentHashMap().get(uniqueId);
        if (rrResponseFuture != null && !rrResponseFuture.release()) {
            if (rrResponseFuture.getRrCallback() != null) {
                rrResponseFuture.getRrCallback().onSuccess(msg);
                ResponseTable.getRrResponseFurtureConcurrentHashMap().remove(uniqueId);
            } else {
                rrResponseFuture.putResponse(msg);
            }
        } else {
            LOGGER.warn("receive reply message {} , but requester has gone away", msg.toString());
        }
    }

    private RemotingCommand notifyWhenTopicConfigChange(ChannelHandlerContext ctx, RemotingCommand request) {
        try {
            final NotifyTopicChangedRequestHeader requestHeader =
                (NotifyTopicChangedRequestHeader) request.decodeCommandCustomHeader(NotifyTopicChangedRequestHeader.class);
            LOGGER.info("receive broker's notification[{}], topic: {} config changed, update topic route info from nameserver immediately",
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()),
                requestHeader.getTopic());
            this.mqClientFactory.updateTopicRouteInfoFromNameServer(requestHeader.getTopic());
            this.mqClientFactory.rebalanceImmediately();
        } catch (Exception e) {
            LOGGER.warn("notifyWhenTopicConfigChange failed", RemotingHelper.exceptionSimpleDesc(e));
        }
        return null;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}