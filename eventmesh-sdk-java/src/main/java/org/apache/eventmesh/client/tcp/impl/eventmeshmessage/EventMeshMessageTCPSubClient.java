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

package org.apache.eventmesh.client.tcp.impl.eventmeshmessage;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.collections4.CollectionUtils;

import io.netty.channel.ChannelHandlerContext;

import lombok.extern.slf4j.Slf4j;

import org.apache.eventmesh.client.tcp.EventMeshTCPSubClient;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.client.tcp.common.MessageUtils;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.common.RequestContext;
import org.apache.eventmesh.client.tcp.common.TcpClient;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.client.tcp.impl.AbstractEventMeshTCPSubHandler;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.utils.JsonUtils;

@Slf4j
class EventMeshMessageTCPSubClient extends TcpClient implements EventMeshTCPSubClient<EventMeshMessage> {

    private final List<SubscriptionItem> subscriptionItems = Collections.synchronizedList(new LinkedList<>());
    private ReceiveMsgHook<EventMeshMessage> callback;

    public EventMeshMessageTCPSubClient(EventMeshTCPClientConfig eventMeshTcpClientConfig) {
        super(eventMeshTcpClientConfig);
    }

    @Override
    public void init() throws EventMeshException {
        try {
            open(new EventMeshMessageTCPSubHandler(contexts));
            hello();
            heartbeat();
            log.info("SimpleSubClientImpl|{}|started!", clientNo);
        } catch (Exception ex) {
            throw new EventMeshException("Initialize EventMeshMessageTcpSubClient error", ex);
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

    public void listen() throws EventMeshException {
        try {
            Package request = MessageUtils.listen();
            io(request, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
        } catch (Exception ex) {
            throw new EventMeshException("Listen error", ex);
        }
    }


    @Override
    public void registerBusiHandler(ReceiveMsgHook<EventMeshMessage> receiveMsgHook) throws EventMeshException {
        this.callback = receiveMsgHook;
    }

    @Override
    public void close() {
        try {
            super.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private class EventMeshMessageTCPSubHandler extends AbstractEventMeshTCPSubHandler<EventMeshMessage> {
        public EventMeshMessageTCPSubHandler(ConcurrentHashMap<Object, RequestContext> contexts) {
            super(contexts);
        }

        @Override
        public EventMeshMessage getProtocolMessage(Package tcpPackage) {
            return JsonUtils.deserialize(tcpPackage.getBody().toString(), EventMeshMessage.class);
        }

        @Override
        public void callback(EventMeshMessage eventMeshMessage, ChannelHandlerContext ctx) {
            if (callback != null) {
                callback.handle(eventMeshMessage).ifPresent(
                        responseMessage -> ctx.writeAndFlush(MessageUtils.buildPackage(responseMessage, Command.RESPONSE_TO_SERVER))
                );
            }
        }

        @Override
        public void response(Package tcpPackage) {
            try {
                send(tcpPackage);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
