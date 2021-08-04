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

package org.apache.eventmesh.runtime.core.protocol.tcp.client.task;

import static org.apache.eventmesh.common.protocol.tcp.Command.RESPONSE_TO_SERVER;

import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.openmessaging.api.OnExceptionContext;
import io.openmessaging.api.SendCallback;
import io.openmessaging.api.SendResult;

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.send.EventMeshTcpSendResult;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.send.EventMeshTcpSendStatus;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.apache.eventmesh.runtime.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageTransferTask extends AbstractTask {

    private final Logger messageLogger = LoggerFactory.getLogger("message");

    private final int TRY_PERMIT_TIME_OUT = 5;

    public MessageTransferTask(Package pkg, ChannelHandlerContext ctx, long startTime, EventMeshTCPServer eventMeshTCPServer) {
        super(pkg, ctx, startTime, eventMeshTCPServer);
    }

    @Override
    public void run() {
        long taskExecuteTime = System.currentTimeMillis();
        Command cmd = pkg.getHeader().getCommand();
        Command replyCmd = getReplyCmd(cmd);
        Package msg = new Package();
        EventMeshMessage eventMeshMessage = (EventMeshMessage) pkg.getBody();
        int retCode = 0;
        EventMeshTcpSendResult sendStatus;
        try {
            if (eventMeshMessage == null) {
                throw new Exception("eventMeshMessage is null");
            }

            //do acl check in sending msg
            if(eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshServerSecurityEnable){
                String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
                Acl.doAclCheckInTcpSend(remoteAddr, session.getClient(), eventMeshMessage.getTopic(), cmd.value());
            }

            if (eventMeshTCPServer.getRateLimiter().tryAcquire(TRY_PERMIT_TIME_OUT, TimeUnit.MILLISECONDS)) {
                synchronized (session) {
                    long sendTime = System.currentTimeMillis();
                    addTimestamp(eventMeshMessage, cmd, sendTime);

                    sendStatus = session.upstreamMsg(pkg.getHeader(), EventMeshUtil.decodeMessage(eventMeshMessage), createSendCallback(replyCmd, taskExecuteTime, eventMeshMessage), startTime, taskExecuteTime);

                    if (StringUtils.equals(EventMeshTcpSendStatus.SUCCESS.name(), sendStatus.getSendStatus().name())) {
                        messageLogger.info("pkg|eventMesh2mq|cmd={}|Msg={}|user={}|wait={}ms|cost={}ms", cmd, EventMeshUtil.printMqMessage
                                (eventMeshMessage), session.getClient(), taskExecuteTime - startTime, sendTime - startTime);
                    } else {
                        throw new Exception(sendStatus.getDetail());
                    }
                }
            }else{
                msg.setHeader(new Header(replyCmd, OPStatus.FAIL.getCode(), "Tps overload, global flow control", pkg.getHeader().getSeq()));
                ctx.writeAndFlush(msg).addListener(
                        new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                Utils.logSucceedMessageFlow(msg, session.getClient(), startTime, taskExecuteTime);
                            }
                        }
                );
                logger.warn("======Tps overload, global flow control, rate:{}! PLEASE CHECK!========", eventMeshTCPServer.getRateLimiter().getRate());
                return;
            }
        } catch (Exception e) {
            logger.error("MessageTransferTask failed|cmd={}|Msg={}|user={}|errMsg={}", cmd, eventMeshMessage, session.getClient(), e);
            if (!cmd.equals(RESPONSE_TO_SERVER)) {
                msg.setHeader(new Header(replyCmd, OPStatus.FAIL.getCode(), e.getStackTrace().toString(), pkg.getHeader()
                        .getSeq()));
                Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(), session);
            }
        }
    }

    private void addTimestamp(EventMeshMessage eventMeshMessage, Command cmd, long sendTime) {
        if (cmd.equals(RESPONSE_TO_SERVER)) {
            eventMeshMessage.getProperties().put(EventMeshConstants.RSP_C2EVENTMESH_TIMESTAMP, String.valueOf(startTime));
            eventMeshMessage.getProperties().put(EventMeshConstants.RSP_EVENTMESH2MQ_TIMESTAMP, String.valueOf(sendTime));
            eventMeshMessage.getProperties().put(EventMeshConstants.RSP_SEND_EVENTMESH_IP, CommonConfiguration.eventMeshServerIp);
        } else {
            eventMeshMessage.getProperties().put(EventMeshConstants.REQ_C2EVENTMESH_TIMESTAMP, String.valueOf(startTime));
            eventMeshMessage.getProperties().put(EventMeshConstants.REQ_EVENTMESH2MQ_TIMESTAMP, String.valueOf(sendTime));
            eventMeshMessage.getProperties().put(EventMeshConstants.REQ_SEND_EVENTMESH_IP, CommonConfiguration.eventMeshServerIp);
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

    protected SendCallback createSendCallback(Command replyCmd, long taskExecuteTime, EventMeshMessage eventMeshMessage) {
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
                    msg.setBody(eventMeshMessage);
                    Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(), session);
                }
            }

            @Override
            public void onException(OnExceptionContext context) {
                session.getSender().getUpstreamBuff().release();
                session.getSender().failMsgCount.incrementAndGet();
                messageLogger.error("upstreamMsg mq message error|user={}|callback cost={}, errMsg={}", session.getClient(), String.valueOf
                        (System.currentTimeMillis() - createTime), new Exception(context.getException()));
                msg.setHeader(new Header(replyCmd, OPStatus.FAIL.getCode(), context.getException().toString(), pkg.getHeader().getSeq()));
                msg.setBody(eventMeshMessage);
                Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(), session);
            }

//            @Override
//            public void onException(Throwable e) {
//                session.getSender().getUpstreamBuff().release();
//                session.getSender().failMsgCount.incrementAndGet();
//                messageLogger.error("upstreamMsg mq message error|user={}|callback cost={}, errMsg={}", session.getClient(), String.valueOf
//                        (System.currentTimeMillis() - createTime), new Exception(e));
//                msg.setHeader(new Header(replyCmd, OPStatus.FAIL.getCode(), e.toString(), pkg.getHeader().getSeq()));
//                msg.setBody(accessMessage);
//                Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(), session);
//            }
        };
    }
}
