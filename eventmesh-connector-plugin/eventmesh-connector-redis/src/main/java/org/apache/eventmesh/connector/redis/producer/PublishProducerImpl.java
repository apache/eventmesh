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

package org.apache.eventmesh.connector.redis.producer;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.redis.FullBulkStringRedisMessage;
import io.netty.handler.codec.redis.RedisMessage;
import io.openmessaging.api.*;
import io.openmessaging.api.exception.OMSRuntimeException;
import org.apache.eventmesh.api.RRCallback;
import org.apache.eventmesh.api.producer.MeshMQProducer;
import org.apache.eventmesh.connector.redis.common.Command;
import org.apache.eventmesh.connector.redis.common.IpAndPort;
import org.apache.eventmesh.connector.redis.handler.ClientInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class PublishProducerImpl implements Producer {

    private final static Logger LOGGER = LoggerFactory.getLogger(PublishProducerImpl.class);

    private final Bootstrap bootstrap;

    private Channel channel;

    private final IpAndPort ipAndPort;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private final Properties properties;

    private final static ByteBuf PUBLISH_CMD_BYTE;

    private final static ByteBuf SPACE_BYTE;

    static {
        PUBLISH_CMD_BYTE = PooledByteBufAllocator.DEFAULT.buffer(Command.PUBLISH.getBytes().length);
        PUBLISH_CMD_BYTE.writeBytes(Command.PUBLISH.getBytes());
        SPACE_BYTE = PooledByteBufAllocator.DEFAULT.buffer(1);
        SPACE_BYTE.writeBytes(" ".getBytes());
    }

    public PublishProducerImpl(Properties properties) {
        this.properties = properties;
        this.ipAndPort = IpAndPort.from(properties.getProperty("ipAndPort"));
        if (this.ipAndPort == null) {
            throw new OMSRuntimeException(-1,
                String.format("The redis address %s is invalid", properties.getProperty("ipAndPort")));
        }

        EventLoopGroup subscribeEventLoop = new NioEventLoopGroup(1, r -> {
            Thread thread = new Thread(r, "publish-thread");
            thread.setDaemon(true);
            return thread;
        });

        this.bootstrap = new Bootstrap()
            .group(subscribeEventLoop)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            .option(ChannelOption.SO_LINGER, 0)
            .option(ChannelOption.SO_SNDBUF, 32 * 1024)
            .option(ChannelOption.SO_RCVBUF, 32 * 1024)
            .option(ChannelOption.SO_REUSEADDR, true)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            .option(ChannelOption.RCVBUF_ALLOCATOR,
                new AdaptiveRecvByteBufAllocator(64, 1024, 65536));

        ClientInitializer initializer = new ClientInitializer();
        bootstrap.handler(initializer);
    }


    @Override
    public boolean isStarted() {
        return started.get();
    }

    @Override
    public boolean isClosed() {
        return !isStarted();
    }

    @Override
    public void start() {
        if (started.compareAndSet(false, true)) {
            try {
                ChannelFuture channelFuture = bootstrap.connect(ipAndPort.getIp(), ipAndPort.getPort());
                channelFuture.addListener(future -> {
                    if (future.isSuccess()) {
                        this.channel = channelFuture.channel();
                    } else {
                        LOGGER.warn("Subscribe client can't connect to [{}]", ipAndPort);
                    }
                });
            } catch (Exception e) {
                throw new OMSRuntimeException(e.getMessage());
            }
        }
    }

    @Override
    public void shutdown() {
        if (started.compareAndSet(true, false)
            && channel != null) {
            try {
                channel.close();
            } catch (Exception e) {
                throw new OMSRuntimeException(e.getMessage());
            }
        }
    }

    @Override
    public SendResult send(Message message) {
        SendResult sendResult = new SendResult();
        sendResult.setMessageId(message.getMsgID());
        sendResult.setTopic(message.getTopic());

        if (channel == null || !channel.isActive()) {
            LOGGER.warn("Can't publish msg: [{}] to node: [{}], because channel unavailable", message, ipAndPort);
            return sendResult;
        }

        try {
            channel.writeAndFlush(convert(message)).sync();
        } catch (InterruptedException e) {
            throw new OMSRuntimeException("send message had been interrupted");
        }

        return sendResult;
    }

    @Override
    public void sendOneway(Message message) {
        channel.writeAndFlush(convert(message));
    }

    @Override
    public void sendAsync(Message message, SendCallback sendCallback) {
        channel.writeAndFlush(convert(message))
            .addListener(future -> {
                if (future.isSuccess()) {
                    SendResult sendResult = new SendResult();
                    sendResult.setMessageId(message.getMsgID());
                    sendResult.setTopic(message.getTopic());
                    sendCallback.onSuccess(sendResult);
                } else {
                    OnExceptionContext onExceptionContext = new OnExceptionContext();
                    onExceptionContext.setMessageId(message.getMsgID());
                    onExceptionContext.setTopic(message.getTopic());
                    onExceptionContext.setException(new OMSRuntimeException(future.cause()));
                    sendCallback.onException(onExceptionContext);
                }
            });
    }

    @Override
    public void setCallbackExecutor(ExecutorService callbackExecutor) {

    }

    public void setExtFields() {

    }

    @Override
    public void updateCredential(Properties credentialProperties) {

    }

    @Override
    public <T> MessageBuilder<T> messageBuilder() {
        return null;
    }

    private RedisMessage convert(Message message) {
        ByteBuf byteBuf = UnpooledByteBufAllocator.DEFAULT.compositeDirectBuffer(5);

        PUBLISH_CMD_BYTE.resetReaderIndex();
        byteBuf.writeBytes(PUBLISH_CMD_BYTE);

        SPACE_BYTE.resetReaderIndex();
        byteBuf.writeBytes(SPACE_BYTE);

        byteBuf.writeBytes(message.getTopic().getBytes());

        SPACE_BYTE.resetReaderIndex();
        byteBuf.writeBytes(SPACE_BYTE);

        byteBuf.writeBytes(message.getBody());

        return new FullBulkStringRedisMessage(byteBuf);
    }
}
