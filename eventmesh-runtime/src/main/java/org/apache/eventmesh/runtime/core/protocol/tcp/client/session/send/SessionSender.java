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

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.trace.TraceUtils;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.Utils;
import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.opentelemetry.api.trace.Span;

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
        return "SessionSender{upstreamBuff=" + upstreamBuff.availablePermits()
            +
            ",upMsgs=" + upMsgs.longValue()
            +
            ",failMsgCount=" + failMsgCount.longValue()
            +
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
                Command cmd = header.getCmd();

                String protocolVersion = header.getProperty(Constants.PROTOCOL_VERSION).toString();

                long ttl = EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS;
                if (Command.REQUEST_TO_SERVER == cmd) {
                    if (event.getExtension(EventMeshConstants.PROPERTY_MESSAGE_TTL) != null) {
                        ttl = Long.parseLong((String) Objects.requireNonNull(
                            event.getExtension(EventMeshConstants.PROPERTY_MESSAGE_TTL)));
                    }
                    upStreamMsgContext = new UpStreamMsgContext(session, event, header, startTime, taskExecuteTime);

                    Span span = TraceUtils.prepareClientSpan(EventMeshUtil.getCloudEventExtensionMap(protocolVersion, event),
                        EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_CLIENT_SPAN, false);
                    try {
                        session.getClientGroupWrapper().get()
                            .request(upStreamMsgContext, initSyncRRCallback(header,
                                startTime, taskExecuteTime, event), ttl);
                        upstreamBuff.release();
                    } finally {
                        TraceUtils.finishSpan(span, event);
                    }
                } else if (Command.RESPONSE_TO_SERVER == cmd) {
                    String cluster = (String) event.getExtension(EventMeshConstants.PROPERTY_MESSAGE_CLUSTER);
                    if (!StringUtils.isEmpty(cluster)) {
                        String replyTopic = EventMeshConstants.RR_REPLY_TOPIC;
                        replyTopic = cluster + "-" + replyTopic;
                        event = CloudEventBuilder.from(event).withSubject(replyTopic).build();
                    }

                    upStreamMsgContext = new UpStreamMsgContext(session, event, header, startTime, taskExecuteTime);
                    session.getClientGroupWrapper().get().reply(upStreamMsgContext);
                    upstreamBuff.release();
                } else {
                    upStreamMsgContext = new UpStreamMsgContext(session, event, header, startTime, taskExecuteTime);

                    Span span = TraceUtils.prepareClientSpan(EventMeshUtil.getCloudEventExtensionMap(protocolVersion, event),
                        EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_CLIENT_SPAN, false);
                    try {
                        session.getClientGroupWrapper().get()
                            .send(upStreamMsgContext, sendCallback);
                    } finally {
                        TraceUtils.finishSpan(span, event);
                    }
                }

                session.getClientGroupWrapper().get().getEventMeshTcpMonitor().getTcpSummaryMetrics().getEventMesh2mqMsgNum().incrementAndGet();
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

    private RequestReplyCallback initSyncRRCallback(Header header, long startTime, long taskExecuteTime, CloudEvent cloudEvent) {
        return new RequestReplyCallback() {
            @Override
            public void onSuccess(CloudEvent event) {
                String seq = header.getSeq();
                // TODO: How to assign values here
                event = CloudEventBuilder.from(event)
                    .withExtension(EventMeshConstants.RSP_MQ2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                    .withExtension(EventMeshConstants.RSP_RECEIVE_EVENTMESH_IP, session.getEventMeshTCPConfiguration().eventMeshServerIp)
                    .build();
                session.getClientGroupWrapper().get().getEventMeshTcpMonitor().getTcpSummaryMetrics().getMq2eventMeshMsgNum().incrementAndGet();

                Command cmd;
                if (header.getCmd().equals(Command.REQUEST_TO_SERVER)) {
                    cmd = Command.RESPONSE_TO_CLIENT;
                } else {
                    messageLogger.error("invalid message|messageHeader={}|event={}", header, event);
                    return;
                }
                event = CloudEventBuilder.from(event)
                    .withExtension(EventMeshConstants.RSP_EVENTMESH2C_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                    .build();
                String protocolType = Objects.requireNonNull(event.getExtension(Constants.PROTOCOL_TYPE)).toString();

                ProtocolAdaptor protocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);

                Package pkg = new Package();

                try {
                    pkg = (Package) protocolAdaptor.fromCloudEvent(event);
                    pkg.setHeader(new Header(cmd, OPStatus.SUCCESS.getCode(), null, seq));
                    pkg.getHeader().putProperty(Constants.PROTOCOL_TYPE, protocolType);
                } catch (Exception e) {
                    pkg.setHeader(new Header(cmd, OPStatus.FAIL.getCode(), null, seq));
                } finally {
                    Utils.writeAndFlush(pkg, startTime, taskExecuteTime, session.getContext(), session);

                    TraceUtils.finishSpan(session.getContext(), event);
                }
            }

            @Override
            public void onException(Throwable e) {
                messageLogger.error("exception occur while sending RR message|user={}", session.getClient(), new Exception(e));

                TraceUtils.finishSpanWithException(session.getContext(), cloudEvent, "exception occur while sending RR message", e);
            }
        };
    }
}
