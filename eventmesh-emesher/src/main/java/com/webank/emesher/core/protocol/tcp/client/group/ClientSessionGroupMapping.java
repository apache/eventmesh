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

package com.webank.emesher.core.protocol.tcp.client.group;

import com.webank.emesher.boot.ProxyTCPServer;
import com.webank.emesher.constants.ProxyConstants;
import com.webank.emesher.core.protocol.tcp.client.ProxyTcp2Client;
import com.webank.emesher.core.protocol.tcp.client.group.dispatch.FreePriorityDispatchStrategy;
import com.webank.emesher.core.protocol.tcp.client.session.Session;
import com.webank.emesher.core.protocol.tcp.client.session.SessionState;
import com.webank.emesher.core.protocol.tcp.client.session.push.ClientAckContext;
import com.webank.emesher.core.protocol.tcp.client.session.push.DownStreamMsgContext;
import com.webank.emesher.util.ProxyUtil;
import com.webank.eventmesh.common.ThreadUtil;
import com.webank.eventmesh.common.protocol.tcp.UserAgent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientSessionGroupMapping {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Logger sessionLogger = LoggerFactory.getLogger("sessionLogger");

    private ConcurrentHashMap<InetSocketAddress, Session> sessionTable = new ConcurrentHashMap<>();

    private ConcurrentHashMap<String /** groupName*/, ClientGroupWrapper> clientGroupMap = new ConcurrentHashMap<String, ClientGroupWrapper>();

    private ConcurrentHashMap<String /** groupName*/, Object> lockMap = new ConcurrentHashMap<String, Object>();

    private ProxyTCPServer proxyTCPServer;

    public ClientSessionGroupMapping(ProxyTCPServer proxyTCPServer) {
        this.proxyTCPServer = proxyTCPServer;
    }

    public ProxyTCPServer getProxyTCPServer() {
        return proxyTCPServer;
    }

    public void setProxyTCPServer(ProxyTCPServer proxyTCPServer) {
        this.proxyTCPServer = proxyTCPServer;
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
        if(!sessionTable.containsKey(addr)){
            logger.info("createSession client[{}]", RemotingHelper.parseChannelRemoteAddr(ctx.channel()));
            session = new Session(user, ctx, proxyTCPServer.getAccessConfiguration());
            initClientGroupWrapper(user, session);
            sessionTable.put(addr, session);
            sessionLogger.info("session|open|succeed|user={}", user);
        }else {
            session = sessionTable.get(addr);
            sessionLogger.error("session|open|failed|user={}|msg={}", user, "session has been created!");
        }
        return session;
    }

    public void readySession(Session session) throws Exception {
        if (!ProxyConstants.PURPOSE_SUB.equals(session.getClient().getPurpose())) {
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
        synchronized (session){

            if (SessionState.CLOSED == session.getSessionState()) {
                logger.info("session has been closed in sync, addr:{}", remoteAddress);
                return;
            }

            session.setSessionState(SessionState.CLOSED);

            if (ProxyConstants.PURPOSE_SUB.equals(session.getClient().getPurpose())) {
                cleanClientGroupWrapperByCloseSub(session);
            }else if (ProxyConstants.PURPOSE_PUB.equals(session.getClient().getPurpose())) {
                cleanClientGroupWrapperByClosePub(session);
            }else{
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

    private void  initClientGroupWrapper(UserAgent user, Session session) throws Exception {
        final String clientGroup = ProxyUtil.buildClientGroup(user.getSubsystem(), user.getDcn());
        if(!lockMap.containsKey(clientGroup)){
            Object obj = lockMap.putIfAbsent(clientGroup, new Object());
            if(obj == null) {
                logger.info("add lock to map for group:{}", clientGroup);
            }
        }
        synchronized (lockMap.get(clientGroup)) {
            if (!clientGroupMap.containsKey(clientGroup)) {
                ClientGroupWrapper cgw = new ClientGroupWrapper(user.getSubsystem(), user.getDcn()
                        , proxyTCPServer, new FreePriorityDispatchStrategy());
                clientGroupMap.put(clientGroup, cgw);
                logger.info("create new ClientGroupWrapper,group:{}", clientGroup);
            }

            ClientGroupWrapper cgw = clientGroupMap.get(clientGroup);

            if (ProxyConstants.PURPOSE_PUB.equals(user.getPurpose())){
                startClientGroupProducer(cgw, session);
            }else if (ProxyConstants.PURPOSE_SUB.equals(user.getPurpose())) {
                initClientGroupConsumser(cgw);
            }else{
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
        if(!flag){
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
        final String clientGroup = ProxyUtil.buildClientGroup(session.getClient().getSubsystem(), session.getClient().getDcn());
        if(!lockMap.containsKey(clientGroup)){
            lockMap.putIfAbsent(clientGroup, new Object());
        }
        synchronized (lockMap.get(clientGroup)) {
            logger.info("readySession session[{}]", session);
            ClientGroupWrapper cgw = session.getClientGroupWrapper().get();

            boolean flag = cgw.addGroupConsumerSession(session);
            if(!flag){
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
    private void handleUnackMsgsInSession(Session session){
        ConcurrentHashMap<String /** seq */, ClientAckContext> unAckMsg = session.getPusher().getPushContext().getUnAckMsg();
        if(unAckMsg.size() > 0 && session.getClientGroupWrapper().get().getGroupConsumerSessions().size() > 0){
            for(Map.Entry<String , ClientAckContext> entry : unAckMsg.entrySet()){
                ClientAckContext ackContext = entry.getValue();
                if(ProxyUtil.isBroadcast(ackContext.getMsgs().get(0).getTopic())){
                    logger.warn("exist broadcast msg unack when closeSession,seq:{},bizSeq:{},client:{}",ackContext.getSeq(),ProxyUtil.getMessageBizSeq(ackContext.getMsgs().get(0)),session.getClient());
                    continue;
                }
                List<Session> list = new ArrayList(session.getClientGroupWrapper().get().getGroupConsumerSessions());
                Collections.shuffle(list);
                DownStreamMsgContext downStreamMsgContext= new DownStreamMsgContext(ackContext.getMsgs().get(0),list.get(0),ackContext.getConsumer(), ackContext.getContext(), false);

                downStreamMsgContext.delay(0L);
                proxyTCPServer.getProxyTcpRetryer().pushRetry(downStreamMsgContext);
                logger.warn("rePush msg form unAckMsgs,seq:{},rePushSeq:{},rePushClient:{}",entry.getKey(), downStreamMsgContext.seq, downStreamMsgContext.session.getClient());
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
        ProxyTCPServer.scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Iterator<Session> sessionIterator = sessionTable.values().iterator();
                while (sessionIterator.hasNext()) {
                    Session tmp = sessionIterator.next();
                    if (System.currentTimeMillis() - tmp.getLastHeartbeatTime() > proxyTCPServer.getAccessConfiguration().proxyTcpSessionExpiredInMills) {
                        try {
                            logger.warn("clean expired session,client:{}", tmp.getClient());
                            closeSession(tmp.getContext());
                        } catch (Exception e) {
                            logger.error("say goodbye to session error! {}", tmp, e);
                        }
                    }
                }
            }
        }, 1000, proxyTCPServer.getAccessConfiguration().proxyTcpSessionExpiredInMills, TimeUnit.MILLISECONDS);
    }

    private void initSessionAckContextCleaner() {
        proxyTCPServer.scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Iterator<Session> sessionIterator = sessionTable.values().iterator();
                while (sessionIterator.hasNext()) {
                    Session tmp = sessionIterator.next();
                    for (Map.Entry<String, ClientAckContext> entry : tmp.getPusher().getPushContext().getUnAckMsg().entrySet()) {
                        String seqKey = entry.getKey();
                        ClientAckContext clientAckContext = entry.getValue();
                        if (!clientAckContext.isExpire()) {
                            continue;
                        }
                        tmp.getPusher().getPushContext().ackMsg(seqKey);
                        tmp.getPusher().getPushContext().getUnAckMsg().remove(seqKey);
                        logger.warn("remove expire clientAckContext, session:{}, topic:{}, seq:{}", tmp, clientAckContext.getMsgs().get(0).getTopic(), seqKey);
                    }
                }
            }
        }, 1000, 5 * 1000, TimeUnit.MILLISECONDS);
    }

    private void initDownStreamMsgContextCleaner() {
        proxyTCPServer.scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Iterator<ClientGroupWrapper> cgwIterator = clientGroupMap.values().iterator();
                while (cgwIterator.hasNext()) {
                    ClientGroupWrapper cgw = cgwIterator.next();
                    for (Map.Entry<String, DownStreamMsgContext> entry : cgw.getDownstreamMap().entrySet()) {
                        String seq = entry.getKey();
                        DownStreamMsgContext downStreamMsgContext = entry.getValue();
                        if (!downStreamMsgContext.isExpire()) {
                            continue;
                        }
                        cgw.getDownstreamMap().get(seq).ackMsg();
                        cgw.getDownstreamMap().remove(seq);
                        logger.warn("remove expire DownStreamMsgContext,group:{}, topic:{}, seq:{}", cgw.getGroupName(), downStreamMsgContext.msgExt.getTopic(), seq);
                    }
                }
            }
        }, 1000, 5 * 1000, TimeUnit.MILLISECONDS);
    }


    public void init() throws Exception {
        initSessionCleaner();
        initSessionAckContextCleaner();
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
                ProxyTcp2Client.serverGoodby2Client(itr, this);
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
                if(!StringUtils.equals(session.getClient().getPurpose(), purpose))
                    continue;

                String key = session.getClient().getDcn() + "|" + session.getClient().getSubsystem()+ "|" + purpose;
                if (!result.containsKey(key)) {
                    result.put(key, new AtomicInteger(1));
                } else {
                    result.get(key).incrementAndGet();
                }
            }
        }
        return result;
    }

    public Map<String, Map<String, Integer>> prepareProxyClientDistributionData(){
        Map<String, Map<String, Integer>> result = null;

        if(!clientGroupMap.isEmpty()){
            result = new HashMap<>();
            for(Map.Entry<String, ClientGroupWrapper> entry : clientGroupMap.entrySet()){
                Map<String, Integer> map = new HashMap();
                map.put(ProxyConstants.PURPOSE_SUB,entry.getValue().getGroupConsumerSessions().size());
                map.put(ProxyConstants.PURPOSE_PUB,entry.getValue().getGroupProducerSessions().size());
                result.put(entry.getKey(), map);
            }
        }

        return result;
    }
}
