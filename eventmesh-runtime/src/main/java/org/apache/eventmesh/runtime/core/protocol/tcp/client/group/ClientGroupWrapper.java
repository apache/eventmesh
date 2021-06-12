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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.alibaba.fastjson.JSON;

import io.openmessaging.api.Action;
import io.openmessaging.api.AsyncConsumeContext;
import io.openmessaging.api.AsyncMessageListener;
import io.openmessaging.api.Message;
import io.openmessaging.api.OnExceptionContext;
import io.openmessaging.api.SendCallback;
import io.openmessaging.api.SendResult;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.api.RRCallback;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.core.plugin.MQProducerWrapper;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.dispatch.DownstreamDispatchStrategy;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.push.DownStreamMsgContext;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.push.retry.EventMeshTcpRetryer;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.send.UpStreamMsgContext;
import org.apache.eventmesh.runtime.metrics.tcp.EventMeshTcpMonitor;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.HttpTinyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientGroupWrapper {

    public static Logger logger = LoggerFactory.getLogger(ClientGroupWrapper.class);

    private String groupName;

    private String sysId;

    private String dcn;

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

    private MQConsumerWrapper persistentMsgConsumer = new MQConsumerWrapper();

    private MQConsumerWrapper broadCastMsgConsumer = new MQConsumerWrapper();

    private ConcurrentHashMap<String, Set<Session>> topic2sessionInGroupMapping = new ConcurrentHashMap<String, Set<Session>>();

    public AtomicBoolean producerStarted = new AtomicBoolean(Boolean.FALSE);

    public ClientGroupWrapper(String sysId, String dcn,
                              EventMeshTCPServer eventMeshTCPServer,
                              DownstreamDispatchStrategy downstreamDispatchStrategy) {
        this.sysId = sysId;
        this.dcn = dcn;
        this.eventMeshTCPServer = eventMeshTCPServer;
        this.eventMeshTCPConfiguration = eventMeshTCPServer.getEventMeshTCPConfiguration();
        this.eventMeshTcpRetryer = eventMeshTCPServer.getEventMeshTcpRetryer();
        this.eventMeshTcpMonitor = eventMeshTCPServer.getEventMeshTcpMonitor();
        this.groupName = EventMeshUtil.buildClientGroup(sysId, dcn);
        this.downstreamDispatchStrategy = downstreamDispatchStrategy;
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

    public boolean send(UpStreamMsgContext upStreamMsgContext, SendCallback sendCallback) throws Exception {
        mqProducerWrapper.send(upStreamMsgContext.getMsg(), sendCallback);
        return true;
    }

    public void request(UpStreamMsgContext upStreamMsgContext, SendCallback sendCallback, RRCallback rrCallback, long timeout)
            throws Exception {
        mqProducerWrapper.request(upStreamMsgContext.getMsg(), sendCallback, rrCallback, timeout);
    }

    public boolean reply(UpStreamMsgContext upStreamMsgContext) throws Exception {
        mqProducerWrapper.reply(upStreamMsgContext.getMsg(), new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {

            }

            @Override
            public void onException(OnExceptionContext context) {
                String bizSeqNo = upStreamMsgContext.getMsg().getSystemProperties(EventMeshConstants.PROPERTY_MESSAGE_KEYS);
                logger.error("reply err! topic:{}, bizSeqNo:{}, client:{}",
                        upStreamMsgContext.getMsg().getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION), bizSeqNo,
                        upStreamMsgContext.getSession().getClient(), context.getException());
            }
        });
        return true;
    }

    private MQProducerWrapper mqProducerWrapper = new MQProducerWrapper();

    public MQProducerWrapper getMqProducerWrapper() {
        return mqProducerWrapper;
    }

    public boolean addSubscription(String topic, Session session) throws Exception {
        if (session == null
                || !StringUtils.equalsIgnoreCase(groupName, EventMeshUtil.buildClientGroup(session.getClient().getSubsystem(), session.getClient().getDcn()))) {
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
                logger.info("addSubscription success, group:{} topic:{} client:{}", groupName, topic, session.getClient());
            } else {
                logger.warn("addSubscription fail, group:{} topic:{} client:{}", groupName, topic, session.getClient());
            }
        } catch (Exception e) {
            logger.error("addSubscription error! topic:{} client:{}", topic, session.getClient(), e);
            throw new Exception("addSubscription fail");
        } finally {
            this.groupLock.writeLock().unlock();
        }
        return r;
    }

    public boolean removeSubscription(String topic, Session session) {
        if (session == null
                || !StringUtils.equalsIgnoreCase(groupName, EventMeshUtil.buildClientGroup(session.getClient().getSubsystem(), session.getClient().getDcn()))) {
            logger.error("removeSubscription param error,topic:{},session:{}", topic, session);
            return false;
        }

        boolean r = false;
        try {
            this.groupLock.writeLock().lockInterruptibly();
            if (topic2sessionInGroupMapping.containsKey(topic)) {
                r = topic2sessionInGroupMapping.get(topic).remove(session);
                if (r) {
                    logger.info("removeSubscription remove session success, group:{} topic:{} client:{}", groupName, topic, session.getClient());
                } else {
                    logger.warn("removeSubscription remove session failed, group:{} topic:{} client:{}", groupName, topic, session.getClient());
                }
            }
            if (CollectionUtils.size(topic2sessionInGroupMapping.get(topic)) == 0) {
                topic2sessionInGroupMapping.remove(topic);
                logger.info("removeSubscription remove topic success, group:{} topic:{}", groupName, topic);
            }
        } catch (Exception e) {
            logger.error("removeSubscription error! topic:{} client:{}", topic, session.getClient(), e);
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
//        KeyValue keyValue = OMS.newKeyValue();
        keyValue.put("producerGroup", groupName);
        keyValue.put("instanceName", EventMeshUtil.buildMeshTcpClientID(sysId, dcn, "PUB", eventMeshTCPConfiguration.eventMeshCluster));

        //TODO for defibus
        keyValue.put("eventMeshIDC", eventMeshTCPConfiguration.eventMeshIDC);

        mqProducerWrapper.init(keyValue);
        mqProducerWrapper.start();
        producerStarted.compareAndSet(false, true);
        logger.info("starting producer success, group:{}", groupName);
    }

    public synchronized void shutdownProducer() throws Exception {
        if (!producerStarted.get()) {
            return;
        }
        mqProducerWrapper.shutdown();
        producerStarted.compareAndSet(true, false);
        logger.info("shutdown producer success for group:{}", groupName);
    }


    public String getGroupName() {
        return groupName;
    }

    public boolean addGroupConsumerSession(Session session) {
        if (session == null
                || !StringUtils.equalsIgnoreCase(groupName, EventMeshUtil.buildClientGroup(session.getClient().getSubsystem(), session.getClient().getDcn()))) {
            logger.error("addGroupConsumerSession param error,session:{}", session);
            return false;
        }

        boolean r = false;
        try {
            this.groupLock.writeLock().lockInterruptibly();
            r = groupConsumerSessions.add(session);
            if (r) {
                logger.info("addGroupConsumerSession success, group:{} client:{}", groupName, session.getClient());
            }
        } catch (Exception e) {
            logger.error("addGroupConsumerSession error! group:{} client:{}", groupName, session.getClient(), e);
        } finally {
            this.groupLock.writeLock().unlock();
        }
        return r;
    }

    public boolean addGroupProducerSession(Session session) {
        if (session == null
                || !StringUtils.equalsIgnoreCase(groupName, EventMeshUtil.buildClientGroup(session.getClient().getSubsystem(), session.getClient().getDcn()))) {
            logger.error("addGroupProducerSession param error,session:{}", session);
            return false;
        }

        boolean r = false;
        try {
            this.groupLock.writeLock().lockInterruptibly();
            r = groupProducerSessions.add(session);
            if (r) {
                logger.info("addGroupProducerSession success, group:{} client:{}", groupName, session.getClient());
            }
        } catch (Exception e) {
            logger.error("addGroupProducerSession error! group:{} client:{}", groupName, session.getClient(), e);
        } finally {
            this.groupLock.writeLock().unlock();
        }
        return r;
    }

    public boolean removeGroupConsumerSession(Session session) {
        if (session == null
                || !StringUtils.equalsIgnoreCase(groupName, EventMeshUtil.buildClientGroup(session.getClient().getSubsystem(), session.getClient().getDcn()))) {
            logger.error("removeGroupConsumerSession param error,session:{}", session);
            return false;
        }

        boolean r = false;
        try {
            this.groupLock.writeLock().lockInterruptibly();
            r = groupConsumerSessions.remove(session);
            if (r) {
                logger.info("removeGroupConsumerSession success, group:{} client:{}", groupName, session.getClient());
            }
        } catch (Exception e) {
            logger.error("removeGroupConsumerSession error! group:{} client:{}", groupName, session.getClient(), e);
        } finally {
            this.groupLock.writeLock().unlock();
        }
        return r;
    }

    public boolean removeGroupProducerSession(Session session) {
        if (session == null
                || !StringUtils.equalsIgnoreCase(groupName, EventMeshUtil.buildClientGroup(session.getClient().getSubsystem(), session.getClient().getDcn()))) {
            logger.error("removeGroupProducerSession param error,session:{}", session);
            return false;
        }

        boolean r = false;
        try {
            this.groupLock.writeLock().lockInterruptibly();
            r = groupProducerSessions.remove(session);
            if (r) {
                logger.info("removeGroupProducerSession success, group:{} client:{}", groupName, session.getClient());
            }
        } catch (Exception e) {
            logger.error("removeGroupProducerSession error! group:{} client:{}", groupName, session.getClient(), e);
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
        keyValue.put("consumerGroup", groupName);
        keyValue.put("eventMeshIDC", eventMeshTCPConfiguration.eventMeshIDC);
        keyValue.put("instanceName", EventMeshUtil.buildMeshTcpClientID(sysId, dcn, "SUB", eventMeshTCPConfiguration.eventMeshCluster));

        persistentMsgConsumer.init(keyValue);
//        persistentMsgConsumer.registerMessageListener(new EventMeshMessageListenerConcurrently() {
//
//            @Override
//            public EventMeshConsumeConcurrentlyStatus handleMessage(MessageExt msg, EventMeshConsumeConcurrentlyContext context) {
//
//            if (msg == null)
//                return EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//
//            eventMeshTcpMonitor.getMq2eventMeshMsgNum().incrementAndGet();
//            String topic = msg.getTopic();
//            msg.putUserProperty(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
//            msg.putUserProperty(EventMeshConstants.REQ_RECEIVE_EVENTMESH_IP, accessConfiguration.eventMeshServerIp);
//            msg.putUserProperty(EventMeshConstants.BORN_TIMESTAMP, String.valueOf(msg.getBornTimestamp()));
//            msg.putUserProperty(EventMeshConstants.STORE_TIMESTAMP, String.valueOf(msg.getStoreTimestamp()));
//
//            if (!EventMeshUtil.isValidRMBTopic(topic)) {
//                return EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//            }
//
//            Session session = downstreamDispatchStrategy.select(groupName, topic, groupConsumerSessions);
//            String bizSeqNo = EventMeshUtil.getMessageBizSeq(msg);
//            if(session == null){
//                try {
//                    Integer sendBackTimes = MapUtils.getInteger(msg.getProperties(), EventMeshConstants.EVENTMESH_SEND_BACK_TIMES, new Integer(0));
//                    String sendBackFromEventMeshIp = MapUtils.getString(msg.getProperties(), EventMeshConstants.EVENTMESH_SEND_BACK_IP, "");
//                    logger.error("found no session to downstream msg,groupName:{}, topic:{}, bizSeqNo:{}", groupName, topic, bizSeqNo);
//
//                    if (sendBackTimes >= eventMeshTCPServer.getAccessConfiguration().eventMeshTcpSendBackMaxTimes) {
//                        logger.error("sendBack to broker over max times:{}, groupName:{}, topic:{}, bizSeqNo:{}", eventMeshTCPServer.getAccessConfiguration().eventMeshTcpSendBackMaxTimes, groupName, topic, bizSeqNo);
//                    } else {
//                        sendBackTimes++;
//                        msg.putUserProperty(EventMeshConstants.EVENTMESH_SEND_BACK_TIMES, sendBackTimes.toString());
//                        msg.putUserProperty(EventMeshConstants.EVENTMESH_SEND_BACK_IP, sendBackFromEventMeshIp);
//                        sendMsgBackToBroker(msg, bizSeqNo);
//                    }
//                } catch (Exception e){
//                    logger.warn("handle msg exception when no session found", e);
//                }
//
//                return EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//            }
//
//            DownStreamMsgContext downStreamMsgContext =
//                    new DownStreamMsgContext(msg, session, persistentMsgConsumer, (EventMeshConsumeConcurrentlyContext)context, false);
//
//            if(downstreamMap.size() < eventMeshTCPServer.getAccessConfiguration().eventMeshTcpDownStreamMapSize){
//                downstreamMap.putIfAbsent(downStreamMsgContext.seq, downStreamMsgContext);
//            }else{
//                logger.warn("downStreamMap is full,group:{}", groupName);
//            }
//
//            if (session.isCanDownStream()) {
//                session.downstreamMsg(downStreamMsgContext);
//                return EventMeshConsumeConcurrentlyStatus.CONSUME_FINISH;
//            }
//
//            logger.warn("session is busy,dispatch retry,seq:{}, session:{}, bizSeq:{}", downStreamMsgContext.seq, downStreamMsgContext.session.getClient(), bizSeqNo);
//            long delayTime = EventMeshUtil.isService(downStreamMsgContext.msgExt.getTopic()) ? 0 : eventMeshTCPServer.getAccessConfiguration().eventMeshTcpMsgRetryDelayInMills;
//            downStreamMsgContext.delay(delayTime);
//            eventMeshTcpRetryer.pushRetry(downStreamMsgContext);
//
//            return EventMeshConsumeConcurrentlyStatus.CONSUME_FINISH;
//            }
//        });
        inited4Persistent.compareAndSet(false, true);
        logger.info("init persistentMsgConsumer success, group:{}", groupName);
    }

    public synchronized void startClientGroupPersistentConsumer() throws Exception {
        if (started4Persistent.get()) {
            return;
        }
        persistentMsgConsumer.start();
        started4Persistent.compareAndSet(false, true);
        logger.info("starting persistentMsgConsumer success, group:{}", groupName);
    }

    public synchronized void initClientGroupBroadcastConsumer() throws Exception {
        if (inited4Broadcast.get()) {
            return;
        }

        Properties keyValue = new Properties();
        keyValue.put("isBroadcast", "true");
        keyValue.put("consumerGroup", groupName);
        keyValue.put("eventMeshIDC", eventMeshTCPConfiguration.eventMeshIDC);
        keyValue.put("instanceName", EventMeshUtil.buildMeshTcpClientID(sysId, dcn, "SUB", eventMeshTCPConfiguration.eventMeshCluster));
        broadCastMsgConsumer.init(keyValue);
//        broadCastMsgConsumer.registerMessageListener(new EventMeshMessageListenerConcurrently() {
//            @Override
//            public EventMeshConsumeConcurrentlyStatus handleMessage(MessageExt msg, EventMeshConsumeConcurrentlyContext context) {
//                if (msg == null)
//                    return EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//
//                eventMeshTcpMonitor.getMq2eventMeshMsgNum().incrementAndGet();
//
//                String topic = msg.getTopic();
//
//                msg.putUserProperty(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
//                msg.putUserProperty(EventMeshConstants.REQ_RECEIVE_EVENTMESH_IP, accessConfiguration.eventMeshServerIp);
//                msg.putUserProperty(EventMeshConstants.BORN_TIMESTAMP, String.valueOf(msg.getBornTimestamp()));
//                msg.putUserProperty(EventMeshConstants.STORE_TIMESTAMP, String.valueOf(msg.getStoreTimestamp()));
//
//                if (!EventMeshUtil.isValidRMBTopic(topic)) {
//                    return EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//                }
//
//                if(CollectionUtils.isEmpty(groupConsumerSessions)){
//                    logger.warn("found no session to downstream broadcast msg");
//                    return EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//                }
//
//                Iterator<Session> sessionsItr = groupConsumerSessions.iterator();
//
//                while (sessionsItr.hasNext()) {
//                    Session session = sessionsItr.next();
//
//                    if (!session.isAvailable(topic)) {
//                        logger.warn("downstream broadcast msg,session is not available,client:{}",session.getClient());
//                        continue;
//                    }
//
//                    DownStreamMsgContext downStreamMsgContext =
//                            new DownStreamMsgContext(msg, session, broadCastMsgConsumer, context, false);
//
//                    if (session.isCanDownStream()) {
//                        session.downstreamMsg(downStreamMsgContext);
//                        continue;
//                    }
//
//                    logger.warn("downstream broadcast msg,session is busy,dispatch retry,seq:{}, session:{}, bizSeq:{}", downStreamMsgContext.seq, downStreamMsgContext.session.getClient(), EventMeshUtil.getMessageBizSeq(downStreamMsgContext.msgExt));
//                    long delayTime = EventMeshUtil.isService(downStreamMsgContext.msgExt.getTopic()) ? 0 : eventMeshTCPServer.getAccessConfiguration().eventMeshTcpMsgRetryDelayInMills;
//                    downStreamMsgContext.delay(delayTime);
//                    eventMeshTcpRetryer.pushRetry(downStreamMsgContext);
//                }
//
//                return EventMeshConsumeConcurrentlyStatus.CONSUME_FINISH;
//            }
//        });
        inited4Broadcast.compareAndSet(false, true);
        logger.info("init broadCastMsgConsumer success, group:{}", groupName);
    }

    public synchronized void startClientGroupBroadcastConsumer() throws Exception {
        if (started4Broadcast.get()) {
            return;
        }
        broadCastMsgConsumer.start();
        started4Broadcast.compareAndSet(false, true);
        logger.info("starting broadCastMsgConsumer success, group:{}", groupName);
    }

    public void subscribe(String topic) throws Exception {
        AsyncMessageListener listener = null;
        if (EventMeshUtil.isBroadcast(topic)) {
            listener = new AsyncMessageListener() {
                @Override
                public void consume(Message message, AsyncConsumeContext context) {

                    eventMeshTcpMonitor.getMq2EventMeshMsgNum().incrementAndGet();
                    String topic = message.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION);
                    message.getSystemProperties().put(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
                    message.getSystemProperties().put(EventMeshConstants.REQ_RECEIVE_EVENTMESH_IP, eventMeshTCPConfiguration.eventMeshServerIp);

                    if (CollectionUtils.isEmpty(groupConsumerSessions)) {
                        logger.warn("found no session to downstream broadcast msg");
//                        context.attributes().put(NonStandardKeys.MESSAGE_CONSUME_STATUS, EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS.name());
//                        context.ack();
                        context.commit(Action.CommitMessage);
                        return;
                    }

                    Iterator<Session> sessionsItr = groupConsumerSessions.iterator();

                    DownStreamMsgContext downStreamMsgContext =
                            new DownStreamMsgContext(message, null, broadCastMsgConsumer, broadCastMsgConsumer.getContext(), false);

                    while (sessionsItr.hasNext()) {
                        Session session = sessionsItr.next();

                        if (!session.isAvailable(topic)) {
                            logger.warn("downstream broadcast msg,session is not available,client:{}", session.getClient());
                            continue;
                        }

                        downStreamMsgContext.session = session;

                        //downstream broadcast msg asynchronously
                        eventMeshTCPServer.getBroadcastMsgDownstreamExecutorService().submit(new Runnable() {
                            @Override
                            public void run() {
                                //msg put in eventmesh,waiting client ack
                                session.getPusher().unAckMsg(downStreamMsgContext.seq, downStreamMsgContext);
                                session.downstreamMsg(downStreamMsgContext);
                            }
                        });
                    }

//                    context.attributes().put(NonStandardKeys.MESSAGE_CONSUME_STATUS, EventMeshConsumeConcurrentlyStatus.CONSUME_FINISH.name());
//                    context.ack();
                    context.commit(Action.CommitMessage);
                }
            };
            broadCastMsgConsumer.subscribe(topic, listener);
        } else {
            listener = new AsyncMessageListener() {
                @Override
                public void consume(Message message, AsyncConsumeContext context) {
                    eventMeshTcpMonitor.getMq2EventMeshMsgNum().incrementAndGet();
                    String topic = message.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION);
                    message.getSystemProperties().put(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
                    message.getSystemProperties().put(EventMeshConstants.REQ_RECEIVE_EVENTMESH_IP, eventMeshTCPConfiguration.eventMeshServerIp);

                    Session session = downstreamDispatchStrategy.select(groupName, topic, groupConsumerSessions);
                    String bizSeqNo = EventMeshUtil.getMessageBizSeq(message);
                    if (session == null) {
                        try {
                            Integer sendBackTimes = new Integer(0);
                            String sendBackFromEventMeshIp = "";
                            if (StringUtils.isNotBlank(message.getSystemProperties(EventMeshConstants.EVENTMESH_SEND_BACK_TIMES))) {
                                sendBackTimes = Integer.valueOf(message.getSystemProperties(EventMeshConstants.EVENTMESH_SEND_BACK_TIMES));
                            }
                            if (StringUtils.isNotBlank(message.getSystemProperties(EventMeshConstants.EVENTMESH_SEND_BACK_IP))) {
                                sendBackFromEventMeshIp = message.getSystemProperties(EventMeshConstants.EVENTMESH_SEND_BACK_IP);
                            }

                            logger.error("found no session to downstream msg,groupName:{}, topic:{}, bizSeqNo:{}, sendBackTimes:{}, sendBackFromEventMeshIp:{}", groupName, topic, bizSeqNo, sendBackTimes, sendBackFromEventMeshIp);

                            if (sendBackTimes >= eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshTcpSendBackMaxTimes) {
                                logger.error("sendBack to broker over max times:{}, groupName:{}, topic:{}, bizSeqNo:{}", eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshTcpSendBackMaxTimes, groupName, topic, bizSeqNo);
                            } else {
                                sendBackTimes++;
                                message.getSystemProperties().put(EventMeshConstants.EVENTMESH_SEND_BACK_TIMES, sendBackTimes.toString());
                                message.getSystemProperties().put(EventMeshConstants.EVENTMESH_SEND_BACK_IP, eventMeshTCPConfiguration.eventMeshServerIp);
                                sendMsgBackToBroker(message, bizSeqNo);
                            }
                        } catch (Exception e) {
                            logger.warn("handle msg exception when no session found", e);
                        }

//                        context.attributes().put(NonStandardKeys.MESSAGE_CONSUME_STATUS, EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS.name());
//                        context.ack();
                        context.commit(Action.CommitMessage);
                        return;
                    }

                    DownStreamMsgContext downStreamMsgContext =
                            new DownStreamMsgContext(message, session, persistentMsgConsumer, persistentMsgConsumer.getContext(), false);
                    //msg put in eventmesh,waiting client ack
                    session.getPusher().unAckMsg(downStreamMsgContext.seq, downStreamMsgContext);
                    session.downstreamMsg(downStreamMsgContext);
//                    context.attributes().put(NonStandardKeys.MESSAGE_CONSUME_STATUS, EventMeshConsumeConcurrentlyStatus.CONSUME_FINISH.name());
//                    context.ack();
                    context.commit(Action.CommitMessage);
                }
            };
            persistentMsgConsumer.subscribe(topic, listener);
        }
    }

    public void unsubscribe(String topic) throws Exception {
        if (EventMeshUtil.isBroadcast(topic)) {
            broadCastMsgConsumer.unsubscribe(topic);
        } else {
            persistentMsgConsumer.unsubscribe(topic);
        }
    }

    public synchronized void shutdownBroadCastConsumer() throws Exception {
        if (started4Broadcast.get()) {
            broadCastMsgConsumer.shutdown();
            logger.info("broadcast consumer group:{} shutdown...", groupName);
        }
        started4Broadcast.compareAndSet(true, false);
        inited4Broadcast.compareAndSet(true, false);
        broadCastMsgConsumer = null;
    }

    public synchronized void shutdownPersistentConsumer() throws Exception {

        if (started4Persistent.get()) {
            persistentMsgConsumer.shutdown();
            logger.info("persistent consumer group:{} shutdown...", groupName);
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

    public void setGroupName(String groupName) {
        this.groupName = groupName;
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

    public void setDownstreamDispatchStrategy(DownstreamDispatchStrategy downstreamDispatchStrategy) {
        this.downstreamDispatchStrategy = downstreamDispatchStrategy;
    }

    public String getSysId() {
        return sysId;
    }

    private String pushMsgToEventMesh(Message msg, String ip, int port) throws Exception {
        StringBuilder targetUrl = new StringBuilder();
        targetUrl.append("http://").append(ip).append(":").append(port).append("/eventMesh/msg/push");
        HttpTinyClient.HttpResult result = null;

        try {
            logger.info("pushMsgToEventMesh,targetUrl:{},msg:{}", targetUrl.toString(), msg.toString());
            List<String> paramValues = new ArrayList<String>();
            paramValues.add("msg");
            paramValues.add(JSON.toJSONString(msg));
            paramValues.add("group");
            paramValues.add(groupName);

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
            throw new Exception("httpPost targetUrl[" + targetUrl + "] is not OK when getContentThroughHttp, httpResult: " + result + ".");
        }
    }

    public MQConsumerWrapper getPersistentMsgConsumer() {
        return persistentMsgConsumer;
    }

    private void sendMsgBackToBroker(Message msg, String bizSeqNo) throws Exception {
        try {
            String topic = msg.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION);
            logger.warn("send msg back to broker, bizSeqno:{}, topic:{}", bizSeqNo, topic);

            send(new UpStreamMsgContext(null, null, msg), new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    logger.info("consumerGroup:{} consume fail, sendMessageBack success, bizSeqno:{}, topic:{}", groupName, bizSeqNo, topic);
                }

                @Override
                public void onException(OnExceptionContext context) {
                    logger.warn("consumerGroup:{} consume fail, sendMessageBack fail, bizSeqno:{}, topic:{}", groupName, bizSeqNo, topic);
                }

//                @Override
//                public void onException(Throwable e) {
//                    logger.warn("consumerGroup:{} consume fail, sendMessageBack fail, bizSeqno:{}, topic:{}", groupName, bizSeqNo, topic);
//                }
            });
            eventMeshTcpMonitor.getEventMesh2mqMsgNum().incrementAndGet();
        } catch (Exception e) {
            logger.warn("try send msg back to broker failed");
            throw e;
        }
    }
}
