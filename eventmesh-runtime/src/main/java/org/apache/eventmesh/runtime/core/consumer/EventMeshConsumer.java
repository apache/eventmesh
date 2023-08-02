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

package org.apache.eventmesh.runtime.core.consumer;

import static org.apache.eventmesh.runtime.constants.EventMeshConstants.CONSUMER_GROUP;
import static org.apache.eventmesh.runtime.constants.EventMeshConstants.EVENT_MESH_IDC;
import static org.apache.eventmesh.runtime.constants.EventMeshConstants.INSTANCE_NAME;
import static org.apache.eventmesh.runtime.constants.EventMeshConstants.IS_BROADCAST;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.core.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.producer.ProducerGroupConf;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.push.HTTPPushRequestHandler;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.push.PushRequestContext;
import org.apache.eventmesh.runtime.core.protocol.http.producer.SendMessageContext;
import org.apache.eventmesh.runtime.core.protocol.tcp.consumer.dispatch.DownstreamDispatchStrategy;
import org.apache.eventmesh.runtime.core.protocol.tcp.consumer.push.DownStreamMsgContext;
import org.apache.eventmesh.runtime.core.protocol.tcp.session.Session;
import org.apache.eventmesh.runtime.core.protocol.tcp.session.SessionManager;
import org.apache.eventmesh.runtime.metrics.tcp.EventMeshTcpMonitor;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.TraceUtils;
import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.opentelemetry.api.trace.Span;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshConsumer {
    public final Logger messageLogger = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);

    private EventMeshHTTPServer eventMeshHTTPServer;
    private EventMeshTCPServer eventMeshTCPServer;

    private final AtomicBoolean started4Persistent = new AtomicBoolean(Boolean.FALSE);
    private final AtomicBoolean started4Broadcast = new AtomicBoolean(Boolean.FALSE);
    private final AtomicBoolean inited4Persistent = new AtomicBoolean(Boolean.FALSE);
    private final AtomicBoolean inited4Broadcast = new AtomicBoolean(Boolean.FALSE);

    private ConsumerGroupConf consumerGroupConf;

    private HTTPPushRequestHandler httpPushRequestHandler;

    private final MQConsumerWrapper persistentMqConsumer;
    private final MQConsumerWrapper broadcastMqConsumer;

    public EventMeshConsumer(EventMeshHTTPServer eventMeshHTTPServer, ConsumerGroupConf consumerGroupConf) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.consumerGroupConf = consumerGroupConf;
        this.persistentMqConsumer = new MQConsumerWrapper(eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshStoragePluginType());
        this.broadcastMqConsumer = new MQConsumerWrapper(eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshStoragePluginType());
    }

    public EventMeshConsumer(EventMeshTCPServer eventMeshTCPServer, ConsumerGroupConf consumerGroupConf) {
        this.eventMeshTCPServer = eventMeshTCPServer;
        this.consumerGroupConf = consumerGroupConf;
        this.persistentMqConsumer = new MQConsumerWrapper(eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshStoragePluginType());
        this.broadcastMqConsumer = new MQConsumerWrapper(eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshStoragePluginType());
    }

    public synchronized void init() throws Exception {
        httpPushRequestHandler = new HTTPPushRequestHandler(this);
        Properties keyValue = new Properties();
        keyValue.put(IS_BROADCAST, "false");
        keyValue.put(CONSUMER_GROUP, consumerGroupConf.getGroupName());
        keyValue.put(EVENT_MESH_IDC, eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIDC());
        keyValue.put(INSTANCE_NAME, EventMeshUtil.buildMeshClientID(consumerGroupConf.getGroupName(),
                eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshCluster()));
        persistentMqConsumer.init(keyValue);

        persistentMqConsumer.registerEventListener((event, context) -> {
            String protocolVersion =
                    Objects.requireNonNull(event.getSpecVersion()).toString();

            Span span = TraceUtils.prepareServerSpan(
                    EventMeshUtil.getCloudEventExtensionMap(protocolVersion, event),
                    EventMeshTraceConstants.TRACE_DOWNSTREAM_EVENTMESH_SERVER_SPAN, false);
            try {
                String topic = event.getSubject();
                String bizSeqNo = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.BIZSEQNO.getKey())).toString();
                String uniqueId = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.UNIQUEID.getKey())).toString();

                event = CloudEventBuilder.from(event)
                        .withExtension(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                        .withExtension(EventMeshConstants.REQ_RECEIVE_EVENTMESH_IP,
                                eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshServerIp())
                        .build();
                if (messageLogger.isDebugEnabled()) {
                    messageLogger.debug("message|mq2eventMesh|topic={}|event={}", topic, event);
                } else {
                    messageLogger.info("message|mq2eventMesh|topic={}|bizSeqNo={}|uniqueId={}", topic, bizSeqNo, uniqueId);
                }

                ConsumerGroupTopicConf currentTopicConfig = MapUtils.getObject(consumerGroupConf.getConsumerGroupTopicConfMapping(),
                        topic, null);
                EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext = (EventMeshAsyncConsumeContext) context;

                if (currentTopicConfig == null) {
                    try {
                        sendMessageBack(event, uniqueId, bizSeqNo);
                        log.warn("no ConsumerGroupTopicConf found, sendMessageBack success, consumerGroup:{}, topic:{}, bizSeqNo={}, uniqueId={}",
                                consumerGroupConf.getGroupName(), topic, bizSeqNo, uniqueId);
                    } catch (Exception ex) {
                        log.warn("sendMessageBack fail, consumerGroup:{}, topic:{}, bizSeqNo={}, uniqueId={}",
                                consumerGroupConf.getGroupName(), topic, bizSeqNo, uniqueId, ex);
                    }
                    eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                    return;
                }

                SubscriptionItem subscriptionItem =
                        consumerGroupConf.getConsumerGroupTopicConfMapping().get(topic).getSubscriptionItem();
                PushRequestContext pushRequestContext = new PushRequestContext(
                        EventMeshUtil.buildPushMsgSeqNo(),
                        consumerGroupConf.getGroupName(),
                        EventMeshConsumer.this,
                        topic, event, subscriptionItem, eventMeshAsyncConsumeContext.getAbstractContext(),
                        consumerGroupConf, eventMeshHTTPServer, bizSeqNo, uniqueId, currentTopicConfig);

                if (httpPushRequestHandler.push(pushRequestContext)) {
                    eventMeshAsyncConsumeContext.commit(EventMeshAction.ManualAck);
                } else {
                    try {
                        sendMessageBack(event, uniqueId, bizSeqNo);
                    } catch (Exception e) {
                        //ignore
                        log.warn("sendMessageBack fail,topic:{}, bizSeqNo={}, uniqueId={}", topic, bizSeqNo, uniqueId, e);
                    }
                    eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                }
            } finally {
                TraceUtils.finishSpan(span, event);
            }
        });


        //broadcast consumer
        Properties broadcastKeyValue = new Properties();
        broadcastKeyValue.put(IS_BROADCAST, "true");
        broadcastKeyValue.put(CONSUMER_GROUP, consumerGroupConf.getGroupName());
        broadcastKeyValue.put(EVENT_MESH_IDC, eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIDC());
        broadcastKeyValue.put(INSTANCE_NAME, EventMeshUtil.buildMeshClientID(consumerGroupConf.getGroupName(),
                eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshCluster()));
        broadcastMqConsumer.init(broadcastKeyValue);
        broadcastMqConsumer.registerEventListener((event, context) -> {
            String protocolVersion =
                    Objects.requireNonNull(event.getSpecVersion()).toString();

            Span span = TraceUtils.prepareServerSpan(
                    EventMeshUtil.getCloudEventExtensionMap(protocolVersion, event),
                    EventMeshTraceConstants.TRACE_DOWNSTREAM_EVENTMESH_SERVER_SPAN, false);
            try {

                event = CloudEventBuilder.from(event)
                        .withExtension(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP,
                                String.valueOf(System.currentTimeMillis()))
                        .withExtension(EventMeshConstants.REQ_RECEIVE_EVENTMESH_IP,
                                eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshServerIp())
                        .build();

                String topic = event.getSubject();
                String bizSeqNo = getEventExtension(event, ProtocolKey.ClientInstanceKey.BIZSEQNO.getKey());
                String uniqueId = getEventExtension(event, ProtocolKey.ClientInstanceKey.UNIQUEID.getKey());

                if (messageLogger.isDebugEnabled()) {
                    messageLogger.debug("message|mq2eventMesh|topic={}|msg={}", topic, event);
                } else {
                    messageLogger.info("message|mq2eventMesh|topic={}|bizSeqNo={}|uniqueId={}",
                            topic, bizSeqNo,
                            uniqueId);
                }

                ConsumerGroupTopicConf currentTopicConfig = MapUtils.getObject(
                        consumerGroupConf.getConsumerGroupTopicConfMapping(), topic, null);
                EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext =
                        (EventMeshAsyncConsumeContext) context;

                if (currentTopicConfig == null) {
                    log.error("no topicConfig found, consumerGroup:{} topic:{}",
                            consumerGroupConf.getGroupName(), topic);
                    try {
                        sendMessageBack(event, uniqueId, bizSeqNo);
                        eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                        return;
                    } catch (Exception ex) {
                        //ignore
                    }
                }

                SubscriptionItem subscriptionItem =
                        consumerGroupConf.getConsumerGroupTopicConfMapping().get(topic)
                                .getSubscriptionItem();
                PushRequestContext pushRequestContext =
                        new PushRequestContext(EventMeshUtil.buildPushMsgSeqNo(),
                                consumerGroupConf.getGroupName(), EventMeshConsumer.this,
                                topic, event, subscriptionItem,
                                eventMeshAsyncConsumeContext.getAbstractContext(),
                                consumerGroupConf, eventMeshHTTPServer, bizSeqNo, uniqueId,
                                currentTopicConfig);

                if (httpPushRequestHandler.push(pushRequestContext)) {
                    eventMeshAsyncConsumeContext.commit(EventMeshAction.ManualAck);
                } else {
                    try {
                        sendMessageBack(event, uniqueId, bizSeqNo);
                    } catch (Exception e) {
                        //ignore
                    }
                    eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                }
            } finally {
                TraceUtils.finishSpan(span, event);
            }
        });

        inited4Persistent.compareAndSet(false, true);
        inited4Broadcast.compareAndSet(false, true);
        log.info("EventMeshConsumer [{}] inited.............", consumerGroupConf.getGroupName());
    }

    public synchronized void initTcp() throws Exception {
        EventMeshTCPConfiguration eventMeshTCPConfiguration = eventMeshTCPServer.getEventMeshTCPConfiguration();

        Properties keyValue = new Properties();
        keyValue.put(EventMeshConstants.IS_BROADCAST, "true");
        keyValue.put(EventMeshConstants.CONSUMER_GROUP, consumerGroupConf.getGroupName());
        keyValue.put(EventMeshConstants.EVENT_MESH_IDC, eventMeshTCPConfiguration.getEventMeshIDC());
        keyValue.put(EventMeshConstants.INSTANCE_NAME,
                EventMeshUtil.buildMeshTcpClientID(consumerGroupConf.getSysId(), EventMeshConstants.PURPOSE_SUB_UPPER_CASE,
                        eventMeshTCPConfiguration.getEventMeshCluster()));
        broadcastMqConsumer.init(keyValue);

        broadcastMqConsumer.registerEventListener((event, context) -> {
            SessionManager sessionManager = eventMeshTCPServer.getSessionManager().getClientGroupMap().get(consumerGroupConf.getGroupName());
            Set<Session> groupConsumerSessions = sessionManager.getConsumerMap().getGroupConsumerSessions();
            ConcurrentHashMap<String, SubscriptionItem> subscriptions = sessionManager.getSubscriptionMap().getSubscriptions();

            String protocolVersion =
                    Objects.requireNonNull(event.getSpecVersion()).toString();

            Span span = TraceUtils.prepareServerSpan(
                    EventMeshUtil.getCloudEventExtensionMap(protocolVersion, event),
                    EventMeshTraceConstants.TRACE_DOWNSTREAM_EVENTMESH_SERVER_SPAN, false);
            try {
                EventMeshTcpMonitor metrics = eventMeshTCPServer.getMetrics();
                metrics.getTcpSummaryMetrics().getMq2eventMeshMsgNum()
                        .incrementAndGet();
                event = CloudEventBuilder.from(event)
                        .withExtension(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP,
                                String.valueOf(System.currentTimeMillis()))
                        .withExtension(EventMeshConstants.REQ_RECEIVE_EVENTMESH_IP,
                                eventMeshTCPConfiguration.getEventMeshServerIp()).build();
                String topic = event.getSubject();

                EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext =
                        (EventMeshAsyncConsumeContext) context;

                if (CollectionUtils.isEmpty(groupConsumerSessions)) {
                    if (log.isWarnEnabled()) {
                        log.warn("found no session to downstream broadcast msg");
                    }
                    eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                    return;
                }

                Iterator<Session> sessionsItr = groupConsumerSessions.iterator();

                SubscriptionItem subscriptionItem = subscriptions.get(topic);
                DownStreamMsgContext downStreamMsgContext =
                        new DownStreamMsgContext(event, null, broadcastMqConsumer,
                                eventMeshAsyncConsumeContext.getAbstractContext(), false,
                                subscriptionItem);

                while (sessionsItr.hasNext()) {
                    Session session = sessionsItr.next();

                    if (!session.isAvailable(topic)) {
                        if (log.isWarnEnabled()) {
                            log.warn("downstream broadcast msg,session is not available,client:{}",
                                    session.getClient());
                        }
                        continue;
                    }

                    downStreamMsgContext.setSession(session);

                    //downstream broadcast msg asynchronously
                    eventMeshTCPServer.getTcpThreadPoolGroup().getBroadcastMsgDownstreamExecutorService()
                            .submit(() -> {
                                //msg put in eventmesh,waiting client ack
                                session.getPusher().unAckMsg(downStreamMsgContext.getSeq(), downStreamMsgContext);
                                session.downstreamMsg(downStreamMsgContext);
                            });
                }

                eventMeshAsyncConsumeContext.commit(EventMeshAction.ManualAck);
            } finally {
                TraceUtils.finishSpan(span, event);
            }
        });


        keyValue = new Properties();
        keyValue.put(EventMeshConstants.IS_BROADCAST, "false");
        keyValue.put(EventMeshConstants.CONSUMER_GROUP, consumerGroupConf.getGroupName());
        keyValue.put(EventMeshConstants.EVENT_MESH_IDC, eventMeshTCPConfiguration.getEventMeshIDC());
        keyValue.put(EventMeshConstants.INSTANCE_NAME, EventMeshUtil
                .buildMeshTcpClientID(consumerGroupConf.getSysId(), EventMeshConstants.PURPOSE_SUB_UPPER_CASE,
                        eventMeshTCPConfiguration.getEventMeshCluster()));
        persistentMqConsumer.init(keyValue);

        persistentMqConsumer.registerEventListener((event, context) -> {
            SessionManager sessionManager = eventMeshTCPServer.getSessionManager().getClientGroupMap().get(consumerGroupConf.getGroupName());
            Set<Session> groupConsumerSessions = sessionManager.getConsumerMap().getGroupConsumerSessions();
            ConcurrentHashMap<String, SubscriptionItem> subscriptions = sessionManager.getSubscriptionMap().getSubscriptions();
            DownstreamDispatchStrategy downstreamDispatchStrategy = sessionManager.getDownstreamDispatchStrategy();

            String protocolVersion =
                    Objects.requireNonNull(event.getSpecVersion()).toString();

            Span span = TraceUtils.prepareServerSpan(
                    EventMeshUtil.getCloudEventExtensionMap(protocolVersion, event),
                    EventMeshTraceConstants.TRACE_DOWNSTREAM_EVENTMESH_SERVER_SPAN, false);

            try {
                EventMeshTcpMonitor metrics = eventMeshTCPServer.getMetrics();
                metrics.getTcpSummaryMetrics().getMq2eventMeshMsgNum()
                        .incrementAndGet();
                event = CloudEventBuilder.from(event)
                        .withExtension(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP,
                                String.valueOf(System.currentTimeMillis()))
                        .withExtension(EventMeshConstants.REQ_RECEIVE_EVENTMESH_IP,
                                eventMeshTCPConfiguration.getEventMeshServerIp()).build();
                String topic = event.getSubject();

                EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext =
                        (EventMeshAsyncConsumeContext) context;
                Session session = downstreamDispatchStrategy
                        .select(consumerGroupConf.getGroupName(), topic, groupConsumerSessions);
                String bizSeqNo = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.BIZSEQNO.getKey())).toString();
                String uniqueId = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.UNIQUEID.getKey())).toString();
                if (session == null) {
                    try {
                        Integer sendBackTimes = 0;
                        String sendBackFromEventMeshIp = "";
                        if (StringUtils.isNotBlank(Objects.requireNonNull(event.getExtension(
                                EventMeshConstants.EVENTMESH_SEND_BACK_TIMES)).toString())) {
                            sendBackTimes = (Integer) event.getExtension(
                                    EventMeshConstants.EVENTMESH_SEND_BACK_TIMES);
                        }
                        if (StringUtils.isNotBlank(Objects.requireNonNull(event.getExtension(
                                EventMeshConstants.EVENTMESH_SEND_BACK_IP)).toString())) {
                            sendBackFromEventMeshIp = (String) event.getExtension(
                                    EventMeshConstants.EVENTMESH_SEND_BACK_IP);
                        }

                        log.error(
                                "found no session to downstream msg,groupName:{}, topic:{}, "
                                        + "bizSeqNo:{}, sendBackTimes:{}, sendBackFromEventMeshIp:{}",
                                consumerGroupConf.getGroupName(), topic, bizSeqNo, sendBackTimes,
                                sendBackFromEventMeshIp);

                        int eventMeshTcpSendBackMaxTimes = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshTcpSendBackMaxTimes();
                        if (Objects.requireNonNull(sendBackTimes) >= eventMeshTcpSendBackMaxTimes) {
                            log.error("sendBack to broker over max times:{}, groupName:{}, topic:{}, " + "bizSeqNo:{}", eventMeshTcpSendBackMaxTimes,
                                    consumerGroupConf.getGroupName(), topic, bizSeqNo);
                        } else {
                            sendBackTimes++;
                            event = CloudEventBuilder.from(event)
                                    .withExtension(EventMeshConstants.EVENTMESH_SEND_BACK_TIMES,
                                            sendBackTimes.toString())
                                    .withExtension(EventMeshConstants.EVENTMESH_SEND_BACK_IP,
                                            eventMeshTCPConfiguration.getEventMeshServerIp()).build();
                            sendMessageBack(event, uniqueId, bizSeqNo);
                        }
                    } catch (Exception e) {
                        log.warn("handle msg exception when no session found", e);
                    }

                    eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                    return;
                }

                SubscriptionItem subscriptionItem = subscriptions.get(topic);
                DownStreamMsgContext downStreamMsgContext =
                        new DownStreamMsgContext(event, session, persistentMqConsumer,
                                eventMeshAsyncConsumeContext.getAbstractContext(), false,
                                subscriptionItem);
                //msg put in eventmesh,waiting client ack
                session.getPusher().unAckMsg(downStreamMsgContext.getSeq(), downStreamMsgContext);
                session.downstreamMsg(downStreamMsgContext);
                eventMeshAsyncConsumeContext.commit(EventMeshAction.ManualAck);
            } finally {
                TraceUtils.finishSpan(span, event);
            }
        });

        inited4Persistent.compareAndSet(false, true);
        inited4Broadcast.compareAndSet(false, true);
        log.info("EventMeshConsumer [{}] inited.............", consumerGroupConf.getGroupName());


    }

    private String getEventExtension(CloudEvent event, String protocolKey) {
        Object extension = event.getExtension(protocolKey);
        return Objects.isNull(extension) ? "" : extension.toString();
    }

    public synchronized void start() throws Exception {
        if (started4Persistent.get() || started4Broadcast.get()) {
            return;
        }

        persistentMqConsumer.start();
        started4Persistent.compareAndSet(false, true);
        broadcastMqConsumer.start();
        started4Broadcast.compareAndSet(false, true);
        log.info("EventMeshConsumer [{}] started.............", consumerGroupConf.getGroupName());
    }

    public synchronized void shutdown() throws Exception {
        if (!started4Persistent.get() || !started4Broadcast.get()) {
            return;
        }

        persistentMqConsumer.shutdown();
        started4Persistent.compareAndSet(true, false);
        broadcastMqConsumer.shutdown();
        started4Broadcast.compareAndSet(true, false);
        log.info("EventMeshConsumer [{}] shutdown.............", consumerGroupConf.getGroupName());
    }

    public void subscribe(SubscriptionItem subscriptionItem) throws Exception {
        if (SubscriptionMode.BROADCASTING == subscriptionItem.getMode()) {
            broadcastMqConsumer.subscribe(subscriptionItem.getTopic());

        } else {
            persistentMqConsumer.subscribe(subscriptionItem.getTopic());
        }
    }

    public void unsubscribe(SubscriptionItem subscriptionItem) throws Exception {
        if (SubscriptionMode.BROADCASTING == subscriptionItem.getMode()) {
            broadcastMqConsumer.unsubscribe(subscriptionItem.getTopic());
        } else {
            persistentMqConsumer.unsubscribe(subscriptionItem.getTopic());
        }
    }

    public void updateOffset(String topic, SubscriptionMode subscriptionMode, List<CloudEvent> events,
                             AbstractContext context) {
        if (SubscriptionMode.BROADCASTING == subscriptionMode) {
            broadcastMqConsumer.updateOffset(events, context);
        } else {
            persistentMqConsumer.updateOffset(events, context);
        }
    }

    private void sendMessageBack(final CloudEvent event, final String uniqueId, String bizSeqNo) throws Exception {

        String topic = event.getSubject();

        EventMeshProducer producer
                = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(new ProducerGroupConf(consumerGroupConf.getGroupName()));

        if (producer == null) {
            log.warn("group:{} consume fail, sendMessageBack, bizSeqNo:{}, uniqueId:{}, topic:{}",
                    consumerGroupConf.getGroupName(), bizSeqNo, uniqueId, topic);
            return;
        }

        final SendMessageContext sendMessageBackContext = new SendMessageContext(bizSeqNo, event, producer,
                eventMeshHTTPServer);

        producer.send(sendMessageBackContext, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("group:{} consume fail, sendMessageBack success, bizSeqNo:{}, uniqueId:{}, topic:{}",
                        consumerGroupConf.getGroupName(), bizSeqNo, uniqueId, topic);
            }

            @Override
            public void onException(OnExceptionContext context) {
                log.warn("consumer:{} consume fail, sendMessageBack, bizSeqNo:{}, uniqueId:{}, topic:{}",
                        consumerGroupConf.getGroupName(), bizSeqNo, uniqueId, topic);
            }
        });
    }

    public ConsumerGroupConf getConsumerGroupConf() {
        return consumerGroupConf;
    }

    public void setConsumerGroupConf(ConsumerGroupConf consumerGroupConf) {
        this.consumerGroupConf = consumerGroupConf;
    }

    public EventMeshHTTPServer getEventMeshHTTPServer() {
        return eventMeshHTTPServer;
    }

}
