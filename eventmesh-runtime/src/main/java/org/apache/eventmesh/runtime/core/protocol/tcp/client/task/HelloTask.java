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

import static org.apache.eventmesh.common.protocol.tcp.Command.HELLO_REQUEST;
import static org.apache.eventmesh.common.protocol.tcp.Command.HELLO_RESPONSE;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

public class HelloTask extends AbstractTask {

    private final Logger messageLogger = LoggerFactory.getLogger("message");

    public HelloTask(Package pkg, ChannelHandlerContext ctx, long startTime, EventMeshTCPServer eventMeshTCPServer) {
        super(pkg, ctx, startTime, eventMeshTCPServer);
    }

    @Override
    public void run() {
        long taskExecuteTime = System.currentTimeMillis();
        Package res = new Package();
        Session session = null;
        UserAgent user = (UserAgent) pkg.getBody();
        try {
            //do acl check in connect
            if (eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshServerSecurityEnable) {
                String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
                Acl.doAclCheckInTcpConnect(remoteAddr, user, HELLO_REQUEST.value());
            }

            if (eventMeshTCPServer.getEventMeshServer().getServiceState() != ServiceState.RUNNING) {
                logger.error("server state is not running:{}", eventMeshTCPServer.getEventMeshServer().getServiceState());
                throw new Exception("server state is not running, maybe deploying...");
            }

            validateUserAgent(user);
            session = eventMeshTCPServer.getClientSessionGroupMapping().createSession(user, ctx);
            res.setHeader(new Header(HELLO_RESPONSE, OPStatus.SUCCESS.getCode(), OPStatus.SUCCESS.getDesc(), pkg.getHeader().getSeq()));
            Utils.writeAndFlush(res, startTime, taskExecuteTime, session.getContext(), session);
        } catch (Throwable e) {
            messageLogger.error("HelloTask failed|address={},errMsg={}", ctx.channel().remoteAddress(), e);
            res.setHeader(new Header(HELLO_RESPONSE, OPStatus.FAIL.getCode(), e.getStackTrace().toString(), pkg
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
                            logger.warn("HelloTask failed,close session,addr:{}", ctx.channel().remoteAddress());
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

        //if (user.getUsername() == null) {
        //    throw new Exception("client EventMeshUser cannot be null");
        //}
        //
        //if (user.getPassword() == null) {
        //    throw new Exception("client EventMeshPasswd cannot be null");
        //}

        if (!(StringUtils.equals(EventMeshConstants.PURPOSE_PUB, user.getPurpose()) || StringUtils.equals(
                EventMeshConstants.PURPOSE_SUB, user.getPurpose()))) {

            throw new Exception("client purpose config is error");
        }

        if (StringUtils.isBlank(user.getGroup())) {
            throw new Exception("client group cannot be null");
        }

    }
}
