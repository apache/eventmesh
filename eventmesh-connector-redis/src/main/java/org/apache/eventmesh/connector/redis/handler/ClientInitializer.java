package org.apache.eventmesh.connector.redis.handler;


import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.redis.RedisArrayAggregator;
import io.netty.handler.codec.redis.RedisBulkStringAggregator;
import io.netty.handler.codec.redis.RedisDecoder;
import io.netty.handler.codec.redis.RedisEncoder;


public class ClientInitializer extends ChannelInitializer<NioSocketChannel> {

    private final SubscribeHandler subscribeHandler;

    public ClientInitializer(SubscribeHandler subscribeHandler) {
        this.subscribeHandler = subscribeHandler;
    }


    @Override
    protected void initChannel(NioSocketChannel ch) throws Exception {
        ch.pipeline()
            .addLast(new RedisDecoder(true))
            .addLast(new RedisBulkStringAggregator())
            .addLast(new RedisArrayAggregator())
            .addLast(new RedisEncoder())
            .addLast(this.subscribeHandler);
    }
}
