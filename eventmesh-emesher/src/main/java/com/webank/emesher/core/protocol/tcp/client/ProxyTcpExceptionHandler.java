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

package com.webank.emesher.core.protocol.tcp.client;

import com.webank.emesher.boot.ProxyTCPServer;
import com.webank.emesher.core.protocol.tcp.client.session.Session;
import com.webank.eventmesh.common.protocol.tcp.OPStatus;
import com.webank.eventmesh.common.protocol.tcp.UserAgent;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyTcpExceptionHandler extends ChannelDuplexHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ProxyTCPServer proxyTCPServer;

    public ProxyTcpExceptionHandler(ProxyTCPServer proxyTCPServer) {
        this.proxyTCPServer = proxyTCPServer;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Session session = proxyTCPServer.getClientSessionGroupMapping().getSession(ctx);
        UserAgent client = session == null ? null : session.getClient();
        logger.error("exceptionCaught, push goodbye to client|user={},errMsg={}", client, cause.fillInStackTrace());
        String errMsg;
        if (cause.toString().contains("value not one of declared Enum instance names")) {
            errMsg = "Unknown Command type";
        } else {
            errMsg = cause.toString();
        }

        if (session != null) {
            ProxyTcp2Client.goodBye2Client(session, errMsg, OPStatus.FAIL.getCode(), proxyTCPServer.getClientSessionGroupMapping());
        } else {
            ProxyTcp2Client.goodBye2Client(ctx, errMsg, proxyTCPServer.getClientSessionGroupMapping(), proxyTCPServer.getProxyTcpMonitor());
        }
    }

}
