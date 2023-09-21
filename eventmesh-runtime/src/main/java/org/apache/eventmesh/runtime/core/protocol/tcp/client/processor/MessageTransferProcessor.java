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

package org.apache.eventmesh.runtime.core.protocol.tcp.client.processor;

import static org.apache.eventmesh.common.protocol.tcp.Command.RESPONSE_TO_SERVER;
import static org.apache.eventmesh.runtime.core.protocol.tcp.client.session.send.SessionSender.TRY_PERMIT_TIME_OUT;

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
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.send.EventMeshTcpSendResult;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.send.EventMeshTcpSendStatus;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.send.UpStreamMsgContext;
import org.apache.eventmesh.runtime.trace.AttributeKeys;
import org.apache.eventmesh.runtime.trace.SpanKey;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.apache.eventmesh.runtime.util.TraceUtils;
import org.apache.eventmesh.runtime.util.Utils;
import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageTransferProcessor implements TcpProcessor {

    private static final Logger MESSAGE_LOGGER = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);
    private EventMeshTCPServer eventMeshTCPServer;
    private final Acl acl;

    private Package pkg;
    private ChannelHandlerContext ctx;
    private Session session;
    private long startTime;

    public MessageTransferProcessor(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
        this.acl = eventMeshTCPServer.getAcl();
    }

    @Override
    public void process(final Package pkg, final ChannelHandlerContext ctx, long startTime) {
        Session session = eventMeshTCPServer.getClientSessionGroupMapping().getSession(ctx);

        this.pkg = pkg;
        this.ctx = ctx;
        this.session = session;
        this.startTime = startTime;

        long taskExecuteTime = System.currentTimeMillis();
        Command cmd = pkg.getHeader().getCmd();

        try {
            if (eventMeshTCPServer.getEventMeshTCPConfiguration().isEventMeshServerTraceEnable()
                && RESPONSE_TO_SERVER != cmd) {
                // attach the span to the server context
                Span span = TraceUtils.prepareServerSpan(pkg.getHeader().getProperties(),
                    EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_SERVER_SPAN,
                    startTime, TimeUnit.MILLISECONDS, true);
                Context context = Context.current().with(SpanKey.SERVER_KEY, span);
                // put the context in channel
                ctx.channel().attr(AttributeKeys.SERVER_CONTEXT).set(context);
            }
        } catch (Exception ex) {
            log.warn("upload trace fail in MessageTransferTask[server-span-start]", ex);
        }

        Command replyCmd = getReplyCmd(cmd);
        Package msg = new Package();

        EventMeshTcpSendResult sendStatus;
        CloudEvent event = null;

        try {
            String protocolType = "eventmeshmessage";
            if (pkg.getHeader().getProperties() != null
                && pkg.getHeader().getProperty(Constants.PROTOCOL_TYPE) != null) {
                protocolType = (String) pkg.getHeader().getProperty(Constants.PROTOCOL_TYPE);
            }
            ProtocolAdaptor<ProtocolTransportObject> protocolAdaptor =
                ProtocolPluginFactory.getProtocolAdaptor(protocolType);
            event = protocolAdaptor.toCloudEvent(pkg);

            if (event == null) {
                throw new Exception("event is null");
            }

            String content = new String(Objects.requireNonNull(event.getData()).toBytes(), StandardCharsets.UTF_8);
            int eventMeshEventSize = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshEventSize();
            if (content.length() > eventMeshEventSize) {
                throw new Exception("event size exceeds the limit: " + eventMeshEventSize);
            }

            // do acl check in sending msg
            if (eventMeshTCPServer.getEventMeshTCPConfiguration().isEventMeshServerSecurityEnable()) {
                String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
                this.acl.doAclCheckInTcpSend(remoteAddr, session.getClient(), event.getSubject(), cmd.getValue());
            }

            if (!eventMeshTCPServer.getRateLimiter()
                .tryAcquire(TRY_PERMIT_TIME_OUT, TimeUnit.MILLISECONDS)) {

                msg.setHeader(new Header(replyCmd, OPStatus.FAIL.getCode(), "Tps overload, global flow control", pkg.getHeader().getSeq()));
                ctx.writeAndFlush(msg).addListener(
                    (ChannelFutureListener) future -> Utils.logSucceedMessageFlow(msg, session.getClient(), startTime, taskExecuteTime));

                TraceUtils.finishSpanWithException(ctx, event, "Tps overload, global flow control", null);

                log.warn("======Tps overload, global flow control, rate:{}! PLEASE CHECK!========", eventMeshTCPServer.getRateLimiter().getRate());
                return;
            }

            synchronized (session) {
                long sendTime = System.currentTimeMillis();
                event = addTimestamp(event, cmd, sendTime);

                sendStatus = session
                    .upstreamMsg(pkg.getHeader(), event,
                        createSendCallback(replyCmd, taskExecuteTime, event),
                        startTime, taskExecuteTime);

                if (StringUtils.equals(EventMeshTcpSendStatus.SUCCESS.name(),
                    sendStatus.getSendStatus().name())) {
                    MESSAGE_LOGGER.info("pkg|eventMesh2mq|cmd={}|Msg={}|user={}|wait={}ms|cost={}ms",
                        cmd, event,
                        session.getClient(), taskExecuteTime - startTime, sendTime - startTime);
                } else {
                    throw new Exception(sendStatus.getDetail());
                }
            }
        } catch (Exception e) {
            log.error("MessageTransferTask failed|cmd={}|event={}|user={}", cmd, event,
                session.getClient(),
                e);

            if (cmd != RESPONSE_TO_SERVER) {
                msg.setHeader(
                    new Header(replyCmd, OPStatus.FAIL.getCode(), e.toString(),
                        pkg.getHeader()
                            .getSeq()));
                Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(), session);

                if (event != null) {
                    TraceUtils.finishSpanWithException(ctx, event, "MessageTransferTask failed", e);
                }
            }
        }
    }

    private CloudEvent addTimestamp(CloudEvent event, Command cmd, long sendTime) {
        if (cmd == RESPONSE_TO_SERVER) {
            return buildCloudEventWithTimestamps(event,
                EventMeshConstants.RSP_C2EVENTMESH_TIMESTAMP,
                EventMeshConstants.RSP_EVENTMESH2MQ_TIMESTAMP, sendTime,
                EventMeshConstants.RSP_SEND_EVENTMESH_IP);
        } else {
            return buildCloudEventWithTimestamps(event,
                EventMeshConstants.REQ_C2EVENTMESH_TIMESTAMP,
                EventMeshConstants.REQ_EVENTMESH2MQ_TIMESTAMP, sendTime,
                EventMeshConstants.REQ_SEND_EVENTMESH_IP);
        }
    }

    private CloudEvent buildCloudEventWithTimestamps(CloudEvent event, String client2EventMeshTime,
        String eventMesh2MqTime, long sendTime, String eventMeshIP) {
        return CloudEventBuilder.from(event)
            .withExtension(client2EventMeshTime, String.valueOf(startTime))
            .withExtension(eventMesh2MqTime, String.valueOf(sendTime))
            .withExtension(eventMeshIP, eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshServerIp())
            .build();
    }

    private Command getReplyCmd(Command cmd) {
        switch (cmd) {
            case REQUEST_TO_SERVER:
                return Command.RESPONSE_TO_CLIENT;
            case ASYNC_MESSAGE_TO_SERVER:
                return Command.ASYNC_MESSAGE_TO_SERVER_ACK;
            case BROADCAST_MESSAGE_TO_SERVER:
                return Command.BROADCAST_MESSAGE_TO_SERVER_ACK;
            default:
                return cmd;
        }
    }

    protected SendCallback createSendCallback(Command replyCmd, long taskExecuteTime,
        CloudEvent event) {
        final long createTime = System.currentTimeMillis();
        Package msg = new Package();

        return new SendCallback() {

            @Override
            public void onSuccess(SendResult sendResult) {
                session.getSender().getUpstreamBuff().release();
                MESSAGE_LOGGER.info("upstreamMsg message success|user={}|callback cost={}",
                    session.getClient(),
                    System.currentTimeMillis() - createTime);
                if (replyCmd == Command.BROADCAST_MESSAGE_TO_SERVER_ACK
                    || replyCmd == Command.ASYNC_MESSAGE_TO_SERVER_ACK) {
                    msg.setHeader(
                        new Header(replyCmd, OPStatus.SUCCESS.getCode(), OPStatus.SUCCESS.getDesc(),
                            pkg.getHeader().getSeq()));
                    msg.setBody(event);
                    Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(),
                        session);

                    // async request need finish span when callback, rr request will finish span when rrCallback
                    TraceUtils.finishSpan(ctx, event);
                }
            }

            @Override
            public void onException(OnExceptionContext context) {
                session.getSender().getUpstreamBuff().release();

                // retry
                UpStreamMsgContext upStreamMsgContext = new UpStreamMsgContext(
                    session, event, pkg.getHeader(), startTime, taskExecuteTime);
                upStreamMsgContext.delay(10000);
                Objects.requireNonNull(
                    session.getClientGroupWrapper().get()).getEventMeshTcpRetryer()
                    .pushRetry(upStreamMsgContext);

                session.getSender().getFailMsgCount().incrementAndGet();
                MESSAGE_LOGGER
                    .error("upstreamMsg mq message error|user={}|callback cost={}, errMsg={}",
                        session.getClient(),
                        (System.currentTimeMillis() - createTime),
                        new Exception(context.getException()));
                msg.setHeader(
                    new Header(replyCmd, OPStatus.FAIL.getCode(), context.getException().toString(),
                        pkg.getHeader().getSeq()));
                msg.setBody(event);
                Utils.writeAndFlush(msg, startTime, taskExecuteTime, session.getContext(), session);

                // both rr request and async request need finish span when reqeust fail
                if (replyCmd != RESPONSE_TO_SERVER) {
                    // upload trace
                    TraceUtils.finishSpanWithException(ctx, event,
                        "upload trace fail in MessageTransferTask.createSendCallback.onException",
                        context.getException());
                }
            }
        };
    }
}
