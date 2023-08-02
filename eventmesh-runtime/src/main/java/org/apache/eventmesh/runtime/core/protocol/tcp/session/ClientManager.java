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

import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.consumer.dispatch.FreePriorityDispatchStrategy;
import org.apache.eventmesh.runtime.core.protocol.tcp.consumer.push.DownStreamMsgContext;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientManager {

    private static final Logger SESSION_LOGGER = LoggerFactory.getLogger("tcpSessionLogger");

    private EventMeshTCPServer eventMeshTCPServer;

    private final ConcurrentHashMap<InetSocketAddress, Session> sessionTable = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String /*group*/, SessionManager> clientGroupMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String /*group*/, Object> lockMap = new ConcurrentHashMap<>();

    public ClientManager(EventMeshTCPServer eventMeshTCPServer) {
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
        for (SessionManager sessionManager : clientGroupMap.values()) {
            for (Session subSession : sessionManager.getConsumerMap().getGroupConsumerSessions()) {
                try {
                    EventMeshTcp2Client.serverGoodby2Client(eventMeshTCPServer.getTcpThreadPoolGroup(), subSession, this);
                } catch (Exception e) {
                    log.error("say goodbye to subSession error! {}", subSession, e);
                }
            }

            for (Session pubSession : sessionManager.getProducerMap().getGroupProducerSessions()) {
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

            session = new Session(user, ctx, eventMeshTCPServer);
            injectSessionManager(user, session);
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

        String subsystem = session.getClient().getSubsystem();
        if (!lockMap.containsKey(subsystem)) {
            lockMap.putIfAbsent(subsystem, new Object());
        }

        synchronized (lockMap.get(subsystem)) {
            log.info("readySession session[{}]", session);
            SessionManager sessionManager = session.getSessionMap().get();

            boolean flag = sessionManager != null && sessionManager.getConsumerMap().addConsumer(session);
            if (!flag) {
                throw new Exception("addGroupConsumerSession fail");
            }

            session.setSessionState(SessionState.RUNNING);
        }
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


            SessionManager sessionManager = session.getSessionMap().get();
            if (EventMeshConstants.PURPOSE_SUB.equals(session.getClient().getPurpose())) {
                sessionManager.getConsumerMap().cleanConsumer(session);
            } else if (EventMeshConstants.PURPOSE_PUB.equals(session.getClient().getPurpose())) {
                sessionManager.getProducerMap().cleanProducer(session);
            } else {
                log.error("client purpose config is error:{}", session.getClient().getPurpose());
            }

            if ((CollectionUtils.isEmpty(sessionManager.getConsumerMap().getGroupConsumerSessions()))
                    && (CollectionUtils.isEmpty(sessionManager.getProducerMap().getGroupProducerSessions()))) {
                clientGroupMap.remove(sessionManager.getGroup());
                lockMap.remove(sessionManager.getGroup());
                log.info("remove clientGroupWrapper group[{}]", sessionManager.getGroup());
            }

            if (session.getContext() != null) {
                log.info("begin to close channel to remote address[{}]", remoteAddress);
                session.getContext().channel().close().addListener(
                        (ChannelFutureListener) future -> log.info("close the connection to remote address[{}] result: {}", remoteAddress,
                                future.isSuccess()));
            }
        }
    }

    private void injectSessionManager(UserAgent user, Session session) throws Exception {
        if (!lockMap.containsKey(user.getGroup())) {
            Object obj = lockMap.putIfAbsent(user.getGroup(), new Object());
            if (obj == null) {
                log.info("add lock to map for group:{}", user.getGroup());
            }
        }
        synchronized (lockMap.get(user.getGroup())) {
            if (!clientGroupMap.containsKey(user.getGroup())) {
                SessionManager sessionManager =
                        new SessionManager(user.getSubsystem(), user.getGroup(), eventMeshTCPServer, new FreePriorityDispatchStrategy());

                clientGroupMap.put(user.getGroup(), sessionManager);
                log.info("create new SessionManager, group:{}", user.getGroup());
            }

            SessionManager sessionManager = clientGroupMap.get(user.getGroup());

            if (!(EventMeshConstants.PURPOSE_PUB.equals(user.getPurpose())
                    || EventMeshConstants.PURPOSE_SUB.equals(user.getPurpose()))) {
                log.error("unknown client purpose:{}", user.getPurpose());
                throw new Exception("client purpose config is error");
            }

            session.setSessionMap(new WeakReference<>(sessionManager));
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
                            downStreamMsgContext.getEvent().getSubject(), seqKey);
                }
            }
        }, 1000, 5 * 1000, TimeUnit.MILLISECONDS);
    }


    public Map<String, Map<String, Integer>> prepareProxyClientDistributionData() {
        Map<String, Map<String, Integer>> result = null;

        if (!clientGroupMap.isEmpty()) {
            result = new HashMap<>();
            for (Map.Entry<String, SessionManager> entry : clientGroupMap.entrySet()) {
                Map<String, Integer> map = new HashMap<>();
                map.put(EventMeshConstants.PURPOSE_SUB, entry.getValue().getConsumerMap().getGroupConsumerSessions().size());
                map.put(EventMeshConstants.PURPOSE_PUB, entry.getValue().getProducerMap().getGroupProducerSessions().size());
                result.put(entry.getKey(), map);
            }
        }

        return result;
    }

    public Map<String, Map<String, Integer>> prepareEventMeshClientDistributionData() {
        Map<String, Map<String, Integer>> result = null;

        if (!clientGroupMap.isEmpty()) {
            result = new HashMap<>();
            for (Map.Entry<String, SessionManager> entry : clientGroupMap.entrySet()) {
                Map<String, Integer> map = new HashMap<>();
                map.put(EventMeshConstants.PURPOSE_SUB, entry.getValue().getConsumerMap().getGroupConsumerSessions().size());
                map.put(EventMeshConstants.PURPOSE_PUB, entry.getValue().getProducerMap().getGroupProducerSessions().size());
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

    public Session getSession(ChannelHandlerContext ctx) {
        return getSession((InetSocketAddress) ctx.channel().remoteAddress());
    }

    public Session getSession(InetSocketAddress address) {
        return sessionTable.get(address);
    }

    public ConcurrentHashMap<InetSocketAddress, Session> getSessionTable() {
        return sessionTable;
    }

    public ConcurrentHashMap<String, SessionManager> getClientGroupMap() {
        return clientGroupMap;
    }


}
