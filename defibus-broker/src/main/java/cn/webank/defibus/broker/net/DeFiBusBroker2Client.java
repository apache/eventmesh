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

package cn.webank.defibus.broker.net;

import cn.webank.defibus.broker.DeFiBrokerController;
import cn.webank.defibus.common.protocol.DeFiBusRequestCode;
import cn.webank.defibus.common.protocol.header.NotifyTopicChangedRequestHeader;
import cn.webank.defibus.common.protocol.header.ReplyMessageRequestHeader;
import io.netty.channel.Channel;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeFiBusBroker2Client {
    private static final Logger LOG = LoggerFactory.getLogger(LoggerName.BROKER_LOGGER_NAME);
    private final DeFiBrokerController deFiBrokerController;

    public DeFiBusBroker2Client(DeFiBrokerController deFiBrokerController) {
        this.deFiBrokerController = deFiBrokerController;
    }

    public boolean pushRRReplyMessageToClient(final Channel channel,
        ReplyMessageRequestHeader replyMessageRequestHeader, MessageExt msgInner) {
        replyMessageRequestHeader.setSysFlag(msgInner.getSysFlag());
        RemotingCommand request = RemotingCommand.createRequestCommand(DeFiBusRequestCode.PUSH_RR_REPLY_MSG_TO_CLIENT, replyMessageRequestHeader);
        request.markOnewayRPC();
        request.setBody(msgInner.getBody());
        try {
            this.deFiBrokerController.getRemotingServer().invokeOneway(channel, request, 3000);
        } catch (RemotingTimeoutException e) {
            LOG.warn("push reply message to client failed ", e);
            try {
                this.deFiBrokerController.getRemotingServer().invokeOneway(channel, request, 3000);
            } catch (Exception sube) {
                LOG.warn("push reply message to client failed again ", sube);
                return false;
            }
        } catch (Exception e) {
            LOG.warn("push reply message to client failed ", e);
            return false;
        }

        return true;
    }

    public void notifyWhenTopicConfigChange(final Channel channel, String topic) {
        NotifyTopicChangedRequestHeader notifyTopicChangedRequestHeader = new NotifyTopicChangedRequestHeader();
        notifyTopicChangedRequestHeader.setTopic(topic);
        RemotingCommand remotingCommand = RemotingCommand.createRequestCommand(DeFiBusRequestCode.NOTIFY_WHEN_TOPIC_CONFIG_CHANGE, notifyTopicChangedRequestHeader);
        remotingCommand.markOnewayRPC();
        try {
            this.deFiBrokerController.getRemotingServer().invokeOneway(channel, remotingCommand, 500);
        } catch (Exception e) {
            LOG.warn("notify consumer <" + channel + "> topic config change fail.", e);
        }
    }
}
