/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.apache.eventmesh.runtime.core.protocol.amqp.processor;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.LongString;
import com.rabbitmq.client.impl.AMQImpl;
import com.rabbitmq.client.impl.Frame;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.Getter;
import org.apache.eventmesh.runtime.boot.EventMeshAmqpServer;
import org.apache.eventmesh.runtime.configuration.EventMeshAmqpConfiguration;
import org.apache.eventmesh.runtime.core.protocol.amqp.consumer.AmqpMessageSender;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.AMQData;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.AMQPFrame;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.CommandFactory;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.codec.AmqpCodeDecoder;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ErrorCodes;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ProtocolFrame;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ProtocolVersion;
import org.apache.eventmesh.runtime.core.protocol.amqp.util.AmqpInOutputConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Amqp server level method processor.
 */
public class AmqpConnection extends AmqpHandler {


    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public static final String DEFAULT_VIRTUALHOST = "/";

    public enum ConnectionState {
        INIT,
        AWAIT_START_OK,
        AWAIT_SECURE_OK,
        AWAIT_TUNE_OK,
        AWAIT_OPEN,
        OPEN
    }

    private String connectionId;
    @Getter
    private final ConcurrentHashMap<Integer, AmqpChannel> channels;
    private final ConcurrentHashMap<Integer, Long> closingChannelsList;
    @Getter
    private final EventMeshAmqpConfiguration amqpConfig;
    private ProtocolVersion protocolVersion;
    private CommandFactory commandFactory;
    private volatile ConnectionState state = ConnectionState.INIT;
    private volatile int currentClassId;
    private volatile int currentMethodId;
    @Getter
    private final AtomicBoolean orderlyClose = new AtomicBoolean(false);
    private volatile int maxChannels;
    private volatile int maxFrameSize;
    private volatile int heartBeat;
    private String virtualHostName;
    private final Object channelAddRemoveLock = new Object();
    private AtomicBoolean blocked = new AtomicBoolean();
    private AmqpMessageSender amqpOutputConverter;


    public AmqpConnection(EventMeshAmqpServer amqpBrokerService) {
        super(amqpBrokerService);
        this.connectionId = UUID.randomUUID().toString();
        this.channels = new ConcurrentHashMap<>();
        this.closingChannelsList = new ConcurrentHashMap<>();
        this.protocolVersion = ProtocolVersion.v0_91;
        this.commandFactory = new CommandFactory(this.protocolVersion);
        this.amqpConfig = amqpBrokerService.getEventMeshAmqpConfiguration();
        this.maxChannels = amqpConfig.maxNoOfChannels;
        this.maxFrameSize = amqpConfig.maxFrameSize;
        this.heartBeat = amqpConfig.heartBeat;
        this.amqpOutputConverter = new AmqpMessageSender(this);
    }

    public AmqpMessageSender getAmqpOutputConverter() {
        return amqpOutputConverter;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.remoteAddress = ctx.channel().remoteAddress();
        this.ctx = ctx;
        isActive.set(true);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        completeAndCloseAllChannels();
        //amqpBrokerService.getConnectionContainer().removeConnection(virtualHostName, this);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("[{}] Got exception: {}", remoteAddress, cause.getMessage(), cause);
        close();
    }

    @Override
    public void close() {
        if (isActive.getAndSet(false)) {
            log.info("close netty channel {}", ctx.channel());
            ctx.close();
        }
    }

    @Override
    public void receiveConnectionStartOk(Map<String, Object> clientProperties, String mechanism, LongString response,
                                         String locale) {
        if (log.isDebugEnabled()) {
            log.debug("RECV ConnectionStartOk[clientProperties: {}, mechanism: {}, locale: {}]",
                    clientProperties, mechanism, locale);
        }
        assertState(ConnectionState.AWAIT_START_OK);

        log.debug("SASL Mechanism selected: {} Locale : {}, response: {}", mechanism, locale, response);
        writeMethod(this.commandFactory.createConnectionTuneBody(maxChannels, maxFrameSize, heartBeat), 0);
        state = ConnectionState.AWAIT_TUNE_OK;
    }

    @Override
    public void receiveConnectionSecureOk(LongString response) {
        if (log.isDebugEnabled()) {
            log.debug("RECV ConnectionSecureOk");
        }
        assertState(ConnectionState.AWAIT_SECURE_OK);

        writeMethod(this.commandFactory.createConnectionTuneBody(maxChannels,
                maxFrameSize,
                heartBeat), 0);
        state = ConnectionState.AWAIT_TUNE_OK;
    }

