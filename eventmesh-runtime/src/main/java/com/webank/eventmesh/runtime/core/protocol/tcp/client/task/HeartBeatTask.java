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

package com.webank.eventmesh.runtime.core.protocol.tcp.client.task;

import com.webank.eventmesh.runtime.util.Utils;
import com.webank.eventmesh.runtime.boot.ProxyTCPServer;
import com.webank.eventmesh.common.protocol.tcp.Header;
import com.webank.eventmesh.common.protocol.tcp.OPStatus;
import com.webank.eventmesh.common.protocol.tcp.Package;
import io.netty.channel.ChannelHandlerContext;

import static com.webank.eventmesh.common.protocol.tcp.Command.HEARTBEAT_RESPONSE;

public class HeartBeatTask extends AbstractTask {

    public HeartBeatTask(Package pkg, ChannelHandlerContext ctx, long startTime, ProxyTCPServer proxyTCPServer) {
        super(pkg, ctx, startTime, proxyTCPServer);
    }

    @Override
    public void run() {
        long taskExecuteTime = System.currentTimeMillis();
        Package res = new Package();
        try {
            if (session != null) {
                session.notifyHeartbeat(startTime);
            }
            res.setHeader(new Header(HEARTBEAT_RESPONSE, OPStatus.SUCCESS.getCode(), OPStatus.SUCCESS.getDesc(), pkg.getHeader().getSeq()));
        }catch (Exception e){
            logger.error("HeartBeatTask failed|user={}|errMsg={}", session.getClient(), e);
            res.setHeader(new Header(HEARTBEAT_RESPONSE, OPStatus.FAIL.getCode(), "exception while " +
                    "heartbeating", pkg.getHeader().getSeq()));
        }finally {
            Utils.writeAndFlush(res, startTime, taskExecuteTime, session.getContext(), session);
        }
    }
}
