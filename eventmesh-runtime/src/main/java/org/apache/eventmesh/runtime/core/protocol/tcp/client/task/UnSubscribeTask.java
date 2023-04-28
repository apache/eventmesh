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

package org.apache.eventmesh.runtime.core.protocol.tcp.client.task;

import static org.apache.eventmesh.common.protocol.tcp.Command.UNSUBSCRIBE_RESPONSE;
import static org.apache.eventmesh.common.protocol.tcp.OPStatus.FAIL;
import static org.apache.eventmesh.common.protocol.tcp.OPStatus.SUCCESS;
import static org.apache.eventmesh.runtime.constants.EventMeshConstants.MESSAGE;

import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.util.Utils;

import org.apache.commons.collections4.MapUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;

public class UnSubscribeTask extends AbstractTask {

    private static final Logger MESSAGE_LOGGER = LoggerFactory.getLogger(MESSAGE);

    public UnSubscribeTask(Package pkg, ChannelHandlerContext ctx, long startTime, EventMeshTCPServer eventMeshTCPServer) {
        super(pkg, ctx, startTime, eventMeshTCPServer);
    }

    @Override
    public void run() {
        long taskExecuteTime = System.currentTimeMillis();
        Package msg = new Package();
        try {
            synchronized (session) {
                ConcurrentHashMap<String, SubscriptionItem> subscribeTopics = session.getSessionContext().getSubscribeTopics();
                if (MapUtils.isNotEmpty(subscribeTopics)) {
                    List<SubscriptionItem> topics = new ArrayList<>(subscribeTopics.values());
                    session.unsubscribe(topics);
                    MESSAGE_LOGGER.info("UnSubscriberTask succeed|user={}|topics={}", session.getClient(), topics);
                }
            }
            msg.setHeader(new Header(UNSUBSCRIBE_RESPONSE, SUCCESS.getCode(), SUCCESS.getDesc(), pkg.getHeader()
                .getSeq()));
        } catch (Exception e) {
            MESSAGE_LOGGER.error("UnSubscribeTask failed|user={}|errMsg={}", session.getClient(), e);
            msg.setHeader(new Header(UNSUBSCRIBE_RESPONSE, FAIL.getCode(), "exception while "
                +
                "unSubscribing", pkg.getHeader().getSeq()));
        } finally {
            Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(), session);
        }
    }


}
