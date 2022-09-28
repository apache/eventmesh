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

package org.apache.eventmesh.runtime.core.protocol.amqp.processor;

import org.apache.eventmesh.runtime.boot.EventMeshAmqpServer;
import org.apache.eventmesh.runtime.core.protocol.amqp.MetaMessageService;
import org.apache.eventmesh.runtime.core.protocol.amqp.VirtualHost;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.AMQPFrame;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.constants.ProtocolKey;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ErrorCodes;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ProtocolFrame;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ProtocolVersion;
import org.apache.eventmesh.runtime.util.AmqpUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.Attribute;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.LongString;
import com.rabbitmq.client.impl.AMQImpl;
import com.rabbitmq.client.impl.Frame;

public class AmqpConnection  extends AmqpHandler {
    private static final Logger logger = LoggerFactory.getLogger(AmqpConnection.class);

    enum ConnectionState {
        INIT,
        AWAIT_START_OK,
        AWAIT_SECURE_OK,
        AWAIT_TUNE_OK,
        AWAIT_OPEN,
        OPEN
    }

    private volatile ConnectionState state;

    private ProtocolVersion protocolVersion;

    private int heartBeatDelay;

    private int maxFrameSize;

    private int maxChannelNo;

    private VirtualHost vhost;

    private MetaMessageService metaMessageService;

    private ConcurrentHashMap<Integer, AmqpChannel> channels;

    private ConcurrentHashMap<Integer, AmqpChannel> closingChannelsList;

    public AmqpConnection(EventMeshAmqpServer amqpServer) {
        super(amqpServer);
        this.channels = new ConcurrentHashMap<>();
        this.closingChannelsList = new ConcurrentHashMap<>();
        this.state = ConnectionState.INIT;
    }

    @Override
    public void close() {

    }

