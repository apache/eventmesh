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

import static org.apache.eventmesh.common.protocol.tcp.Command.RECOMMEND_RESPONSE;
import static org.apache.eventmesh.runtime.util.Utils.writeAndFlush;

import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.recommend.EventMeshRecommendImpl;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.recommend.EventMeshRecommendStrategy;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;

import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;

public class RecommendProcessor implements TcpProcessor {

    private static final Logger MESSAGE_LOGGER = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);

    private EventMeshTCPServer eventMeshTCPServer;
    private final Acl acl;

    public RecommendProcessor(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
        this.acl = eventMeshTCPServer.getAcl();
    }

    @Override
    public void process(final Package pkg, final ChannelHandlerContext ctx, long startTime) {
        Session session = eventMeshTCPServer.getClientSessionGroupMapping().getSession(ctx);
        long taskExecuteTime = System.currentTimeMillis();
        Package res = new Package();
        try {
            if (!eventMeshTCPServer.getEventMeshTCPConfiguration().isEventMeshServerMetaStorageEnable()) {
                throw new Exception("registry enable config is false, not support");
            }
            UserAgent user = (UserAgent) pkg.getBody();
            validateUserAgent(user);
            String group = getGroupOfClient(user);
            EventMeshRecommendStrategy eventMeshRecommendStrategy = new EventMeshRecommendImpl(eventMeshTCPServer);
            String eventMeshRecommendResult = eventMeshRecommendStrategy.calculateRecommendEventMesh(group, user.getPurpose());
            res.setHeader(new Header(RECOMMEND_RESPONSE, OPStatus.SUCCESS.getCode(), OPStatus.SUCCESS.getDesc(),
                pkg.getHeader().getSeq()));
            res.setBody(eventMeshRecommendResult);
        } catch (Exception e) {
            MESSAGE_LOGGER.error("RecommendTask failed|address={}|errMsg={}", ctx.channel().remoteAddress(), e);
            res.setHeader(new Header(RECOMMEND_RESPONSE, OPStatus.FAIL.getCode(), e.toString(), pkg
                .getHeader().getSeq()));

        } finally {
            writeAndFlush(res, startTime, taskExecuteTime, session.getContext(), session);
            // session.write2Client(res);
        }
    }

    private void validateUserAgent(UserAgent user) throws Exception {
        if (user == null) {
            throw new Exception("client info cannot be null");
        }

        if (user.getVersion() == null) {
            throw new Exception("client version cannot be null");
        }

        if (user.getUsername() == null) {
            throw new Exception("client wemqUser cannot be null");
        }

        if (user.getPassword() == null) {
            throw new Exception("client wemqPasswd cannot be null");
        }

        if (!StringUtils.equalsAny(user.getPurpose(), EventMeshConstants.PURPOSE_PUB, EventMeshConstants.PURPOSE_SUB)) {
            throw new Exception("client purpose config is error");
        }
    }

    private String getGroupOfClient(UserAgent userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.getGroup();
    }
}
