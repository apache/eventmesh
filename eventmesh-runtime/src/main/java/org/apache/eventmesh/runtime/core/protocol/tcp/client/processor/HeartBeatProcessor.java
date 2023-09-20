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

import static org.apache.eventmesh.common.protocol.tcp.Command.HEARTBEAT_REQUEST;
import static org.apache.eventmesh.common.protocol.tcp.Command.HEARTBEAT_RESPONSE;

import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.apache.eventmesh.runtime.util.Utils;

import java.util.Objects;

import io.netty.channel.ChannelHandlerContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HeartBeatProcessor implements TcpProcessor {

    private EventMeshTCPServer eventMeshTCPServer;
    private final Acl acl;

    public HeartBeatProcessor(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
        this.acl = eventMeshTCPServer.getAcl();
    }

    @Override
    public void process(final Package pkg, final ChannelHandlerContext ctx, long startTime) {
        Session session = eventMeshTCPServer.getClientSessionGroupMapping().getSession(ctx);
        long taskExecuteTime = System.currentTimeMillis();
        Package res = new Package();
        try {
            // do acl check in heartbeat
            if (eventMeshTCPServer.getEventMeshTCPConfiguration().isEventMeshServerSecurityEnable()) {
                String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
                this.acl.doAclCheckInTcpHeartbeat(remoteAddr, session.getClient(), HEARTBEAT_REQUEST.getValue());
            }

            if (session != null) {
                session.notifyHeartbeat(startTime);
            }
            res.setHeader(new Header(HEARTBEAT_RESPONSE, OPStatus.SUCCESS.getCode(), OPStatus.SUCCESS.getDesc(),
                pkg.getHeader().getSeq()));
        } catch (Exception e) {
            log.error("HeartBeatTask failed|user={}|errMsg={}", Objects.requireNonNull(session).getClient(), e);
            res.setHeader(new Header(HEARTBEAT_RESPONSE, OPStatus.FAIL.getCode(), "exception while "
                + "heartbeating", pkg.getHeader().getSeq()));
        } finally {
            Utils.writeAndFlush(res, startTime, taskExecuteTime, Objects.requireNonNull(session).getContext(), session);
        }
    }
}