    @Override
    public void receiveConnectionTuneOk(int channelMax, long frameMax, int heartbeat) {
        if (log.isDebugEnabled()) {
            log.debug("RECV ConnectionTuneOk[ channelMax: {} frameMax: {} heartbeat: {} ]",
                    channelMax, frameMax, heartbeat);
        }
        assertState(ConnectionState.AWAIT_TUNE_OK);

        if (heartbeat > 0) {
            this.heartBeat = heartbeat;
            long writerDelay = 1000L * heartbeat;
            long readerDelay = 1000L * 2 * heartbeat;
            initHeartBeatHandler(writerDelay, readerDelay);
        }
        int brokerFrameMax = maxFrameSize;
        if (brokerFrameMax <= 0) {
            brokerFrameMax = Integer.MAX_VALUE;
        }

        if (frameMax > (long) brokerFrameMax) {
            sendConnectionClose(ErrorCodes.SYNTAX_ERROR,
                    "Attempt to set max frame size to " + frameMax
                            + " greater than the broker will allow: "
                            + brokerFrameMax, 0);
        } else if (frameMax > 0 && frameMax < amqpConfig.minFrameSize) {
            sendConnectionClose(ErrorCodes.SYNTAX_ERROR,
                    "Attempt to set max frame size to " + frameMax
                            + " which is smaller than the specification defined minimum: "
                            + amqpConfig.maxFrameSize, 0);
        } else {
            int calculatedFrameMax = frameMax == 0 ? brokerFrameMax : (int) frameMax;
            setMaxFrameSize(calculatedFrameMax);

            //0 means no implied limit, except that forced by protocol limitations (0xFFFF)
            int value = ((channelMax == 0) || (channelMax > 0xFFFF))
                    ? 0xFFFF
                    : channelMax;
            maxChannels = value;
        }
        state = ConnectionState.AWAIT_OPEN;

    }

    @Override
    public void receiveConnectionOpen(String virtualHost, String capabilities, boolean insist) {
        if (log.isDebugEnabled()) {
            log.debug("RECV ConnectionOpen[virtualHost: {} capabilities: {} insist: {} ]",
                    virtualHost, capabilities, insist);
        }

        assertState(ConnectionState.AWAIT_OPEN);


        if (!DEFAULT_VIRTUALHOST.equals(virtualHost)) {
            sendConnectionClose(ErrorCodes.NOT_FOUND, "vhost: " + virtualHost
                    + " not found ", 0);
            return;
        }


        this.virtualHostName = virtualHost;
        writeMethod(this.commandFactory.createConnectionOpenOkBody(virtualHost), 0);
        state = ConnectionState.OPEN;
    }

    @Override
    public void receiveConnectionClose(int replyCode, String replyText,
                                       int classId, int methodId) {
        if (log.isDebugEnabled()) {
            log.debug("RECV ConnectionClose[ replyCode: {} replyText: {} classId: {} methodId: {} ]",
                    replyCode, replyText, classId, methodId);
        }

        try {
            if (orderlyClose.compareAndSet(false, true)) {
                completeAndCloseAllChannels();
            }

            writeMethod(this.commandFactory.createConnectionCloseOkBody(), 0);
        } catch (Exception e) {
            log.error("Error closing connection for " + this.remoteAddress.toString(), e);
        } finally {
            close();
        }
    }

    @Override
    public void receiveConnectionCloseOk() {
        if (log.isDebugEnabled()) {
            log.debug("RECV ConnectionCloseOk");
        }
        close();
    }

    public void sendConnectionClose(int errorCode, String message, int channelId) {
        sendConnectionClose(channelId, new AMQImpl.Connection.Close(errorCode,
                message, currentClassId, currentMethodId));
    }

    private void sendConnectionClose(int channelId, com.rabbitmq.client.Method method) {
        if (orderlyClose.compareAndSet(false, true)) {
            try {
                markChannelAwaitingCloseOk(channelId);
                completeAndCloseAllChannels();
            } finally {
                writeMethod(method, 0);
            }
        }
    }

    @Override
    public void receiveChannelOpen(int channelId) {

        if (log.isDebugEnabled()) {
            log.debug("RECV[" + channelId + "] ChannelOpen");
        }
        assertState(ConnectionState.OPEN);

        if (this.virtualHostName == null) {
            sendConnectionClose(ErrorCodes.COMMAND_INVALID,
                    "Virtualhost has not yet been set. ConnectionOpen has not been called.", channelId);
        } else if (channels.get(channelId) != null || channelAwaitingClosure(channelId)) {
            sendConnectionClose(ErrorCodes.CHANNEL_ERROR, "Channel " + channelId + " already exists", channelId);
        } else if (channelId > maxChannels) {
            sendConnectionClose(ErrorCodes.CHANNEL_ERROR,
                    "Channel " + channelId + " cannot be created as the max allowed channel id is "
                            + maxChannels,
                    channelId);
        } else {
            log.debug("Connecting to: {}", virtualHostName.toString());
            final AmqpChannel channel = new AmqpChannel(channelId, this);
            addChannel(channel);
            writeMethod(this.commandFactory.createChannelOpenOkBody(channelId), channelId);
        }

    }

