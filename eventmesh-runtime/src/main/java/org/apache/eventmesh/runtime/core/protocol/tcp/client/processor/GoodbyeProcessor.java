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

package org.apache.eventmesh.runtime.core.protocol.tcp.client.processor;

import static org.apache.eventmesh.common.protocol.tcp.Command.CLIENT_GOODBYE_RESPONSE;

import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.util.Utils;

import java.util.Arrays;

import io.netty.channel.ChannelHandlerContext;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GoodbyeProcessor implements TcpProcessor {

    private EventMeshTCPServer eventMeshTCPServer;
    private final Acl acl;

    public GoodbyeProcessor(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
        this.acl = eventMeshTCPServer.getAcl();
    }

    @Override
    public void process(final Package pkg, final ChannelHandlerContext ctx, long startTime) {
        Session session = eventMeshTCPServer.getClientSessionGroupMapping().getSession(ctx);
        long taskExecuteTime = System.currentTimeMillis();
        Package msg = new Package();
        try {
            if (pkg.getHeader().getCmd() == Command.SERVER_GOODBYE_RESPONSE) {
                log.info("client|address={}| has reject ", session.getContext().channel().remoteAddress());
            } else {
                msg.setHeader(
                    new Header(CLIENT_GOODBYE_RESPONSE, OPStatus.SUCCESS.getCode(), OPStatus.SUCCESS.getDesc(),
                        pkg.getHeader().getSeq()));
            }
        } catch (Exception e) {
            log.error("GoodbyeTask failed|user={}|errMsg={}", session.getClient(), e);
            msg.setHeader(new Header(CLIENT_GOODBYE_RESPONSE, OPStatus.FAIL.getCode(), Arrays.toString(e.getStackTrace()),
                pkg.getHeader().getSeq()));
        } finally {
            this.eventMeshTCPServer.getTcpThreadPoolGroup().getScheduler()
                    .submit(() -> Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(), session));
        }
        EventMeshTcp2Client
            .closeSessionIfTimeout(this.eventMeshTCPServer.getTcpThreadPoolGroup(), session, eventMeshTCPServer.getClientSessionGroupMapping());
    }
}
