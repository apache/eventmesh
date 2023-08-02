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

package org.apache.eventmesh.runtime.core.protocol.tcp.session;

import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.consumer.dispatch.DownstreamDispatchStrategy;
import org.apache.eventmesh.runtime.core.protocol.tcp.consumer.push.DownStreamMsgContext;
import org.apache.eventmesh.runtime.core.protocol.tcp.retry.TcpRetryer;
import org.apache.eventmesh.runtime.metrics.tcp.EventMeshTcpMonitor;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.HttpTinyClient;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.cloudevents.CloudEvent;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

/**
 * The data structure manages the mapping of sessions and corresponding
 * subscription items, consumers,and producers in a client group.
 *
 */
@Slf4j
public class SessionManager {
    private EventMeshTCPConfiguration eventMeshTCPConfiguration;

    private TcpRetryer tcpRetryer;
    private EventMeshTcpMonitor eventMeshTcpMonitor;
    private DownstreamDispatchStrategy downstreamDispatchStrategy;

    private final String sysId;
    private String group;

    private final ReadWriteLock groupLock = new ReentrantReadWriteLock();

    private final SubscriptionMap subscriptionMap = new SubscriptionMap();
    private final ConsumerMap consumerMap = new ConsumerMap();
    private final ProducerMap producerMap = new ProducerMap();

    public SessionManager(String sysId, String group,
                          EventMeshTCPServer eventMeshTCPServer,
                          DownstreamDispatchStrategy downstreamDispatchStrategy) {
        this.sysId = sysId;
        this.group = group;
        this.eventMeshTCPConfiguration = eventMeshTCPServer.getEventMeshTCPConfiguration();
        this.tcpRetryer = eventMeshTCPServer.getEventMeshTcpRetryer();
        this.eventMeshTcpMonitor =
                Preconditions.checkNotNull(eventMeshTCPServer.getMetrics());
        this.downstreamDispatchStrategy = downstreamDispatchStrategy;

    }

    /**
     * Mapping between subscription items and sessions
     *
     */
    public class SubscriptionMap {

        private final ConcurrentHashMap<String/*topic*/, Map<String/*sessionId*/, Session>> topic2sessionInGroupMapping =
                new ConcurrentHashMap<>();

        private final ConcurrentHashMap<String/*topic*/, SubscriptionItem> subscriptions =
                new ConcurrentHashMap<>();


        public boolean hasSubscription(String topic) {
            boolean has = false;
            try {
                groupLock.readLock().lockInterruptibly();
                has = topic2sessionInGroupMapping.containsKey(topic);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.error("hasSubscription error! topic[{}]", topic);
            } finally {
                groupLock.readLock().unlock();
            }

            return has;
        }

        public boolean addSubscription(SubscriptionItem subscriptionItem, Session session)
                throws Exception {
            if (subscriptionItem == null) {
                log.error("addSubscription param error,subscriptionItem is null, session:{}", session);
                return false;
            }
            String topic = subscriptionItem.getTopic();
            if (session == null || !StringUtils.equalsIgnoreCase(group,
                    EventMeshUtil.buildClientGroup(session.getClient().getGroup()))) {
                log.error("addSubscription param error,topic:{},session:{}", topic, session);
                return false;
            }

            boolean r = false;
            try {
                groupLock.writeLock().lockInterruptibly();
                if (!topic2sessionInGroupMapping.containsKey(topic)) {
                    Map<String, Session> sessions = new HashMap<>();
                    topic2sessionInGroupMapping.put(topic, sessions);
                }
                if (r = topic2sessionInGroupMapping.get(topic).putIfAbsent(session.getSessionId(), session) == null) {
                    if (log.isInfoEnabled()) {
                        log.info("Cache session success, group:{} topic:{} client:{} sessionId:{}", group,
                                topic, session.getClient(), session.getSessionId());
                    }
                } else {
                    if (log.isWarnEnabled()) {
                        log.warn("Session already exists in topic2sessionInGroupMapping. group:{} topic:{} client:{} sessionId:{}", group, topic,
                                session.getClient(), session.getSessionId());
                    }
                }

                subscriptions.putIfAbsent(topic, subscriptionItem);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.error("addSubscription error! topic:{} client:{}", topic, session.getClient(), e);
                throw new Exception("addSubscription fail");
            } finally {
                groupLock.writeLock().unlock();
            }
            return r;
        }

