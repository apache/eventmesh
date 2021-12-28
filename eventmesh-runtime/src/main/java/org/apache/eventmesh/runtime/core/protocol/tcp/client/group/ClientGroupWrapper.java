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

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.apache.eventmesh.api.*;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.Constants;
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
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.retry.EventMeshTcpRetryer;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.send.UpStreamMsgContext;
import org.apache.eventmesh.runtime.metrics.tcp.EventMeshTcpMonitor;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.HttpTinyClient;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ClientGroupWrapper {

    public static Logger logger = LoggerFactory.getLogger(ClientGroupWrapper.class);

    private String producerGroup;

    private String consumerGroup;

    //can be sysid + ext(eg dcn)
    private String sysId;

    private EventMeshTCPConfiguration eventMeshTCPConfiguration;

    private EventMeshTCPServer eventMeshTCPServer;

    private EventMeshTcpRetryer eventMeshTcpRetryer;

    private EventMeshTcpMonitor eventMeshTcpMonitor;

    private DownstreamDispatchStrategy downstreamDispatchStrategy;

    private final ReadWriteLock groupLock = new ReentrantReadWriteLock();

    public Set<Session> groupConsumerSessions = new HashSet<Session>();

    public Set<Session> groupProducerSessions = new HashSet<Session>();

    public AtomicBoolean started4Persistent = new AtomicBoolean(Boolean.FALSE);

    public AtomicBoolean started4Broadcast = new AtomicBoolean(Boolean.FALSE);

    public AtomicBoolean inited4Persistent = new AtomicBoolean(Boolean.FALSE);

    public AtomicBoolean inited4Broadcast = new AtomicBoolean(Boolean.FALSE);

    private MQConsumerWrapper persistentMsgConsumer;

    private MQConsumerWrapper broadCastMsgConsumer;

    private ConcurrentHashMap<String, Set<Session>> topic2sessionInGroupMapping =
        new ConcurrentHashMap<String, Set<Session>>();

    public AtomicBoolean producerStarted = new AtomicBoolean(Boolean.FALSE);

    private MQProducerWrapper mqProducerWrapper;

    public ClientGroupWrapper(String sysId, String producerGroup, String consumerGroup,
                              EventMeshTCPServer eventMeshTCPServer,
                              DownstreamDispatchStrategy downstreamDispatchStrategy) {
        this.sysId = sysId;
        this.producerGroup = producerGroup;
        this.consumerGroup = consumerGroup;
        this.eventMeshTCPServer = eventMeshTCPServer;
        this.eventMeshTCPConfiguration = eventMeshTCPServer.getEventMeshTCPConfiguration();
        this.eventMeshTcpRetryer = eventMeshTCPServer.getEventMeshTcpRetryer();
        this.eventMeshTcpMonitor = eventMeshTCPServer.getEventMeshTcpMonitor();
        this.downstreamDispatchStrategy = downstreamDispatchStrategy;
        this.persistentMsgConsumer = new MQConsumerWrapper(
            eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshConnectorPluginType);
        this.broadCastMsgConsumer = new MQConsumerWrapper(
            eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshConnectorPluginType);
        this.mqProducerWrapper = new MQProducerWrapper(
            eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshConnectorPluginType);
    }

    public ConcurrentHashMap<String, Set<Session>> getTopic2sessionInGroupMapping() {
        return topic2sessionInGroupMapping;
    }

    public boolean hasSubscription(String topic) {
        boolean has = false;
        try {
            this.groupLock.readLock().lockInterruptibly();
            has = topic2sessionInGroupMapping.containsKey(topic);
        } catch (Exception e) {
            logger.error("hasSubscription error! topic[{}]", topic);
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

    public void request(UpStreamMsgContext upStreamMsgContext, RequestReplyCallback rrCallback, long timeout)
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
                String bizSeqNo = (String) upStreamMsgContext.getEvent().getExtension(EventMeshConstants.PROPERTY_MESSAGE_KEYS);
                logger.error("reply err! topic:{}, bizSeqNo:{}, client:{}",
                    upStreamMsgContext.getEvent().getSubject(), bizSeqNo,
                    upStreamMsgContext.getSession().getClient(), context.getException());
            }
        });
        return true;
    }

    public MQProducerWrapper getMqProducerWrapper() {
        return mqProducerWrapper;
    }

    public boolean addSubscription(String topic, Session session) throws Exception {
        if (session == null || !StringUtils.equalsIgnoreCase(consumerGroup,
            EventMeshUtil.buildClientGroup(session.getClient().getConsumerGroup()))) {
            logger.error("addSubscription param error,topic:{},session:{}", topic, session);
            return false;
        }

        boolean r = false;
        try {
            this.groupLock.writeLock().lockInterruptibly();
            if (!topic2sessionInGroupMapping.containsKey(topic)) {
                Set<Session> sessions = new HashSet<Session>();
                topic2sessionInGroupMapping.put(topic, sessions);
            }
            r = topic2sessionInGroupMapping.get(topic).add(session);
            if (r) {
                logger.info("addSubscription success, group:{} topic:{} client:{}", consumerGroup,
                    topic, session.getClient());
            } else {
                logger
                    .warn("addSubscription fail, group:{} topic:{} client:{}", consumerGroup, topic,
                        session.getClient());
            }
        } catch (Exception e) {
            logger
                .error("addSubscription error! topic:{} client:{}", topic, session.getClient(), e);
            throw new Exception("addSubscription fail");
        } finally {
            this.groupLock.writeLock().unlock();
        }
        return r;
    }

    public boolean removeSubscription(String topic, Session session) {
        if (session == null
            || !StringUtils.equalsIgnoreCase(consumerGroup,
            EventMeshUtil.buildClientGroup(session.getClient().getConsumerGroup()))) {
            logger.error("removeSubscription param error,topic:{},session:{}", topic, session);
            return false;
        }

        boolean r = false;
        try {
            this.groupLock.writeLock().lockInterruptibly();
            if (topic2sessionInGroupMapping.containsKey(topic)) {
                r = topic2sessionInGroupMapping.get(topic).remove(session);
                if (r) {
                    logger.info(
                        "removeSubscription remove session success, group:{} topic:{} client:{}",
                        consumerGroup, topic, session.getClient());
                } else {
                    logger.warn(
                        "removeSubscription remove session failed, group:{} topic:{} client:{}",
                        consumerGroup, topic, session.getClient());
                }
            }
            if (CollectionUtils.size(topic2sessionInGroupMapping.get(topic)) == 0) {
                topic2sessionInGroupMapping.remove(topic);
                logger.info("removeSubscription remove topic success, group:{} topic:{}",
                    consumerGroup, topic);
            }
        } catch (Exception e) {
            logger.error("removeSubscription error! topic:{} client:{}", topic, session.getClient(),
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
        keyValue.put("producerGroup", producerGroup);
        keyValue.put("instanceName", EventMeshUtil
            .buildMeshTcpClientID(sysId, "PUB", eventMeshTCPConfiguration.eventMeshCluster));

        //TODO for defibus
        keyValue.put("eventMeshIDC", eventMeshTCPConfiguration.eventMeshIDC);

        mqProducerWrapper.init(keyValue);
        mqProducerWrapper.start();
        producerStarted.compareAndSet(false, true);
        logger.info("starting producer success, group:{}", producerGroup);
    }

    public synchronized void shutdownProducer() throws Exception {
        if (!producerStarted.get()) {
            return;
        }
        mqProducerWrapper.shutdown();
        producerStarted.compareAndSet(true, false);
        logger.info("shutdown producer success for group:{}", producerGroup);
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public boolean addGroupConsumerSession(Session session) {
        if (session == null
            || !StringUtils.equalsIgnoreCase(consumerGroup,
            EventMeshUtil.buildClientGroup(session.getClient().getConsumerGroup()))) {
            logger.error("addGroupConsumerSession param error,session:{}", session);
            return false;
        }

        boolean r = false;
        try {
            this.groupLock.writeLock().lockInterruptibly();
            r = groupConsumerSessions.add(session);
            if (r) {
                logger.info("addGroupConsumerSession success, group:{} client:{}", consumerGroup,
                    session.getClient());
            }
        } catch (Exception e) {
            logger.error("addGroupConsumerSession error! group:{} client:{}", consumerGroup,
                session.getClient(), e);
        } finally {
            this.groupLock.writeLock().unlock();
        }
        return r;
    }

    public boolean addGroupProducerSession(Session session) {
        if (session == null
            || !StringUtils.equalsIgnoreCase(producerGroup,
            EventMeshUtil.buildClientGroup(session.getClient().getProducerGroup()))) {
            logger.error("addGroupProducerSession param error,session:{}", session);
            return false;
        }

        boolean r = false;
        try {
            this.groupLock.writeLock().lockInterruptibly();
            r = groupProducerSessions.add(session);
            if (r) {
                logger.info("addGroupProducerSession success, group:{} client:{}", producerGroup,
                    session.getClient());
            }
        } catch (Exception e) {
            logger.error("addGroupProducerSession error! group:{} client:{}", producerGroup,
                session.getClient(), e);
        } finally {
            this.groupLock.writeLock().unlock();
        }
        return r;
    }

    public boolean removeGroupConsumerSession(Session session) {
        if (session == null
            || !StringUtils.equalsIgnoreCase(consumerGroup,
            EventMeshUtil.buildClientGroup(session.getClient().getConsumerGroup()))) {
            logger.error("removeGroupConsumerSession param error,session:{}", session);
            return false;
        }

        boolean r = false;
        try {
            this.groupLock.writeLock().lockInterruptibly();
            r = groupConsumerSessions.remove(session);
            if (r) {
                logger.info("removeGroupConsumerSession success, group:{} client:{}", consumerGroup,
                    session.getClient());
            }
        } catch (Exception e) {
            logger.error("removeGroupConsumerSession error! group:{} client:{}", consumerGroup,
                session.getClient(), e);
        } finally {
            this.groupLock.writeLock().unlock();
        }
        return r;
    }

    public boolean removeGroupProducerSession(Session session) {
        if (session == null
            || !StringUtils.equalsIgnoreCase(producerGroup,
            EventMeshUtil.buildClientGroup(session.getClient().getProducerGroup()))) {
            logger.error("removeGroupProducerSession param error,session:{}", session);
            return false;
        }

        boolean r = false;
        try {
            this.groupLock.writeLock().lockInterruptibly();
            r = groupProducerSessions.remove(session);
            if (r) {
                logger.info("removeGroupProducerSession success, group:{} client:{}", producerGroup,
                    session.getClient());
            }
        } catch (Exception e) {
            logger.error("removeGroupProducerSession error! group:{} client:{}", producerGroup,
                session.getClient(), e);
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
        keyValue.put("isBroadcast", "false");
        keyValue.put("consumerGroup", consumerGroup);
        keyValue.put("eventMeshIDC", eventMeshTCPConfiguration.eventMeshIDC);
        keyValue.put("instanceName", EventMeshUtil
            .buildMeshTcpClientID(sysId, "SUB", eventMeshTCPConfiguration.eventMeshCluster));

        persistentMsgConsumer.init(keyValue);

        inited4Persistent.compareAndSet(false, true);
        logger.info("init persistentMsgConsumer success, group:{}", consumerGroup);
    }

    public synchronized void startClientGroupPersistentConsumer() throws Exception {
        if (started4Persistent.get()) {
            return;
        }
        persistentMsgConsumer.start();
        started4Persistent.compareAndSet(false, true);
        logger.info("starting persistentMsgConsumer success, group:{}", consumerGroup);
    }

    public synchronized void initClientGroupBroadcastConsumer() throws Exception {
        if (inited4Broadcast.get()) {
            return;
        }

        Properties keyValue = new Properties();
        keyValue.put("isBroadcast", "true");
        keyValue.put("consumerGroup", consumerGroup);
        keyValue.put("eventMeshIDC", eventMeshTCPConfiguration.eventMeshIDC);
        keyValue.put("instanceName", EventMeshUtil
            .buildMeshTcpClientID(sysId, "SUB", eventMeshTCPConfiguration.eventMeshCluster));
        broadCastMsgConsumer.init(keyValue);

        inited4Broadcast.compareAndSet(false, true);
        logger.info("init broadCastMsgConsumer success, group:{}", consumerGroup);
    }

    public synchronized void startClientGroupBroadcastConsumer() throws Exception {
        if (started4Broadcast.get()) {
            return;
        }
        broadCastMsgConsumer.start();
        started4Broadcast.compareAndSet(false, true);
        logger.info("starting broadCastMsgConsumer success, group:{}", consumerGroup);
    }

    public void subscribe(SubscriptionItem subscriptionItem) throws Exception {
        EventListener listener = null;
        if (SubscriptionMode.BROADCASTING.equals(subscriptionItem.getMode())) {
            listener = new EventListener() {
                @Override
                public void consume(CloudEvent event, AsyncConsumeContext context) {

                    eventMeshTcpMonitor.getMq2EventMeshMsgNum().incrementAndGet();
                    event = CloudEventBuilder.from(event)
                            .withExtension(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP,
                            String.valueOf(System.currentTimeMillis()))
                            .withExtension(EventMeshConstants.REQ_RECEIVE_EVENTMESH_IP,
                                    eventMeshTCPConfiguration.eventMeshServerIp).build();
                    String topic = event.getSubject();
//                        message.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION);
//                    message.getSystemProperties().put(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP,
//                        String.valueOf(System.currentTimeMillis()));
//                    message.getSystemProperties().put(EventMeshConstants.REQ_RECEIVE_EVENTMESH_IP,
//                        eventMeshTCPConfiguration.eventMeshServerIp);

                    EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext =
                        (EventMeshAsyncConsumeContext) context;
                    if (CollectionUtils.isEmpty(groupConsumerSessions)) {
                        logger.warn("found no session to downstream broadcast msg");
                        eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                        return;
                    }

                    Iterator<Session> sessionsItr = groupConsumerSessions.iterator();

                    DownStreamMsgContext downStreamMsgContext =
                        new DownStreamMsgContext(event, null, broadCastMsgConsumer,
                            eventMeshAsyncConsumeContext.getAbstractContext(), false,
                            subscriptionItem);

                    while (sessionsItr.hasNext()) {
                        Session session = sessionsItr.next();

                        if (!session.isAvailable(topic)) {
                            logger
                                .warn("downstream broadcast msg,session is not available,client:{}",
                                    session.getClient());
                            continue;
                        }

                        downStreamMsgContext.session = session;

                        //downstream broadcast msg asynchronously
                        eventMeshTCPServer.getBroadcastMsgDownstreamExecutorService()
                            .submit(new Runnable() {
                                @Override
                                public void run() {
                                    //msg put in eventmesh,waiting client ack
                                    session.getPusher()
                                        .unAckMsg(downStreamMsgContext.seq, downStreamMsgContext);
                                    session.downstreamMsg(downStreamMsgContext);
                                }
                            });
                    }

                    eventMeshAsyncConsumeContext.commit(EventMeshAction.ManualAck);
                }
            };
            broadCastMsgConsumer.subscribe(subscriptionItem.getTopic(), listener);
        } else {
            listener = new EventListener() {
                @Override
                public void consume(CloudEvent event, AsyncConsumeContext context) {
                    eventMeshTcpMonitor.getMq2EventMeshMsgNum().incrementAndGet();
                    event = CloudEventBuilder.from(event)
                            .withExtension(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP,
                                    String.valueOf(System.currentTimeMillis()))
                            .withExtension(EventMeshConstants.REQ_RECEIVE_EVENTMESH_IP,
                                    eventMeshTCPConfiguration.eventMeshServerIp).build();
                    String topic = event.getSubject();

                    EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext =
                        (EventMeshAsyncConsumeContext) context;
                    Session session = downstreamDispatchStrategy
                        .select(consumerGroup, topic, groupConsumerSessions);
                    String bizSeqNo = EventMeshUtil.getMessageBizSeq(event);
                    if (session == null) {
                        try {
                            Integer sendBackTimes = new Integer(0);
                            String sendBackFromEventMeshIp = "";
                            if (StringUtils.isNotBlank(Objects.requireNonNull(event.getExtension(
                                    EventMeshConstants.EVENTMESH_SEND_BACK_TIMES)).toString())) {
                                sendBackTimes = (Integer) event.getExtension(EventMeshConstants.EVENTMESH_SEND_BACK_TIMES);
                            }
                            if (StringUtils.isNotBlank(Objects.requireNonNull(event.getExtension(
                                    EventMeshConstants.EVENTMESH_SEND_BACK_IP)).toString())) {
                                sendBackFromEventMeshIp = (String) event.getExtension(EventMeshConstants.EVENTMESH_SEND_BACK_IP);
                            }

                            logger.error(
                                "found no session to downstream msg,groupName:{}, topic:{}, "
                                    + "bizSeqNo:{}, sendBackTimes:{}, sendBackFromEventMeshIp:{}",
                                consumerGroup, topic, bizSeqNo, sendBackTimes,
                                sendBackFromEventMeshIp);

                            if (sendBackTimes >= eventMeshTCPServer
                                .getEventMeshTCPConfiguration().eventMeshTcpSendBackMaxTimes) {
                                logger.error(
                                    "sendBack to broker over max times:{}, groupName:{}, topic:{}, "
                                        + "bizSeqNo:{}", eventMeshTCPServer
                                        .getEventMeshTCPConfiguration()
                                        .eventMeshTcpSendBackMaxTimes,
                                    consumerGroup, topic, bizSeqNo);
                            } else {
                                sendBackTimes++;
                                event = CloudEventBuilder.from(event)
                                        .withExtension(EventMeshConstants.EVENTMESH_SEND_BACK_TIMES,
                                                sendBackTimes.toString())
                                        .withExtension(EventMeshConstants.EVENTMESH_SEND_BACK_IP,
                                                eventMeshTCPConfiguration.eventMeshServerIp).build();
                                sendMsgBackToBroker(event, bizSeqNo);
                            }
                        } catch (Exception e) {
                            logger.warn("handle msg exception when no session found", e);
                        }

                        eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                        return;
                    }

                    DownStreamMsgContext downStreamMsgContext =
                        new DownStreamMsgContext(event, session, persistentMsgConsumer,
                            eventMeshAsyncConsumeContext.getAbstractContext(), false,
                            subscriptionItem);
                    //msg put in eventmesh,waiting client ack
                    session.getPusher().unAckMsg(downStreamMsgContext.seq, downStreamMsgContext);
                    session.downstreamMsg(downStreamMsgContext);
                    eventMeshAsyncConsumeContext.commit(EventMeshAction.ManualAck);
                }
            };
            persistentMsgConsumer.subscribe(subscriptionItem.getTopic(), listener);
        }
    }

    public void unsubscribe(SubscriptionItem subscriptionItem) throws Exception {
        if (SubscriptionMode.BROADCASTING.equals(subscriptionItem.getMode())) {
            broadCastMsgConsumer.unsubscribe(subscriptionItem.getTopic());
        } else {
            persistentMsgConsumer.unsubscribe(subscriptionItem.getTopic());
        }
    }

    public synchronized void shutdownBroadCastConsumer() throws Exception {
        if (started4Broadcast.get()) {
            broadCastMsgConsumer.shutdown();
            logger.info("broadcast consumer group:{} shutdown...", consumerGroup);
        }
        started4Broadcast.compareAndSet(true, false);
        inited4Broadcast.compareAndSet(true, false);
        broadCastMsgConsumer = null;
    }

    public synchronized void shutdownPersistentConsumer() throws Exception {

        if (started4Persistent.get()) {
            persistentMsgConsumer.shutdown();
            logger.info("persistent consumer group:{} shutdown...", consumerGroup);
        }
        started4Persistent.compareAndSet(true, false);
        inited4Persistent.compareAndSet(true, false);
        persistentMsgConsumer = null;
    }

    public Set<Session> getGroupConsumerSessions() {
        return groupConsumerSessions;
    }

    public Set<Session> getGroupProducerSessions() {
        return groupProducerSessions;
    }

    public EventMeshTCPConfiguration getEventMeshTCPConfiguration() {
        return eventMeshTCPConfiguration;
    }

    public void setEventMeshTCPConfiguration(EventMeshTCPConfiguration eventMeshTCPConfiguration) {
        this.eventMeshTCPConfiguration = eventMeshTCPConfiguration;
    }

    public EventMeshTcpRetryer getEventMeshTcpRetryer() {
        return eventMeshTcpRetryer;
    }

    public void setEventMeshTcpRetryer(EventMeshTcpRetryer eventMeshTcpRetryer) {
        this.eventMeshTcpRetryer = eventMeshTcpRetryer;
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
            logger.info("pushMsgToEventMesh,targetUrl:{},msg:{}", targetUrl.toString(),
                msg);
            List<String> paramValues = new ArrayList<String>();
            paramValues.add("msg");
            paramValues.add(JsonUtils.serialize(msg));
            paramValues.add("group");
            paramValues.add(consumerGroup);

            result = HttpTinyClient.httpPost(
                targetUrl.toString(),
                null,
                paramValues,
                "UTF-8",
                3000);
        } catch (Exception e) {
            logger.error("httpPost " + targetUrl + " is fail,", e);
            //throw new RuntimeException("httpPost " + targetUrl + " is fail," , e);
            throw e;
        }

        if (200 == result.code && result.content != null) {
            return result.content;

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
            logger.warn("send msg back to broker, bizSeqno:{}, topic:{}", bizSeqNo, topic);

            long startTime = System.currentTimeMillis();
            long taskExcuteTime = startTime;
            send(new UpStreamMsgContext(null, event, null, startTime, taskExcuteTime),
                new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        logger.info(
                            "consumerGroup:{} consume fail, sendMessageBack success, bizSeqno:{}, "
                                + "topic:{}", consumerGroup, bizSeqNo, topic);
                    }

                    @Override
                    public void onException(OnExceptionContext context) {
                        logger.warn(
                            "consumerGroup:{} consume fail, sendMessageBack fail, bizSeqno:{},"
                                + " topic:{}", consumerGroup, bizSeqNo, topic);
                    }

                });
            eventMeshTcpMonitor.getEventMesh2mqMsgNum().incrementAndGet();
        } catch (Exception e) {
            logger.warn("try send msg back to broker failed");
            throw e;
        }
    }
}
