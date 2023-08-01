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

import org.apache.eventmesh.client.tcp.EventMeshTCPPubClient;
import org.apache.eventmesh.client.tcp.common.AsyncRRCallback;
import org.apache.eventmesh.client.tcp.common.MessageUtils;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.common.RequestContext;
import org.apache.eventmesh.client.tcp.common.TcpClient;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.client.tcp.impl.AbstractEventMeshTCPPubHandler;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelHandlerContext;

import lombok.extern.slf4j.Slf4j;

/**
 * EventMeshMessage TCP publish client implementation.
 */
@Slf4j
class EventMeshMessageTCPPubClient extends TcpClient implements EventMeshTCPPubClient<EventMeshMessage> {

    private transient ReceiveMsgHook<EventMeshMessage> callback;

    private final transient ConcurrentHashMap<String, AsyncRRCallback> callbackConcurrentHashMap = new ConcurrentHashMap<>();

    public EventMeshMessageTCPPubClient(EventMeshTCPClientConfig eventMeshTcpClientConfig) {
        super(eventMeshTcpClientConfig);
    }

    @Override
    public void init() throws EventMeshException {
        try {
            open(new EventMeshMessageTCPPubHandler(contexts));
            hello();
            heartbeat();
        } catch (Exception e) {
            throw new EventMeshException("Initialize EventMeshMessageTCPPubClient error", e);
        }

    }

    @Override
    public void reconnect() throws EventMeshException {
        try {
            super.reconnect();
            hello();
        } catch (Exception e) {
            throw new EventMeshException("reconnect error", e);
        }
    }

    // todo: Maybe use org.apache.eventmesh.common.EvetMesh here is better
    @Override
    public Package rr(EventMeshMessage eventMeshMessage, long timeout) throws EventMeshException {
        try {
            Package msg = MessageUtils.buildPackage(eventMeshMessage, Command.REQUEST_TO_SERVER);
            if (log.isInfoEnabled()) {
                log.info("{}|rr|send|type={}|msg={}", CLIENTNO, msg, msg);
            }
            return io(msg, timeout);
        } catch (Exception e) {
            throw new EventMeshException("rr error", e);
        }
    }

    @Override
    public void asyncRR(EventMeshMessage eventMeshMessage, AsyncRRCallback callback, long timeout)
        throws EventMeshException {
        try {
            Package msg = MessageUtils.buildPackage(eventMeshMessage, Command.REQUEST_TO_SERVER);
            super.send(msg);
            this.callbackConcurrentHashMap.put((String) RequestContext.key(msg), callback);
        } catch (Exception e) {
            // should trigger callback?
            throw new EventMeshException("asyncRR error", e);
        }
    }

    @Override
    public Package publish(EventMeshMessage eventMeshMessage, long timeout) throws EventMeshException {
        try {
            Package msg = MessageUtils.buildPackage(eventMeshMessage, Command.ASYNC_MESSAGE_TO_SERVER);
            log.info("SimplePubClientImpl em message|{}|publish|send|type={}|protocol={}|msg={}",
                CLIENTNO, msg.getHeader().getCmd(),
                msg.getHeader().getProperty(Constants.PROTOCOL_TYPE), msg);
            return io(msg, timeout);
        } catch (Exception e) {
            throw new EventMeshException("publish error", e);
        }
    }

    @Override
    public void broadcast(EventMeshMessage eventMeshMessage, long timeout) throws EventMeshException {
        try {
            // todo: transform EventMeshMessage to Package
            Package msg = MessageUtils.buildPackage(eventMeshMessage, Command.BROADCAST_MESSAGE_TO_SERVER);
            log.info("{}|publish|send|type={}|protocol={}|msg={}", CLIENTNO, msg.getHeader().getCmd(),
                msg.getHeader().getProperty(Constants.PROTOCOL_TYPE), msg);
            super.send(msg);
        } catch (Exception e) {
            throw new EventMeshException("Broadcast message error", e);
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
        } catch (Exception e) {
            log.error("Close EventMeshMessage TCP publish client error", e);
        }
    }

    private class EventMeshMessageTCPPubHandler extends AbstractEventMeshTCPPubHandler<EventMeshMessage> {

        public EventMeshMessageTCPPubHandler(ConcurrentHashMap<Object, RequestContext> contexts) {
            super(contexts);
        }

        @Override
        public void callback(EventMeshMessage eventMeshMessage, ChannelHandlerContext ctx) {
            if (callback != null) {
                callback.handle(eventMeshMessage).ifPresent(
                    responseMessage ->
                        ctx.writeAndFlush(
                            MessageUtils.buildPackage(responseMessage, Command.RESPONSE_TO_SERVER))
                );
            }
        }

        @Override
        public EventMeshMessage getMessage(Package tcpPackage) {
            return JsonUtils.parseObject(tcpPackage.getBody().toString(), EventMeshMessage.class);
        }

        @Override
        public void sendResponse(Package tcpPackage) {
            try {
                send(tcpPackage);
            } catch (Exception e) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
