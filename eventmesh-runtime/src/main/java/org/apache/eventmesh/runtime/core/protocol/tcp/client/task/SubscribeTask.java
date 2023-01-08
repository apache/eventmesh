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

import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.Subscription;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.apache.eventmesh.runtime.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;

public class SubscribeTask extends AbstractTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscribeTask.class);

    public SubscribeTask(final Package pkg, final ChannelHandlerContext ctx, long startTime, final EventMeshTCPServer eventMeshTCPServer) {
        super(pkg, ctx, startTime, eventMeshTCPServer);
    }

    @Override
    public void run() {
        final long taskExecuteTime = System.currentTimeMillis();

        final Package msg = new Package();
        try {
            final Subscription subscriptionInfo = (Subscription) pkg.getBody();
            Objects.requireNonNull(subscriptionInfo, "subscriptionInfo can not be null");

            final List<SubscriptionItem> subscriptionItems = new ArrayList<>();
            final boolean eventMeshServerSecurityEnable = eventMeshTCPServer.getEventMeshTCPConfiguration().isEventMeshServerSecurityEnable();
            final String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            subscriptionInfo.getTopicList().forEach(item -> {
                //do acl check for receive msg
                if (eventMeshServerSecurityEnable) {
                    Acl.doAclCheckInTcpReceive(remoteAddr, session.getClient(), item.getTopic(),
                            Command.SUBSCRIBE_REQUEST.getValue());
                }

                subscriptionItems.add(item);
            });

            synchronized (session) {
                session.subscribe(subscriptionItems);
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("SubscribeTask succeed|user={}|topics={}", session.getClient(), subscriptionItems);
                }
            }
            msg.setHeader(new Header(Command.SUBSCRIBE_RESPONSE, OPStatus.SUCCESS.getCode(), OPStatus.SUCCESS.getDesc(),
                    pkg.getHeader().getSeq()));
        } catch (Exception e) {
            LOGGER.error("SubscribeTask failed|user={}|errMsg={}", session.getClient(), e);
            msg.setHeader(new Header(Command.SUBSCRIBE_RESPONSE, OPStatus.FAIL.getCode(), e.toString(), pkg.getHeader()
                    .getSeq()));
        } finally {
            Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(), session);
        }
    }


}
