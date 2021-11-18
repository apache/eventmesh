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

package org.apache.eventmesh.runtime.core.protocol.tcp.client.session.send;

import io.cloudevents.core.builder.CloudEventBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.eventmesh.api.RRCallback;
import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.cloudevents.CloudEvent;

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
                ",createTime=" + DateFormatUtils.format(createTime, EventMeshConstants.DATE_FORMAT) + '}';
    }

    public Semaphore getUpstreamBuff() {
        return upstreamBuff;
    }

    private Semaphore upstreamBuff;

    public SessionSender(Session session) {
        this.session = session;
        this.upstreamBuff = new Semaphore(session.getEventMeshTCPConfiguration().eventMeshTcpSessionUpstreamBufferSize);
    }

    public EventMeshTcpSendResult send(Header header, CloudEvent event, SendCallback sendCallback, long startTime, long taskExecuteTime) {
        try {
            if (upstreamBuff.tryAcquire(TRY_PERMIT_TIME_OUT, TimeUnit.MILLISECONDS)) {
                upMsgs.incrementAndGet();
                UpStreamMsgContext upStreamMsgContext = null;
                Command cmd = header.getCommand();
                long ttl = EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS;
                if (Command.REQUEST_TO_SERVER == cmd) {
                    if (event.getExtension(EventMeshConstants.PROPERTY_MESSAGE_TTL) != null){
                       ttl = Long.parseLong((String) Objects.requireNonNull(event.getExtension(EventMeshConstants.PROPERTY_MESSAGE_TTL)));
                    }
//                    long ttl = msg.getSystemProperties(EventMeshConstants.PROPERTY_MESSAGE_TTL) != null ? Long.parseLong(msg.getSystemProperties(EventMeshConstants.PROPERTY_MESSAGE_TTL)) : EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS;
                    upStreamMsgContext = new UpStreamMsgContext(session, event, header, startTime, taskExecuteTime);
                    session.getClientGroupWrapper().get().request(upStreamMsgContext, initSyncRRCallback(header, startTime, taskExecuteTime), ttl);
                    upstreamBuff.release();
                } else if (Command.RESPONSE_TO_SERVER == cmd) {
                    String cluster = (String)event.getExtension(EventMeshConstants.PROPERTY_MESSAGE_CLUSTER);
//                    String cluster = msg.getUserProperties(EventMeshConstants.PROPERTY_MESSAGE_CLUSTER);
                    if (!StringUtils.isEmpty(cluster)) {
                        String replyTopic = EventMeshConstants.RR_REPLY_TOPIC;
                        replyTopic = cluster + "-" + replyTopic;
                        event = CloudEventBuilder.from(event).withSubject(replyTopic).build();
//                        msg.getSystemProperties().put(Constants.PROPERTY_MESSAGE_DESTINATION, replyTopic);
//                        event(replyTopic);
                    }

//                    //for rocketmq support
//                    MessageAccessor.putProperty(msg, MessageConst.PROPERTY_MESSAGE_TYPE, MixAll.REPLY_MESSAGE_FLAG);
//                    MessageAccessor.putProperty(msg, MessageConst.PROPERTY_CORRELATION_ID, msg.getProperty(DeFiBusConstant.PROPERTY_RR_REQUEST_ID));
//                    MessageAccessor.putProperty(msg, MessageConst.PROPERTY_MESSAGE_REPLY_TO_CLIENT, msg.getProperty(DeFiBusConstant.PROPERTY_MESSAGE_REPLY_TO));

                    upStreamMsgContext = new UpStreamMsgContext(session, event, header, startTime, taskExecuteTime);
                    session.getClientGroupWrapper().get().reply(upStreamMsgContext);
                    upstreamBuff.release();
                } else {
                    upStreamMsgContext = new UpStreamMsgContext(session, event, header, startTime, taskExecuteTime);
                    session.getClientGroupWrapper().get().send(upStreamMsgContext, sendCallback);
                }

                session.getClientGroupWrapper().get().getEventMeshTcpMonitor().getEventMesh2mqMsgNum().incrementAndGet();
            } else {
                logger.warn("send too fast,session flow control,session:{}", session.getClient());
                return new EventMeshTcpSendResult(header.getSeq(), EventMeshTcpSendStatus.SEND_TOO_FAST, EventMeshTcpSendStatus.SEND_TOO_FAST.name());
            }
        } catch (Exception e) {
            logger.warn("SessionSender send failed", e);
            if (!(e instanceof InterruptedException)) {
                upstreamBuff.release();
            }
            failMsgCount.incrementAndGet();
            return new EventMeshTcpSendResult(header.getSeq(), EventMeshTcpSendStatus.OTHER_EXCEPTION, e.getCause().toString());
        }
        return new EventMeshTcpSendResult(header.getSeq(), EventMeshTcpSendStatus.SUCCESS, EventMeshTcpSendStatus.SUCCESS.name());
    }

    private RequestReplyCallback initSyncRRCallback(Header header, long startTime, long taskExecuteTime) {
        return new RequestReplyCallback() {
            @Override
            public void onSuccess(CloudEvent event) {
                String seq = header.getSeq();
                // TODO: How to assign values here
//                if (msg instanceof MessageExt) {
//                    msg.putUserProperty(EventMeshConstants.BORN_TIMESTAMP, String.valueOf(((MessageExt) msg)
//                            .getBornTimestamp()));
//                    msg.putUserProperty(EventMeshConstants.STORE_TIMESTAMP, String.valueOf(((MessageExt) msg)
//                            .getStoreTimestamp()));
//                }
                event = CloudEventBuilder.from(event)
                        .withExtension(EventMeshConstants.RSP_MQ2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                        .withExtension(EventMeshConstants.RSP_RECEIVE_EVENTMESH_IP, session.getEventMeshTCPConfiguration().eventMeshServerIp)
                        .build();
                session.getClientGroupWrapper().get().getEventMeshTcpMonitor().getMq2EventMeshMsgNum().incrementAndGet();

                Command cmd;
                if (header.getCommand().equals(Command.REQUEST_TO_SERVER)) {
                    cmd = Command.RESPONSE_TO_CLIENT;
                } else {
                    messageLogger.error("invalid message|messageHeader={}|event={}", header, event);
                    return;
                }
                Package pkg = new Package();
                pkg.setHeader(new Header(cmd, OPStatus.SUCCESS.getCode(), null, seq));
                event = CloudEventBuilder.from(event)
                        .withExtension(EventMeshConstants.RSP_EVENTMESH2C_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                        .build();
//                msg.getSystemProperties().put(EventMeshConstants.RSP_EVENTMESH2C_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
                try {
//                    pkg.setBody(EventMeshUtil.encodeMessage(msg));
                    pkg.setBody(event);
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
