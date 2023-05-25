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

import static org.apache.eventmesh.common.protocol.tcp.Command.HELLO_RESPONSE;

import org.apache.eventmesh.api.exception.AclException;
import org.apache.eventmesh.api.registry.bo.EventMeshAppSubTopicInfo;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.apache.eventmesh.runtime.util.Utils;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HelloTask extends AbstractTask {

    private static final Logger MESSAGE_LOGGER = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);

    private final Acl acl;

    public HelloTask(Package pkg, ChannelHandlerContext ctx, long startTime, EventMeshTCPServer eventMeshTCPServer) {
        super(pkg, ctx, startTime, eventMeshTCPServer);
        this.acl = eventMeshTCPServer.getAcl();
    }

    @Override
    public void run() {
        long taskExecuteTime = System.currentTimeMillis();
        Package res = new Package();
        Session session = null;
        UserAgent user = (UserAgent) pkg.getBody();
        try {

            //do acl check in connect
            String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            String group = user.getGroup();
            String token = user.getToken();
            String subsystem = user.getSubsystem();

            if (eventMeshTCPServer.getEventMeshTCPConfiguration().isEventMeshServerSecurityEnable()) {
                EventMeshAppSubTopicInfo eventMeshAppSubTopicInfo = eventMeshTCPServer.getRegistry().findEventMeshAppSubTopicInfo(group);
                if (eventMeshAppSubTopicInfo == null) {
                    throw new AclException("no group register");
                }
                this.acl.doAclCheckInTcpConnect(remoteAddr, token, subsystem, eventMeshAppSubTopicInfo);
            }

            if (eventMeshTCPServer.getEventMeshServer().getServiceState() != ServiceState.RUNNING) {
                log.error("server state is not running:{}", eventMeshTCPServer.getEventMeshServer().getServiceState());
                throw new Exception("server state is not running, maybe deploying...");
            }

            validateUserAgent(user);
            session = eventMeshTCPServer.getClientSessionGroupMapping().createSession(user, ctx);
            res.setHeader(new Header(HELLO_RESPONSE, OPStatus.SUCCESS.getCode(), OPStatus.SUCCESS.getDesc(),
                pkg.getHeader().getSeq()));
            Utils.writeAndFlush(res, startTime, taskExecuteTime, session.getContext(), session);
        } catch (Throwable e) {
            MESSAGE_LOGGER.error("HelloTask failed|address={},errMsg={}", ctx.channel().remoteAddress(), e);
            res.setHeader(new Header(HELLO_RESPONSE, OPStatus.FAIL.getCode(), Arrays.toString(e.getStackTrace()), pkg
                .getHeader().getSeq()));
            ctx.writeAndFlush(res).addListener(
                new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            Utils.logFailedMessageFlow(future, res, user, startTime, taskExecuteTime);
                        } else {
                            Utils.logSucceedMessageFlow(res, user, startTime, taskExecuteTime);
                        }
                        log.warn("HelloTask failed,close session,addr:{}", ctx.channel().remoteAddress());
                        eventMeshTCPServer.getClientSessionGroupMapping().closeSession(ctx);
                    }
                }
            );
        }
    }

    private void validateUserAgent(UserAgent user) throws Exception {
        if (user == null) {
            throw new Exception("client info cannot be null");
        }

        if (user.getVersion() == null) {
            throw new Exception("client version cannot be null");
        }


        if (!StringUtils.equalsAny(user.getPurpose(), EventMeshConstants.PURPOSE_PUB, EventMeshConstants.PURPOSE_SUB)) {
            throw new Exception("client purpose config is error");
        }

        if (StringUtils.isBlank(user.getGroup())) {
            throw new Exception("client group cannot be null");
        }

    }
}
