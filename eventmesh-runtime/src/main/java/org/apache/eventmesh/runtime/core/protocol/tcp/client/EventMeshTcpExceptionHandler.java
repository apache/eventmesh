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

package org.apache.eventmesh.runtime.core.protocol.tcp.client;

import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMeshTcpExceptionHandler extends ChannelDuplexHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private EventMeshTCPServer eventMeshTCPServer;

    public EventMeshTcpExceptionHandler(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Session session = eventMeshTCPServer.getClientSessionGroupMapping().getSession(ctx);
        UserAgent client = session == null ? null : session.getClient();
        logger.error("exceptionCaught, push goodbye to client|user={},errMsg={}", client, cause.fillInStackTrace());
        String errMsg;
        if (cause.toString().contains("value not one of declared Enum instance names")) {
            errMsg = "Unknown Command type";
        } else {
            errMsg = cause.toString();
        }

        if (session != null) {
            EventMeshTcp2Client.goodBye2Client(session, errMsg, OPStatus.FAIL.getCode(), eventMeshTCPServer.getClientSessionGroupMapping());
        } else {
            EventMeshTcp2Client.goodBye2Client(ctx, errMsg, eventMeshTCPServer.getClientSessionGroupMapping(), eventMeshTCPServer.getEventMeshTcpMonitor());
        }
    }

}
