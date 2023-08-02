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

package org.apache.eventmesh.runtime.core.protocol.tcp.producer;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.tcp.session.Session;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.TraceUtils;
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


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SessionSender {

    private static final Logger MESSAGE_LOGGER = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);

    private final transient Session session;
    private EventMeshProducer eventMeshProducer;

    public final transient long createTime = System.currentTimeMillis();

    public final transient AtomicLong upMsgs = new AtomicLong(0);

    public final transient AtomicLong failMsgCount = new AtomicLong(0);

    public static final int TRY_PERMIT_TIME_OUT = 5;

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

    private final Semaphore upstreamBuff;

    public SessionSender(Session session, EventMeshProducer eventMeshProducer) {
        this.session = session;
        this.eventMeshProducer = eventMeshProducer;
        this.upstreamBuff = new Semaphore(session.getEventMeshTCPConfiguration().getEventMeshTcpSessionUpstreamBufferSize());
    }

    public EventMeshTcpSendResult send(Header header, CloudEvent event, SendCallback sendCallback, long startTime,
        long taskExecuteTime) {
        try {
            if (upstreamBuff.tryAcquire(TRY_PERMIT_TIME_OUT, TimeUnit.MILLISECONDS)) {
                upMsgs.incrementAndGet();
                UpStreamMsgContext upStreamMsgContext;
                Command cmd = header.getCmd();

                String protocolVersion = header.getProperty(Constants.PROTOCOL_VERSION).toString();

                long ttl = EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS;
                if (Command.REQUEST_TO_SERVER == cmd) {
                    if (event.getExtension(EventMeshConstants.PROPERTY_MESSAGE_TTL) != null) {
                        ttl = Long.parseLong((String) Objects.requireNonNull(
                            event.getExtension(EventMeshConstants.PROPERTY_MESSAGE_TTL)));
                    }
                    upStreamMsgContext = new UpStreamMsgContext(session, event, header, startTime, taskExecuteTime);

                    Span span = TraceUtils.prepareClientSpan(EventMeshUtil.getCloudEventExtensionMap(protocolVersion,
                            event),
                        EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_CLIENT_SPAN, false);
                    try {
                        eventMeshProducer.request(upStreamMsgContext, initSyncRRCallback(header, startTime, taskExecuteTime, event), ttl);
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
                    eventMeshProducer.reply(upStreamMsgContext, new SendCallback() {
                        @Override
                        public void onSuccess(SendResult sendResult) {

                        }

                        @Override
                        public void onException(OnExceptionContext context) {
                            String bizSeqNo = (String) upStreamMsgContext.getEvent()
                                    .getExtension(EventMeshConstants.PROPERTY_MESSAGE_KEYS);
                            log.error("reply err! topic:{}, bizSeqNo:{}, client:{}",
                                    upStreamMsgContext.getEvent().getSubject(), bizSeqNo,
                                    session.getClient(), context.getException());
                        }
                    });

                    upstreamBuff.release();
                } else {
                    upStreamMsgContext = new UpStreamMsgContext(session, event, header, startTime, taskExecuteTime);

                    Span span = TraceUtils.prepareClientSpan(EventMeshUtil.getCloudEventExtensionMap(protocolVersion,
                            event),
                        EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_CLIENT_SPAN, false);
                    try {
                        eventMeshProducer.send(upStreamMsgContext, sendCallback);
                    } finally {
                        TraceUtils.finishSpan(span, event);
                    }
                }

                Objects.requireNonNull(session.getSessionMap().get())
                    .getEventMeshTcpMonitor()
                    .getTcpSummaryMetrics()
                    .getEventMesh2mqMsgNum()
                    .incrementAndGet();
            } else {
                log.warn("send too fast,session flow control,session:{}", session.getClient());
                return new EventMeshTcpSendResult(header.getSeq(), EventMeshTcpSendStatus.SEND_TOO_FAST,
                    EventMeshTcpSendStatus.SEND_TOO_FAST.name());
            }
        } catch (Exception e) {
            log.warn("SessionSender send failed", e);
            if (!(e instanceof InterruptedException)) {
                upstreamBuff.release();
            }
            failMsgCount.incrementAndGet();
            return new EventMeshTcpSendResult(header.getSeq(), EventMeshTcpSendStatus.OTHER_EXCEPTION,
                e.getCause().toString());
        }
        return new EventMeshTcpSendResult(header.getSeq(), EventMeshTcpSendStatus.SUCCESS,
            EventMeshTcpSendStatus.SUCCESS.name());
    }

    private RequestReplyCallback initSyncRRCallback(Header header, long startTime, long taskExecuteTime,
        CloudEvent cloudEvent) {
        return new RequestReplyCallback() {
            @Override
            public void onSuccess(CloudEvent event) {
                String seq = header.getSeq();
                // TODO: How to assign values here
                event = CloudEventBuilder.from(event)
                    .withExtension(EventMeshConstants.RSP_MQ2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                    .withExtension(EventMeshConstants.RSP_RECEIVE_EVENTMESH_IP,
                        session.getEventMeshTCPConfiguration().getEventMeshServerIp())
                    .build();
                Objects.requireNonNull(session.getSessionMap().get())
                    .getEventMeshTcpMonitor().getTcpSummaryMetrics().getMq2eventMeshMsgNum()
                    .incrementAndGet();

                Command cmd;
                if (Command.REQUEST_TO_SERVER == header.getCmd()) {
                    cmd = Command.RESPONSE_TO_CLIENT;
                } else {
                    MESSAGE_LOGGER.error("invalid message|messageHeader={}|event={}", header, event);
                    return;
                }
                event = CloudEventBuilder.from(event)
                    .withExtension(EventMeshConstants.RSP_EVENTMESH2C_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                    .build();
                String protocolType = Objects.requireNonNull(event.getExtension(Constants.PROTOCOL_TYPE)).toString();

                ProtocolAdaptor<ProtocolTransportObject> protocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);

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
                MESSAGE_LOGGER.error("exception occur while sending RR message|user={}", session.getClient(),
                    new Exception(e));

                TraceUtils.finishSpanWithException(session.getContext(), cloudEvent,
                    "exception occur while sending RR message", e);
            }
        };
    }

    public AtomicLong getFailMsgCount() {
        return failMsgCount;
    }
}
