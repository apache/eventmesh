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

package org.apache.eventmesh.runtime.util;

import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.session.Session;
import org.apache.eventmesh.runtime.core.protocol.tcp.session.SessionState;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Utils {

    private static final Logger MESSAGE_LOGGER = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);

    /**
     * used to send messages to the client
     *
     * @param pkg
     * @param startTime
     * @param ctx
     * @param session
     */
    public static void writeAndFlush(final Package pkg, long startTime, long taskExecuteTime, ChannelHandlerContext ctx,
        Session session) {
        try {
            UserAgent user = session == null ? null : session.getClient();
            if (session != null && session.getSessionState() == SessionState.CLOSED) {
                logFailedMessageFlow(pkg, user, startTime, taskExecuteTime,
                    new Exception("the session has been closed"));
                return;
            }
            ctx.writeAndFlush(pkg).addListener(
                (ChannelFutureListener) future -> {
                    if (!future.isSuccess()) {
                        logFailedMessageFlow(future, pkg, user, startTime, taskExecuteTime);
                    } else {
                        logSucceedMessageFlow(pkg, user, startTime, taskExecuteTime);

                        if (session != null) {
                            Objects.requireNonNull(session.getPubSubManager().get())
                                .getEventMeshTcpMonitor().getTcpSummaryMetrics().getEventMesh2clientMsgNum().incrementAndGet();
                        }
                    }
                }
            );
        } catch (Exception e) {
            log.error("exception while sending message to client", e);
        }
    }

    /**
     * print the message flow of failed sending
     *
     * @param future
     * @param pkg
     * @param user
     * @param startTime
     */
    public static void logFailedMessageFlow(ChannelFuture future, Package pkg, UserAgent user, long startTime,
        long taskExecuteTime) {
        logFailedMessageFlow(pkg, user, startTime, taskExecuteTime, future.cause());
    }

    private static void logFailedMessageFlow(Package pkg, UserAgent user, long startTime, long taskExecuteTime,
        Throwable e) {
        if (pkg.getBody() instanceof EventMeshMessage) {
            final String mqMessage = EventMeshUtil.printMqMessage((EventMeshMessage) pkg.getBody());
            MESSAGE_LOGGER.error("pkg|eventMesh2c|failed|cmd={}|mqMsg={}|user={}|wait={}ms|cost={}ms|errMsg={}",
                pkg.getHeader().getCmd(), mqMessage, user, taskExecuteTime - startTime,
                System.currentTimeMillis() - startTime, e);
        } else {
            MESSAGE_LOGGER.error("pkg|eventMesh2c|failed|cmd={}|pkg={}|user={}|wait={}ms|cost={}ms|errMsg={}",
                pkg.getHeader().getCmd(),
                pkg, user, taskExecuteTime - startTime, System.currentTimeMillis() - startTime, e);
        }
    }

    /**
     * print the message flow of successful sending.
     *
     * @param pkg
     * @param user
     * @param startTime
     */
    public static void logSucceedMessageFlow(Package pkg, UserAgent user, long startTime, long taskExecuteTime) {
        if (pkg.getBody() instanceof EventMeshMessage) {
            final String mqMessage = EventMeshUtil.printMqMessage((EventMeshMessage) pkg.getBody());
            MESSAGE_LOGGER.info("pkg|eventMesh2c|cmd={}|mqMsg={}|user={}|wait={}ms|cost={}ms", pkg.getHeader().getCmd(),
                mqMessage, user, taskExecuteTime - startTime,
                System.currentTimeMillis() - startTime);
        } else {
            MESSAGE_LOGGER
                .info("pkg|eventMesh2c|cmd={}|pkg={}|user={}|wait={}ms|cost={}ms", pkg.getHeader().getCmd(), pkg,
                    user, taskExecuteTime - startTime, System.currentTimeMillis() - startTime);

        }
    }

    /**
     * get serviceId according to topic
     */
    public static String getServiceId(String topic) {
        String[] topicStrArr = topic.split("-");
        if (topicStrArr.length >= 3) {
            return topicStrArr[2];
        } else {
            return null;
        }
    }

    /**
     * parse http header
     *
     * @param fullReq request parameter
     * @return http header
     */
    public static Map<String, Object> parseHttpRequestHeader(HttpRequest fullReq) {
        Map<String, Object> headerParam = new HashMap<>();
        for (String key : fullReq.headers().names()) {
            if (StringUtils.equalsAnyIgnoreCase(key, HttpHeaderNames.CONTENT_TYPE.toString(),
                    HttpHeaderNames.ACCEPT_ENCODING.toString(),
                    HttpHeaderNames.CONTENT_LENGTH.toString())) {
                continue;
            }
            headerParam.put(key, fullReq.headers().get(key));
        }
        return headerParam;
    }
}
