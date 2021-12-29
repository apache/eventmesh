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

import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

import org.apache.eventmesh.client.tcp.common.MessageUtils;
import org.apache.eventmesh.client.tcp.common.RequestContext;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Package;

@Slf4j
public abstract class AbstractEventMeshTCPPubHandler<ProtocolMessage> extends SimpleChannelInboundHandler<Package> {

    private final ConcurrentHashMap<Object, RequestContext> contexts;

    public AbstractEventMeshTCPPubHandler(ConcurrentHashMap<Object, RequestContext> contexts) {
        this.contexts = contexts;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Package msg) {
        log.info("SimplePubClientImpl|receive|msg={}", msg);

        Preconditions.checkNotNull(msg.getHeader(), "Tcp package header cannot be null");
        Command cmd = msg.getHeader().getCmd();
        switch (cmd) {
            case RESPONSE_TO_CLIENT:
                callback(getMessage(msg), ctx);
                sendResponse(MessageUtils.responseToClientAck(msg));
                break;
            case SERVER_GOODBYE_REQUEST:
                //TODO
                break;
            default:
                break;

        }
        RequestContext context = contexts.get(RequestContext.key(msg));
        if (context != null) {
            contexts.remove(context.getKey());
            context.finish(msg);
        }
    }

    public abstract void callback(ProtocolMessage protocolMessage, ChannelHandlerContext ctx);

    public abstract ProtocolMessage getMessage(Package tcpPackage);

    public abstract void sendResponse(Package tcpPackage);

}
