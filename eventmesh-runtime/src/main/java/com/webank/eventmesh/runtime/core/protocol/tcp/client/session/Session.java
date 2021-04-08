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

package com.webank.eventmesh.runtime.core.protocol.tcp.client.session;

import com.webank.eventmesh.common.Constants;
import com.webank.eventmesh.runtime.core.protocol.tcp.client.session.push.DownStreamMsgContext;
import com.webank.eventmesh.runtime.core.protocol.tcp.client.session.push.SessionPusher;
import com.webank.eventmesh.runtime.core.protocol.tcp.client.session.send.SessionSender;
import com.webank.eventmesh.runtime.util.Utils;
import com.webank.eventmesh.runtime.core.protocol.tcp.client.session.send.EventMeshTcpSendResult;
import com.webank.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import com.webank.eventmesh.runtime.constants.EventMeshConstants;
import com.webank.eventmesh.runtime.core.protocol.tcp.client.group.ClientGroupWrapper;
import com.webank.eventmesh.common.protocol.tcp.Header;
import com.webank.eventmesh.common.protocol.tcp.OPStatus;
import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.eventmesh.common.protocol.tcp.UserAgent;
import com.webank.eventmesh.runtime.util.RemotingHelper;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.openmessaging.api.Message;
import io.openmessaging.api.SendCallback;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static com.webank.eventmesh.common.protocol.tcp.Command.LISTEN_RESPONSE;

public class Session {

    protected final Logger messageLogger = LoggerFactory.getLogger("message");

    private final Logger subscribeLogger = LoggerFactory.getLogger("subscribeLogger");

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private UserAgent client;

    private InetSocketAddress remoteAddress;

    protected ChannelHandlerContext context;

    private WeakReference<ClientGroupWrapper> clientGroupWrapper;

    private EventMeshTCPConfiguration eventMeshTCPConfiguration;

    private SessionPusher pusher;

    private SessionSender sender;

    private long createTime = System.currentTimeMillis();

    private long lastHeartbeatTime = System.currentTimeMillis();

    private long isolateTime = 0;

    private SessionContext sessionContext = new SessionContext(this);

    private boolean listenRspSend = false;

    private ReentrantLock listenRspLock = new ReentrantLock();

    private String listenRequestSeq = null;

    protected SessionState sessionState = SessionState.CREATED;

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(InetSocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public long getLastHeartbeatTime() {
        return lastHeartbeatTime;
    }

    public void notifyHeartbeat(long heartbeatTime) throws Exception {
        this.lastHeartbeatTime = heartbeatTime;
    }

    public SessionState getSessionState() {
        return sessionState;
    }

    public void setSessionState(SessionState sessionState) {
        this.sessionState = sessionState;
    }

    public void setClient(UserAgent client) {
        this.client = client;
    }

    public SessionPusher getPusher() {
        return pusher;
    }

    public void setPusher(SessionPusher pusher) {
        this.pusher = pusher;
    }

    public SessionSender getSender() {
        return sender;
    }

    public void setSender(SessionSender sender) {
        this.sender = sender;
    }

    public void setLastHeartbeatTime(long lastHeartbeatTime) {
        this.lastHeartbeatTime = lastHeartbeatTime;
    }

    public SessionContext getSessionContext() {
        return sessionContext;
    }

    public void setSessionContext(SessionContext sessionContext) {
        this.sessionContext = sessionContext;
    }

    public ChannelHandlerContext getContext() {
        return context;
    }

    public void setContext(ChannelHandlerContext context) {
        this.context = context;
    }

    public UserAgent getClient() {
        return client;
    }

    public String getListenRequestSeq() {
        return listenRequestSeq;
    }

    public void setListenRequestSeq(String listenRequestSeq) {
        this.listenRequestSeq = listenRequestSeq;
    }

    public void subscribe(List<String> topics) throws Exception {
        for (String topic : topics) {
            sessionContext.subscribeTopics.putIfAbsent(topic, topic);
            clientGroupWrapper.get().subscribe(topic);

            clientGroupWrapper.get().getMqProducerWrapper().getMeshMQProducer().getDefaultTopicRouteInfoFromNameServer(topic,
                    EventMeshConstants.DEFAULT_TIME_OUT_MILLS);

            clientGroupWrapper.get().addSubscription(topic, this);
            subscribeLogger.info("subscribe|succeed|topic={}|user={}", topic, client);
        }
    }

    public void unsubscribe(List<String> topics) throws Exception {
        for (String topic : topics) {
            sessionContext.subscribeTopics.remove(topic);
            clientGroupWrapper.get().removeSubscription(topic, this);

            if (!clientGroupWrapper.get().hasSubscription(topic)) {
                clientGroupWrapper.get().unsubscribe(topic);
                subscribeLogger.info("unSubscribe|succeed|topic={}|lastUser={}", topic, client);
            }
        }
    }

    public EventMeshTcpSendResult upstreamMsg(Header header, Message msg, SendCallback sendCallback, long startTime, long taskExecuteTime) {
        String topic = msg.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION);
        sessionContext.sendTopics.putIfAbsent(topic, topic);
        return sender.send(header, msg, sendCallback, startTime, taskExecuteTime);
    }

