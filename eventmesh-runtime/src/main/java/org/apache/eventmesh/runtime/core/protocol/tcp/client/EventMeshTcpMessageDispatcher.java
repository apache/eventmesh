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

package org.apache.eventmesh.runtime.core.protocol.tcp.client;

import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.SessionState;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.task.GoodbyeTask;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.task.HeartBeatTask;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.task.HelloTask;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.task.ListenTask;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.task.MessageAckTask;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.task.MessageTransferTask;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.task.SubscribeTask;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.task.UnSubscribeTask;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Package;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMeshTcpMessageDispatcher extends SimpleChannelInboundHandler<Package> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Logger messageLogger = LoggerFactory.getLogger("message");
    private EventMeshTCPServer eventMeshTCPServer;

    public EventMeshTcpMessageDispatcher(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Package pkg) throws Exception {
        long startTime = System.currentTimeMillis();
        validateMsg(pkg);
        eventMeshTCPServer.getEventMeshTcpMonitor().getClient2EventMeshMsgNum().incrementAndGet();
        Command cmd = null;
        try {
            Runnable task;
            cmd = pkg.getHeader().getCommand();
            if (cmd.equals(Command.HELLO_REQUEST)) {
                messageLogger.info("pkg|c2eventMesh|cmd={}|pkg={}", cmd, pkg);
                task = new HelloTask(pkg, ctx, startTime, eventMeshTCPServer);
                EventMeshTCPServer.taskHandleExecutorService.submit(task);
                return;
            }

            if (eventMeshTCPServer.getClientSessionGroupMapping().getSession(ctx) == null) {
                messageLogger.info("pkg|c2eventMesh|cmd={}|pkg={},no session is found", cmd, pkg);
                throw new Exception("no session is found");
            }

            logMessageFlow(ctx, pkg, cmd);

            if (eventMeshTCPServer.getClientSessionGroupMapping().getSession(ctx).getSessionState() == SessionState.CLOSED) {
                throw new Exception("this eventMesh tcp session will be closed, may be reboot or version change!");
            }

            dispatch(ctx, pkg, startTime, cmd);
        } catch (Exception e) {
            logger.error("exception occurred while pkg|cmd={}|pkg={}|errMsg={}", cmd, pkg, e);
            throw new RuntimeException(e);
        }
    }

    private void logMessageFlow(ChannelHandlerContext ctx, Package pkg, Command cmd) {
        if (pkg.getBody() instanceof EventMeshMessage) {
            messageLogger.info("pkg|c2eventMesh|cmd={}|Msg={}|user={}", cmd, EventMeshUtil.printMqMessage((EventMeshMessage) pkg
                    .getBody()), eventMeshTCPServer.getClientSessionGroupMapping().getSession(ctx).getClient());
        } else {
            messageLogger.info("pkg|c2eventMesh|cmd={}|pkg={}|user={}", cmd, pkg, eventMeshTCPServer.getClientSessionGroupMapping().getSession(ctx).getClient());
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
                task = new HeartBeatTask(pkg, ctx, startTime, eventMeshTCPServer);
                break;
            case CLIENT_GOODBYE_REQUEST:
            case SERVER_GOODBYE_RESPONSE:
                task = new GoodbyeTask(pkg, ctx, startTime, eventMeshTCPServer);
                break;
            case SUBSCRIBE_REQUEST:
                task = new SubscribeTask(pkg, ctx, startTime, eventMeshTCPServer);
                break;
            case UNSUBSCRIBE_REQUEST:
                task = new UnSubscribeTask(pkg, ctx, startTime, eventMeshTCPServer);
                break;
            case LISTEN_REQUEST:
                task = new ListenTask(pkg, ctx, startTime, eventMeshTCPServer);
                break;
            case REQUEST_TO_SERVER:
            case RESPONSE_TO_SERVER:
            case ASYNC_MESSAGE_TO_SERVER:
            case BROADCAST_MESSAGE_TO_SERVER:
                task = new MessageTransferTask(pkg, ctx, startTime, eventMeshTCPServer);
                break;
            case RESPONSE_TO_CLIENT_ACK:
            case ASYNC_MESSAGE_TO_CLIENT_ACK:
            case BROADCAST_MESSAGE_TO_CLIENT_ACK:
            case REQUEST_TO_CLIENT_ACK:
                task = new MessageAckTask(pkg, ctx, startTime, eventMeshTCPServer);
                break;
            default:
                throw new Exception("unknown cmd");
        }
        EventMeshTCPServer.taskHandleExecutorService.submit(task);
    }
}
