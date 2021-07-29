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


package org.apache.eventmesh.connector.redis.consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.redis.InlineCommandRedisMessage;
import io.openmessaging.api.AsyncGenericMessageListener;
import io.openmessaging.api.AsyncMessageListener;
import io.openmessaging.api.Consumer;
import io.openmessaging.api.GenericMessageListener;
import io.openmessaging.api.MessageListener;
import io.openmessaging.api.MessageSelector;
import io.openmessaging.api.exception.OMSRuntimeException;
import org.apache.eventmesh.connector.redis.common.Command;
import org.apache.eventmesh.connector.redis.common.IpAndPort;
import org.apache.eventmesh.connector.redis.handler.ClientInitializer;
import org.apache.eventmesh.connector.redis.handler.SubscribeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class SubscribeConsumerImpl implements Consumer {

    private final static Logger LOGGER = LoggerFactory.getLogger(SubscribeConsumerImpl.class);

    private Bootstrap bootstrap;

    private Channel channel;

    private IpAndPort ipAndPort;

    private Properties properties;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private final Map<String, AsyncMessageListener> subscribeTable = new ConcurrentHashMap<>();

    private final List<String> topics = new CopyOnWriteArrayList<>();

    public SubscribeConsumerImpl(final Properties properties) {
        this.properties = properties;
        this.ipAndPort = IpAndPort.from(properties.getProperty("ipAndPort"));
        if (this.ipAndPort == null) {
            throw new OMSRuntimeException(-1,
                String.format("The redis address %s is invalid", properties.getProperty("ipAndPort")));
        }

        EventLoopGroup subscribeEventLoop = new NioEventLoopGroup(1, r -> {
            Thread thread = new Thread(r, "subscribe-thread");
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

        SubscribeHandler subscribeHandler = new SubscribeHandler(bootstrap, subscribeTable);
        ClientInitializer initializer = new ClientInitializer(subscribeHandler);

        bootstrap.handler(initializer);
    }

    /**
     * create a connection (channel) of redis node
     */
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
    public boolean isStarted() {
        return this.started.get();
    }

    @Override
    public boolean isClosed() {
        return !this.isStarted();
    }


    @Override
    public void subscribe(String topic, String subExpression, MessageListener listener) {

    }

    @Override
    public void subscribe(String topic, MessageSelector selector, MessageListener listener) {

    }

    @Override
    public <T> void subscribe(String topic, String subExpression, GenericMessageListener<T> listener) {

    }

    @Override
    public <T> void subscribe(String topic, MessageSelector selector, GenericMessageListener<T> listener) {

    }

    @Override
    public void subscribe(String topic, String subExpression, AsyncMessageListener listener) {
        if (topic.contains("*") || topic.contains("?") || topic.contains("[") || topic.contains("]")) {
            LOGGER.warn("Invalid topic: [{}], not support patterns subscribe yet", topic);
            return;
        }

        StringJoiner joiner = new StringJoiner(" ");
        joiner.add(Command.SUBSCRIBE.getCmdName());
        joiner.add(topic);

        InlineCommandRedisMessage message = new InlineCommandRedisMessage(joiner.toString());
        channel.writeAndFlush(message)
            .addListener(f -> {
                if (f.isSuccess()) {
                    LOGGER.info("Success subscribe topic: [{}]", topic);
                    topics.add(topic);
                    subscribeTable.put(topic, listener);
                } else {
                    LOGGER.warn("Fail subscribe topic: [{}], exception: [{}]", topic, f.cause().getMessage());
                }
            });
    }

    @Override
    public void subscribe(String topic, MessageSelector selector, AsyncMessageListener listener) {

    }

    @Override
    public <T> void subscribe(String topic, String subExpression, AsyncGenericMessageListener<T> listener) {

    }

    @Override
    public <T> void subscribe(String topic, MessageSelector selector, AsyncGenericMessageListener<T> listener) {

    }

    @Override
    public void unsubscribe(String topic) {
        if (topic == null || "".equals(topic)) {
            LOGGER.warn("unsubscribe topic is null");
            return;
        }

        if ("*".equals(topic)) {
            topic = "";
            LOGGER.warn("unsubscribe all of topics");
        }

        StringJoiner joiner = new StringJoiner(" ");
        joiner.add(Command.UNSUBSCRIBE.getCmdName());
        joiner.add(topic);

        InlineCommandRedisMessage message = new InlineCommandRedisMessage(joiner.toString());
        String finalTopic = topic;
        channel.writeAndFlush(message)
            .addListener(f -> {
                if (f.isSuccess()) {
                    LOGGER.info("Success unsubscribe topic: [{}]", finalTopic);
                    if ("".equals(finalTopic)) {
                        topics.clear();
                        subscribeTable.clear();
                    } else {
                        topics.remove(finalTopic);
                        subscribeTable.remove(finalTopic);
                    }
                } else {
                    LOGGER.warn("Fail unsubscribe topic: [{}], exception: [{}]", finalTopic, f.cause().getMessage());
                }
            });
    }

    @Override
    public void updateCredential(Properties credentialProperties) {

    }

}
