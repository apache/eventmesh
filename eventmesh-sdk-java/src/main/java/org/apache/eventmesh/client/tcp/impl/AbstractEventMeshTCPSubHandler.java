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

package org.apache.eventmesh.client.tcp.impl;

import org.apache.eventmesh.client.tcp.common.MessageUtils;
import org.apache.eventmesh.client.tcp.common.RequestContext;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Package;

import java.util.concurrent.ConcurrentHashMap;

import com.google.common.base.Preconditions;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractEventMeshTCPSubHandler<ProtocolMessage> extends SimpleChannelInboundHandler<Package> {

    protected final ConcurrentHashMap<Object, RequestContext> contexts;

    public AbstractEventMeshTCPSubHandler(ConcurrentHashMap<Object, RequestContext> contexts) {
        this.contexts = contexts;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Package msg) throws Exception {
        Preconditions.checkNotNull(msg, "TCP package cannot be null");
        Preconditions.checkNotNull(msg.getHeader(), "TCP package header cannot be null");
        Command cmd = msg.getHeader().getCmd();
        log.info("|receive|type={}|msg={}", cmd, msg);
        switch (cmd) {
            case REQUEST_TO_CLIENT:
                callback(getProtocolMessage(msg), ctx);
                response(MessageUtils.requestToClientAck(msg));
                break;
            case ASYNC_MESSAGE_TO_CLIENT:
                callback(getProtocolMessage(msg), ctx);
                response(MessageUtils.asyncMessageAck(msg));
                break;
            case BROADCAST_MESSAGE_TO_CLIENT:
                callback(getProtocolMessage(msg), ctx);
                response(MessageUtils.broadcastMessageAck(msg));
                break;
            case SERVER_GOODBYE_REQUEST:
                // TODO
                break;
            default:
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

    public abstract ProtocolMessage getProtocolMessage(Package tcpPackage);

    public abstract void callback(ProtocolMessage protocolMessage, ChannelHandlerContext ctx);

    public abstract void response(Package tcpPackage);
}
