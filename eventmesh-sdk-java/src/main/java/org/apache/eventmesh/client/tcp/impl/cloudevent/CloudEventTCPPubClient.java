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

import org.apache.eventmesh.client.tcp.EventMeshTCPPubClient;
import org.apache.eventmesh.client.tcp.common.AsyncRRCallback;
import org.apache.eventmesh.client.tcp.common.MessageUtils;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.common.RequestContext;
import org.apache.eventmesh.client.tcp.common.TcpClient;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.util.concurrent.ConcurrentHashMap;

import io.cloudevents.CloudEvent;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * A CloudEvent TCP publish client implementation.
 */
@Slf4j
class CloudEventTCPPubClient extends TcpClient implements EventMeshTCPPubClient<CloudEvent> {

    private ReceiveMsgHook<CloudEvent> callback;

    private final ConcurrentHashMap<String, AsyncRRCallback> callbackConcurrentHashMap = new ConcurrentHashMap<>();

    public CloudEventTCPPubClient(EventMeshTCPClientConfig eventMeshTcpClientConfig) {
        super(eventMeshTcpClientConfig);
    }

    @Override
    public void init() throws EventMeshException {
        try {
            super.open(new Handler());
            super.hello();
            super.heartbeat();
        } catch (Exception ex) {
            throw new EventMeshException("Initialize EventMeshMessageTCPPubClient error", ex);
        }
    }

    @Override
    public void reconnect() throws EventMeshException {
        try {
            super.reconnect();
            super.hello();
        } catch (Exception ex) {
            throw new EventMeshException("reconnect error", ex);
        }
    }

    @Override
    public Package rr(CloudEvent event, long timeout) throws EventMeshException {
        try {
            Package msg = MessageUtils.buildPackage(event, Command.REQUEST_TO_SERVER);
            log.info("{}|rr|send|type={}|msg={}", clientNo, msg, msg);
            return io(msg, timeout);
        } catch (Exception ex) {
            throw new EventMeshException("rr error");
        }
    }

    @Override
    public void asyncRR(CloudEvent event, AsyncRRCallback callback, long timeout) throws EventMeshException {
        try {
            Package msg = MessageUtils.buildPackage(event, Command.REQUEST_TO_SERVER);
            super.send(msg);
            this.callbackConcurrentHashMap.put((String) RequestContext._key(msg), callback);
        } catch (Exception ex) {
            // should trigger callback?
            throw new EventMeshException("asyncRR error", ex);
        }
    }

    @Override
    public Package publish(CloudEvent cloudEvent, long timeout) throws EventMeshException {
        try {
            Package msg = MessageUtils.buildPackage(cloudEvent, Command.ASYNC_MESSAGE_TO_SERVER);
            log.info("SimplePubClientImpl cloud event|{}|publish|send|type={}|protocol={}|msg={}",
                clientNo, msg.getHeader().getCommand(),
                msg.getHeader().getProperty(Constants.PROTOCOL_TYPE), msg);
            return io(msg, timeout);
        } catch (Exception ex) {
            throw new EventMeshException("publish error", ex);
        }
    }

    @Override
    public void broadcast(CloudEvent cloudEvent, long timeout) throws EventMeshException {
        try {
            Package msg = MessageUtils.buildPackage(cloudEvent, Command.BROADCAST_MESSAGE_TO_SERVER);
            log.info("{}|publish|send|type={}|protocol={}|msg={}", clientNo, msg.getHeader().getCommand(),
                msg.getHeader().getProperty(Constants.PROTOCOL_TYPE), msg);
            super.send(msg);
        } catch (Exception ex) {
            throw new EventMeshException("Broadcast message error", ex);
        }
    }

    @Override
    public void registerBusiHandler(ReceiveMsgHook<CloudEvent> handler) throws EventMeshException {
        callback = handler;
    }

    @Override
    public void close() {
        try {
            super.goodbye();
            super.close();
        } catch (Exception ex) {
            log.error("Close CloudEvent TCP publish client error", ex);
        }
    }

    // todo: move to abstract class
    @ChannelHandler.Sharable
    private class Handler extends SimpleChannelInboundHandler<Package> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Package msg) throws Exception {
            log.info("SimplePubClientImpl|{}|receive|type={}|msg={}", clientNo, msg.getHeader(), msg);

            Command cmd = msg.getHeader().getCommand();
            if (cmd == Command.RESPONSE_TO_CLIENT) {
                Package pkg = responseToClientAck(msg);
                if (callback != null) {
                    callback.handle((CloudEvent) pkg.getBody(), ctx);
                }
                send(pkg);
            } else if (cmd == Command.SERVER_GOODBYE_REQUEST) {
                //TODO
            }

            RequestContext context = contexts.get(RequestContext._key(msg));
            if (context != null) {
                contexts.remove(context.getKey());
                context.finish(msg);
            }
        }
    }

    private Package responseToClientAck(Package in) {
        Package msg = new Package();
        msg.setHeader(new Header(Command.RESPONSE_TO_CLIENT_ACK, 0, null, in.getHeader().getSeq()));
        msg.setBody(JsonUtils.deserialize(in.getBody().toString(), EventMeshMessage.class));
        return msg;
    }
}