    @Override
    public void receiveConnectionStartOk(Map<String, Object> clientProperties, String mechanism, LongString response, String locale) {
        if (logger.isDebugEnabled()) {
            logger.debug("RECV ConnectionStartOk[clientProperties: {}, mechanism: {}, locale: {}]",
                    clientProperties, mechanism, locale);
        }
        assertState(ConnectionState.AWAIT_START_OK);
        if (StringUtils.isBlank(mechanism)) {
            AmqpUtils.sendConnectionClose(ErrorCodes.CONNECTION_FORCED, "No mechanism provided", 0);
            return;
        }
        if (response == null || response.length() == 0) {
            AmqpUtils.sendConnectionClose(ErrorCodes.CONNECTION_FORCED, "No authentication data provided", 0);
            return;
        }

        // TODO: 2022/9/20 Authentication Mechanism set up

        AMQImpl.Connection.Tune tune = (AMQImpl.Connection.Tune) new AMQImpl.Connection.Tune.Builder()
                .channelMax(maxChannelNo)
                .frameMax(maxFrameSize)
                .heartbeat(heartBeatDelay)
                .build();
        this.state = ConnectionState.AWAIT_TUNE_OK;
        try {
            ctx.writeAndFlush(AMQPFrame.get(tune.toFrame(0)));
        } catch (IOException e) {
            logger.error("Exception occur while handling startOk, e:", e);
            AmqpUtils.sendConnectionClose(ErrorCodes.INTERNAL_ERROR, "exception happen when generate frame", 0);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void receiveConnectionSecureOk(LongString response) {

    }

    @Override
    public void receiveConnectionTuneOk(int channelMax, long frameMax, int heartbeat) {
        if (logger.isDebugEnabled()) {
            logger.debug("RECV ConnectionTuneOk[ channelMax: {} frameMax: {} heartbeat: {} ]",
                    channelMax, frameMax, heartbeat);
        }
        assertState(ConnectionState.AWAIT_TUNE_OK);
        if (heartbeat > 0) {
            this.heartBeatDelay = heartbeat;
            long writerDelay = 1000L * heartbeat;
            long readerDelay = 1000L * 2 * heartbeat;
            initHeartBeatHandler(writerDelay, readerDelay);
        }

        int serverFrameMax = getDefaultServerMaxFrameSize();
        if (serverFrameMax <= 0) {
            serverFrameMax = Integer.MAX_VALUE;
        }

        if (frameMax > (long) serverFrameMax) {
            AmqpUtils.sendConnectionClose(ErrorCodes.SYNTAX_ERROR,
                    "Attempt to set max frame size to " + frameMax
                            + " greater than the broker will allow: "
                            + serverFrameMax, 0);
        } else if (frameMax > 0 && frameMax < amqpServer.getEventMeshAmqpConfiguration().minFrameSize) {
            AmqpUtils.sendConnectionClose(ErrorCodes.SYNTAX_ERROR,
                    "Attempt to set max frame size to " + frameMax
                            + " which is smaller than the specification defined minimum: "
                            + amqpServer.getEventMeshAmqpConfiguration().minFrameSize, 0);
        } else {
            int calculatedFrameMax = frameMax == 0 ? serverFrameMax : (int) frameMax;
            setMaxFrameSize(calculatedFrameMax);
        }

        this.maxChannelNo = ((channelMax == 0) || (channelMax > 0xFFFF))
                ? 0xFFFF
                : channelMax;
        this.state = ConnectionState.AWAIT_OPEN;
    }

    @Override
    public void receiveConnectionOpen(String virtualHost, String capabilities, boolean insist) {
        if (logger.isDebugEnabled()) {
            logger.debug("RECV ConnectionOpen[virtualHost: {} capabilities: {} insist: {} ]",
                    virtualHost, capabilities, insist);
        }

        assertState(ConnectionState.AWAIT_OPEN);

        boolean isDefaultNamespace = false;
        if ((virtualHost != null) && virtualHost.charAt(0) == '/') {
            virtualHost = virtualHost.substring(1);
            if (StringUtils.isEmpty(virtualHost)) {
                isDefaultNamespace = true;
                this.vhost = VirtualHost.DEFAULT_VHOST;
            }
        } else {
            // currently only support default vhost: "/"
            AmqpUtils.sendConnectionClose(ErrorCodes.NOT_FOUND, "Unknown virtual host: '" + virtualHost + "'", 0);
            return;
        }
        AMQImpl.Connection.OpenOk openOk = (AMQImpl.Connection.OpenOk) new AMQP.Connection.OpenOk.Builder()
                .knownHosts(virtualHost)
                .build();
        try {
            ctx.writeAndFlush(AMQPFrame.get(openOk.toFrame(0)));
        } catch (IOException e) {
            logger.error("Exception occur while handling open, e:", e);
            AmqpUtils.sendConnectionClose(ErrorCodes.INTERNAL_ERROR, "exception happen when generate frame", 0);
            throw new RuntimeException(e);
        }
        this.state = ConnectionState.OPEN;
    }

    @Override
    public void receiveChannelOpen(int channelId) {
        if (logger.isDebugEnabled()) {
            logger.debug("RECV[" + channelId + "] ChannelOpen");
        }
        assertState(ConnectionState.OPEN);

        if (vhost == null) {
            AmqpUtils.sendConnectionClose(ErrorCodes.COMMAND_INVALID,
                    "Virtualhost has not yet been set. ConnectionOpen has not been called.", channelId);
        } else if (channels.containsKey(channelId) || channelAwaitingClosure(channelId)) {
            AmqpUtils.sendConnectionClose(ErrorCodes.CHANNEL_ERROR, "Channel " + channelId + " already exists", channelId);
        } else if (channelId > maxChannelNo) {
            AmqpUtils.sendConnectionClose(ErrorCodes.CHANNEL_ERROR,
                    "Channel " + channelId + " cannot be created as the max allowed channel id is "
                            + maxChannelNo,
                    channelId);
        } else {
            AmqpChannel amqpChannel = new AmqpChannel(channelId, this);
            this.channels.put(channelId, amqpChannel);

            AMQImpl.Channel.OpenOk openOk = (AMQImpl.Channel.OpenOk) new AMQP.Channel.OpenOk.Builder()
                    .channelId(String.valueOf(channelId))
                    .build();
            try {
                ctx.writeAndFlush(AMQPFrame.get(openOk.toFrame(channelId)));
            } catch (IOException e) {
                logger.error("Exception occur while handling open, e:", e);
                AmqpUtils.sendConnectionClose(ErrorCodes.INTERNAL_ERROR, "exception happen when generate frame", 0);
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return null;
    }

    @Override
    public ChannelMethodProcessor getChannelMethodProcessor(int channelId) {
        return null;
    }

    @Override
    public void receiveConnectionClose(int replyCode, String replyText, int classId, int methodId) {

    }

    @Override
    public void receiveConnectionCloseOk() {

    }

    @Override
    public void receiveHeartbeat() {

    }

    /**
     * process protocol header, validate protocol version etc
     * @param protocolFrame protocol header
     */
    @Override
    public void receiveProtocolHeader(ProtocolFrame protocolFrame) {
        if (logger.isDebugEnabled()) {
            logger.debug("RECV Protocol Header [{}]", protocolFrame);
        }
        try {
            ProtocolVersion pv = protocolFrame.checkVersion();
            setProtocolVersion(pv);
            // TODO: 2022/9/21 build mechanism
            String mechanisms = "PLAIN token";
            String locales = "en_US";
            // TODO: 2022/9/19 put server Properties into map
            this.state = ConnectionState.AWAIT_START_OK;
            AMQImpl.Connection.Start start = (AMQImpl.Connection.Start) new AMQP.Connection.Start.Builder()
                    .versionMajor(pv.getProtocolMajor())
                    .versionMinor(pv.getActualMinorVersion())
                    .serverProperties(null)
                    .mechanisms(mechanisms)
                    .locales(locales)
                    .build();
            ctx.writeAndFlush(AMQPFrame.get(start.toFrame(0)));
        } catch (Exception e) {
            logger.error("Exception occur while handling protocol header, e:", e);
            AmqpUtils.writeFrame(new ProtocolFrame(ProtocolVersion.v0_91));
            throw new RuntimeException(e);
        }

    }

    @Override
    public void setCurrentMethod(int classId, int methodId) {

    }

    @Override
    public boolean ignoreAllButCloseOk() {
        return false;
    }

    private void setProtocolVersion(ProtocolVersion pv) {
        this.protocolVersion = pv;
    }

    private int getDefaultServerMaxFrameSize() {
        return this.amqpServer.getEventMeshAmqpConfiguration().defaultNetworkBufferSize - AMQPFrame.NON_BODY_SIZE;
    }

    private void setMaxFrameSize(int frameSize) {
        this.maxFrameSize = frameSize;
        Attribute<Integer> maxFrameSizeAttr = this.ctx.channel().attr(ProtocolKey.MAX_FRAME_SIZE);
        maxFrameSizeAttr.setIfAbsent(frameSize);
    }

    public void initHeartBeatHandler(long writerIdle, long readerIdle) {

        this.ctx.pipeline().addFirst("idleStateHandler", new IdleStateHandler(readerIdle, writerIdle, 0,
                TimeUnit.MILLISECONDS));
        this.ctx.pipeline().addLast("connectionIdleHandler", new ConnectionIdleHandler());

    }

    void assertState(final ConnectionState requiredState) {
        if (state != requiredState) {
            String replyText = "Command Invalid, expected " + requiredState + " but was " + state;
            AmqpUtils.sendConnectionClose(ErrorCodes.COMMAND_INVALID, replyText, 0);
            throw new RuntimeException(replyText);
        }
    }

    public boolean channelAwaitingClosure(int channelId) {
        return ignoreAllButCloseOk() || (!closingChannelsList.isEmpty()
                && closingChannelsList.containsKey(channelId));
    }

    public MetaMessageService getAmqpBrokerService() {
        return metaMessageService;
    }

    class ConnectionIdleHandler extends ChannelDuplexHandler {

        @Override public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state().equals(IdleState.READER_IDLE)) {
                    logger.error("heartbeat timeout close remoteSocketAddress [{}]",
                            AmqpConnection.this.remoteAddress.toString());
                    AmqpConnection.this.close();
                } else if (event.state().equals(IdleState.WRITER_IDLE)) {
                    logger.warn("heartbeat write  idle [{}]", AmqpConnection.this.remoteAddress.toString());
                    AmqpUtils.writeFrame(AMQPFrame.get(new Frame(AMQP.FRAME_HEARTBEAT, 0)));
                }
            }
            super.userEventTriggered(ctx, evt);
        }
    }

}
