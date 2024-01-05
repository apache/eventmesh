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

import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.utils.LogUtils;
import org.apache.eventmesh.runtime.client.api.SubClient;
import org.apache.eventmesh.runtime.client.common.ClientConstants;
import org.apache.eventmesh.runtime.client.common.MessageUtils;
import org.apache.eventmesh.runtime.client.common.RequestContext;
import org.apache.eventmesh.runtime.client.common.TCPClient;
import org.apache.eventmesh.runtime.client.hook.ReceiveMsgHook;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SubClientImpl extends TCPClient implements SubClient {

    private final transient UserAgent userAgent;

    private transient ReceiveMsgHook callback;

    private final transient List<SubscriptionItem> subscriptionItems = new ArrayList<SubscriptionItem>();

    private transient ScheduledFuture<?> task;

    public SubClientImpl(String accessIp, int port, UserAgent agent) {
        super(accessIp, port);
        this.userAgent = agent;
    }

    public void registerBusiHandler(ReceiveMsgHook handler) throws Exception {
        callback = handler;
    }

    public void init() throws Exception {
        open(new Handler());
        hello();
        LogUtils.info(log, "SubClientImpl|{}|started!", clientNo);
    }

    public void reconnect() throws Exception {
        super.reconnect();
        hello();
        if (!CollectionUtils.isEmpty(subscriptionItems)) {
            for (SubscriptionItem item : subscriptionItems) {
                Package request = MessageUtils.subscribe(item.getTopic(), item.getMode(), item.getType());
                this.dispatcher(request, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
            }
        }
        listen();
    }

    public void close() {
        try {
            task.cancel(false);
            super.close();
        } catch (Exception e) {
            log.error("cancel close err", e);
        }
    }

    public void heartbeat() throws Exception {
        task = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!isActive()) {
                    SubClientImpl.this.reconnect();
                }
                Package msg = MessageUtils.heartBeat();
                LogUtils.debug(log, "SubClientImpl|{}|send heartbeat|Command={}|msg={}", clientNo,
                    msg.getHeader().getCommand(), msg);
                SubClientImpl.this.dispatcher(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
            } catch (Exception e) {
                // ignore
            }
        }, ClientConstants.HEARTBEAT, ClientConstants.HEARTBEAT, TimeUnit.MILLISECONDS);
    }

    public Package goodbye() throws Exception {
        Package msg = MessageUtils.goodbye();
        return this.io(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
    }

    private void hello() throws Exception {
        Package msg = MessageUtils.hello(userAgent);
        this.dispatcher(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
    }

    public Package justSubscribe(String topic, SubscriptionMode subscriptionMode, SubscriptionType subscriptionType)
        throws Exception {
        subscriptionItems.add(new SubscriptionItem(topic, subscriptionMode, subscriptionType));
        Package msg = MessageUtils.subscribe(topic, subscriptionMode, subscriptionType);
        return this.dispatcher(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
    }

    public Package listen() throws Exception {
        Package request = MessageUtils.listen();
        return this.dispatcher(request, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
    }

    /**
     * @Override
     * public void traceLog() throws Exception {
     *     Package msg = MessageUtils.traceLog();
     *     this.dispatcher(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
     * }
     */

    /**
     *
     * public void sysLog() throws Exception {
     *     Package msg = MessageUtils.sysLog();
     *     this.dispatcher(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
     * }
     */

    public Package justUnsubscribe(String topic, SubscriptionMode subscriptionMode,
        SubscriptionType subscriptionType) throws Exception {
        subscriptionItems.remove(topic);
        Package msg = MessageUtils.unsubscribe(topic, subscriptionMode, subscriptionType);
        return this.dispatcher(msg, ClientConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
    }

    public UserAgent getUserAgent() {
        return userAgent;
    }

    public Package dispatcher(Package request, long timeout) throws Exception {
        Assertions.assertNotNull(request);
        Package response = super.io(request, timeout);
        switch (request.getHeader().getCommand()) {
            case HELLO_REQUEST:
                Assertions.assertEquals(Command.HELLO_RESPONSE, response.getHeader().getCommand());
                break;
            case HEARTBEAT_REQUEST:
                Assertions.assertEquals(Command.HEARTBEAT_RESPONSE, response.getHeader().getCommand());
                break;
            case LISTEN_REQUEST:
                Assertions.assertEquals(Command.LISTEN_RESPONSE, response.getHeader().getCommand());
                break;
            case CLIENT_GOODBYE_REQUEST:
                Assertions.assertEquals(Command.CLIENT_GOODBYE_RESPONSE, response.getHeader().getCommand());
                break;
            case SUBSCRIBE_REQUEST:
                Assertions.assertEquals(Command.SUBSCRIBE_RESPONSE, response.getHeader().getCommand());
                break;
            case UNSUBSCRIBE_REQUEST:
                Assertions.assertEquals(Command.UNSUBSCRIBE_RESPONSE, response.getHeader().getCommand());
                break;
            case SYS_LOG_TO_LOGSERVER:
            case TRACE_LOG_TO_LOGSERVER:
                Assertions.assertNull(response);
                break;
            default:
                break;
        }
        if (response != null) {
            Assertions.assertEquals(OPStatus.SUCCESS.getCode(), response.getHeader().getCode());
        }
        return response;
    }

    @ChannelHandler.Sharable
    private class Handler extends SimpleChannelInboundHandler<Package> {

        @SuppressWarnings("Duplicates")
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Package msg) throws Exception {
            LogUtils.info(log, SubClientImpl.class.getSimpleName() + "|receive|command={}|msg={}",
                msg.getHeader().getCommand(), msg);
            Command cmd = msg.getHeader().getCommand();
            if (callback != null) {
                callback.handle(msg, ctx);
            }
            if (cmd == Command.REQUEST_TO_CLIENT) {
                try {
                    Package ackMsg = MessageUtils.requestToClientAck(msg);
                    send(ackMsg);
                    Package responsePKG = MessageUtils.rrResponse(msg);
                    send(responsePKG);
                } catch (Exception e) {
                    log.error("send rr request to client ack failed", e);
                }
            } else if (cmd == Command.ASYNC_MESSAGE_TO_CLIENT) {
                Package asyncAck = MessageUtils.asyncMessageAck(msg);
                try {
                    send(asyncAck);
                } catch (Exception e) {
                    log.error("send async request to client ack failed", e);
                }
            } else if (cmd == Command.BROADCAST_MESSAGE_TO_CLIENT) {
                Package broadcastAck = MessageUtils.broadcastMessageAck(msg);
                try {
                    send(broadcastAck);
                } catch (Exception e) {
                    log.error("send broadcast request to client ack failed", e);
                }
            } else if (cmd == Command.SERVER_GOODBYE_REQUEST) {
                log.info("server goodby request: ---------------------------" + msg);
                close();
            } else {
                // control instruction set
                RequestContext context = contexts.get(RequestContext.getHeaderSeq(msg));
                if (context != null) {
                    contexts.remove(context.getKey());
                    context.finish(msg);
                } else {
                    log.error("msg ignored,context not found.|{}|{}", cmd, msg);
                }
            }
        }
    }

    @Override
    public String toString() {
        return "SubClientImpl|clientNo=" + clientNo + "|" + userAgent;
    }
}
