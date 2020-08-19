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

package com.webank.emesher.core.protocol.tcp.client.task;

import com.webank.emesher.boot.ProxyTCPServer;
import com.webank.eventmesh.common.protocol.tcp.Command;
import com.webank.eventmesh.common.protocol.tcp.Header;
import com.webank.eventmesh.common.protocol.tcp.OPStatus;
import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.emesher.util.Utils;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class UnSubscribeTask extends AbstractTask {

    private final Logger messageLogger = LoggerFactory.getLogger("message");

    public UnSubscribeTask(Package pkg, ChannelHandlerContext ctx, long startTime, ProxyTCPServer proxyTCPServer) {
        super(pkg, ctx, startTime, proxyTCPServer);
    }

    @Override
    public void run() {
        long taskExecuteTime = System.currentTimeMillis();
        Package msg = new Package();
        try {
            synchronized (session) {
                List<String> topics = new ArrayList<String>();
                if (MapUtils.isNotEmpty(session.getSessionContext().subscribeTopics)) {
                    for (String topic : session.getSessionContext().subscribeTopics.keySet()) {
                        topics.add(topic);
                    }
                    session.unsubscribe(topics);
                    messageLogger.info("UnSubscriberTask succeed|user={}|topics={}", session.getClient(), topics);
                }
            }
            msg.setHeader(new Header(Command.UNSUBSCRIBE_RESPONSE, OPStatus.SUCCESS.getCode(), OPStatus.SUCCESS.getDesc(), pkg.getHeader()
                    .getSeq()));
        } catch (Exception e) {
            messageLogger.error("UnSubscribeTask failed|user={}|errMsg={}", session.getClient(), e);
            msg.setHeader(new Header(Command.UNSUBSCRIBE_RESPONSE, OPStatus.FAIL.getCode(), "exception while " +
                    "unSubscribing", pkg.getHeader().getSeq()));
        } finally {
            Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(), session);
        }
    }


}
