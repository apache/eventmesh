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

package org.apache.eventmesh.runtime.client.impl;

import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.runtime.client.api.PubClient;
import org.apache.eventmesh.runtime.client.common.ClientConstants;
import org.apache.eventmesh.runtime.client.common.MessageUtils;
import org.apache.eventmesh.runtime.client.common.RequestContext;
import org.apache.eventmesh.runtime.client.common.TCPClient;
import org.apache.eventmesh.runtime.client.hook.ReceiveMsgHook;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class PubClientImpl extends TCPClient implements PubClient {

    private Logger publogger = LoggerFactory.getLogger(this.getClass());

    private UserAgent userAgent;

    private ReceiveMsgHook callback;

    private ScheduledFuture<?> task;

    public PubClientImpl(String accessIp, int port, UserAgent agent) {
        super(accessIp, port);
        this.userAgent = agent;
    }

    public void registerBusiHandler(ReceiveMsgHook handler) throws Exception {
        callback = handler;
    }

    public void init() throws Exception {
        open(new Handler());
        hello();
        publogger.info("PubClientImpl|{}|started!", clientNo);
    }

    public void reconnect() throws Exception {
        super.reconnect();
        hello();
    }

    public void close() {
        try {
            task.cancel(false);
            super.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void heartbeat() throws Exception {
        task = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isActive()) {
                        PubClientImpl.this.reconnect();
                    }
                    Package msg = MessageUtils.heartBeat();
                    publogger.debug("PubClientImpl|{}|send heartbeat|Command={}|msg={}", clientNo, msg.getHeader().getCommand(), msg);
                    PubClientImpl.this.dispatcher(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
                } catch (Exception e) {
                    //ignore
                }
            }
        }, ClientConstants.HEARTBEAT, ClientConstants.HEARTBEAT, TimeUnit.MILLISECONDS);
    }

    public void goodbye() throws Exception {
        Package msg = MessageUtils.goodbye();
        this.io(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
    }

    public Package askRecommend() throws Exception {
        Package msg = MessageUtils.askRecommend(userAgent);
        return this.dispatcher(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
    }

    private void hello() throws Exception {
        Package msg = MessageUtils.hello(userAgent);
        this.dispatcher(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
    }

    /**
     * send RR message
     */
    @Override
    public Package rr(Package msg, long timeout) throws Exception {
        publogger.info("PubClientImpl|{}|rr|send|Command={}|msg={}", clientNo, msg.getHeader().getCommand().REQUEST_TO_SERVER, msg);
        return dispatcher(msg, timeout);
    }

    /**
     * Add test case assertions on the basis of the original IO
     */
    public Package dispatcher(Package request, long timeout) throws Exception {
        Assert.assertNotNull(request);
        Package response = super.io(request, timeout);
        Assert.assertNotNull(response);
        Command cmd = response.getHeader().getCommand();
        switch (request.getHeader().getCommand()) {
            case RECOMMEND_REQUEST:
                Assert.assertEquals(cmd, Command.RECOMMEND_RESPONSE);
                break;
            case HELLO_REQUEST:
                Assert.assertEquals(cmd, Command.HELLO_RESPONSE);
                break;
            case HEARTBEAT_REQUEST:
                Assert.assertEquals(cmd, Command.HEARTBEAT_RESPONSE);
                break;
            case CLIENT_GOODBYE_REQUEST:
                Assert.assertEquals(cmd, Command.CLIENT_GOODBYE_RESPONSE);
                break;
            case BROADCAST_MESSAGE_TO_SERVER:
                Assert.assertEquals(cmd, Command.BROADCAST_MESSAGE_TO_SERVER_ACK);
                break;
            case ASYNC_MESSAGE_TO_SERVER:
                Assert.assertEquals(cmd, Command.ASYNC_MESSAGE_TO_SERVER_ACK);
                break;
            case REQUEST_TO_SERVER:
                Assert.assertEquals(cmd, Command.RESPONSE_TO_CLIENT);
                break;
            default:
                break;
        }
        assert response.getHeader().getCode() == OPStatus.SUCCESS.getCode();
        return response;
    }

    /**
     * Send an event message, the return value is ACCESS and ACK is given
     */
    public Package publish(Package msg, long timeout) throws Exception {
        publogger.info("PubClientImpl|{}|publish|send|command={}|msg={}", clientNo, msg.getHeader().getCommand(), msg);
        return dispatcher(msg, timeout);
    }

    /**
     * send broadcast message
     */
    public Package broadcast(Package msg, long timeout) throws Exception {
        publogger.info("PubClientImpl|{}|broadcast|send|type={}|msg={}", clientNo, msg.getHeader().getCommand(), msg);
        return dispatcher(msg, timeout);
    }

    @Override
    public UserAgent getUserAgent() {
        return userAgent;
    }

    @ChannelHandler.Sharable
    private class Handler extends SimpleChannelInboundHandler<Package> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Package msg) throws Exception {
            publogger.info("PubClientImpl|{}|receive|type={}|msg={}", clientNo, msg.getHeader().getCommand(), msg);
            Command cmd = msg.getHeader().getCommand();
            if (callback != null) {
                callback.handle(msg, ctx);
            }
            /**
             * RR send and accept the return packet ,and Ack
             */
            if (cmd == Command.RESPONSE_TO_CLIENT) {
                Package responseToClientAck = MessageUtils.responseToClientAck(msg);
                send(responseToClientAck);
                RequestContext context = contexts.get(RequestContext.key(msg));
                if (context != null) {
                    contexts.remove(context.getKey());
                    context.finish(msg);
                    return;
                } else {
                    publogger.error("msg ignored,context not found .|{}|{}", cmd, msg);
                    return;
                }
            } else if (cmd == Command.SERVER_GOODBYE_REQUEST) {
                publogger.error("server goodby request: ---------------------------" + msg);
                close();
            } else {
                RequestContext context = contexts.get(RequestContext.key(msg));
                if (context != null) {
                    contexts.remove(context.getKey());
                    context.finish(msg);
                    return;
                } else {
                    publogger.error("msg ignored,context not found .|{}|{}", cmd, msg);
                    return;
                }
            }
        }
    }

    @Override
    public String toString() {
        return "PubClientImpl|clientNo=" + clientNo + "|" + userAgent;
    }
}
