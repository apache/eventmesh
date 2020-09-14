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

package com.webank.emesher.core.protocol.tcp.client.session.send;

import com.webank.defibus.client.impl.producer.RRCallback;
import com.webank.defibus.common.DeFiBusConstant;
import com.webank.emesher.constants.ProxyConstants;
import com.webank.emesher.core.protocol.tcp.client.session.Session;
import com.webank.eventmesh.common.protocol.tcp.Command;
import com.webank.eventmesh.common.protocol.tcp.Header;
import com.webank.eventmesh.common.protocol.tcp.OPStatus;
import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.emesher.util.ProxyUtil;
import com.webank.emesher.util.Utils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageAccessor;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SessionSender {

    private final Logger messageLogger = LoggerFactory.getLogger("message");
    private final Logger logger = LoggerFactory.getLogger(SessionSender.class);

    private Session session;

    public long createTime = System.currentTimeMillis();

    public AtomicLong upMsgs = new AtomicLong(0);

    public AtomicLong failMsgCount = new AtomicLong(0);

    private static final int TRY_PERMIT_TIME_OUT = 5;

    @Override
    public String toString() {
        return "SessionSender{upstreamBuff=" + upstreamBuff.availablePermits() +
                ",upMsgs=" + upMsgs.longValue() +
                ",failMsgCount=" + failMsgCount.longValue() +
                ",createTime=" + DateFormatUtils.format(createTime, ProxyConstants.DATE_FORMAT) + '}';
    }

    public Semaphore getUpstreamBuff() {
        return upstreamBuff;
    }

    private Semaphore upstreamBuff ;

    public SessionSender(Session session) {
        this.session = session;
        this.upstreamBuff = new Semaphore(session.getAccessConfiguration().proxyTcpSessionUpstreamBufferSize);
    }

    public ProxyTcpSendResult send(Header header, Message msg, SendCallback sendCallback, long startTime, long taskExecuteTime) {
        try {
            if (upstreamBuff.tryAcquire(TRY_PERMIT_TIME_OUT, TimeUnit.MILLISECONDS)) {
                upMsgs.incrementAndGet();
                UpStreamMsgContext upStreamMsgContext = null;
                Command cmd = header.getCommand();
                if (Command.REQUEST_TO_SERVER == cmd) {
                    long ttl = msg.getProperty(DeFiBusConstant.PROPERTY_MESSAGE_TTL) != null ? Long.valueOf(msg.getProperty
                            (DeFiBusConstant.PROPERTY_MESSAGE_TTL)) : ProxyConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS;
                    upStreamMsgContext = new UpStreamMsgContext(header.getSeq(), session, msg);
                    session.getClientGroupWrapper().get().request(upStreamMsgContext, sendCallback, initSyncRRCallback(header, startTime, taskExecuteTime), ttl);
                } else if (Command.RESPONSE_TO_SERVER == cmd) {
                    String cluster = msg.getUserProperty(DeFiBusConstant.PROPERTY_MESSAGE_CLUSTER);
                    if (!StringUtils.isEmpty(cluster)) {
                        String replyTopic = DeFiBusConstant.RR_REPLY_TOPIC;
                        replyTopic = cluster + "-" + replyTopic;
                        msg.setTopic(replyTopic);
                    }

                    //for rocketmq
                    MessageAccessor.putProperty(msg, MessageConst.PROPERTY_MESSAGE_TYPE, MixAll.REPLY_MESSAGE_FLAG);
                    MessageAccessor.putProperty(msg, MessageConst.PROPERTY_CORRELATION_ID, msg.getProperty(DeFiBusConstant.PROPERTY_RR_REQUEST_ID));
                    MessageAccessor.putProperty(msg, MessageConst.PROPERTY_MESSAGE_REPLY_TO_CLIENT, msg.getProperty(DeFiBusConstant.PROPERTY_MESSAGE_REPLY_TO));

                    upStreamMsgContext = new UpStreamMsgContext(header.getSeq(), session, msg);
                    session.getClientGroupWrapper().get().reply(upStreamMsgContext);
                    upstreamBuff.release();
                } else {
                    upStreamMsgContext = new UpStreamMsgContext(header.getSeq(), session, msg);
                    session.getClientGroupWrapper().get().send(upStreamMsgContext, sendCallback);
                }

                session.getClientGroupWrapper().get().getProxyTcpMonitor().getProxy2mqMsgNum().incrementAndGet();
            } else {
                logger.warn("send too fast,session flow control,session:{}",session.getClient());
                return new ProxyTcpSendResult(header.getSeq(), ProxyTcpSendStatus.SEND_TOO_FAST, ProxyTcpSendStatus.SEND_TOO_FAST.name());
            }
        } catch (Exception e) {
            logger.warn("SessionSender send failed", e);
            if(!(e instanceof InterruptedException)) {
                upstreamBuff.release();
            }
            failMsgCount.incrementAndGet();
            return new ProxyTcpSendResult(header.getSeq(), ProxyTcpSendStatus.OTHER_EXCEPTION, e.getCause().toString());
        }
        return new ProxyTcpSendResult(header.getSeq(), ProxyTcpSendStatus.SUCCESS, ProxyTcpSendStatus.SUCCESS.name());
    }

    private RRCallback initSyncRRCallback(Header header, long startTime, long taskExecuteTime) {
        return new RRCallback() {
            @Override
            public void onSuccess(Message msg) {
                String seq = header.getSeq();
                if (msg instanceof MessageExt) {
                    msg.putUserProperty(ProxyConstants.BORN_TIMESTAMP, String.valueOf(((MessageExt) msg)
                            .getBornTimestamp()));
                    msg.putUserProperty(ProxyConstants.STORE_TIMESTAMP, String.valueOf(((MessageExt) msg)
                            .getStoreTimestamp()));
                }
                msg.putUserProperty(ProxyConstants.RSP_MQ2PROXY_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
                msg.putUserProperty(ProxyConstants.RSP_RECEIVE_PROXY_IP, session.getAccessConfiguration().proxyServerIp);
                session.getClientGroupWrapper().get().getProxyTcpMonitor().getMq2proxyMsgNum().incrementAndGet();

                Command cmd;
                if (header.getCommand().equals(Command.REQUEST_TO_SERVER)) {
                    cmd = Command.RESPONSE_TO_CLIENT;
                } else {
                    messageLogger.error("invalid message|messageHeader={}|msg={}", header, msg);
                    return;
                }
                Package pkg = new Package();
                pkg.setHeader(new Header(cmd, OPStatus.SUCCESS.getCode(), null, seq));
                msg.putUserProperty(ProxyConstants.RSP_PROXY2C_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
                try {
                    pkg.setBody(ProxyUtil.encodeMessage(msg));
                    pkg.setHeader(new Header(cmd, OPStatus.SUCCESS.getCode(), null, seq));
                } catch (Exception e) {
                    pkg.setHeader(new Header(cmd, OPStatus.FAIL.getCode(), null, seq));
                } finally {
                    Utils.writeAndFlush(pkg, startTime, taskExecuteTime, session.getContext(), session);
                    //session.write2Client(pkg);
                }
            }

            @Override
            public void onException(Throwable e) {
                messageLogger.error("exception occur while sending RR message|user={}", session.getClient(), new Exception(e));
            }
        };
    }
}
