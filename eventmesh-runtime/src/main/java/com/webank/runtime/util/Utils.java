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

package com.webank.runtime.util;

import com.webank.runtime.constants.ProxyConstants;
import com.webank.runtime.core.protocol.tcp.client.session.Session;
import com.webank.runtime.core.protocol.tcp.client.session.SessionState;
import com.webank.eventmesh.common.protocol.tcp.AccessMessage;
import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.eventmesh.common.protocol.tcp.UserAgent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

public class Utils {
    private final static Logger logger = LoggerFactory.getLogger(Utils.class);
    private final static Logger messageLogger = LoggerFactory.getLogger("message");

    /**
     * 用于向客户端发送消息
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
                                    session.getClientGroupWrapper().get().getProxyTcpMonitor().getProxy2clientMsgNum().incrementAndGet();
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
     * 打印发送失败的消息流水
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
        if (pkg.getBody() instanceof AccessMessage) {
            messageLogger.error("pkg|proxy2c|failed|cmd={}|mqMsg={}|user={}|wait={}ms|cost={}ms|errMsg={}", pkg.getHeader().getCommand(),
                    printMqMessage((AccessMessage) pkg.getBody()), user, taskExecuteTime - startTime, System.currentTimeMillis() - startTime, e);
        } else {
            messageLogger.error("pkg|proxy2c|failed|cmd={}|pkg={}|user={}|wait={}ms|cost={}ms|errMsg={}", pkg.getHeader().getCommand(),
                    pkg, user, taskExecuteTime - startTime, System.currentTimeMillis() - startTime, e);
        }
    }

    /**
     * 打印发送发成的消息流水
     *
     * @param pkg
     * @param user
     * @param startTime
     */
    public static void logSucceedMessageFlow(Package pkg, UserAgent user, long startTime, long taskExecuteTime) {
        if (pkg.getBody() instanceof AccessMessage) {
            messageLogger.info("pkg|proxy2c|cmd={}|mqMsg={}|user={}|wait={}ms|cost={}ms", pkg.getHeader().getCommand(),
                    printMqMessage((AccessMessage) pkg.getBody()), user, taskExecuteTime - startTime, System.currentTimeMillis() - startTime);
        } else {
            messageLogger.info("pkg|proxy2c|cmd={}|pkg={}|user={}|wait={}ms|cost={}ms", pkg.getHeader().getCommand(), pkg,
                    user, taskExecuteTime - startTime, System.currentTimeMillis() - startTime);
        }
    }

    public static org.apache.rocketmq.common.message.Message decodeMessage(AccessMessage accessMessage) {
        org.apache.rocketmq.common.message.Message msg = new org.apache.rocketmq.common.message.Message();
        msg.setTopic(accessMessage.getTopic());
        msg.setBody(accessMessage.getBody().getBytes());
        msg.getProperty("init");
        for (Map.Entry<String, String> property : accessMessage.getProperties().entrySet()) {
            msg.getProperties().put(property.getKey(), property.getValue());
        }
        return msg;
    }

    public static AccessMessage encodeMessage(org.apache.rocketmq.common.message.Message msg) throws Exception {
        AccessMessage accessMessage = new AccessMessage();
        accessMessage.setBody(new String(msg.getBody(), "UTF-8"));
        accessMessage.setTopic(msg.getTopic());
        for (Map.Entry<String, String> property : msg.getProperties().entrySet()) {
            accessMessage.getProperties().put(property.getKey(), property.getValue());
        }
        return accessMessage;
    }

    public static org.apache.rocketmq.common.message.Message messageMapper(org.apache.rocketmq.common.message.Message
                                                                                   message) {
        org.apache.rocketmq.common.message.Message msg = new org.apache.rocketmq.common.message.Message();
        msg.setTopic(message.getTopic());
        msg.setBody(message.getBody());
        msg.getProperty("init");
        for (Map.Entry<String, String> property : message.getProperties().entrySet()) {
            msg.getProperties().put(property.getKey(), property.getValue());
        }
        return msg;
    }

    /**
     * 打印mq消息的一部分内容
     *
     * @param accessMessage
     * @return
     */
    public static String printMqMessage(AccessMessage accessMessage) {
        Map<String, String> properties = accessMessage.getProperties();

        String bizSeqNo = properties.get(ProxyConstants.KEYS_UPPERCASE);
        if (!StringUtils.isNotBlank(bizSeqNo)) {
            bizSeqNo = properties.get(ProxyConstants.KEYS_LOWERCASE);
        }

        String result = String.format("Message [topic=%s,TTL=%s,uniqueId=%s,bizSeq=%s]", accessMessage
                .getTopic(), properties.get(ProxyConstants.TTL), properties.get(ProxyConstants.RR_REQUEST_UNIQ_ID), bizSeqNo);
        return result;
    }

    /**
     * 打印mq消息的一部分内容
     *
     * @param message
     * @return
     */
    public static String printMqMessage(org.apache.rocketmq.common.message.Message message) {
        Map<String, String> properties = message.getProperties();
        String bizSeqNo = message.getKeys();
        String result = String.format("Message [topic=%s,TTL=%s,uniqueId=%s,bizSeq=%s]", message.getTopic()
                , properties.get(ProxyConstants.TTL), properties.get(ProxyConstants.RR_REQUEST_UNIQ_ID), bizSeqNo);
        return result;
    }

    /**
     * 根据topic获取serviceId
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