    private void addChannel(AmqpChannel channel) {
        synchronized (channelAddRemoveLock) {
            channels.put(channel.getChannelId(), channel);
            if (blocked.get()) {
                channel.block();
            }
        }
    }

    @Override
    public void receiveHeartbeat() {
        if (log.isDebugEnabled()) {
            log.debug("RECV Heartbeat");
        }
        // noop
    }

    @Override
    public void receiveProtocolHeader(ProtocolFrame pf) {
        if (log.isDebugEnabled()) {
            log.debug("RECV Protocol Header [{}]", pf);
        }

        try {
            ProtocolVersion pv = pf.checkVersion(); // Fails if not correct
            // TODO serverProperties mechanis
            this.protocolVersion = pv;
            writeMethod(this.commandFactory.createConnectionStartBody(
                    (short) pv.getProtocolMajor(),
                    (short) pv.getProtocolMinor(),
                    null,
                    // TODO temporary modification
                    "PLAIN AMQPLAIN",
                    "en_US"), 0);
            state = ConnectionState.AWAIT_START_OK;
        } catch (Exception e) {
            log.error("Received unsupported protocol initiation for protocol version: {} ", getProtocolVersion(), e);
            writeFrame(new ProtocolFrame(ProtocolVersion.v0_91));
            close();
            throw new RuntimeException(e);
        }
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return protocolVersion;
    }

    @Override
    public ChannelMethodProcessor getChannelMethodProcessor(int channelId) {
        assertState(ConnectionState.OPEN);
        ChannelMethodProcessor channelMethodProcessor = getChannel(channelId);
        if (channelMethodProcessor == null) {
            channelMethodProcessor =
                    (ChannelMethodProcessor) Proxy.newProxyInstance(ChannelMethodProcessor.class.getClassLoader(),
                            new Class[]{ChannelMethodProcessor.class}, new InvocationHandler() {
                                @Override
                                public Object invoke(final Object proxy, final Method method, final Object[] args)
                                        throws Throwable {
                                    if (method.getName().equals("receiveChannelCloseOk") && channelAwaitingClosure(channelId)) {
                                        closeChannelOk(channelId);
                                    } else if (method.getName().startsWith("receive")) {
                                        sendConnectionClose(ErrorCodes.CHANNEL_ERROR,
                                                "Unknown channel id: " + channelId, channelId);
                                    } else if (method.getName().equals("ignoreAllButCloseOk")) {
                                        return channelAwaitingClosure(channelId);
                                    }
                                    return null;
                                }
                            });
        }
        return channelMethodProcessor;
    }

    @Override
    public void setCurrentMethod(int classId, int methodId) {
        currentClassId = classId;
        currentMethodId = methodId;
    }

    void assertState(final ConnectionState requiredState) {
        if (state != requiredState) {
            String replyText = "Command Invalid, expected " + requiredState + " but was " + state;
            sendConnectionClose(ErrorCodes.COMMAND_INVALID, replyText, 0);
            throw new RuntimeException(replyText);
        }
    }

    public boolean channelAwaitingClosure(int channelId) {
        return ignoreAllButCloseOk() || (!closingChannelsList.isEmpty()
                && closingChannelsList.containsKey(channelId));
    }

    public void completeAndCloseAllChannels() {
        try {
            receivedCompleteAllChannels();
        } finally {
            closeAllChannels();
        }
    }

    private void receivedCompleteAllChannels() {
        RuntimeException exception = null;

        for (AmqpChannel channel : channels.values()) {
            try {
                channel.receivedComplete();
            } catch (RuntimeException exceptionForThisChannel) {
                if (exception == null) {
                    exception = exceptionForThisChannel;
                }
                log.error("error informing channel that receiving is complete. Channel:{} ", channel,
                        exceptionForThisChannel);
            }
        }

        if (exception != null) {
            throw exception;
        }
    }

    public void writeFrame(AMQData frame) {
        if (log.isDebugEnabled()) {
            log.debug("send: {}", frame);
        }
        getCtx().writeAndFlush(frame);
    }

    public void writeMethod(com.rabbitmq.client.Method method, int channelNo) {
        if (log.isDebugEnabled()) {
            log.debug("send method:{}", method);
        }
        try {
            Frame frame = ((com.rabbitmq.client.impl.Method) method).toFrame(channelNo);
            writeFrame(AMQPFrame.get(frame));
        } catch (IOException e) {
            log.error("write method frame exce.", e);
            close();
        }
    }

