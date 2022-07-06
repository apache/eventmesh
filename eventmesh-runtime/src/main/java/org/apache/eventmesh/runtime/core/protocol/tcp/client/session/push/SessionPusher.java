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

package org.apache.eventmesh.runtime.core.protocol.tcp.client.session.push;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.trace.TraceUtils;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.core.builder.CloudEventBuilder;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.opentelemetry.api.trace.Span;

public class SessionPusher {

    private final Logger messageLogger = LoggerFactory.getLogger("message");

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private AtomicLong deliveredMsgsCount = new AtomicLong(0);

    private AtomicLong deliverFailMsgsCount = new AtomicLong(0);

    private ConcurrentHashMap<String /** seq */, DownStreamMsgContext> downStreamMap = new ConcurrentHashMap<String, DownStreamMsgContext>();

    private Session session;

    public SessionPusher(Session session) {
        this.session = session;
    }

    @Override
    public String toString() {
        return "SessionPusher{"
            +
            "deliveredMsgsCount=" + deliveredMsgsCount.longValue()
            +
            ",deliverFailCount=" + deliverFailMsgsCount.longValue()
            +
            ",unAckMsg=" + CollectionUtils.size(downStreamMap) + '}';
    }

    public void push(final DownStreamMsgContext downStreamMsgContext) {
        Command cmd;
        if (SubscriptionMode.BROADCASTING.equals(downStreamMsgContext.subscriptionItem.getMode())) {
            cmd = Command.BROADCAST_MESSAGE_TO_CLIENT;
        } else if (SubscriptionType.SYNC.equals(downStreamMsgContext.subscriptionItem.getType())) {
            cmd = Command.REQUEST_TO_CLIENT;
        } else {
            cmd = Command.ASYNC_MESSAGE_TO_CLIENT;
        }

        String protocolType = Objects.requireNonNull(downStreamMsgContext.event.getExtension(Constants.PROTOCOL_TYPE)).toString();

        ProtocolAdaptor protocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);

        Package pkg = new Package();

        downStreamMsgContext.event = CloudEventBuilder.from(downStreamMsgContext.event)
            .withExtension(EventMeshConstants.REQ_EVENTMESH2C_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
            .withExtension(EventMeshConstants.RSP_SYS, session.getClient().getSubsystem())
            .withExtension(EventMeshConstants.RSP_GROUP, session.getClient().getGroup())
            .withExtension(EventMeshConstants.RSP_IDC, session.getClient().getIdc())
            .withExtension(EventMeshConstants.RSP_IP, session.getClient().getHost())
            .build();
        EventMeshMessage body = null;
        int retCode = 0;
        String retMsg = null;
        try {
            pkg = (Package) protocolAdaptor.fromCloudEvent(downStreamMsgContext.event);
            pkg.setHeader(new Header(cmd, OPStatus.SUCCESS.getCode(), null, downStreamMsgContext.seq));
            pkg.getHeader().putProperty(Constants.PROTOCOL_TYPE, protocolType);
            messageLogger.info("pkg|mq2eventMesh|cmd={}|mqMsg={}|user={}", cmd, pkg, session.getClient());
        } catch (Exception e) {
            pkg.setHeader(new Header(cmd, OPStatus.FAIL.getCode(), e.getStackTrace().toString(), downStreamMsgContext.seq));
            retCode = -1;
            retMsg = e.toString();
        } finally {
            session.getClientGroupWrapper().get().getEventMeshTcpMonitor().getTcpSummaryMetrics().getEventMesh2clientMsgNum().incrementAndGet();

            //TODO uploadTrace
            String protocolVersion = Objects.requireNonNull(downStreamMsgContext.event.getExtension(Constants.PROTOCOL_VERSION)).toString();

            Span span = TraceUtils.prepareClientSpan(EventMeshUtil.getCloudEventExtensionMap(protocolVersion, downStreamMsgContext.event),
                EventMeshTraceConstants.TRACE_DOWNSTREAM_EVENTMESH_CLIENT_SPAN, false);

            try {
                session.getContext().writeAndFlush(pkg).addListener(
                    new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (!future.isSuccess()) {
                                logger.error("downstreamMsg fail,seq:{}, retryTimes:{}, event:{}", downStreamMsgContext.seq,
                                    downStreamMsgContext.retryTimes, downStreamMsgContext.event);
                                deliverFailMsgsCount.incrementAndGet();

                                //how long to isolate client when push fail
                                long isolateTime = System.currentTimeMillis()
                                    + session.getEventMeshTCPConfiguration().eventMeshTcpPushFailIsolateTimeInMills;
                                session.setIsolateTime(isolateTime);
                                logger.warn("isolate client:{},isolateTime:{}", session.getClient(), isolateTime);

                                //retry
                                long delayTime = SubscriptionType.SYNC.equals(downStreamMsgContext.subscriptionItem.getType())
                                    ? session.getEventMeshTCPConfiguration().eventMeshTcpMsgRetrySyncDelayInMills
                                    : session.getEventMeshTCPConfiguration().eventMeshTcpMsgRetryAsyncDelayInMills;
                                downStreamMsgContext.delay(delayTime);
                                session.getClientGroupWrapper().get().getEventMeshTcpRetryer().pushRetry(downStreamMsgContext);
                            } else {
                                deliveredMsgsCount.incrementAndGet();
                                logger.info("downstreamMsg success,seq:{}, retryTimes:{}, bizSeq:{}", downStreamMsgContext.seq,
                                    downStreamMsgContext.retryTimes, EventMeshUtil.getMessageBizSeq(downStreamMsgContext.event));

                                if (session.isIsolated()) {
                                    logger.info("cancel isolated,client:{}", session.getClient());
                                    session.setIsolateTime(System.currentTimeMillis());
                                }
                            }
                        }
                    }
                );
            } finally {
                TraceUtils.finishSpan(span, downStreamMsgContext.event);
            }

        }
    }

    public void unAckMsg(String seq, DownStreamMsgContext downStreamMsgContext) {
        downStreamMap.put(seq, downStreamMsgContext);
        logger.info("put msg in unAckMsg,seq:{},unAckMsgSize:{}", seq, getTotalUnackMsgs());
    }

    public int getTotalUnackMsgs() {
        return downStreamMap.size();
    }

    public ConcurrentHashMap<String, DownStreamMsgContext> getUnAckMsg() {
        return downStreamMap;
    }

    public AtomicLong getDeliveredMsgsCount() {
        return deliveredMsgsCount;
    }

    public AtomicLong getDeliverFailMsgsCount() {
        return deliverFailMsgsCount;
    }
}
