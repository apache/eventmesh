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

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.ThreadUtil;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.dispatch.DownstreamDispatchStrategy;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.dispatch.FreePriorityDispatchStrategy;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.SessionState;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.push.DownStreamMsgContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientSessionGroupMapping {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Logger sessionLogger = LoggerFactory.getLogger("sessionLogger");

    private ConcurrentHashMap<InetSocketAddress, Session> sessionTable = new ConcurrentHashMap<>();

    private ConcurrentHashMap<String /** groupName*/, ClientGroupWrapper> clientGroupMap = new ConcurrentHashMap<String, ClientGroupWrapper>();

    private ConcurrentHashMap<String /** groupName*/, Object> lockMap = new ConcurrentHashMap<String, Object>();

    private EventMeshTCPServer eventMeshTCPServer;

    public ClientSessionGroupMapping(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    public EventMeshTCPServer getEventMeshTCPServer() {
        return eventMeshTCPServer;
    }

    public void setEventMeshTCPServer(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    public ClientGroupWrapper getClientGroupWrapper(String groupName) {
        return MapUtils.getObject(clientGroupMap, groupName, null);
    }

    public Session getSession(ChannelHandlerContext ctx) {
        Session session = getSession((InetSocketAddress) ctx.channel().remoteAddress());
        return session;
    }

    public Session getSession(InetSocketAddress address) {
        return sessionTable.get(address);
    }

    public Session createSession(UserAgent user, ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress addr = (InetSocketAddress) ctx.channel().remoteAddress();
        user.setHost(addr.getHostString());
        user.setPort(addr.getPort());
        Session session = null;
        if (!sessionTable.containsKey(addr)) {
            logger.info("createSession client[{}]", RemotingHelper.parseChannelRemoteAddr(ctx.channel()));
            session = new Session(user, ctx, eventMeshTCPServer.getEventMeshTCPConfiguration());
            initClientGroupWrapper(user, session);
            sessionTable.put(addr, session);
            sessionLogger.info("session|open|succeed|user={}", user);
        } else {
            session = sessionTable.get(addr);
            sessionLogger.error("session|open|failed|user={}|msg={}", user, "session has been created!");
        }
        return session;
    }

    public void readySession(Session session) throws Exception {
        if (!EventMeshConstants.PURPOSE_SUB.equals(session.getClient().getPurpose())) {
            throw new Exception("client purpose config is not sub");
        }
        startClientGroupConsumer(session);
    }

    public synchronized void closeSession(ChannelHandlerContext ctx) throws Exception {

        InetSocketAddress addr = (InetSocketAddress) ctx.channel().remoteAddress();
        Session session = MapUtils.getObject(sessionTable, addr, null);
        if (session == null) {
            final String remoteAddress = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            logger.info("begin to close channel to remote address[{}]", remoteAddress);
            ctx.channel().close().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    logger.info("close the connection to remote address[{}] result: {}", remoteAddress,
                            future.isSuccess());
                }
            });
            sessionLogger.info("session|close|succeed|address={}|msg={}", addr, "no session was found");
            return;
        }

        closeSession(session);

        //remove session from sessionTable
        sessionTable.remove(addr);

        sessionLogger.info("session|close|succeed|user={}", session.getClient());
    }

    private void closeSession(Session session) throws Exception {
        final String remoteAddress = RemotingHelper.parseChannelRemoteAddr(session.getContext().channel());
        if (SessionState.CLOSED == session.getSessionState()) {
            logger.info("session has been closed, addr:{}", remoteAddress);
            return;
        }

        //session must be synchronized to avoid SessionState be confound, for example adding subscribe when session closing
        synchronized (session) {

            if (SessionState.CLOSED == session.getSessionState()) {
                logger.info("session has been closed in sync, addr:{}", remoteAddress);
                return;
            }

            session.setSessionState(SessionState.CLOSED);

            if (EventMeshConstants.PURPOSE_SUB.equals(session.getClient().getPurpose())) {
                cleanClientGroupWrapperByCloseSub(session);
            } else if (EventMeshConstants.PURPOSE_PUB.equals(session.getClient().getPurpose())) {
                cleanClientGroupWrapperByClosePub(session);
            } else {
                logger.error("client purpose config is error:{}", session.getClient().getPurpose());
            }

            if (session.getContext() != null) {
                logger.info("begin to close channel to remote address[{}]", remoteAddress);
                session.getContext().channel().close().addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        logger.info("close the connection to remote address[{}] result: {}", remoteAddress,
                                future.isSuccess());
                    }
                });
            }
        }
    }

    private ClientGroupWrapper constructClientGroupWrapper(String sysId, String dcn,
                                                           EventMeshTCPServer eventMeshTCPServer,
                                                           DownstreamDispatchStrategy downstreamDispatchStrategy) {
        return new ClientGroupWrapper(sysId, dcn
                , eventMeshTCPServer, downstreamDispatchStrategy);
    }

    private void initClientGroupWrapper(UserAgent user, Session session) throws Exception {
        final String clientGroup = EventMeshUtil.buildClientGroup(user.getSubsystem(), user.getDcn());
        if (!lockMap.containsKey(clientGroup)) {
            Object obj = lockMap.putIfAbsent(clientGroup, new Object());
            if (obj == null) {
                logger.info("add lock to map for group:{}", clientGroup);
            }
        }
        synchronized (lockMap.get(clientGroup)) {
            if (!clientGroupMap.containsKey(clientGroup)) {
                ClientGroupWrapper cgw = constructClientGroupWrapper(user.getSubsystem(), user.getDcn()
                        , eventMeshTCPServer, new FreePriorityDispatchStrategy());
                clientGroupMap.put(clientGroup, cgw);
                logger.info("create new ClientGroupWrapper,group:{}", clientGroup);
            }

            ClientGroupWrapper cgw = clientGroupMap.get(clientGroup);

            if (EventMeshConstants.PURPOSE_PUB.equals(user.getPurpose())) {
                startClientGroupProducer(cgw, session);
            } else if (EventMeshConstants.PURPOSE_SUB.equals(user.getPurpose())) {
                initClientGroupConsumser(cgw);
            } else {
                logger.error("unknown client purpose:{}", user.getPurpose());
                throw new Exception("client purpose config is error");
            }

            session.setClientGroupWrapper(new WeakReference<ClientGroupWrapper>(cgw));
        }
    }

    private void startClientGroupProducer(ClientGroupWrapper cgw, Session session) throws Exception {
        if (!cgw.producerStarted.get()) {
            cgw.startClientGroupProducer();
        }
        boolean flag = cgw.addGroupProducerSession(session);
        if (!flag) {
            throw new Exception("addGroupProducerSession fail");
        }
        session.setSessionState(SessionState.RUNNING);
    }

    private void initClientGroupConsumser(ClientGroupWrapper cgw) throws Exception {
        if (!cgw.producerStarted.get()) {
            cgw.startClientGroupProducer();
        }

        if (!cgw.inited4Broadcast.get()) {
            cgw.initClientGroupBroadcastConsumer();
        }

        if (!cgw.inited4Persistent.get()) {
            cgw.initClientGroupPersistentConsumer();
        }
    }

    private void startClientGroupConsumer(Session session) throws Exception {
        final String clientGroup = EventMeshUtil.buildClientGroup(session.getClient().getSubsystem(), session.getClient().getDcn());
        if (!lockMap.containsKey(clientGroup)) {
            lockMap.putIfAbsent(clientGroup, new Object());
        }
        synchronized (lockMap.get(clientGroup)) {
            logger.info("readySession session[{}]", session);
            ClientGroupWrapper cgw = session.getClientGroupWrapper().get();

            boolean flag = cgw.addGroupConsumerSession(session);
            if (!flag) {
                throw new Exception("addGroupConsumerSession fail");
            }

            if (cgw.inited4Persistent.get() && !cgw.started4Persistent.get()) {
                cgw.startClientGroupPersistentConsumer();
            }
            if (cgw.inited4Broadcast.get() && !cgw.started4Broadcast.get()) {
                cgw.startClientGroupBroadcastConsumer();
            }
            session.setSessionState(SessionState.RUNNING);
        }
    }

    private void cleanClientGroupWrapperByCloseSub(Session session) throws Exception {
        cleanSubscriptionInSession(session);
        session.getClientGroupWrapper().get().removeGroupConsumerSession(session);
        handleUnackMsgsInSession(session);
        cleanClientGroupWrapperCommon(session);
    }

    private void cleanClientGroupWrapperByClosePub(Session session) throws Exception {
        session.getClientGroupWrapper().get().removeGroupProducerSession(session);
        cleanClientGroupWrapperCommon(session);
    }

    /**
     * clean subscription of the session
     *
     * @param session
     */
    private void cleanSubscriptionInSession(Session session) throws Exception {
        for (String topic : session.getSessionContext().subscribeTopics.values()) {
            session.getClientGroupWrapper().get().removeSubscription(topic, session);
            if (!session.getClientGroupWrapper().get().hasSubscription(topic)) {
                session.getClientGroupWrapper().get().unsubscribe(topic);
            }
        }
    }

    /**
     * handle unAck msgs in this session
     *
     * @param session
     */
    private void handleUnackMsgsInSession(Session session) {
        ConcurrentHashMap<String /** seq */, DownStreamMsgContext> unAckMsg = session.getPusher().getUnAckMsg();
        if (unAckMsg.size() > 0 && session.getClientGroupWrapper().get().getGroupConsumerSessions().size() > 0) {
            for (Map.Entry<String, DownStreamMsgContext> entry : unAckMsg.entrySet()) {
                DownStreamMsgContext downStreamMsgContext = entry.getValue();
                if (EventMeshUtil.isBroadcast(downStreamMsgContext.msgExt.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION))) {
                    logger.warn("exist broadcast msg unack when closeSession,seq:{},bizSeq:{},client:{}", downStreamMsgContext.seq, EventMeshUtil.getMessageBizSeq(downStreamMsgContext.msgExt), session.getClient());
                    continue;
                }
                Session reChooseSession = session.getClientGroupWrapper().get().getDownstreamDispatchStrategy().select(session.getClientGroupWrapper().get().getGroupName()
                        , downStreamMsgContext.msgExt.getTopic()
                        , session.getClientGroupWrapper().get().groupConsumerSessions);
                if(reChooseSession != null){
                    downStreamMsgContext.session = reChooseSession;
                    reChooseSession.downstreamMsg(downStreamMsgContext);
                    logger.info("rePush msg form unAckMsgs,seq:{},rePushClient:{}", entry.getKey(), downStreamMsgContext.session.getClient());
                }else{
                    logger.warn("select session fail in handleUnackMsgsInSession,seq:{},topic:{}", entry.getKey(), downStreamMsgContext.msgExt.getTopic());
                }
            }
        }
    }

    private void cleanClientGroupWrapperCommon(Session session) throws Exception {
        logger.info("GroupConsumerSessions size:{}", session.getClientGroupWrapper().get().getGroupConsumerSessions().size());
        if (session.getClientGroupWrapper().get().getGroupConsumerSessions().size() == 0) {
            shutdownClientGroupConsumer(session);
        }

        logger.info("GroupProducerSessions size:{}", session.getClientGroupWrapper().get().getGroupProducerSessions().size());
        if ((session.getClientGroupWrapper().get().getGroupConsumerSessions().size() == 0)
                && (session.getClientGroupWrapper().get().getGroupProducerSessions().size() == 0)) {
            shutdownClientGroupProducer(session);

            clientGroupMap.remove(session.getClientGroupWrapper().get().getGroupName());
            lockMap.remove(session.getClientGroupWrapper().get().getGroupName());
            logger.info("remove clientGroupWrapper group[{}]", session.getClientGroupWrapper().get().getGroupName());
        }
    }

    private void shutdownClientGroupConsumer(Session session) throws Exception {
        if (session.getClientGroupWrapper().get().started4Broadcast.get() == Boolean.TRUE) {
            session.getClientGroupWrapper().get().shutdownBroadCastConsumer();
        }

        if (session.getClientGroupWrapper().get().started4Persistent.get() == Boolean.TRUE) {
            session.getClientGroupWrapper().get().shutdownPersistentConsumer();
        }
    }


    private void shutdownClientGroupProducer(Session session) throws Exception {
        if (session.getClientGroupWrapper().get().producerStarted.get() == Boolean.TRUE) {
            session.getClientGroupWrapper().get().shutdownProducer();
        }
    }

    private void initSessionCleaner() {
        eventMeshTCPServer.getScheduler().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Iterator<Session> sessionIterator = sessionTable.values().iterator();
                while (sessionIterator.hasNext()) {
                    Session tmp = sessionIterator.next();
                    if (System.currentTimeMillis() - tmp.getLastHeartbeatTime() > eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshTcpSessionExpiredInMills) {
                        try {
                            logger.warn("clean expired session,client:{}", tmp.getClient());
                            closeSession(tmp.getContext());
                        } catch (Exception e) {
                            logger.error("say goodbye to session error! {}", tmp, e);
                        }
                    }
                }
            }
        }, 1000, eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshTcpSessionExpiredInMills, TimeUnit.MILLISECONDS);
    }

    private void initDownStreamMsgContextCleaner() {
        eventMeshTCPServer.getScheduler().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {

                //scan non-broadcast msg
                Iterator<Session> sessionIterator = sessionTable.values().iterator();
                while (sessionIterator.hasNext()) {
                    Session tmp = sessionIterator.next();
                    for (Map.Entry<String, DownStreamMsgContext> entry : tmp.getPusher().getUnAckMsg().entrySet()) {
                        String seqKey = entry.getKey();
                        DownStreamMsgContext downStreamMsgContext = entry.getValue();
                        if (!downStreamMsgContext.isExpire()) {
                            continue;
                        }
                        downStreamMsgContext.ackMsg();
                        tmp.getPusher().getUnAckMsg().remove(seqKey);
                        logger.warn("remove expire downStreamMsgContext, session:{}, topic:{}, seq:{}", tmp,
                                downStreamMsgContext.msgExt.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION), seqKey);
                    }
                }
            }
        }, 1000, 5 * 1000, TimeUnit.MILLISECONDS);
    }


    public void init() throws Exception {
        initSessionCleaner();
        initDownStreamMsgContextCleaner();
        logger.info("ClientSessionGroupMapping inited......");
    }

    public void start() throws Exception {
        logger.info("ClientSessionGroupMapping started......");
    }

    public void shutdown() throws Exception {
        logger.info("begin to close sessions gracefully");
        sessionTable.values().parallelStream().forEach(itr -> {
            try {
                EventMeshTcp2Client.serverGoodby2Client(this.eventMeshTCPServer,itr, this);
            } catch (Exception e) {
                logger.error("say goodbye to session error! {}", itr, e);
            }
        });
        ThreadUtil.randomSleep(50);
        logger.info("ClientSessionGroupMapping shutdown......");
    }

    public ConcurrentHashMap<InetSocketAddress, Session> getSessionMap() {
        return sessionTable;
    }

    public ConcurrentHashMap<String, ClientGroupWrapper> getClientGroupMap() {
        return clientGroupMap;
    }

    public HashMap<String, AtomicInteger> statDCNSystemInfo() {
        HashMap<String, AtomicInteger> result = new HashMap<String, AtomicInteger>();
        if (!sessionTable.isEmpty()) {
            for (Session session : sessionTable.values()) {
                String key = session.getClient().getDcn() + "|" + session.getClient().getSubsystem();
                if (!result.containsKey(key)) {
                    result.put(key, new AtomicInteger(1));
                } else {
                    result.get(key).incrementAndGet();
                }
            }
        }
        return result;
    }

    public HashMap<String, AtomicInteger> statDCNSystemInfoByPurpose(String purpose) {
        HashMap<String, AtomicInteger> result = new HashMap<String, AtomicInteger>();
        if (!sessionTable.isEmpty()) {
            for (Session session : sessionTable.values()) {
                if (!StringUtils.equals(session.getClient().getPurpose(), purpose)) {
                    continue;
                }

                String key = session.getClient().getDcn() + "|" + session.getClient().getSubsystem() + "|" + purpose;
                if (!result.containsKey(key)) {
                    result.put(key, new AtomicInteger(1));
                } else {
                    result.get(key).incrementAndGet();
                }
            }
        }
        return result;
    }

    public Map<String, Map<String, Integer>> prepareEventMeshClientDistributionData() {
        Map<String, Map<String, Integer>> result = null;

        if (!clientGroupMap.isEmpty()) {
            result = new HashMap<>();
            for (Map.Entry<String, ClientGroupWrapper> entry : clientGroupMap.entrySet()) {
                Map<String, Integer> map = new HashMap<>();
                map.put(EventMeshConstants.PURPOSE_SUB, entry.getValue().getGroupConsumerSessions().size());
                map.put(EventMeshConstants.PURPOSE_PUB, entry.getValue().getGroupProducerSessions().size());
                result.put(entry.getKey(), map);
            }
        }

        return result;
    }
}
