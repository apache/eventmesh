package org.apache.eventmesh.connector.redis.consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.redis.InlineCommandRedisMessage;
import io.openmessaging.api.*;
import io.openmessaging.api.exception.OMSRuntimeException;
import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.consumer.MeshMQPushConsumer;
import org.apache.eventmesh.connector.redis.common.Command;
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

public class RedisSubConsumerImpl implements MeshMQPushConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisSubConsumerImpl.class);

    private Bootstrap bootstrap;

    private Channel channel;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private final List<String> topics = new CopyOnWriteArrayList<>();

    private final Map<String, AsyncMessageListener> subscribeTable = new ConcurrentHashMap<>();

    private Properties properties;

    public RedisSubConsumerImpl(Properties properties) {
        this.properties = properties;
    }

    @Override
    public void init(Properties keyValue) throws Exception {
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

    @Override
    public boolean isStarted() {
        return started.get();
    }

    @Override
    public boolean isClosed() {
        return !isStarted();
    }

    /**
     * create a connection (channel) of redis node
     */
    @Override
    public void start() {
        if (started.compareAndSet(false, true)) {
            try {
                ChannelFuture channelFuture = bootstrap.connect("", 8080);
                channelFuture.addListener(future -> {
                    if (future.isSuccess()) {
                        this.channel = channelFuture.channel();
                    } else {
                        LOGGER.warn("Subscribe client can't connect to [{}]", "xxxxxxxxx");
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
    public void updateOffset(List<Message> msgs, AbstractContext context) {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public void subscribe(String topic, AsyncMessageListener listener) throws Exception {
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
    public void unsubscribe(String topic) {
        if (topic == null) {
            LOGGER.warn("unsubscribe topic is null");
            return;
        }

        if ("".equals(topic)) {
            LOGGER.warn("unsubscribe all of topics");
        }

        StringJoiner joiner = new StringJoiner(" ");
        joiner.add(Command.UNSUBSCRIBE.getCmdName());
        joiner.add(topic);

        InlineCommandRedisMessage message = new InlineCommandRedisMessage(joiner.toString());
        channel.writeAndFlush(message)
            .addListener(f -> {
                if (f.isSuccess()) {
                    LOGGER.info("Success unsubscribe topic: [{}]", topic);
                    if ("".equals(topic)) {
                        topics.clear();
                        subscribeTable.clear();
                    } else {
                        topics.remove(topic);
                        subscribeTable.remove(topic);
                    }
                } else {
                    LOGGER.warn("Fail unsubscribe topic: [{}], exception: [{}]", topic, f.cause().getMessage());
                }
            });
    }

    @Override
    public void subscribe(String topic, String subExpression, MessageListener listener) {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public void subscribe(String topic, MessageSelector selector, MessageListener listener) {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public <T> void subscribe(String topic, String subExpression, GenericMessageListener<T> listener) {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public <T> void subscribe(String topic, MessageSelector selector, GenericMessageListener<T> listener) {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public void subscribe(String topic, String subExpression, AsyncMessageListener listener) {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public void subscribe(String topic, MessageSelector selector, AsyncMessageListener listener) {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public <T> void subscribe(String topic, String subExpression, AsyncGenericMessageListener<T> listener) {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public <T> void subscribe(String topic, MessageSelector selector, AsyncGenericMessageListener<T> listener) {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public void updateCredential(Properties credentialProperties) {
        throw new UnsupportedOperationException("not supported yet");
    }
}
