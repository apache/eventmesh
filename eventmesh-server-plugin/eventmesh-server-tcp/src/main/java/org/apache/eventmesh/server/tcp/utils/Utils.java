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

package org.apache.eventmesh.server.tcp.utils;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.server.tcp.client.session.Session;
import org.apache.eventmesh.server.tcp.client.session.SessionState;
import org.apache.eventmesh.server.tcp.config.TcpProtocolConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class Utils {
    private final static Logger logger        = LoggerFactory.getLogger(Utils.class);
    private final static Logger messageLogger = LoggerFactory.getLogger("message");

    /**
     * used to send messages to the client
     *
     * @param pkg
     * @param startTime
     * @param ctx
     * @param session
     */
    public static void writeAndFlush(final Package pkg, long startTime, long taskExecuteTime, ChannelHandlerContext ctx, Session
            session) {
        try {
            UserAgent user = session == null ? null : session.getClient();
            if (session != null && session.getSessionState().equals(SessionState.CLOSED)) {
                logFailedMessageFlow(pkg, user, startTime, taskExecuteTime, new Exception("the session has been closed"));
                return;
            }
            ctx.writeAndFlush(pkg).addListener(
                    new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (!future.isSuccess()) {
                                logFailedMessageFlow(future, pkg, user, startTime, taskExecuteTime);
                            } else {
                                logSucceedMessageFlow(pkg, user, startTime, taskExecuteTime);

                                if (session != null) {
                                    session.getClientGroupWrapper().get().getEventMeshTcpMonitor().getEventMesh2clientMsgNum().incrementAndGet();
                                }
                            }
                        }
                    }
            );
        } catch (Exception e) {
            logger.error("exception while sending message to client", e);
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
    public static void logFailedMessageFlow(ChannelFuture future, Package pkg, UserAgent user, long startTime, long taskExecuteTime) {
        logFailedMessageFlow(pkg, user, startTime, taskExecuteTime, future.cause());
    }

    private static void logFailedMessageFlow(Package pkg, UserAgent user, long startTime, long taskExecuteTime, Throwable e) {
        if (pkg.getBody() instanceof EventMeshMessage) {
            messageLogger.error("pkg|eventMesh2c|failed|cmd={}|mqMsg={}|user={}|wait={}ms|cost={}ms|errMsg={}", pkg.getHeader().getCommand(),
                    printMqMessage((EventMeshMessage) pkg.getBody()), user, taskExecuteTime - startTime, System.currentTimeMillis() - startTime, e);
        } else {
            messageLogger.error("pkg|eventMesh2c|failed|cmd={}|pkg={}|user={}|wait={}ms|cost={}ms|errMsg={}", pkg.getHeader().getCommand(),
                    pkg, user, taskExecuteTime - startTime, System.currentTimeMillis() - startTime, e);
        }
    }

    /**
     * print the message flow of successful sending
     *
     * @param pkg
     * @param user
     * @param startTime
     */
    public static void logSucceedMessageFlow(Package pkg, UserAgent user, long startTime, long taskExecuteTime) {
        if (pkg.getBody() instanceof EventMeshMessage) {
            messageLogger.info("pkg|eventMesh2c|cmd={}|mqMsg={}|user={}|wait={}ms|cost={}ms", pkg.getHeader().getCommand(),
                    printMqMessage((EventMeshMessage) pkg.getBody()), user, taskExecuteTime - startTime, System.currentTimeMillis() - startTime);
        } else {
            messageLogger.info("pkg|eventMesh2c|cmd={}|pkg={}|user={}|wait={}ms|cost={}ms", pkg.getHeader().getCommand(), pkg,
                    user, taskExecuteTime - startTime, System.currentTimeMillis() - startTime);
        }
    }

    /**
     * print part of the mq message
     *
     * @param eventMeshMessage
     * @return
     */
    public static String printMqMessage(EventMeshMessage eventMeshMessage) {
        Map<String, String> properties = eventMeshMessage.getProperties();

        String bizSeqNo = properties.get(TcpProtocolConstants.KEYS_UPPERCASE);
        if (!StringUtils.isNotBlank(bizSeqNo)) {
            bizSeqNo = properties.get(TcpProtocolConstants.KEYS_LOWERCASE);
        }

        String result = String.format("Message [topic=%s,TTL=%s,uniqueId=%s,bizSeq=%s]", eventMeshMessage
                .getTopic(), properties.get(TcpProtocolConstants.TTL), properties.get(TcpProtocolConstants.RR_REQUEST_UNIQ_ID), bizSeqNo);
        return result;
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
}
