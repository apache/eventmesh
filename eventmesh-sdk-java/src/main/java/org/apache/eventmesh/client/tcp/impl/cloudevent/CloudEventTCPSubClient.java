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

package org.apache.eventmesh.client.tcp.impl.cloudevent;

import org.apache.eventmesh.client.tcp.EventMeshTCPSubClient;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.client.tcp.common.MessageUtils;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.common.RequestContext;
import org.apache.eventmesh.client.tcp.common.TcpClient;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * CloudEvent TCP subscribe client implementation.
 */
@Slf4j
class CloudEventTCPSubClient extends TcpClient implements EventMeshTCPSubClient<CloudEvent> {

    private final UserAgent                  userAgent;
    private final List<SubscriptionItem>     subscriptionItems = Collections.synchronizedList(new ArrayList<>());
    private       ReceiveMsgHook<CloudEvent> callback;
    private       ScheduledFuture<?>         task;

    public CloudEventTCPSubClient(EventMeshTCPClientConfig eventMeshTcpClientConfig) {
        super(eventMeshTcpClientConfig);
        this.userAgent = eventMeshTcpClientConfig.getUserAgent();
    }

    @Override
    public void init() throws EventMeshException {
        try {
            open(new Handler());
            hello();
            log.info("SimpleSubClientImpl|{}|started!", clientNo);
        } catch (Exception ex) {
            throw new EventMeshException("Initialize EventMeshMessageTcpSubClient error", ex);
        }
    }

    @Override
    public void heartbeat() throws EventMeshException {
        if (task == null) {
            synchronized (CloudEventTCPSubClient.class) {
                task = scheduler.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (!isActive()) {
                                reconnect();
                            }
                            Package msg = MessageUtils.heartBeat();
                            io(msg, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
                        } catch (Exception ignore) {
                            //
                        }
                    }
                }, EventMeshCommon.HEARTBEAT, EventMeshCommon.HEARTBEAT, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public void reconnect() throws EventMeshException {
        try {
            super.reconnect();
            hello();
            if (!CollectionUtils.isEmpty(subscriptionItems)) {
                for (SubscriptionItem item : subscriptionItems) {
                    Package request = MessageUtils.subscribe(item.getTopic(), item.getMode(), item.getType());
                    this.io(request, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
                }
            }
            listen();
        } catch (Exception ex) {
            //
        }
    }

    @Override
    public void subscribe(String topic, SubscriptionMode subscriptionMode, SubscriptionType subscriptionType)
        throws EventMeshException {
        try {
            subscriptionItems.add(new SubscriptionItem(topic, subscriptionMode, subscriptionType));
            Package request = MessageUtils.subscribe(topic, subscriptionMode, subscriptionType);
            io(request, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
        } catch (Exception ex) {
            throw new EventMeshException("Subscribe error", ex);
        }
    }

    @Override
    public void unsubscribe() throws EventMeshException {
        try {
            Package request = MessageUtils.unsubscribe();
            io(request, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
        } catch (Exception ex) {
            throw new EventMeshException("Unsubscribe error", ex);
        }
    }

    @Override
    public void listen() throws EventMeshException {
        try {
            Package request = MessageUtils.listen();
            io(request, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
        } catch (Exception ex) {
            throw new EventMeshException("Listen error", ex);
        }
    }

    private void goodbye() throws Exception {
        Package msg = MessageUtils.goodbye();
        this.io(msg, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
    }

    private void hello() throws Exception {
        Package msg = MessageUtils.hello(userAgent);
        this.io(msg, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
    }

    @Override
    public void registerBusiHandler(ReceiveMsgHook<CloudEvent> handler) throws EventMeshException {
        this.callback = handler;
    }

    @Override
    public void close() {
        try {
            task.cancel(false);
            goodbye();
            super.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private class Handler extends SimpleChannelInboundHandler<Package> {
        @SuppressWarnings("Duplicates")
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Package msg) throws Exception {
            Command cmd = msg.getHeader().getCommand();
            log.info("|receive|type={}|msg={}", cmd, msg);
            if (cmd == Command.REQUEST_TO_CLIENT) {
                if (callback != null) {
                    callback.handle(msg, ctx);
                }
                Package pkg = MessageUtils.requestToClientAck(msg);
                send(pkg);
            } else if (cmd == Command.ASYNC_MESSAGE_TO_CLIENT) {
                Package pkg = MessageUtils.asyncMessageAck(msg);
                if (callback != null) {
                    callback.handle(msg, ctx);
                }
                send(pkg);
            } else if (cmd == Command.BROADCAST_MESSAGE_TO_CLIENT) {
                Package pkg = MessageUtils.broadcastMessageAck(msg);
                if (callback != null) {
                    callback.handle(msg, ctx);
                }
                send(pkg);
            } else if (cmd == Command.SERVER_GOODBYE_REQUEST) {
                //TODO
            } else {
                log.error("msg ignored|{}|{}", cmd, msg);
            }
            RequestContext context = contexts.get(RequestContext._key(msg));
            if (context != null) {
                contexts.remove(context.getKey());
                context.finish(msg);
            } else {
                log.error("msg ignored,context not found.|{}|{}", cmd, msg);
            }
        }
    }

}