        public boolean removeSubscription(SubscriptionItem subscriptionItem, Session session) {
            if (subscriptionItem == null) {
                log.error("addSubscription param error,subscriptionItem is null, session:{}", session);
                return false;
            }
            String topic = subscriptionItem.getTopic();
            if (session == null
                    || !StringUtils.equalsIgnoreCase(group,
                    EventMeshUtil.buildClientGroup(session.getClient().getGroup()))) {
                log.error("removeSubscription param error,topic:{},session:{}", topic, session);
                return false;
            }

            boolean r = false;
            try {
                groupLock.writeLock().lockInterruptibly();
                if (topic2sessionInGroupMapping.containsKey(topic)) {
                    if (topic2sessionInGroupMapping.get(topic).remove(session.getSessionId()) != null) {

                        if (log.isInfoEnabled()) {
                            log.info(
                                    "removeSubscription remove session success, group:{} topic:{} client:{}",
                                    group, topic, session.getClient());
                        }
                    } else {
                        if (log.isWarnEnabled()) {
                            log.warn(
                                    "Not found session in cache, group:{} topic:{} client:{} sessionId:{}",
                                    group, topic, session.getClient(), session.getSessionId());
                        }
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
                groupLock.writeLock().unlock();
            }
            return r;
        }

        /**
         * clean subscription of the session
         *
         */
        public void cleanSubscription(Session session) {
            for (SubscriptionItem item : session.getSessionContext().getSubscribeTopics().values()) {
                SessionManager sessionManager = Objects.requireNonNull(session.getSessionMap().get());
                sessionManager.getSubscriptionMap().removeSubscription(item, session);

                // todo if no more subscription del consumer, concurrent with session and broker
            }
        }

        public ConcurrentHashMap<String, SubscriptionItem> getSubscriptions() {
            return subscriptions;
        }

        public ConcurrentHashMap<String, Map<String, Session>> getTopic2sessionInGroupMapping() {
            return topic2sessionInGroupMapping;
        }
    }


    /**
     * Mapping between consumers and sessions
     */
    public class ConsumerMap {
        private final Set<Session> groupConsumerSessions = new HashSet<>();

        public boolean addConsumer(Session session) {
            if (session == null
                    || !StringUtils.equalsIgnoreCase(group,
                    EventMeshUtil.buildClientGroup(session.getClient().getGroup()))) {

                log.error("addGroupConsumerSession param error,session:{}", session);
                return false;
            }

            boolean r = false;
            try {
                groupLock.writeLock().lockInterruptibly();
                r = groupConsumerSessions.add(session);
                if (r) {

                    if (log.isInfoEnabled()) {
                        log.info("addGroupConsumerSession success, group:{} client:{}", group,
                                session.getClient());
                    }
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.error("addGroupConsumerSession error! group:{} client:{}", group,
                        session.getClient(), e);
            } finally {
                groupLock.writeLock().unlock();
            }
            return r;
        }


        public boolean removeConsumer(Session session) {
            if (session == null
                    || !StringUtils.equalsIgnoreCase(group,
                    EventMeshUtil.buildClientGroup(session.getClient().getGroup()))) {

                log.error("removeGroupConsumerSession param error,session:{}", session);
                return false;
            }

            boolean r = false;
            try {
                groupLock.writeLock().lockInterruptibly();
                r = groupConsumerSessions.remove(session);
                if (r) {

                    if (log.isInfoEnabled()) {
                        log.info("removeGroupConsumerSession success, group:{} client:{}", group,
                                session.getClient());
                    }
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.error("removeGroupConsumerSession error! group:{} client:{}", group,
                        session.getClient(), e);
            } finally {
                groupLock.writeLock().unlock();
            }
            return r;
        }

        public void cleanConsumer(Session session) {
            SessionManager sessionManager = Objects.requireNonNull(session.getSessionMap().get());
            sessionManager.getSubscriptionMap().cleanSubscription(session);
            sessionManager.getConsumerMap().removeConsumer(session);
            sessionManager.getConsumerMap().handleUnackMsgsInSession(session);
        }


        /**
         * handle unAck msgs in this session
         *
         * @param session
         */
        private void handleUnackMsgsInSession(Session session) {
            ConcurrentHashMap<String /** seq */, DownStreamMsgContext> unAckMsg = session.getPusher().getUnAckMsg();
            SessionManager sessionManager = Objects.requireNonNull(session.getSessionMap().get());
            if (unAckMsg.size() > 0 && !sessionManager.getConsumerMap().getGroupConsumerSessions().isEmpty()) {
                for (Map.Entry<String, DownStreamMsgContext> entry : unAckMsg.entrySet()) {
                    DownStreamMsgContext downStreamMsgContext = entry.getValue();
                    if (SubscriptionMode.BROADCASTING == downStreamMsgContext.getSubscriptionItem().getMode()) {
                        log.warn("exist broadcast msg unack when closeSession,seq:{},bizSeq:{},client:{}",
                                downStreamMsgContext.getSeq(), downStreamMsgContext.getSeq(),
                                session.getClient());
                        continue;
                    }
                    Session reChooseSession = sessionManager.getDownstreamDispatchStrategy()
                            .select(sessionManager.getGroup(),
                                    downStreamMsgContext.getEvent().getSubject(),
                                    sessionManager.getConsumerMap().getGroupConsumerSessions());
                    if (reChooseSession != null) {
                        downStreamMsgContext.setSession(reChooseSession);
                        reChooseSession.getPusher().unAckMsg(downStreamMsgContext.getSeq(), downStreamMsgContext);
                        reChooseSession.downstreamMsg(downStreamMsgContext);
                        log.info("rePush msg form unAckMsgs,seq:{},rePushClient:{}", entry.getKey(),
                                downStreamMsgContext.getSession().getClient());
                    } else {
                        log.warn("select session fail in handleUnackMsgsInSession,seq:{},topic:{}", entry.getKey(),
                                downStreamMsgContext.getEvent().getSubject());
                    }
                }
            }
        }

        public Set<Session> getGroupConsumerSessions() {
            return groupConsumerSessions;
        }

    }

    /**
     * Mapping between producers and sessions
     */
    public class ProducerMap {
        private final Set<Session> groupProducerSessions = new HashSet<>();

        public boolean addProducer(Session session) {
            if (session == null
                    || !StringUtils.equalsIgnoreCase(group,
                    EventMeshUtil.buildClientGroup(session.getClient().getGroup()))) {

                log.error("addGroupProducerSession param error,session:{}", session);
                return false;
            }

            boolean r = false;
            try {
                groupLock.writeLock().lockInterruptibly();
                r = groupProducerSessions.add(session);
                if (r) {

                    log.info("addGroupProducerSession success, group:{} client:{}", group,
                            session.getClient());
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.error("addGroupProducerSession error! group:{} client:{}", group,
                        session.getClient(), e);
            } finally {
                groupLock.writeLock().unlock();
            }
            return r;
        }

        public boolean removeProducer(Session session) {
            if (session == null
                    || !StringUtils.equalsIgnoreCase(group,
                    EventMeshUtil.buildClientGroup(session.getClient().getGroup()))) {
                log.error("removeGroupProducerSession param error,session:{}", session);
                return false;
            }

            boolean r = false;
            try {
                groupLock.writeLock().lockInterruptibly();
                r = groupProducerSessions.remove(session);
                if (r) {

                    log.info("removeGroupProducerSession success, group:{} client:{}", group,
                            session.getClient());
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.error("removeGroupProducerSession error! group:{} client:{}", group,
                        session.getClient(), e);
            } finally {
                groupLock.writeLock().unlock();
            }

            return r;
        }

        public void cleanProducer(Session session) {
            SessionManager sessionManager = Objects.requireNonNull(session.getSessionMap().get());
            sessionManager.getProducerMap().removeProducer(session);
        }

        public Set<Session> getGroupProducerSessions() {
            return groupProducerSessions;
        }
    }


    private String pushMsgToEventMesh(CloudEvent msg, String ip, int port) throws Exception {
        StringBuilder targetUrl = new StringBuilder();
        targetUrl.append("http://").append(ip).append(":").append(port)
                .append("/eventMesh/msg/push");
        HttpTinyClient.HttpResult result = null;

        try {
            if (log.isInfoEnabled()) {
                log.info("pushMsgToEventMesh,targetUrl:{},msg:{}", targetUrl, msg);
            }
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

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }


    public SubscriptionMap getSubscriptionMap() {
        return subscriptionMap;
    }

    public ConsumerMap getConsumerMap() {
        return consumerMap;
    }

    public ProducerMap getProducerMap() {
        return producerMap;
    }

}
