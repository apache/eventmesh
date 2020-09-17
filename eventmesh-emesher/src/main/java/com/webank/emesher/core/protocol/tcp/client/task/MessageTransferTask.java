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

package com.webank.emesher.core.protocol.tcp.client.task;

import com.webank.defibus.common.DeFiBusConstant;
import com.webank.emesher.boot.ProxyTCPServer;
import com.webank.emesher.constants.ProxyConstants;
import com.webank.emesher.core.protocol.tcp.client.session.send.ProxyTcpSendResult;
import com.webank.emesher.core.protocol.tcp.client.session.send.ProxyTcpSendStatus;
import com.webank.eventmesh.common.protocol.tcp.AccessMessage;
import com.webank.eventmesh.common.protocol.tcp.Command;
import com.webank.eventmesh.common.protocol.tcp.Header;
import com.webank.eventmesh.common.protocol.tcp.OPStatus;
import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.emesher.util.ProxyUtil;
import com.webank.emesher.util.Utils;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static com.webank.eventmesh.common.protocol.tcp.Command.RESPONSE_TO_SERVER;

public class MessageTransferTask extends AbstractTask {

    private final Logger messageLogger = LoggerFactory.getLogger("message");

    private final int TRY_PERMIT_TIME_OUT = 5;

    public MessageTransferTask(Package pkg, ChannelHandlerContext ctx, long startTime, ProxyTCPServer proxyTCPServer) {
        super(pkg, ctx, startTime, proxyTCPServer);
    }

    @Override
    public void run() {
        long taskExecuteTime = System.currentTimeMillis();
        Command cmd = pkg.getHeader().getCommand();
        Command replyCmd = getReplyCmd(cmd);
        Package msg = new Package();
        AccessMessage accessMessage = (AccessMessage) pkg.getBody();
        int retCode = 0;
        ProxyTcpSendResult sendStatus;
        try {
            if (accessMessage == null) {
                throw new Exception("accessMessage is null");
            }

            if (!cmd.equals(RESPONSE_TO_SERVER) && !proxyTCPServer.rateLimiter.tryAcquire(TRY_PERMIT_TIME_OUT, TimeUnit.MILLISECONDS)) {
                msg.setHeader(new Header(replyCmd, OPStatus.FAIL.getCode(), "Tps overload, global flow control", pkg.getHeader().getSeq()));
                ctx.writeAndFlush(msg).addListener(
                        new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                Utils.logSucceedMessageFlow(msg, session.getClient(), startTime, taskExecuteTime);
                            }
                        }
                );
                logger.warn("======Tps overload, global flow control, rate:{}! PLEASE CHECK!========", proxyTCPServer.rateLimiter.getRate());
                return;
            }

            synchronized (session) {
                long sendTime = System.currentTimeMillis();
                addTimestamp(accessMessage, cmd, sendTime);
                if (cmd.equals(Command.REQUEST_TO_SERVER)) {
                    accessMessage.getProperties().put(DeFiBusConstant.PROPERTY_MESSAGE_REPLY_TO, session.getClientGroupWrapper()
                            .get().getMqProducerWrapper().getDefaultMQProducer().buildMQClientId());
                }

                sendStatus = session.upstreamMsg(pkg.getHeader(), ProxyUtil.decodeMessage(accessMessage), createSendCallback(replyCmd, taskExecuteTime, accessMessage), startTime, taskExecuteTime);

                if (StringUtils.equals(ProxyTcpSendStatus.SUCCESS.name(), sendStatus.getSendStatus().name())) {
                    messageLogger.info("pkg|proxy2mq|cmd={}|Msg={}|user={}|wait={}ms|cost={}ms", cmd, ProxyUtil.printMqMessage
                            (accessMessage), session.getClient(), taskExecuteTime - startTime, sendTime - startTime);
                } else {
                    throw new Exception(sendStatus.getDetail());
                }
            }
        } catch (Exception e) {
            logger.error("MessageTransferTask failed|cmd={}|Msg={}|user={}|errMsg={}", cmd, accessMessage, session.getClient(), e);
            if (!cmd.equals(RESPONSE_TO_SERVER)) {
                msg.setHeader(new Header(replyCmd, OPStatus.FAIL.getCode(), e.getStackTrace().toString(), pkg.getHeader()
                        .getSeq()));
                Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(), session);
            }
        }
    }

    private void addTimestamp(AccessMessage accessMessage, Command cmd, long sendTime) {
        if (cmd.equals(RESPONSE_TO_SERVER)) {
            accessMessage.getProperties().put(ProxyConstants.RSP_C2PROXY_TIMESTAMP, String.valueOf(startTime));
            accessMessage.getProperties().put(ProxyConstants.RSP_PROXY2MQ_TIMESTAMP, String.valueOf(sendTime));
            accessMessage.getProperties().put(ProxyConstants.RSP_SEND_PROXY_IP, proxyTCPServer.getAccessConfiguration().proxyServerIp);
        } else {
            accessMessage.getProperties().put(ProxyConstants.REQ_C2PROXY_TIMESTAMP, String.valueOf(startTime));
            accessMessage.getProperties().put(ProxyConstants.REQ_PROXY2MQ_TIMESTAMP, String.valueOf(sendTime));
            accessMessage.getProperties().put(ProxyConstants.REQ_SEND_PROXY_IP, proxyTCPServer.getAccessConfiguration().proxyServerIp);
        }
    }

    private Command getReplyCmd(Command cmd) {
        switch (cmd) {
            case REQUEST_TO_SERVER:
                return Command.RESPONSE_TO_CLIENT;
            case ASYNC_MESSAGE_TO_SERVER:
                return Command.ASYNC_MESSAGE_TO_SERVER_ACK;
            case BROADCAST_MESSAGE_TO_SERVER:
                return Command.BROADCAST_MESSAGE_TO_SERVER_ACK;
            default:
                return cmd;
        }
    }

    protected SendCallback createSendCallback(Command replyCmd, long taskExecuteTime, AccessMessage accessMessage) {
        final long createTime = System.currentTimeMillis();
        Package msg = new Package();

        return new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                session.getSender().getUpstreamBuff().release();
                messageLogger.info("upstreamMsg message success|user={}|callback cost={}", session.getClient(),
                        String.valueOf(System.currentTimeMillis() - createTime));
                if (replyCmd.equals(Command.BROADCAST_MESSAGE_TO_SERVER_ACK) || replyCmd.equals(Command
                        .ASYNC_MESSAGE_TO_SERVER_ACK)) {
                    msg.setHeader(new Header(replyCmd, OPStatus.SUCCESS.getCode(), OPStatus.SUCCESS.getDesc(), pkg.getHeader().getSeq()));
                    msg.setBody(accessMessage);
                    Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(), session);
                }
            }

            @Override
            public void onException(Throwable e) {
                session.getSender().getUpstreamBuff().release();
                session.getSender().failMsgCount.incrementAndGet();
                messageLogger.error("upstreamMsg mq message error|user={}|callback cost={}, errMsg={}", session.getClient(), String.valueOf
                        (System.currentTimeMillis() - createTime), new Exception(e));
                msg.setHeader(new Header(replyCmd, OPStatus.FAIL.getCode(), e.toString(), pkg.getHeader().getSeq()));
                msg.setBody(accessMessage);
                Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(), session);
            }
        };
    }
}
