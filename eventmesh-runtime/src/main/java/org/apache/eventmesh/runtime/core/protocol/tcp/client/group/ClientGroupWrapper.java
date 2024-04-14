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

package org.apache.eventmesh.runtime.core.protocol.tcp.client.group;

import org.apache.eventmesh.api.AsyncConsumeContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.core.plugin.MQProducerWrapper;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.dispatch.DownstreamDispatchStrategy;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.push.DownStreamMsgContext;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.retry.TcpRetryer;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.send.UpStreamMsgContext;
import org.apache.eventmesh.runtime.metrics.tcp.EventMeshTcpMonitor;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.HttpTinyClient;
import org.apache.eventmesh.runtime.util.TraceUtils;
import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.opentelemetry.api.trace.Span;

import com.google.common.base.Preconditions;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientGroupWrapper {

    private final String sysId;

    private String group;

    private EventMeshTCPConfiguration eventMeshTCPConfiguration;

    private final EventMeshTCPServer eventMeshTCPServer;

    private TcpRetryer tcpRetryer;

    private EventMeshTcpMonitor eventMeshTcpMonitor;

    private DownstreamDispatchStrategy downstreamDispatchStrategy;

    private final ReadWriteLock groupLock = new ReentrantReadWriteLock();

    @Getter
    private final Set<Session> groupConsumerSessions = new HashSet<Session>();

    @Getter
    private final Set<Session> groupProducerSessions = new HashSet<Session>();

    @Getter
    private final AtomicBoolean started4Persistent = new AtomicBoolean(Boolean.FALSE);

    @Getter
    private final AtomicBoolean started4Broadcast = new AtomicBoolean(Boolean.FALSE);

    @Getter
    private final AtomicBoolean inited4Persistent = new AtomicBoolean(Boolean.FALSE);

    @Getter
    private final AtomicBoolean inited4Broadcast = new AtomicBoolean(Boolean.FALSE);

    @Getter
    private final AtomicBoolean producerStarted = new AtomicBoolean(Boolean.FALSE);

    private MQConsumerWrapper persistentMsgConsumer;

    private MQConsumerWrapper broadCastMsgConsumer;

    private final ConcurrentHashMap<String, Map<String, Session>> topic2sessionInGroupMapping =
        new ConcurrentHashMap<String, Map<String, Session>>();

    private final ConcurrentHashMap<String, SubscriptionItem> subscriptions = new ConcurrentHashMap<>();

    private final MQProducerWrapper mqProducerWrapper;

    public ClientGroupWrapper(String sysId, String group,
        EventMeshTCPServer eventMeshTCPServer,
        DownstreamDispatchStrategy downstreamDispatchStrategy) {
        this.sysId = sysId;
        this.group = group;
        this.eventMeshTCPServer = eventMeshTCPServer;
        this.eventMeshTCPConfiguration = eventMeshTCPServer.getEventMeshTCPConfiguration();
        this.tcpRetryer = eventMeshTCPServer.getTcpRetryer();
        this.eventMeshTcpMonitor =
            Preconditions.checkNotNull(eventMeshTCPServer.getEventMeshTcpMonitor());
        this.downstreamDispatchStrategy = downstreamDispatchStrategy;
        this.persistentMsgConsumer = new MQConsumerWrapper(eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshStoragePluginType());
        this.broadCastMsgConsumer = new MQConsumerWrapper(eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshStoragePluginType());
        this.mqProducerWrapper = new MQProducerWrapper(eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshStoragePluginType());
    }

    public ConcurrentHashMap<String, Map<String, Session>> getTopic2sessionInGroupMapping() {
        return topic2sessionInGroupMapping;
    }

    public boolean hasSubscription(String topic) {
        boolean has = false;
        try {
            this.groupLock.readLock().lockInterruptibly();
            has = topic2sessionInGroupMapping.containsKey(topic);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("hasSubscription error! topic[{}]", topic);
        } finally {
            this.groupLock.readLock().unlock();
        }

        return has;
    }

    public boolean send(UpStreamMsgContext upStreamMsgContext, SendCallback sendCallback)
        throws Exception {
        mqProducerWrapper.send(upStreamMsgContext.getEvent(), sendCallback);
        return true;
    }

    public void request(UpStreamMsgContext upStreamMsgContext, RequestReplyCallback rrCallback,
        long timeout)
        throws Exception {
        mqProducerWrapper.request(upStreamMsgContext.getEvent(), rrCallback, timeout);
    }

    public boolean reply(UpStreamMsgContext upStreamMsgContext) throws Exception {
        mqProducerWrapper.reply(upStreamMsgContext.getEvent(), new SendCallback() {

            @Override
            public void onSuccess(SendResult sendResult) {
            }

            @Override
            public void onException(OnExceptionContext context) {
                String bizSeqNo = (String) upStreamMsgContext.getEvent()
                    .getExtension(EventMeshConstants.PROPERTY_MESSAGE_KEYS);
                log.error("reply err! topic:{}, bizSeqNo:{}, client:{}",
                    upStreamMsgContext.getEvent().getSubject(), bizSeqNo,
                    upStreamMsgContext.getSession().getClient(), context.getException());
            }
        });
        return true;
    }

    public MQProducerWrapper getMqProducerWrapper() {
        return mqProducerWrapper;
    }

    public boolean addSubscription(SubscriptionItem subscriptionItem, Session session)
        throws Exception {
        if (subscriptionItem == null) {
            log.error("addSubscription param error, subscriptionItem is null, session:{}", session);
            return false;
        }
        String topic = subscriptionItem.getTopic();
        if (session == null || !StringUtils.equalsIgnoreCase(group,
            EventMeshUtil.buildClientGroup(session.getClient().getGroup()))) {
            log.error("addSubscription param error, topic:{}, session:{}", topic, session);
            return false;
        }

        boolean r = false;
        try {
            this.groupLock.writeLock().lockInterruptibly();
            if (!topic2sessionInGroupMapping.containsKey(topic)) {
                Map<String, Session> sessions = new HashMap<>();
                topic2sessionInGroupMapping.put(topic, sessions);
            }
            Session s = topic2sessionInGroupMapping.get(topic).putIfAbsent(session.getSessionId(), session);
            if (s == null) {
                log.info("Cache session success, group:{} topic:{} client:{} sessionId:{}",
                    group, topic, session.getClient(), session.getSessionId());
            } else {
                log.warn("Session already exists in topic2sessionInGroupMapping. group:{} topic:{} client:{} sessionId:{}",
                    group, topic, session.getClient(), session.getSessionId());
            }

            subscriptions.putIfAbsent(topic, subscriptionItem);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("addSubscription error! topic:{} client:{}", topic, session.getClient(), e);
            throw new Exception("addSubscription fail");
        } finally {
            this.groupLock.writeLock().unlock();
        }
        return r;
    }

    public boolean removeSubscription(SubscriptionItem subscriptionItem, Session session) {
        if (subscriptionItem == null) {
            log.error("addSubscription param error,subscriptionItem is null, session:{}", session);
            return false;
        }
        String topic = subscriptionItem.getTopic();
        if (session == null || !StringUtils.equalsIgnoreCase(group, EventMeshUtil.buildClientGroup(session.getClient().getGroup()))) {
            log.error("removeSubscription param error, topic:{}, session:{}", topic, session);
            return false;
        }

        boolean r = false;
        try {
            this.groupLock.writeLock().lockInterruptibly();
            if (topic2sessionInGroupMapping.containsKey(topic)) {
                if (topic2sessionInGroupMapping.get(topic).remove(session.getSessionId()) != null) {
                    log.info("removeSubscription remove session success, group:{} topic:{} client:{}",
                        group, topic, session.getClient());
                } else {
                    log.warn("Not found session in cache, group:{} topic:{} client:{} sessionId:{}",
                        group, topic, session.getClient(), session.getSessionId());
                }
            }
            if (CollectionUtils.size(topic2sessionInGroupMapping.get(topic)) == 0) {
                topic2sessionInGroupMapping.remove(topic);
                subscriptions.remove(topic);

                log.info("removeSubscription remove topic success, group:{} topic:{}",
                    group, topic);
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("removeSubscription error! topic:{} client:{}", topic, session.getClient(),
                e);
        } finally {
            this.groupLock.writeLock().unlock();
        }
        return r;
    }

    public synchronized void startClientGroupProducer() throws Exception {
        if (producerStarted.get()) {
            return;
        }

        Properties keyValue = new Properties();
        keyValue.put(EventMeshConstants.PRODUCER_GROUP, group);
        keyValue.put(EventMeshConstants.INSTANCE_NAME, EventMeshUtil
            .buildMeshTcpClientID(sysId, EventMeshConstants.PURPOSE_PUB_UPPER_CASE,
                eventMeshTCPConfiguration.getEventMeshCluster()));

        keyValue.put(EventMeshConstants.EVENT_MESH_IDC, eventMeshTCPConfiguration.getEventMeshIDC());

        mqProducerWrapper.init(keyValue);
        mqProducerWrapper.start();
        producerStarted.compareAndSet(false, true);
        log.info("starting producer success, group:{}", group);
    }

    public synchronized void shutdownProducer() throws Exception {
        if (!producerStarted.get()) {
            return;
        }
        mqProducerWrapper.shutdown();
        producerStarted.compareAndSet(true, false);
        log.info("shutdown producer success for group:{}", group);
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public boolean addGroupConsumerSession(Session session) {
        if (session == null || !StringUtils.equalsIgnoreCase(group, EventMeshUtil.buildClientGroup(session.getClient().getGroup()))) {
            log.error("addGroupConsumerSession param error, session:{}", session);
            return false;
        }

        boolean r = false;
        try {
            this.groupLock.writeLock().lockInterruptibly();
            r = groupConsumerSessions.add(session);
            if (r) {
                log.info("addGroupConsumerSession success, group:{} client:{}", group, session.getClient());
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("addGroupConsumerSession error! group:{} client:{}", group, session.getClient(), e);
        } finally {
            this.groupLock.writeLock().unlock();
        }
        return r;
    }

    public boolean addGroupProducerSession(Session session) {
        if (session == null || !StringUtils.equalsIgnoreCase(group, EventMeshUtil.buildClientGroup(session.getClient().getGroup()))) {
            log.error("addGroupProducerSession param error, session:{}", session);
            return false;
        }

        boolean r = false;
        try {
            this.groupLock.writeLock().lockInterruptibly();
            r = groupProducerSessions.add(session);
            if (r) {

                log.info("addGroupProducerSession success, group:{} client:{}", group, session.getClient());
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("addGroupProducerSession error! group:{} client:{}", group, session.getClient(), e);
        } finally {
            this.groupLock.writeLock().unlock();
        }
        return r;
    }

    public boolean removeGroupConsumerSession(Session session) {
        if (session == null || !StringUtils.equalsIgnoreCase(group, EventMeshUtil.buildClientGroup(session.getClient().getGroup()))) {
            log.error("removeGroupConsumerSession param error, session:{}", session);
            return false;
        }

        boolean r = false;
        try {
            this.groupLock.writeLock().lockInterruptibly();
            r = groupConsumerSessions.remove(session);
            if (r) {
                log.info("removeGroupConsumerSession success, group:{} client:{}", group, session.getClient());
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("removeGroupConsumerSession error! group:{} client:{}", group, session.getClient(), e);
        } finally {
            this.groupLock.writeLock().unlock();
        }
        return r;
    }

    public boolean removeGroupProducerSession(Session session) {
        if (session == null || !StringUtils.equalsIgnoreCase(group, EventMeshUtil.buildClientGroup(session.getClient().getGroup()))) {
            log.error("removeGroupProducerSession param error, session:{}", session);
            return false;
        }

        boolean r = false;
        try {
            this.groupLock.writeLock().lockInterruptibly();
            r = groupProducerSessions.remove(session);
            if (r) {

                log.info("removeGroupProducerSession success, group:{} client:{}", group, session.getClient());
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("removeGroupProducerSession error! group:{} client:{}", group, session.getClient(), e);
        } finally {
            this.groupLock.writeLock().unlock();
        }

        return r;
    }

    public synchronized void initClientGroupPersistentConsumer() throws Exception {
        if (inited4Persistent.get()) {
            return;
        }

        Properties keyValue = new Properties();
        keyValue.put(EventMeshConstants.IS_BROADCAST, "false");
        keyValue.put(EventMeshConstants.CONSUMER_GROUP, group);
        keyValue.put(EventMeshConstants.EVENT_MESH_IDC, eventMeshTCPConfiguration.getEventMeshIDC());
        keyValue.put(EventMeshConstants.INSTANCE_NAME, EventMeshUtil
            .buildMeshTcpClientID(sysId, EventMeshConstants.PURPOSE_SUB_UPPER_CASE,
                eventMeshTCPConfiguration.getEventMeshCluster()));

        persistentMsgConsumer.init(keyValue);

        EventListener listener = (CloudEvent event, AsyncConsumeContext context) -> {
            String protocolVersion =
                Objects.requireNonNull(event.getSpecVersion()).toString();

            Span span = TraceUtils.prepareServerSpan(
                EventMeshUtil.getCloudEventExtensionMap(protocolVersion, event),
                EventMeshTraceConstants.TRACE_DOWNSTREAM_EVENTMESH_SERVER_SPAN, false);

            try {
                eventMeshTcpMonitor.getTcpSummaryMetrics().getMq2eventMeshMsgNum()
                    .incrementAndGet();
                event = CloudEventBuilder.from(event)
                    .withExtension(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP,
                        String.valueOf(System.currentTimeMillis()))
                    .withExtension(EventMeshConstants.REQ_RECEIVE_EVENTMESH_IP,
                        eventMeshTCPConfiguration.getEventMeshServerIp())
                    .build();
                String topic = event.getSubject();

                EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext =
                    (EventMeshAsyncConsumeContext) context;
                Session session = downstreamDispatchStrategy
                    .select(group, topic, groupConsumerSessions);
                String bizSeqNo = EventMeshUtil.getMessageBizSeq(event);
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
                            group, topic, bizSeqNo, sendBackTimes,
                            sendBackFromEventMeshIp);

                        int eventMeshTcpSendBackMaxTimes = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshTcpSendBackMaxTimes();
                        if (Objects.requireNonNull(sendBackTimes) >= eventMeshTcpSendBackMaxTimes) {
                            log.error("sendBack to broker over max times:{}, groupName:{}, topic:{}, " + "bizSeqNo:{}", eventMeshTcpSendBackMaxTimes,
                                group, topic, bizSeqNo);
                        } else {
                            sendBackTimes++;
                            event = CloudEventBuilder.from(event)
                                .withExtension(EventMeshConstants.EVENTMESH_SEND_BACK_TIMES,
                                    sendBackTimes.toString())
                                .withExtension(EventMeshConstants.EVENTMESH_SEND_BACK_IP,
                                    eventMeshTCPConfiguration.getEventMeshServerIp())
                                .build();
                            sendMsgBackToBroker(event, bizSeqNo);
                        }
                    } catch (Exception e) {
                        log.warn("handle msg exception when no session found", e);
                    }

                    eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                    return;
                }

                SubscriptionItem subscriptionItem = subscriptions.get(topic);
                DownStreamMsgContext downStreamMsgContext =
                    new DownStreamMsgContext(event, session, persistentMsgConsumer,
                        eventMeshAsyncConsumeContext.getAbstractContext(), false,
                        subscriptionItem);
                // msg put in eventmesh,waiting client ack
                session.getPusher().unAckMsg(downStreamMsgContext.seq, downStreamMsgContext);
                session.downstreamMsg(downStreamMsgContext);
                eventMeshAsyncConsumeContext.commit(EventMeshAction.ManualAck);
            } finally {
                TraceUtils.finishSpan(span, event);
            }
        };
        persistentMsgConsumer.registerEventListener(listener);

        inited4Persistent.compareAndSet(false, true);
        log.info("init persistentMsgConsumer success, group:{}", group);
    }

    public synchronized void startClientGroupPersistentConsumer() throws Exception {
        if (started4Persistent.get()) {
            return;
        }
        persistentMsgConsumer.start();
        started4Persistent.compareAndSet(false, true);
        log.info("starting persistentMsgConsumer success, group:{}", group);
    }

    public synchronized void initClientGroupBroadcastConsumer() throws Exception {
        if (inited4Broadcast.get()) {
            return;
        }

        Properties keyValue = new Properties();
        keyValue.put(EventMeshConstants.IS_BROADCAST, "true");
        keyValue.put(EventMeshConstants.CONSUMER_GROUP, group);
        keyValue.put(EventMeshConstants.EVENT_MESH_IDC, eventMeshTCPConfiguration.getEventMeshIDC());
        keyValue.put(EventMeshConstants.INSTANCE_NAME, EventMeshUtil
            .buildMeshTcpClientID(sysId, EventMeshConstants.PURPOSE_SUB_UPPER_CASE,
                eventMeshTCPConfiguration.getEventMeshCluster()));
        broadCastMsgConsumer.init(keyValue);

        EventListener listener = (event, context) -> {
            String protocolVersion =
                Objects.requireNonNull(event.getSpecVersion()).toString();

            Span span = TraceUtils.prepareServerSpan(
                EventMeshUtil.getCloudEventExtensionMap(protocolVersion, event),
                EventMeshTraceConstants.TRACE_DOWNSTREAM_EVENTMESH_SERVER_SPAN, false);
            try {
                eventMeshTcpMonitor.getTcpSummaryMetrics().getMq2eventMeshMsgNum()
                    .incrementAndGet();
                event = CloudEventBuilder.from(event)
                    .withExtension(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP,
                        String.valueOf(System.currentTimeMillis()))
                    .withExtension(EventMeshConstants.REQ_RECEIVE_EVENTMESH_IP,
                        eventMeshTCPConfiguration.getEventMeshServerIp())
                    .build();
                String topic = event.getSubject();

                EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext =
                    (EventMeshAsyncConsumeContext) context;
                if (CollectionUtils.isEmpty(groupConsumerSessions)) {
                    log.warn("found no session to downstream broadcast msg");
                    eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                    return;
                }

                Iterator<Session> sessionsItr = groupConsumerSessions.iterator();

                SubscriptionItem subscriptionItem = subscriptions.get(topic);
                DownStreamMsgContext downStreamMsgContext =
                    new DownStreamMsgContext(event, null, broadCastMsgConsumer,
                        eventMeshAsyncConsumeContext.getAbstractContext(), false,
                        subscriptionItem);

                while (sessionsItr.hasNext()) {
                    Session session = sessionsItr.next();

                    if (!session.isAvailable(topic)) {
                        log.warn("downstream broadcast msg, session is not available, client:{}", session.getClient());
                        continue;
                    }

                    downStreamMsgContext.setSession(session);

                    // downstream broadcast msg asynchronously
                    eventMeshTCPServer.getTcpThreadPoolGroup().getBroadcastMsgDownstreamExecutorService()
                        .submit(() -> {
                            // msg put in eventmesh,waiting client ack
                            session.getPusher().unAckMsg(downStreamMsgContext.seq, downStreamMsgContext);
                            session.downstreamMsg(downStreamMsgContext);
                        });
                }

                eventMeshAsyncConsumeContext.commit(EventMeshAction.ManualAck);
            } finally {
                TraceUtils.finishSpan(span, event);
            }
        };
        broadCastMsgConsumer.registerEventListener(listener);

        inited4Broadcast.compareAndSet(false, true);
        log.info("init broadCastMsgConsumer success, group:{}", group);
    }

    public synchronized void startClientGroupBroadcastConsumer() throws Exception {
        if (started4Broadcast.get()) {
            return;
        }
        broadCastMsgConsumer.start();
        started4Broadcast.compareAndSet(false, true);
        log.info("starting broadCastMsgConsumer success, group:{}", group);
    }

    public void subscribe(SubscriptionItem subscriptionItem) throws Exception {
        if (SubscriptionMode.BROADCASTING == subscriptionItem.getMode()) {
            broadCastMsgConsumer.subscribe(subscriptionItem.getTopic());
        } else {
            persistentMsgConsumer.subscribe(subscriptionItem.getTopic());
        }
    }

    public void unsubscribe(SubscriptionItem subscriptionItem) throws Exception {
        if (SubscriptionMode.BROADCASTING == subscriptionItem.getMode()) {
            broadCastMsgConsumer.unsubscribe(subscriptionItem.getTopic());
        } else {
            persistentMsgConsumer.unsubscribe(subscriptionItem.getTopic());
        }
    }

    public synchronized void shutdownBroadCastConsumer() throws Exception {
        if (started4Broadcast.get()) {
            broadCastMsgConsumer.shutdown();
            log.info("broadcast consumer group:{} shutdown...", group);
        }
        started4Broadcast.compareAndSet(true, false);
        inited4Broadcast.compareAndSet(true, false);
        broadCastMsgConsumer = null;
    }

    public synchronized void shutdownPersistentConsumer() throws Exception {

        if (started4Persistent.get()) {
            persistentMsgConsumer.shutdown();
            log.info("persistent consumer group:{} shutdown...", group);
        }
        started4Persistent.compareAndSet(true, false);
        inited4Persistent.compareAndSet(true, false);
        persistentMsgConsumer = null;
    }

    public EventMeshTCPConfiguration getEventMeshTCPConfiguration() {
        return eventMeshTCPConfiguration;
    }

    public void setEventMeshTCPConfiguration(EventMeshTCPConfiguration eventMeshTCPConfiguration) {
        this.eventMeshTCPConfiguration = eventMeshTCPConfiguration;
    }

    public TcpRetryer getTcpRetryer() {
        return tcpRetryer;
    }

    public void setTcpRetryer(TcpRetryer tcpRetryer) {
        this.tcpRetryer = tcpRetryer;
    }

    public EventMeshTcpMonitor getEventMeshTcpMonitor() {
        return eventMeshTcpMonitor;
    }

    public void setEventMeshTcpMonitor(EventMeshTcpMonitor eventMeshTcpMonitor) {
        this.eventMeshTcpMonitor = eventMeshTcpMonitor;
    }

    public DownstreamDispatchStrategy getDownstreamDispatchStrategy() {
        return downstreamDispatchStrategy;
    }

    public void setDownstreamDispatchStrategy(
        DownstreamDispatchStrategy downstreamDispatchStrategy) {
        this.downstreamDispatchStrategy = downstreamDispatchStrategy;
    }

    public String getSysId() {
        return sysId;
    }

    private String pushMsgToEventMesh(CloudEvent msg, String ip, int port) throws Exception {
        StringBuilder targetUrl = new StringBuilder();
        targetUrl.append("http://").append(ip).append(":").append(port)
            .append("/eventMesh/msg/push");
        HttpTinyClient.HttpResult result = null;

        try {
            log.info("pushMsgToEventMesh,targetUrl:{},msg:{}", targetUrl, msg);
            List<String> paramValues = new ArrayList<String>();
            paramValues.add(EventMeshConstants.MANAGE_MSG);
            paramValues.add(JsonUtils.toJSONString(msg));
            paramValues.add(EventMeshConstants.MANAGE_GROUP);
            paramValues.add(group);

            result = HttpTinyClient.httpPost(
                targetUrl.toString(),
                null,
                paramValues,
                StandardCharsets.UTF_8.name(),
                3000);
        } catch (Exception e) {
            log.error("httpPost " + targetUrl + " is fail,", e);
            throw e;
        }

        if (200 == result.getCode() && result.getContent() != null) {
            return result.getContent();

        } else {
            throw new Exception("httpPost targetUrl[" + targetUrl
                + "] is not OK when getContentThroughHttp, httpResult: " + result + ".");
        }
    }

    public MQConsumerWrapper getPersistentMsgConsumer() {
        return persistentMsgConsumer;
    }

    private void sendMsgBackToBroker(CloudEvent event, String bizSeqNo) throws Exception {
        try {
            String topic = event.getSubject();
            log.warn("send msg back to broker, bizSeqno:{}, topic:{}", bizSeqNo, topic);

            long startTime = System.currentTimeMillis();
            long taskExcuteTime = startTime;
            send(new UpStreamMsgContext(null, event, null, startTime, taskExcuteTime),
                new SendCallback() {

                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.info("group:{} consume fail, sendMessageBack success, bizSeqno:{}, topic:{}",
                            group, bizSeqNo, topic);
                    }

                    @Override
                    public void onException(OnExceptionContext context) {
                        log.warn("group:{} consume fail, sendMessageBack fail, bizSeqno:{}, topic:{}",
                            group, bizSeqNo, topic);
                    }

                });
            eventMeshTcpMonitor.getTcpSummaryMetrics().getEventMesh2mqMsgNum().incrementAndGet();
        } catch (Exception e) {
            log.warn("try send msg back to broker failed");
            throw e;
        }
    }
}
