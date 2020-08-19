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

package cn.webank.emesher.core.protocol.tcp.client.group;

import cn.webank.defibus.client.common.DeFiBusClientConfig;
import cn.webank.defibus.client.impl.producer.RRCallback;
import cn.webank.defibus.consumer.DeFiBusPushConsumer;
import cn.webank.defibus.producer.DeFiBusProducer;
import cn.webank.emesher.boot.ProxyTCPServer;
import cn.webank.emesher.configuration.AccessConfiguration;
import cn.webank.emesher.constants.ProxyConstants;
import cn.webank.emesher.core.protocol.tcp.client.group.dispatch.DownstreamDispatchStrategy;
import cn.webank.emesher.core.protocol.tcp.client.session.Session;
import cn.webank.emesher.core.protocol.tcp.client.session.push.DownStreamMsgContext;
import cn.webank.emesher.core.protocol.tcp.client.session.push.retry.ProxyTcpRetryer;
import cn.webank.emesher.core.protocol.tcp.client.session.send.UpStreamMsgContext;
import cn.webank.emesher.metrics.tcp.ProxyTcpMonitor;
import cn.webank.emesher.util.ProxyUtil;
import com.alibaba.fastjson.JSON;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyContext;
import org.apache.rocketmq.client.impl.consumer.ProxyConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.impl.consumer.ProxyMessageListenerConcurrentlyOnce;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageClientIDSetter;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.common.utils.HttpTinyClient;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ClientGroupWrapper {

    public static Logger logger = LoggerFactory.getLogger(ClientGroupWrapper.class);

    private String groupName;

    private String sysId;

    private String dcn;

    private AccessConfiguration accessConfiguration;

    private ProxyTCPServer proxyTCPServer;

    private ProxyTcpRetryer proxyTcpRetryer;

    private ProxyTcpMonitor proxyTcpMonitor;

    private DownstreamDispatchStrategy downstreamDispatchStrategy;

    private final ReadWriteLock groupLock = new ReentrantReadWriteLock();

    public Set<Session> groupConsumerSessions = new HashSet<Session>();

    public Set<Session> groupProducerSessions = new HashSet<Session>();

    public AtomicBoolean started4Persistent = new AtomicBoolean(Boolean.FALSE);

    public AtomicBoolean started4Broadcast = new AtomicBoolean(Boolean.FALSE);

    public AtomicBoolean inited4Persistent = new AtomicBoolean(Boolean.FALSE);

    public AtomicBoolean inited4Broadcast = new AtomicBoolean(Boolean.FALSE);

    private DeFiBusClientConfig clientConfig4Clustering;

    private DeFiBusPushConsumer persistentMsgConsumer;

    private DeFiBusClientConfig clientConfig4Broadcasting;

    private DeFiBusPushConsumer broadCastMsgConsumer;

    private ConcurrentHashMap<String, Set<Session>> topic2sessionInGroupMapping = new ConcurrentHashMap<String, Set<Session>>();

    private ConcurrentHashMap<String, DownStreamMsgContext> downstreamMap = new ConcurrentHashMap<String, DownStreamMsgContext>();

    public ConcurrentHashMap<String, DownStreamMsgContext> getDownstreamMap() {
        return downstreamMap;
    }

    public AtomicBoolean producerStarted = new AtomicBoolean(Boolean.FALSE);

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
        defibusProducer.publish(upStreamMsgContext.getMsg(), sendCallback);
        return true;
    }

    public void request(UpStreamMsgContext upStreamMsgContext, SendCallback sendCallback, RRCallback rrCallback, long timeout)
            throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        defibusProducer.request(upStreamMsgContext.getMsg(), sendCallback, rrCallback, timeout);
    }

    public boolean reply(UpStreamMsgContext upStreamMsgContext) throws Exception {
        defibusProducer.reply(upStreamMsgContext.getMsg(), new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {

            }

            @Override
            public void onException(Throwable e) {
                String bizSeqNo = upStreamMsgContext.getMsg().getKeys();
                logger.error("reply err! topic:{}, bizSeqNo:{}, client:{}", upStreamMsgContext.getMsg().getTopic(), bizSeqNo, upStreamMsgContext.getSession().getClient(), e);
            }
        });
        return true;
    }

    private DeFiBusProducer defibusProducer;

    public DeFiBusProducer getDefibusProducer() {
        return defibusProducer;
    }

    public boolean addSubscription(String topic, Session session) throws Exception {
        if (session == null
                || !StringUtils.equalsIgnoreCase(groupName, ProxyUtil.buildClientGroup(session.getClient().getSubsystem(), session.getClient().getDcn()))) {
            logger.error("addSubscription param error,topic:{},session:{}",topic, session);
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
            if(r){
                logger.info("addSubscription success, group:{} topic:{} client:{}", groupName, topic, session.getClient());
            }else{
                logger.warn("addSubscription fail, group:{} topic:{} client:{}", groupName, topic, session.getClient());
            }
        } catch (Exception e) {
            logger.error("addSubscription error! topic:{} client:{}", topic, session.getClient(), e);
            throw  new Exception("addSubscription fail");
        } finally {
            this.groupLock.writeLock().unlock();
        }
        return r;
    }

    public boolean removeSubscription(String topic, Session session) {
        if (session == null
                || !StringUtils.equalsIgnoreCase(groupName, ProxyUtil.buildClientGroup(session.getClient().getSubsystem(), session.getClient().getDcn()))) {
            logger.error("removeSubscription param error,topic:{},session:{}",topic, session);
            return false;
        }

        boolean r = false;
        try {
            this.groupLock.writeLock().lockInterruptibly();
            if (topic2sessionInGroupMapping.containsKey(topic)) {
                r = topic2sessionInGroupMapping.get(topic).remove(session);
                if(r){
                    logger.info("removeSubscription remove session success, group:{} topic:{} client:{}", groupName, topic, session.getClient());
                }else{
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

    public synchronized void startClientGroupProducer() throws MQClientException {
        if (producerStarted.get()) {
            return;
        }
        DeFiBusClientConfig wcc = new DeFiBusClientConfig();
        wcc.setClusterPrefix(accessConfiguration.proxyIDC);
        wcc.setProducerGroup(ProxyUtil.buildClientProducerGroup(sysId, dcn));
        if (StringUtils.isEmpty(accessConfiguration.namesrvAddr)) {
            wcc.setWsAddr(ProxyUtil.buildCCAddr(accessConfiguration.configCenterAddr));
        } else {
            wcc.setNamesrvAddr(accessConfiguration.namesrvAddr);
        }

        MessageClientIDSetter.createUniqID();
        defibusProducer = new DeFiBusProducer(wcc);
        defibusProducer.getDefaultMQProducer().setVipChannelEnabled(false);
        defibusProducer.getDefaultMQProducer().setInstanceName(ProxyUtil.buildProxyTcpClientID(sysId, dcn, "PUB", accessConfiguration.proxyCluster));//set instance name
        defibusProducer.getDefaultMQProducer().setCompressMsgBodyOverHowmuch(1024 * 2);

        defibusProducer.start();
        producerStarted.compareAndSet(false, true);
        logger.info("starting producer success, group:{} clientConfig:{}", groupName, wcc);
    }

    public synchronized void shutdownProducer() {
        if (!producerStarted.get()) {
            return;
        }
        defibusProducer.shutdown();
        producerStarted.compareAndSet(true, false);
        logger.info("shutdown producer success for group:{}", groupName);
    }

    public ClientGroupWrapper(String sysId, String dcn,
                              ProxyTCPServer proxyTCPServer,
                              DownstreamDispatchStrategy downstreamDispatchStrategy) {
        this.sysId = sysId;
        this.dcn = dcn;
        this.proxyTCPServer = proxyTCPServer;
        this.accessConfiguration = proxyTCPServer.getAccessConfiguration();
        this.proxyTcpRetryer = proxyTCPServer.getProxyTcpRetryer();
        this.proxyTcpMonitor = proxyTCPServer.getProxyTcpMonitor();
        this.groupName = ProxyUtil.buildClientGroup(sysId, dcn);
        this.downstreamDispatchStrategy = downstreamDispatchStrategy;
    }

    public String getGroupName() {
        return groupName;
    }

    public boolean addGroupConsumerSession(Session session) {
        if (session == null
                || !StringUtils.equalsIgnoreCase(groupName, ProxyUtil.buildClientGroup(session.getClient().getSubsystem(), session.getClient().getDcn()))) {
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
                || !StringUtils.equalsIgnoreCase(groupName, ProxyUtil.buildClientGroup(session.getClient().getSubsystem(), session.getClient().getDcn()))) {
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
                || !StringUtils.equalsIgnoreCase(groupName, ProxyUtil.buildClientGroup(session.getClient().getSubsystem(), session.getClient().getDcn()))) {
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
                || !StringUtils.equalsIgnoreCase(groupName, ProxyUtil.buildClientGroup(session.getClient().getSubsystem(), session.getClient().getDcn()))) {
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

    private DeFiBusClientConfig initDefibusClientConfig(boolean broadcast) {
        DeFiBusClientConfig wcc = new DeFiBusClientConfig();
        wcc.setPubWindowSize(accessConfiguration.pubWindow);
        wcc.setAckWindowSize(accessConfiguration.ackWindow);
        wcc.setThreadPoolCoreSize(accessConfiguration.consumeThreadMin);
        wcc.setThreadPoolMaxSize(accessConfiguration.consumeThreadMax);
        wcc.setPullBatchSize(accessConfiguration.pullBatchSize);
        wcc.setClusterPrefix(accessConfiguration.proxyIDC);

        wcc.setConsumeTimeout(accessConfiguration.consumeTimeout);
        if (broadcast) {
            wcc.setConsumerGroup(ProxyUtil.buildBroadcastClientConsumerGroup(sysId, dcn));
        } else {
            wcc.setConsumerGroup(ProxyUtil.buildPersistentClientConsumerGroup(sysId, dcn));
        }

        if (StringUtils.isEmpty(accessConfiguration.namesrvAddr)) {
            wcc.setWsAddr(ProxyUtil.buildCCAddr(accessConfiguration.configCenterAddr));
        } else {
            wcc.setNamesrvAddr(accessConfiguration.namesrvAddr);
        }

        return wcc;
    }

    public synchronized void initClientGroupPersistentConsumer() {
        if(inited4Persistent.get()){
            return;
        }
        clientConfig4Clustering = initDefibusClientConfig(false);
        persistentMsgConsumer = new DeFiBusPushConsumer(clientConfig4Clustering);
        persistentMsgConsumer.getDefaultMQPushConsumer().setInstanceName(ProxyUtil.buildProxyTcpClientID(sysId, dcn, "SUB", accessConfiguration.proxyCluster));
        persistentMsgConsumer.getDefaultMQPushConsumer().setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        persistentMsgConsumer.getDefaultMQPushConsumer().setMessageModel(MessageModel.CLUSTERING);
        persistentMsgConsumer.getDefaultMQPushConsumer().setNamesrvAddr(clientConfig4Clustering.getNamesrvAddr());
        persistentMsgConsumer.registerMessageListener(new ProxyMessageListenerConcurrentlyOnce() {

            @Override
            public ProxyConsumeConcurrentlyStatus handleMessage(MessageExt msg, ConsumeMessageConcurrentlyContext context) {

            if (msg == null)
                return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;

            proxyTcpMonitor.getMq2proxyMsgNum().incrementAndGet();
            String topic = msg.getTopic();
            msg.putUserProperty(ProxyConstants.REQ_MQ2PROXY_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
            msg.putUserProperty(ProxyConstants.REQ_RECEIVE_PROXY_IP, accessConfiguration.proxyServerIp);
            msg.putUserProperty(ProxyConstants.BORN_TIMESTAMP, String.valueOf(msg.getBornTimestamp()));
            msg.putUserProperty(ProxyConstants.STORE_TIMESTAMP, String.valueOf(msg.getStoreTimestamp()));

            if (!ProxyUtil.isValidRMBTopic(topic)) {
                return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }

            Session session = downstreamDispatchStrategy.select(groupName, topic, groupConsumerSessions);
            String bizSeqNo = ProxyUtil.getMessageBizSeq(msg);
            if(session == null){
                try {
                    Integer sendBackTimes = MapUtils.getInteger(msg.getProperties(), ProxyConstants.PROXY_SEND_BACK_TIMES, new Integer(0));
                    String sendBackFromProxyIp = MapUtils.getString(msg.getProperties(), ProxyConstants.PROXY_SEND_BACK_IP, "");
                    logger.error("found no session to downstream msg,groupName:{}, topic:{}, bizSeqNo:{}", groupName, topic, bizSeqNo);

                    if (sendBackTimes >= proxyTCPServer.getAccessConfiguration().proxyTcpSendBackMaxTimes) {
                        logger.error("sendBack to broker over max times:{}, groupName:{}, topic:{}, bizSeqNo:{}", proxyTCPServer.getAccessConfiguration().proxyTcpSendBackMaxTimes, groupName, topic, bizSeqNo);
                    } else {
                        sendBackTimes++;
                        msg.putUserProperty(ProxyConstants.PROXY_SEND_BACK_TIMES, sendBackTimes.toString());
                        msg.putUserProperty(ProxyConstants.PROXY_SEND_BACK_IP, sendBackFromProxyIp);
                        sendMsgBackToBroker(msg, bizSeqNo);
                    }
                } catch (Exception e){
                    logger.warn("handle msg exception when no session found", e);
                }

                return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }

            DownStreamMsgContext downStreamMsgContext =
                    new DownStreamMsgContext(msg, session, persistentMsgConsumer, (ConsumeMessageConcurrentlyContext)context, false);

            if(downstreamMap.size() < proxyTCPServer.getAccessConfiguration().proxyTcpDownStreamMapSize){
                downstreamMap.putIfAbsent(downStreamMsgContext.seq, downStreamMsgContext);
            }else{
                logger.warn("downStreamMap is full,group:{}", groupName);
            }

            if (session.isCanDownStream()) {
                session.downstreamMsg(downStreamMsgContext);
                return ProxyConsumeConcurrentlyStatus.CONSUME_FINISH;
            }

            logger.warn("session is busy,dispatch retry,seq:{}, session:{}, bizSeq:{}", downStreamMsgContext.seq, downStreamMsgContext.session.getClient(), bizSeqNo);
            long delayTime = ProxyUtil.isService(downStreamMsgContext.msgExt.getTopic()) ? 0 : proxyTCPServer.getAccessConfiguration().proxyTcpMsgRetryDelayInMills;
            downStreamMsgContext.delay(delayTime);
            proxyTcpRetryer.pushRetry(downStreamMsgContext);

            return ProxyConsumeConcurrentlyStatus.CONSUME_FINISH;
            }
        });
        inited4Persistent.compareAndSet(false, true);
        logger.info("init persistentMsgConsumer success, group:{} persistentMsgConsumerMQClientConfig:{}", groupName, clientConfig4Clustering);
    }

    public synchronized void startClientGroupPersistentConsumer() throws MQClientException {
        if (started4Persistent.get()) {
            return;
        }
        persistentMsgConsumer.start();
        persistentMsgConsumer.getDefaultMQPushConsumer().unsubscribe(MixAll.getRetryTopic(clientConfig4Clustering.getConsumerGroup()));
        started4Persistent.compareAndSet(false, true);
        logger.info("starting persistentMsgConsumer success, group:{}", groupName);
    }

    public synchronized void initClientGroupBroadcastConsumer() {
        if(inited4Broadcast.get()){
            return;
        }
        clientConfig4Broadcasting = initDefibusClientConfig(true);
        broadCastMsgConsumer = new DeFiBusPushConsumer(clientConfig4Broadcasting);
        broadCastMsgConsumer.getDefaultMQPushConsumer().setInstanceName(ProxyUtil.buildProxyTcpClientID(sysId, dcn, "SUB", accessConfiguration.proxyCluster));
        broadCastMsgConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        broadCastMsgConsumer.getDefaultMQPushConsumer().setMessageModel(MessageModel.BROADCASTING);
        broadCastMsgConsumer.setNamesrvAddr(clientConfig4Broadcasting.getNamesrvAddr());
        broadCastMsgConsumer.registerMessageListener(new ProxyMessageListenerConcurrentlyOnce() {
            @Override
            public ProxyConsumeConcurrentlyStatus handleMessage(MessageExt msg, ConsumeMessageConcurrentlyContext context) {
                if (msg == null)
                    return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;

                proxyTcpMonitor.getMq2proxyMsgNum().incrementAndGet();

                String topic = msg.getTopic();

                msg.putUserProperty(ProxyConstants.REQ_MQ2PROXY_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
                msg.putUserProperty(ProxyConstants.REQ_RECEIVE_PROXY_IP, accessConfiguration.proxyServerIp);
                msg.putUserProperty(ProxyConstants.BORN_TIMESTAMP, String.valueOf(msg.getBornTimestamp()));
                msg.putUserProperty(ProxyConstants.STORE_TIMESTAMP, String.valueOf(msg.getStoreTimestamp()));

                if (!ProxyUtil.isValidRMBTopic(topic)) {
                    return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }

                if(CollectionUtils.isEmpty(groupConsumerSessions)){
                    logger.warn("found no session to downstream broadcast msg");
                    return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }

                Iterator<Session> sessionsItr = groupConsumerSessions.iterator();

                while (sessionsItr.hasNext()) {
                    Session session = sessionsItr.next();

                    if (!session.isAvailable(topic)) {
                        logger.warn("downstream broadcast msg,session is not available,client:{}",session.getClient());
                        continue;
                    }

                    DownStreamMsgContext downStreamMsgContext =
                            new DownStreamMsgContext(msg, session, broadCastMsgConsumer, context, false);

                    if (session.isCanDownStream()) {
                        session.downstreamMsg(downStreamMsgContext);
                        continue;
                    }

                    logger.warn("downstream broadcast msg,session is busy,dispatch retry,seq:{}, session:{}, bizSeq:{}", downStreamMsgContext.seq, downStreamMsgContext.session.getClient(), ProxyUtil.getMessageBizSeq(downStreamMsgContext.msgExt));
                    long delayTime = ProxyUtil.isService(downStreamMsgContext.msgExt.getTopic()) ? 0 : proxyTCPServer.getAccessConfiguration().proxyTcpMsgRetryDelayInMills;
                    downStreamMsgContext.delay(delayTime);
                    proxyTcpRetryer.pushRetry(downStreamMsgContext);
                }

                return ProxyConsumeConcurrentlyStatus.CONSUME_FINISH;
            }
        });
        inited4Broadcast.compareAndSet(false, true);
        logger.info("init broadCastMsgConsumer success, group:{} broadcastMsgConsumerMQClientConfig:{}", groupName, clientConfig4Broadcasting);
    }

    public synchronized void startClientGroupBroadcastConsumer() throws MQClientException{
        if (started4Broadcast.get()) {
            return;
        }
        broadCastMsgConsumer.start();
        broadCastMsgConsumer.getDefaultMQPushConsumer().unsubscribe(MixAll.getRetryTopic(clientConfig4Broadcasting.getConsumerGroup()));
        started4Broadcast.compareAndSet(false, true);
        logger.info("starting broadCastMsgConsumer success, group:{}", groupName);
    }

    public void subscribe(String topic) throws Exception {
        if (ProxyUtil.isBroadcast(topic)) {
            broadCastMsgConsumer.subscribe(topic);
        } else {
            persistentMsgConsumer.subscribe(topic);
        }
    }

    public void unsubscribe(String topic) {
        if (ProxyUtil.isBroadcast(topic)) {
            broadCastMsgConsumer.unsubscribe(topic);
        } else {
            persistentMsgConsumer.unsubscribe(topic);
        }
    }

    public synchronized void shutdownBroadCastConsumer() {
        if (started4Broadcast.get()) {
            broadCastMsgConsumer.shutdown();
            logger.info("broadcast consumer group:{} shutdown...", groupName);
        }
        started4Broadcast.compareAndSet(true, false);
        inited4Broadcast.compareAndSet(true, false);
        broadCastMsgConsumer = null;
        clientConfig4Broadcasting = null;
    }

    public synchronized void shutdownPersistentConsumer() {

        if (started4Persistent.get()) {
            persistentMsgConsumer.shutdown();
            logger.info("persistent consumer group:{} shutdown...", groupName);
        }
        started4Persistent.compareAndSet(true, false);
        inited4Persistent.compareAndSet(true,false);
        persistentMsgConsumer = null;
        clientConfig4Clustering = null;
    }

    public Set<Session> getGroupConsumerSessions() {
        Set<Session> res = null;
        try {
            this.groupLock.readLock().lockInterruptibly();
            res = groupConsumerSessions;
        } catch (Exception e) {
        } finally {
            this.groupLock.readLock().unlock();
        }
        return res;
    }


    public Set<Session> getGroupProducerSessions() {
        Set<Session> res = null;
        try {
            this.groupLock.readLock().lockInterruptibly();
            res = groupProducerSessions;
        } catch (Exception e) {
        } finally {
            this.groupLock.readLock().unlock();
        }
        return res;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public AccessConfiguration getAccessConfiguration() {
        return accessConfiguration;
    }

    public void setAccessConfiguration(AccessConfiguration accessConfiguration) {
        this.accessConfiguration = accessConfiguration;
    }

    public ProxyTcpRetryer getProxyTcpRetryer() {
        return proxyTcpRetryer;
    }

    public void setProxyTcpRetryer(ProxyTcpRetryer proxyTcpRetryer) {
        this.proxyTcpRetryer = proxyTcpRetryer;
    }

    public ProxyTcpMonitor getProxyTcpMonitor() {
        return proxyTcpMonitor;
    }

    public void setProxyTcpMonitor(ProxyTcpMonitor proxyTcpMonitor) {
        this.proxyTcpMonitor = proxyTcpMonitor;
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

    private String pushMsgToProxy(MessageExt msg, String ip, int port) {
        StringBuilder targetUrl = new StringBuilder();
        targetUrl.append("http://").append(ip).append(":").append(port).append("/proxy/msg/push");
        HttpTinyClient.HttpResult result = null;

        try {
            logger.info("pushMsgToProxy,targetUrl:{},msg:{}",targetUrl.toString(),msg.toString());
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
            throw new RuntimeException("httpPost " + targetUrl + " is fail," +  e);
        }

        if (200 == result.code && result.content != null) {
            return result.content;

        } else {
            throw new RuntimeException("httpPost targetUrl[" + targetUrl + "] is not OK when getContentThroughHttp, httpResult: " + result + ".");
        }
    }

    public DeFiBusPushConsumer getPersistentMsgConsumer() {
        return persistentMsgConsumer;
    }

    private void sendMsgBackToBroker(MessageExt msg, String bizSeqNo){
        try {
            logger.warn("send msg back to broker, bizSeqno:{}, topic:{}",bizSeqNo, msg.getTopic());

            send(new UpStreamMsgContext(null,null, msg), new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    logger.info("consumerGroup:{} consume fail, sendMessageBack success, bizSeqno:{}, topic:{}", groupName, bizSeqNo, msg.getTopic());
                }

                @Override
                public void onException(Throwable e) {
                    logger.warn("consumerGroup:{} consume fail, sendMessageBack fail, bizSeqno:{}, topic:{}", groupName, bizSeqNo, msg.getTopic());
                }
            });
            proxyTcpMonitor.getProxy2mqMsgNum().incrementAndGet();
        }catch (Exception e){
            logger.warn("try send msg back to broker failed");
        }
    }
}
