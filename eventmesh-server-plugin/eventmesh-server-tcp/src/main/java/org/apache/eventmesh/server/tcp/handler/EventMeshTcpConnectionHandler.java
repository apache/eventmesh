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

package org.apache.eventmesh.server.tcp.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.apache.eventmesh.server.tcp.EventMeshTCPServer;
import org.apache.eventmesh.server.tcp.config.EventMeshTCPConfiguration;
import org.apache.eventmesh.server.tcp.utils.ChannelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class EventMeshTcpConnectionHandler extends ChannelDuplexHandler {

    public static AtomicInteger              connections = new AtomicInteger(0);
    private final Logger             logger      = LoggerFactory.getLogger(this.getClass());
    private       EventMeshTCPServer eventMeshTCPServer;

    public EventMeshTcpConnectionHandler(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        final String remoteAddress = ChannelUtils.parseChannelRemoteAddr(ctx.channel());
        logger.info("client|tcp|channelRegistered|remoteAddress={}|msg={}", remoteAddress, "");
        super.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        final String remoteAddress = ChannelUtils.parseChannelRemoteAddr(ctx.channel());
        logger.info("client|tcp|channelUnregistered|remoteAddress={}|msg={}", remoteAddress, "");
        super.channelUnregistered(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        final String remoteAddress = ChannelUtils.parseChannelRemoteAddr(ctx.channel());
        logger.info("client|tcp|channelActive|remoteAddress={}|msg={}", remoteAddress, "");

        int c = connections.incrementAndGet();
        if (c > EventMeshTCPConfiguration.eventMeshTcpClientMaxNum) {
            logger.warn("client|tcp|channelActive|remoteAddress={}|msg={}", remoteAddress, "too many client connect " +
                    "this eventMesh server");
            ctx.close();
            return;
        }

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        connections.decrementAndGet();
        final String remoteAddress = ChannelUtils.parseChannelRemoteAddr(ctx.channel());
        logger.info("client|tcp|channelInactive|remoteAddress={}|msg={}", remoteAddress, "");
        eventMeshTCPServer.getClientSessionGroupMapping().closeSession(ctx);
        super.channelInactive(ctx);
    }


    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state().equals(IdleState.ALL_IDLE)) {
                final String remoteAddress = ChannelUtils.parseChannelRemoteAddr(ctx.channel());
                logger.info("client|tcp|userEventTriggered|remoteAddress={}|msg={}", remoteAddress, evt.getClass().getName());
                eventMeshTCPServer.getClientSessionGroupMapping().closeSession(ctx);
            }
        }

        ctx.fireUserEventTriggered(evt);
    }
}
