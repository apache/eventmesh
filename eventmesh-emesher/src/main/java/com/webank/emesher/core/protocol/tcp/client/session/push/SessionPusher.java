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

package com.webank.emesher.core.protocol.tcp.client.session.push;

import com.webank.emesher.constants.ProxyConstants;
import com.webank.emesher.core.protocol.tcp.client.session.Session;
import com.webank.eventmesh.common.protocol.tcp.AccessMessage;
import com.webank.eventmesh.common.protocol.tcp.Command;
import com.webank.eventmesh.common.protocol.tcp.Header;
import com.webank.eventmesh.common.protocol.tcp.OPStatus;
import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.emesher.util.ProxyUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SessionPusher {

    private final Logger messageLogger = LoggerFactory.getLogger("message");

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private Integer unack;

    private PushContext pushContext = new PushContext(this);

    public PushContext getPushContext() {
        return pushContext;
    }

    public boolean isBusy() {
        return pushContext.getTotalUnackMsgs() >= Math.floor(3 * unack / 4);
    }

    public boolean isCanDownStream() {
        return pushContext.getTotalUnackMsgs() < unack;
    }

    public void setPushContext(PushContext pushContext) {
        this.pushContext = pushContext;
    }

    private Session session;

    public SessionPusher(Session session) {
        this.session = session;
        unack = (0 == session.getClient().getUnack()) ? session.getAccessConfiguration().proxyTcpSessionDownstreamUnackSize : session.getClient().getUnack();
    }

    @Override
    public String toString() {
        return "SessionPusher{unack=" + unack
                + ",busy=" + isBusy()
                + ",canDownStream=" + isCanDownStream()
                + ",pushContext=" + pushContext + "}";
    }

    public void push(final DownStreamMsgContext downStreamMsgContext) {
        Command cmd;
        if (ProxyUtil.isBroadcast(downStreamMsgContext.msgExt.getTopic())) {
            cmd = Command.BROADCAST_MESSAGE_TO_CLIENT;
        } else if (ProxyUtil.isService(downStreamMsgContext.msgExt.getTopic())) {
            cmd = Command.REQUEST_TO_CLIENT;
        } else {
            cmd = Command.ASYNC_MESSAGE_TO_CLIENT;
        }

        Package pkg = new Package();
        downStreamMsgContext.msgExt.putUserProperty(ProxyConstants.REQ_PROXY2C_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
        AccessMessage body = null;
        int retCode = 0;
        String retMsg = null;
        try {
            body = ProxyUtil.encodeMessage(downStreamMsgContext.msgExt);
            pkg.setBody(body);
            pkg.setHeader(new Header(cmd, OPStatus.SUCCESS.getCode(), null, downStreamMsgContext.seq));
            messageLogger.info("pkg|mq2proxy|cmd={}|mqMsg={}|user={}", cmd, ProxyUtil.printMqMessage(body), session.getClient());
        } catch (Exception e) {
            pkg.setHeader(new Header(cmd, OPStatus.FAIL.getCode(), e.getStackTrace().toString(), downStreamMsgContext.seq));
            retCode = -1;
            retMsg = e.toString();
        } finally {
            session.getClientGroupWrapper().get().getProxyTcpMonitor().getProxy2clientMsgNum().incrementAndGet();
            pushContext.deliveredMsgCount();

            //avoid ack arrives to server prior to callback of the method writeAndFlush,may cause ack problem
            List<MessageExt> msgExts = new ArrayList<MessageExt>();
            msgExts.add(downStreamMsgContext.msgExt);
            pushContext.unAckMsg(downStreamMsgContext.seq,
                    msgExts,
                    downStreamMsgContext.consumeConcurrentlyContext,
                    downStreamMsgContext.consumer);

            session.getContext().writeAndFlush(pkg).addListener(
                    new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (!future.isSuccess()) {
                                logger.error("downstreamMsg fail,seq:{}, retryTimes:{}, msg:{}", downStreamMsgContext.seq, downStreamMsgContext.retryTimes, downStreamMsgContext.msgExt);
                                pushContext.deliverFailMsgCount();

                                //push msg failed, remove the msg from unackMap
                                pushContext.getUnAckMsg().remove(downStreamMsgContext.seq);

                                //how long to isolate client when push fail
                                long isolateTime = System.currentTimeMillis() + session.getAccessConfiguration().proxyTcpPushFailIsolateTimeInMills;
                                session.setIsolateTime(isolateTime);
                                logger.warn("isolate client:{},isolateTime:{}", session.getClient(), isolateTime);

                                //retry
                                long delayTime = ProxyUtil.isService(downStreamMsgContext.msgExt.getTopic()) ? 0 : session.getAccessConfiguration().proxyTcpMsgRetryDelayInMills;
                                downStreamMsgContext.delay(delayTime);
                                session.getClientGroupWrapper().get().getProxyTcpRetryer().pushRetry(downStreamMsgContext);
                            } else {
                                pushContext.deliveredMsgCount();
                                logger.info("downstreamMsg success,seq:{}, retryTimes:{}, bizSeq:{}", downStreamMsgContext.seq,downStreamMsgContext.retryTimes, ProxyUtil.getMessageBizSeq(downStreamMsgContext.msgExt));

                                session.getClientGroupWrapper().get().getDownstreamMap().remove(downStreamMsgContext.seq);
                                if(session.isIsolated()){
                                    logger.info("cancel isolated,client:{}", session.getClient());
                                    session.setIsolateTime(System.currentTimeMillis());
                                }
                            }
                        }
                    }
            );
        }
    }
}
