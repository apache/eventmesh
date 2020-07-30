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

package cn.webank.emesher.core.protocol.tcp.client.task;

import cn.webank.emesher.boot.ProxyTCPServer;
import cn.webank.eventmesh.common.protocol.tcp.Command;
import cn.webank.eventmesh.common.protocol.tcp.Header;
import cn.webank.eventmesh.common.protocol.tcp.OPStatus;
import cn.webank.eventmesh.common.protocol.tcp.Package;
import cn.webank.emesher.util.Utils;
import io.netty.channel.ChannelHandlerContext;

import static cn.webank.emesher.core.protocol.tcp.client.ProxyTcp2Client.closeSessionIfTimeout;
import static cn.webank.eventmesh.common.protocol.tcp.Command.CLIENT_GOODBYE_RESPONSE;

public class GoodbyeTask extends AbstractTask {

    public GoodbyeTask(Package pkg, ChannelHandlerContext ctx, long startTime, ProxyTCPServer proxyTCPServer) {
        super(pkg, ctx, startTime, proxyTCPServer);
    }

    @Override
    public void run() {
        long taskExecuteTime = System.currentTimeMillis();
        Package msg = new Package();
        try {
            if (pkg.getHeader().getCommand() == Command.SERVER_GOODBYE_RESPONSE) {
                logger.info("client|address={}| has reject ", session.getContext().channel().remoteAddress());
            } else {
                msg.setHeader(new Header(CLIENT_GOODBYE_RESPONSE, OPStatus.SUCCESS.getCode(), OPStatus.SUCCESS.getDesc(), pkg.getHeader().getSeq
                        ()));
            }
        } catch (Exception e) {
            logger.error("GoodbyeTask failed|user={}|errMsg={}", session.getClient(), e);
            msg.setHeader(new Header(CLIENT_GOODBYE_RESPONSE, OPStatus.FAIL.getCode(), e.getStackTrace().toString(), pkg
                    .getHeader().getSeq()));
        } finally {
            ProxyTCPServer.scheduler.submit(new Runnable() {
                @Override
                public void run() {
                    Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(), session);
                }
            });
            //session.write2Client(msg);
        }
        closeSessionIfTimeout(session, proxyTCPServer.getClientSessionGroupMapping());
    }
}
