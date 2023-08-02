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

package org.apache.eventmesh.runtime.core.protocol.tcp.processor;

import static org.apache.eventmesh.common.protocol.tcp.Command.LISTEN_RESPONSE;

import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.session.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;

import lombok.extern.slf4j.Slf4j;



@Slf4j
public class ListenProcessor implements TcpRequestProcessor {

    private static final Logger MESSAGE_LOGGER = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);

    private final EventMeshTCPServer eventMeshTCPServer;

    private final Acl acl;


    public ListenProcessor(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
        this.acl = eventMeshTCPServer.getAcl();
    }

    @Override
    public void process(Package pkg, ChannelHandlerContext ctx, long startTime) {
        Session session = eventMeshTCPServer.getSessionManager().getSession(ctx);
        long taskExecuteTime = System.currentTimeMillis();
        Header header = new Header(LISTEN_RESPONSE, OPStatus.SUCCESS.getCode(), OPStatus.SUCCESS.getDesc(), pkg.getHeader().getSeq());
        session.setListenRequestSeq(pkg.getHeader().getSeq());
        try {
            synchronized (session) {
                eventMeshTCPServer.getSessionManager().readySession(session);
            }
        } catch (Exception e) {
            log.error("ListenTask failed|user={}|errMsg={}", session.getClient(), e);
            Integer status = OPStatus.FAIL.getCode();
            header = new Header(LISTEN_RESPONSE, status, e.toString(), pkg.getHeader().getSeq());
        } finally {
            //check to avoid send repeatedly
            session.trySendListenResponse(header, startTime, taskExecuteTime);
        }
    }


}
