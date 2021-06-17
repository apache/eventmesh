package org.apache.eventmesh.connector.redis.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.redis.*;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ThreadLocalRandom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * 处理PUBLISH的返回和SUBSCRIBE接收到的消息
 */
@ChannelHandler.Sharable
public class SubscribeHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LogManager.getLogger(SubscribeHandler.class);

    private static final ByteBuf SUBSCRIBE_BYTEBUF;

    private static final ByteBuf MESSAGE_BYTEBUF;

    private final Bootstrap bootstrap;

    static {
        byte[] subscribeBytes = "subscribe".getBytes();
        SUBSCRIBE_BYTEBUF = PooledByteBufAllocator.DEFAULT.buffer(subscribeBytes.length);
        SUBSCRIBE_BYTEBUF.writeBytes(subscribeBytes);

        byte[] messageBytes = "message".getBytes();
        MESSAGE_BYTEBUF = PooledByteBufAllocator.DEFAULT.buffer(messageBytes.length);
        MESSAGE_BYTEBUF.writeBytes(messageBytes);
    }


    public SubscribeHandler(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }


    /**
     * Subscribe接收到的消息
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ArrayRedisMessage && ((ArrayRedisMessage) msg).children().size() == 3) {
            List<RedisMessage> children = ((ArrayRedisMessage) msg).children();

            ByteBuf header = ((FullBulkStringRedisMessage) children.get(0)).content();
            String chStr = new String(ByteBufUtil.getBytes(((FullBulkStringRedisMessage) children.get(1)).content()));
            if (MESSAGE_BYTEBUF.equals(header)) {
                String msgStr = new String(ByteBufUtil.getBytes(((FullBulkStringRedisMessage) children.get(2)).content()));
                System.out.println(chStr + " : " + msgStr);

            } else if (SUBSCRIBE_BYTEBUF.equals(header)) {
                long value = ((IntegerRedisMessage) children.get(2)).value();
                System.out.println(chStr + " : " + value);
            }

        } else {
            LOGGER.warn("Subscribe handler can't deal response from: [{}]", ctx.channel().remoteAddress());
        }
        ReferenceCountUtil.release(msg);
    }


    /**
     * 监听subscribe连接断线事件
     * todo
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }
}
