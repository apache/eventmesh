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

package com.webank.emesher.core.protocol.tcp.client;

import com.webank.emesher.boot.ProxyTCPServer;
import com.webank.emesher.core.protocol.tcp.client.session.SessionState;
import com.webank.emesher.core.protocol.tcp.client.task.GoodbyeTask;
import com.webank.emesher.core.protocol.tcp.client.task.HeartBeatTask;
import com.webank.emesher.core.protocol.tcp.client.task.HelloTask;
import com.webank.emesher.core.protocol.tcp.client.task.ListenTask;
import com.webank.emesher.core.protocol.tcp.client.task.MessageAckTask;
import com.webank.emesher.core.protocol.tcp.client.task.MessageTransferTask;
import com.webank.emesher.core.protocol.tcp.client.task.SubscribeTask;
import com.webank.emesher.core.protocol.tcp.client.task.UnSubscribeTask;
import com.webank.eventmesh.common.protocol.tcp.AccessMessage;
import com.webank.eventmesh.common.protocol.tcp.Command;
import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.emesher.util.ProxyUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyTcpMessageDispatcher extends SimpleChannelInboundHandler<Package> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Logger messageLogger = LoggerFactory.getLogger("message");
    private ProxyTCPServer proxyTCPServer;

    public ProxyTcpMessageDispatcher(ProxyTCPServer proxyTCPServer) {
        this.proxyTCPServer = proxyTCPServer;
    }
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Package pkg) throws Exception {
        long startTime = System.currentTimeMillis();
        validateMsg(pkg);
        proxyTCPServer.getProxyTcpMonitor().getClient2proxyMsgNum().incrementAndGet();
        Command cmd = null;
        try {
            Runnable task;
            cmd = pkg.getHeader().getCommand();
            if (cmd.equals(Command.HELLO_REQUEST)) {
                messageLogger.info("pkg|c2proxy|cmd={}|pkg={}", cmd, pkg);
                task = new HelloTask(pkg, ctx, startTime, proxyTCPServer);
                ProxyTCPServer.taskHandleExecutorService.submit(task);
                return;
            }

            if (proxyTCPServer.getClientSessionGroupMapping().getSession(ctx) == null) {
                messageLogger.info("pkg|c2proxy|cmd={}|pkg={},no session is found", cmd, pkg);
                throw new Exception("no session is found");
            }

            logMessageFlow(ctx, pkg, cmd);

            if (proxyTCPServer.getClientSessionGroupMapping().getSession(ctx).getSessionState() == SessionState.CLOSED) {
                throw new Exception("this proxy tcp session will be closed, may be reboot or version change!");
            }

            dispatch(ctx, pkg, startTime, cmd);
        } catch (Exception e) {
            logger.error("exception occurred while pkg|cmd={}|pkg={}|errMsg={}", cmd, pkg, e);
            throw new RuntimeException(e);
        }
    }

    private void logMessageFlow(ChannelHandlerContext ctx, Package pkg, Command cmd) {
        if (pkg.getBody() instanceof AccessMessage) {
            messageLogger.info("pkg|c2proxy|cmd={}|Msg={}|user={}", cmd, ProxyUtil.printMqMessage((AccessMessage) pkg
                    .getBody()), proxyTCPServer.getClientSessionGroupMapping().getSession(ctx).getClient());
        } else {
            messageLogger.info("pkg|c2proxy|cmd={}|pkg={}|user={}", cmd, pkg, proxyTCPServer.getClientSessionGroupMapping().getSession(ctx).getClient());
        }
    }

    private void validateMsg(Package pkg) throws Exception {
        if (pkg == null) {
            throw new Exception("the incoming message is empty.");
        }
        if (pkg.getHeader() == null) {
            logger.error("the incoming message does not have a header|pkg={}", pkg);
            throw new Exception("the incoming message does not have a header.");
        }
        if (pkg.getHeader().getCommand() == null) {
            logger.error("the incoming message does not have a command type|pkg={}", pkg);
            throw new Exception("the incoming message does not have a command type.");
        }
    }

    private void dispatch(ChannelHandlerContext ctx, Package pkg, long startTime, Command cmd) throws
            Exception {
        Runnable task;
        switch (cmd) {
            case HEARTBEAT_REQUEST:
                task = new HeartBeatTask(pkg, ctx, startTime, proxyTCPServer);
                break;
            case CLIENT_GOODBYE_REQUEST:
            case SERVER_GOODBYE_RESPONSE:
                task = new GoodbyeTask(pkg, ctx, startTime, proxyTCPServer);
                break;
            case SUBSCRIBE_REQUEST:
                task = new SubscribeTask(pkg, ctx, startTime, proxyTCPServer);
                break;
            case UNSUBSCRIBE_REQUEST:
                task = new UnSubscribeTask(pkg, ctx, startTime, proxyTCPServer);
                break;
            case LISTEN_REQUEST:
                task = new ListenTask(pkg, ctx, startTime, proxyTCPServer);
                break;
            case REQUEST_TO_SERVER:
            case RESPONSE_TO_SERVER:
            case ASYNC_MESSAGE_TO_SERVER:
            case BROADCAST_MESSAGE_TO_SERVER:
                task = new MessageTransferTask(pkg, ctx, startTime, proxyTCPServer);
                break;
            case RESPONSE_TO_CLIENT_ACK:
            case ASYNC_MESSAGE_TO_CLIENT_ACK:
            case BROADCAST_MESSAGE_TO_CLIENT_ACK:
            case REQUEST_TO_CLIENT_ACK:
                task = new MessageAckTask(pkg, ctx, startTime, proxyTCPServer);
                break;
            default:
                throw new Exception("unknown cmd");
        }
        ProxyTCPServer.taskHandleExecutorService.submit(task);
    }
}