    public void downstreamMsg(DownStreamMsgContext downStreamMsgContext) {
        long currTime = System.currentTimeMillis();
        trySendListenResponse(new Header(LISTEN_RESPONSE, OPStatus.SUCCESS.getCode(), "succeed", getListenRequestSeq()), currTime, currTime);

        pusher.push(downStreamMsgContext);
    }

    public boolean isDownStreamBusy() {
        return pusher.isBusy();
    }

    public boolean isCanDownStream() {
        return pusher.isCanDownStream();
    }

    public boolean isIsolated(){
        return System.currentTimeMillis() < isolateTime;
    }

    public void write2Client(final Package pkg) {

        try {
            if (SessionState.CLOSED.equals(sessionState)) {
                return;
            }
            context.writeAndFlush(pkg).addListener(
                    new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (!future.isSuccess()) {
                                messageLogger.error("write2Client fail, pkg[{}] session[{}]", pkg, this);
                            }else{
                                clientGroupWrapper.get().getEventMeshTcpMonitor().getEventMesh2clientMsgNum().incrementAndGet();
                            }
                        }
                    }
            );
        } catch (Exception e) {
            logger.error("exception while write2Client", e);
        }
    }

    /**
     * ACK MSG
     *
     * @param seq
     */
    public void ackMsg(String seq) {
        logger.info("ackMsg start,seq:{}", seq);
        pusher.getPushContext().ackMsg(seq);
        logger.info("ackMsg end,seq:{}", seq);
    }

    @Override
    public String toString() {
        return "Session{" +
                "group=" + clientGroupWrapper.get().getGroupName() +
                ",remoteAddr=" + RemotingHelper.parseSocketAddressAddr(remoteAddress) +
                ",client=" + client +
                ",sessionState=" + sessionState +
                ",sessionContext=" + sessionContext +
                ",pusher=" + pusher +
                ",sender=" + sender +
                ",createTime=" + DateFormatUtils.format(createTime, EventMeshConstants.DATE_FORMAT) +
                ",lastHeartbeatTime=" + DateFormatUtils.format(lastHeartbeatTime, EventMeshConstants.DATE_FORMAT) + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Session session = (Session) o;
        if (client != null ? !client.equals(session.client) : session.client != null) {
            return false;
        }
        if (context != null ? !context.equals(session.context) : session.context != null) {
            return false;
        }
        if (sessionState != null ? !sessionState.equals(session.sessionState) : session.sessionState != null) {
            return false;
        }
        return true;
    }

    public WeakReference<ClientGroupWrapper> getClientGroupWrapper() {
        return clientGroupWrapper;
    }

    public void setClientGroupWrapper(WeakReference<ClientGroupWrapper> clientGroupWrapper) {
        this.clientGroupWrapper = clientGroupWrapper;
    }

    public Session(UserAgent client, ChannelHandlerContext context, EventMeshTCPConfiguration eventMeshTCPConfiguration) {
        this.client = client;
        this.context = context;
        this.eventMeshTCPConfiguration = eventMeshTCPConfiguration;
        this.remoteAddress = (InetSocketAddress) context.channel().remoteAddress();
        this.sender = new SessionSender(this);
        this.pusher = new SessionPusher(this);
    }

    public EventMeshTCPConfiguration getEventMeshTCPConfiguration() {
        return eventMeshTCPConfiguration;
    }

    public void setEventMeshTCPConfiguration(EventMeshTCPConfiguration eventMeshTCPConfiguration) {
        this.eventMeshTCPConfiguration = eventMeshTCPConfiguration;
    }

    public void trySendListenResponse(Header header, long startTime, long taskExecuteTime) {
        if (!listenRspSend) {
            if (listenRspLock.tryLock()) {
                if (!listenRspSend) {
                    if (header == null) {
                        header = new Header(LISTEN_RESPONSE, OPStatus.SUCCESS.getCode(), "succeed", null);
                    }
                    Package msg = new Package();
                    msg.setHeader(header);

                    //TODO startTime 修改了
                    Utils.writeAndFlush(msg, startTime, taskExecuteTime, context, this);
                    listenRspSend = true;
                }
                listenRspLock.unlock();
            }
        }
    }

    public long getIsolateTime() {
        return isolateTime;
    }

    public void setIsolateTime(long isolateTime) {
        this.isolateTime = isolateTime;
    }

    public boolean isAvailable(String topic){
        if(SessionState.CLOSED == sessionState){
            logger.warn("session is not available because session has been closed");
            return false;
        }

        if(!sessionContext.subscribeTopics.containsKey(topic)){
            logger.warn("session is not available because session has not subscribe topic:{}",topic);
            return false;
        }
        if(isIsolated()){
            logger.warn("session is not available because session is isolated,isolateTime:{}",isolateTime);
            return false;
        }
        return true;
    }
}