    public void initHeartBeatHandler(long writerIdle, long readerIdle) {

        this.ctx.pipeline().addFirst("idleStateHandler", new IdleStateHandler(readerIdle, writerIdle, 0,
                TimeUnit.MILLISECONDS));
        this.ctx.pipeline().addLast("connectionIdleHandler", new ConnectionIdleHandler());

    }

    class ConnectionIdleHandler extends ChannelDuplexHandler {

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state().equals(IdleState.READER_IDLE)) {
                    log.error("heartbeat timeout close remoteSocketAddress [{}]",
                            AmqpConnection.this.remoteAddress.toString());
                    AmqpConnection.this.close();
                } else if (event.state().equals(IdleState.WRITER_IDLE)) {
                    log.debug("heartbeat write  idle [{}]", AmqpConnection.this.remoteAddress.toString());
                    writeFrame(new AMQPFrame(AMQP.FRAME_HEARTBEAT, 0));
                }
            }

            super.userEventTriggered(ctx, evt);
        }

    }

    public void setMaxFrameSize(int frameMax) {
        maxFrameSize = frameMax;
        if (ctx.channel().attr(AmqpCodeDecoder.ATTRIBUTE_KEY_MAX_FRAME_SIZE) != null) {
            ctx.channel().attr(AmqpCodeDecoder.ATTRIBUTE_KEY_MAX_FRAME_SIZE).set(frameMax);
        }

    }

    public AmqpChannel getChannel(int channelId) {
        final AmqpChannel channel = channels.get(channelId);
        if ((channel == null) || channel.isClosing()) {
            return null;
        } else {
            return channel;
        }
    }

    public boolean isClosing() {
        return orderlyClose.get();
    }

    @Override
    public boolean ignoreAllButCloseOk() {
        return isClosing();
    }

    public void closeChannelOk(int channelId) {
        closingChannelsList.remove(channelId);
    }

    private void markChannelAwaitingCloseOk(int channelId) {
        closingChannelsList.put(channelId, System.currentTimeMillis());
    }

    private void removeChannel(int channelId) {
        synchronized (channelAddRemoveLock) {
            channels.remove(channelId);
        }
    }

    public void closeChannel(AmqpChannel channel) {
        closeChannel(channel, false);
    }

    public void closeChannelAndWriteFrame(AmqpChannel channel, int cause, String message) {
        writeMethod(this.commandFactory.createChannelCloseBody(cause, message,
                currentClassId, currentMethodId), channel.getChannelId());
        closeChannel(channel, true);
    }

    void closeChannel(AmqpChannel channel, boolean mark) {
        int channelId = channel.getChannelId();
        try {
            channel.close();
            if (mark) {
                markChannelAwaitingCloseOk(channelId);
            }
        } finally {
            removeChannel(channelId);
        }
    }

    private void closeAllChannels() {
        RuntimeException exception = null;
        try {
            for (AmqpChannel channel : channels.values()) {
                try {
                    channel.close();
                } catch (RuntimeException exceptionForThisChannel) {
                    if (exception == null) {
                        exception = exceptionForThisChannel;
                    }
                    log.error("error informing channel that receiving is complete. Channel:{}", channel,
                            exceptionForThisChannel);
                }
            }
            if (exception != null) {
                throw exception;
            }
        } finally {
            synchronized (channelAddRemoveLock) {
                channels.clear();
            }
        }
    }

    public void block() {
        synchronized (channelAddRemoveLock) {
            if (blocked.compareAndSet(false, true)) {
                for (AmqpChannel channel : channels.values()) {
                    channel.block();
                }
            }
        }
    }


    public int getMaxChannels() {
        return maxChannels;
    }

    public int getMaxFrameSize() {
        return maxFrameSize;
    }

    public int getHeartBeat() {
        return heartBeat;
    }

    public String getVirtualHostName() {
        return virtualHostName;
    }


//    public AmqpMessageSender getAmqpOutputConverter() {
//        return amqpOutputConverter;
//    }

    public String getConnectionId() {
        return connectionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AmqpConnection that = (AmqpConnection) o;
        return connectionId.equals(that.connectionId) && Objects.equals(virtualHostName, that.virtualHostName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId, virtualHostName);
    }


    public ConnectionState getState() {
        return state;
    }

    public long getChannelNum() {
        return channels.size();
    }

    public CommandFactory getCommandFactory() {
        return commandFactory;
    }

    public boolean isWritable() {
        return ctx.channel().isWritable();
    }

    public boolean isActive() {
        return ctx.channel().isActive();
    }

}
