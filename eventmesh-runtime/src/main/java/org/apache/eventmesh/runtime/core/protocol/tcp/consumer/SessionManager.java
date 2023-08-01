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

package org.apache.eventmesh.runtime.core.protocol.tcp.consumer;

import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.consumer.dispatch.DownstreamDispatchStrategy;
import org.apache.eventmesh.runtime.core.protocol.tcp.consumer.dispatch.FreePriorityDispatchStrategy;
import org.apache.eventmesh.runtime.core.protocol.tcp.consumer.push.DownStreamMsgContext;
import org.apache.eventmesh.runtime.core.protocol.tcp.session.Session;
import org.apache.eventmesh.runtime.core.protocol.tcp.session.SessionState;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SessionManager {

    private static final Logger SESSION_LOGGER = LoggerFactory.getLogger("tcpSessionLogger");

    private EventMeshTCPServer eventMeshTCPServer;

    private final ConcurrentHashMap<InetSocketAddress, Session> sessionTable = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String /** subsystem eg . 5109 or 5109-1A0 */, PubSubManager> clientGroupMap =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String /** subsystem eg . 5109 or 5109-1A0 */, Object> lockMap =
            new ConcurrentHashMap<>();

    public SessionManager(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    public void init() throws Exception {
        initSessionCleaner();
        initDownStreamMsgContextCleaner();
        log.info("ClientSessionGroupMapping inited......");
    }

    public void start() throws Exception {
        log.info("ClientSessionGroupMapping started......");
    }

    public void shutdown() throws Exception {
        log.info("begin to close sessions gracefully");
        for (PubSubManager pubSubManager : clientGroupMap.values()) {
            for (Session subSession : pubSubManager.getGroupConsumerSessions()) {
                try {
                    EventMeshTcp2Client.serverGoodby2Client(eventMeshTCPServer.getTcpThreadPoolGroup(), subSession, this);
                } catch (Exception e) {
                    log.error("say goodbye to subSession error! {}", subSession, e);
                }
            }

            for (Session pubSession : pubSubManager.getGroupProducerSessions()) {
                try {
                    EventMeshTcp2Client.serverGoodby2Client(eventMeshTCPServer.getTcpThreadPoolGroup(), pubSession, this);
                } catch (Exception e) {
                    log.error("say goodbye to pubSession error! {}", pubSession, e);
                }
            }

            ThreadUtils.sleep(eventMeshTCPServer.getEventMeshTCPConfiguration().getGracefulShutdownSleepIntervalInMills(), TimeUnit.MILLISECONDS);

        }

        ThreadUtils.sleep(1, TimeUnit.SECONDS);

        sessionTable.values().parallelStream().forEach(itr -> {
            try {
                EventMeshTcp2Client.serverGoodby2Client(eventMeshTCPServer.getTcpThreadPoolGroup(), itr, this);
            } catch (Exception e) {
                log.error("say goodbye to session error! {}", itr, e);
            }
        });
        ThreadUtils.randomPause(50);
        log.info("ClientSessionGroupMapping shutdown......");
    }


    public Session createSession(UserAgent user, ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress addr = (InetSocketAddress) ctx.channel().remoteAddress();
        user.setHost(addr.getHostString());
        user.setPort(addr.getPort());
        Session session;
        if (!sessionTable.containsKey(addr)) {
            log.info("createSession client[{}]", RemotingHelper.parseChannelRemoteAddr(ctx.channel()));
            session = new Session(user, ctx, eventMeshTCPServer.getEventMeshTCPConfiguration());
            initPubSubManager(user, session);
            sessionTable.put(addr, session);
            SESSION_LOGGER.info("session|open|succeed|user={}", user);
        } else {
            session = sessionTable.get(addr);
            SESSION_LOGGER.error("session|open|failed|user={}|msg={}", user, "session has been created!");
        }
        return session;
    }

    public void readySession(Session session) throws Exception {
        if (!EventMeshConstants.PURPOSE_SUB.equals(session.getClient().getPurpose())) {
            throw new Exception("client purpose config is not sub");
        }
        startCotnsumer(session);
    }

    public synchronized void closeSession(ChannelHandlerContext ctx) throws Exception {

        InetSocketAddress addr = (InetSocketAddress) ctx.channel().remoteAddress();
        Session session = MapUtils.getObject(sessionTable, addr, null);
        if (session == null) {
            final String remoteAddress = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            log.info("begin to close channel to remote address[{}]", remoteAddress);
            ctx.channel().close().addListener(
                    (ChannelFutureListener) future -> log.info("close the connection to remote address[{}] result: {}", remoteAddress,
                            future.isSuccess()));
            SESSION_LOGGER.info("session|close|succeed|address={}|msg={}", addr, "no session was found");
            return;
        }

        closeSession(session);

        //remove session from sessionTable
        sessionTable.remove(addr);

        SESSION_LOGGER.info("session|close|succeed|user={}", session.getClient());
    }

    private void closeSession(Session session) throws Exception {
        final String remoteAddress = RemotingHelper.parseChannelRemoteAddr(session.getContext().channel());
        if (SessionState.CLOSED == session.getSessionState()) {
            log.info("session has been closed, addr:{}", remoteAddress);
            return;
        }

        //session must be synchronized to avoid SessionState be confound, for example adding subscribe when session closing
        synchronized (session) {

            if (SessionState.CLOSED == session.getSessionState()) {
                log.info("session has been closed in sync, addr:{}", remoteAddress);
                return;
            }

            session.setSessionState(SessionState.CLOSED);

            if (EventMeshConstants.PURPOSE_SUB.equals(session.getClient().getPurpose())) {
                cleanPubSubManagerByCloseSub(session);
            } else if (EventMeshConstants.PURPOSE_PUB.equals(session.getClient().getPurpose())) {
                cleanPubSubManagerByClosePub(session);
            } else {
                log.error("client purpose config is error:{}", session.getClient().getPurpose());
            }

            if (session.getContext() != null) {
                log.info("begin to close channel to remote address[{}]", remoteAddress);
                session.getContext().channel().close().addListener(
                        (ChannelFutureListener) future -> log.info("close the connection to remote address[{}] result: {}", remoteAddress,
                                future.isSuccess()));
            }
        }
    }

    private PubSubManager constructPubSubManager(String sysId, String group,
                                                 EventMeshTCPServer eventMeshTCPServer,
                                                 DownstreamDispatchStrategy downstreamDispatchStrategy) {
        return new PubSubManager(sysId, group, eventMeshTCPServer,
                downstreamDispatchStrategy);
    }

    private void initPubSubManager(UserAgent user, Session session) throws Exception {
        if (!lockMap.containsKey(user.getGroup())) {
            Object obj = lockMap.putIfAbsent(user.getGroup(), new Object());
            if (obj == null) {
                log.info("add lock to map for group:{}", user.getGroup());
            }
        }
        synchronized (lockMap.get(user.getGroup())) {
            if (!clientGroupMap.containsKey(user.getGroup())) {
                PubSubManager manager = constructPubSubManager(user.getSubsystem(), user.getGroup(),
                        eventMeshTCPServer, new FreePriorityDispatchStrategy());
                clientGroupMap.put(user.getGroup(), manager);
                log.info("create new ClientGroupWrapper, group:{}", user.getGroup());
            }

            PubSubManager manager = clientGroupMap.get(user.getGroup());

            if (EventMeshConstants.PURPOSE_PUB.equals(user.getPurpose())) {
                initProducer(manager);
                startProducer(manager, session);
            } else if (EventMeshConstants.PURPOSE_SUB.equals(user.getPurpose())) {
                initConsumser(manager);
            } else {
                log.error("unknown client purpose:{}", user.getPurpose());
                throw new Exception("client purpose config is error");
            }

            session.setPubSubManager(new WeakReference<>(manager));
        }
    }

    private void initProducer(PubSubManager manager) throws Exception {
        manager.initProducer();
    }

    private void startProducer(PubSubManager manager, Session session) throws Exception {

        manager.startProducer();

        boolean flag = manager.addGroupProducerSession(session);
        if (!flag) {
            throw new Exception("addGroupProducerSession fail");
        }
        session.setSessionState(SessionState.RUNNING);
    }

    private void initConsumser(PubSubManager manager) throws Exception {
        if (!manager.getInited4Broadcast().get()) {
            manager.initClientGroupBroadcastConsumer();
        }

        if (!manager.getInited4Persistent().get()) {
            manager.initClientGroupPersistentConsumer();
        }
    }

    private void startCotnsumer(Session session) throws Exception {
        String subsystem = session.getClient().getSubsystem();
        if (!lockMap.containsKey(subsystem)) {
            lockMap.putIfAbsent(subsystem, new Object());
        }
        synchronized (lockMap.get(subsystem)) {
            log.info("readySession session[{}]", session);
            PubSubManager cgw = session.getPubSubManager().get();

            boolean flag = cgw != null && cgw.addGroupConsumerSession(session);
            if (!flag) {
                throw new Exception("addGroupConsumerSession fail");
            }

            if (cgw.getInited4Persistent().get() && !cgw.getStarted4Persistent().get()) {
                cgw.startClientGroupPersistentConsumer();
            }
            if (cgw.getInited4Broadcast().get() && !cgw.getStarted4Broadcast().get()) {
                cgw.startClientGroupBroadcastConsumer();
            }
            session.setSessionState(SessionState.RUNNING);
        }
    }

    private void shutdownConsumer(PubSubManager pubSubManager) throws Exception {
        if (pubSubManager.getStarted4Broadcast().get()) {
            pubSubManager.shutdownBroadCastConsumer();
        }

        if (pubSubManager.getStarted4Persistent().get()) {
            pubSubManager.shutdownPersistentConsumer();
        }
    }

    private void shutdownProducer(PubSubManager pubSubManager) throws Exception {
        pubSubManager.shutdownProducer();
    }

    /**
     * handle unAck msgs in this session
     *
     * @param session
     */
    private void handleUnackMsgsInSession(Session session) {
        ConcurrentHashMap<String /** seq */, DownStreamMsgContext> unAckMsg = session.getPusher().getUnAckMsg();
        PubSubManager pubSubManager = Objects.requireNonNull(session.getPubSubManager().get());
        if (unAckMsg.size() > 0 && !pubSubManager.getGroupConsumerSessions().isEmpty()) {
            for (Map.Entry<String, DownStreamMsgContext> entry : unAckMsg.entrySet()) {
                DownStreamMsgContext downStreamMsgContext = entry.getValue();
                if (SubscriptionMode.BROADCASTING == downStreamMsgContext.getSubscriptionItem().getMode()) {
                    log.warn("exist broadcast msg unack when closeSession,seq:{},bizSeq:{},client:{}",
                            downStreamMsgContext.seq, EventMeshUtil.getMessageBizSeq(downStreamMsgContext.event),
                            session.getClient());
                    continue;
                }
                Session reChooseSession = pubSubManager.getDownstreamDispatchStrategy()
                        .select(pubSubManager.getGroup(),
                                downStreamMsgContext.event.getSubject(),
                                pubSubManager.getGroupConsumerSessions());
                if (reChooseSession != null) {
                    downStreamMsgContext.setSession(reChooseSession);
                    reChooseSession.getPusher().unAckMsg(downStreamMsgContext.seq, downStreamMsgContext);
                    reChooseSession.downstreamMsg(downStreamMsgContext);
                    log.info("rePush msg form unAckMsgs,seq:{},rePushClient:{}", entry.getKey(),
                            downStreamMsgContext.getSession().getClient());
                } else {
                    log.warn("select session fail in handleUnackMsgsInSession,seq:{},topic:{}", entry.getKey(),
                            downStreamMsgContext.event.getSubject());
                }
            }
        }
    }

    private void cleanPubSubManagerByCloseSub(Session session) throws Exception {
        cleanSubscriptionInSession(session);
        PubSubManager pubSubManager = Objects.requireNonNull(session.getPubSubManager().get());
        pubSubManager.removeGroupConsumerSession(session);
        handleUnackMsgsInSession(session);
        cleanClientGroupWrapperCommon(pubSubManager);
    }

    private void cleanPubSubManagerByClosePub(Session session) throws Exception {
        PubSubManager pubSubManager = Objects.requireNonNull(session.getPubSubManager().get());
        pubSubManager.removeGroupProducerSession(session);
        cleanClientGroupWrapperCommon(pubSubManager);
    }

    /**
     * clean subscription of the session
     *
     * @param session
     */
    private void cleanSubscriptionInSession(Session session) throws Exception {
        for (SubscriptionItem item : session.getSessionContext().getSubscribeTopics().values()) {
            PubSubManager pubSubManager = Objects.requireNonNull(session.getPubSubManager().get());
            pubSubManager.removeSubscription(item, session);
            if (!pubSubManager.hasSubscription(item.getTopic())) {
                pubSubManager.unsubscribe(item);
            }
        }
    }

    private void cleanClientGroupWrapperCommon(PubSubManager pubSubManager) throws Exception {

        if (CollectionUtils.isEmpty(pubSubManager.getGroupConsumerSessions())) {
            shutdownConsumer(pubSubManager);
        }

        log.info("GroupProducerSessions size:{}",
                pubSubManager.getGroupProducerSessions().size());
        if ((CollectionUtils.isEmpty(pubSubManager.getGroupConsumerSessions()))
                && (CollectionUtils.isEmpty(pubSubManager.getGroupProducerSessions()))) {
            shutdownProducer(pubSubManager);

            clientGroupMap.remove(pubSubManager.getGroup());
            lockMap.remove(pubSubManager.getGroup());
            log.info("remove clientGroupWrapper group[{}]", pubSubManager.getGroup());
        }
    }


    private void initSessionCleaner() {
        final int SessionExpired = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshTcpSessionExpiredInMills();

        eventMeshTCPServer.getTcpThreadPoolGroup().getScheduler().scheduleAtFixedRate(() -> {
            for (Session tmp : sessionTable.values()) {
                if (System.currentTimeMillis() - tmp.getLastHeartbeatTime() > SessionExpired) {
                    try {
                        if (log.isWarnEnabled()) {
                            log.warn("clean expired session,client:{}", tmp.getClient());
                        }
                        closeSession(tmp.getContext());
                    } catch (Exception e) {
                        log.error("say goodbye to session error! {}", tmp, e);
                    }
                }
            }
        }, 1000, eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshTcpSessionExpiredInMills(), TimeUnit.MILLISECONDS);
    }

    private void initDownStreamMsgContextCleaner() {
        eventMeshTCPServer.getTcpThreadPoolGroup().getScheduler().scheduleAtFixedRate(() -> {

            //scan non-broadcast msg
            for (Session tmp : sessionTable.values()) {
                for (Map.Entry<String, DownStreamMsgContext> entry : tmp.getPusher().getUnAckMsg().entrySet()) {
                    String seqKey = entry.getKey();
                    DownStreamMsgContext downStreamMsgContext = entry.getValue();
                    if (!downStreamMsgContext.isExpire()) {
                        continue;
                    }
                    downStreamMsgContext.ackMsg();
                    tmp.getPusher().getUnAckMsg().remove(seqKey);
                    log.warn("remove expire downStreamMsgContext, session:{}, topic:{}, seq:{}", tmp,
                            downStreamMsgContext.event.getSubject(), seqKey);
                }
            }
        }, 1000, 5 * 1000, TimeUnit.MILLISECONDS);
    }

    public Map<String, Map<String, Integer>> prepareProxyClientDistributionData() {
        Map<String, Map<String, Integer>> result = null;

        if (!clientGroupMap.isEmpty()) {
            result = new HashMap<>();
            for (Map.Entry<String, PubSubManager> entry : clientGroupMap.entrySet()) {
                Map<String, Integer> map = new HashMap<>();
                map.put(EventMeshConstants.PURPOSE_SUB, entry.getValue().getGroupConsumerSessions().size());
                map.put(EventMeshConstants.PURPOSE_PUB, entry.getValue().getGroupProducerSessions().size());
                result.put(entry.getKey(), map);
            }
        }

        return result;
    }

    public Map<String, Map<String, Integer>> prepareEventMeshClientDistributionData() {
        Map<String, Map<String, Integer>> result = null;

        if (!clientGroupMap.isEmpty()) {
            result = new HashMap<>();
            for (Map.Entry<String, PubSubManager> entry : clientGroupMap.entrySet()) {
                Map<String, Integer> map = new HashMap<>();
                map.put(EventMeshConstants.PURPOSE_SUB, entry.getValue().getGroupConsumerSessions().size());
                map.put(EventMeshConstants.PURPOSE_PUB, entry.getValue().getGroupProducerSessions().size());
                result.put(entry.getKey(), map);
            }
        }

        return result;
    }

    public EventMeshTCPServer getEventMeshTCPServer() {
        return eventMeshTCPServer;
    }

    public void setEventMeshTCPServer(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    public PubSubManager getClientGroupWrapper(String sysId) {
        return MapUtils.getObject(clientGroupMap, sysId, null);
    }

    public Session getSession(ChannelHandlerContext ctx) {
        return getSession((InetSocketAddress) ctx.channel().remoteAddress());
    }

    public Session getSession(InetSocketAddress address) {
        return sessionTable.get(address);
    }

    public ConcurrentHashMap<InetSocketAddress, Session> getSessionMap() {
        return sessionTable;
    }

    public ConcurrentHashMap<String, PubSubManager> getClientGroupMap() {
        return clientGroupMap;
    }


}
