package org.apache.eventmesh.connector.redis.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.redis.ArrayRedisMessage;
import io.netty.handler.codec.redis.FullBulkStringRedisMessage;
import io.netty.handler.codec.redis.IntegerRedisMessage;
import io.netty.handler.codec.redis.RedisMessage;
import io.netty.util.ReferenceCountUtil;
import io.openmessaging.api.Action;
import io.openmessaging.api.AsyncMessageListener;
import io.openmessaging.api.Message;
import io.openmessaging.api.exception.OMSRuntimeException;
import org.apache.eventmesh.api.MeshAsyncConsumeContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * 处理PUBLISH的返回和SUBSCRIBE接收到的消息
 */
@ChannelHandler.Sharable
public class SubscribeHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LogManager.getLogger(SubscribeHandler.class);

    private static final ByteBuf SUBSCRIBE_BYTEBUF;

    private static final ByteBuf MESSAGE_BYTEBUF;

    private final Bootstrap bootstrap;

    private final Map<String, AsyncMessageListener> subscribeTable;

    static {
        byte[] subscribeBytes = "subscribe".getBytes();
        SUBSCRIBE_BYTEBUF = PooledByteBufAllocator.DEFAULT.buffer(subscribeBytes.length);
        SUBSCRIBE_BYTEBUF.writeBytes(subscribeBytes);

        byte[] messageBytes = "message".getBytes();
        MESSAGE_BYTEBUF = PooledByteBufAllocator.DEFAULT.buffer(messageBytes.length);
        MESSAGE_BYTEBUF.writeBytes(messageBytes);
    }


    public SubscribeHandler(Bootstrap bootstrap, Map<String, AsyncMessageListener> subscribeTable) {
        this.bootstrap = bootstrap;
        this.subscribeTable = subscribeTable;
    }


    /**
     * Subscribe接收到的消息
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ArrayRedisMessage && ((ArrayRedisMessage) msg).children().size() == 3) {
            List<RedisMessage> children = ((ArrayRedisMessage) msg).children();

            ByteBuf header = ((FullBulkStringRedisMessage) children.get(0)).content();
            String topic = new String(ByteBufUtil.getBytes(((FullBulkStringRedisMessage) children.get(1)).content()));

            if (MESSAGE_BYTEBUF.equals(header)) {
                AsyncMessageListener listener = subscribeTable.get(topic);
                if (listener == null) {
                    throw new OMSRuntimeException(String.format("The topic/queue %s isn't attached to this consumer", topic));
                }


                byte[] message = ByteBufUtil.getBytes(((FullBulkStringRedisMessage) children.get(2)).content());
                listener.consume(new Message(topic, null, null, message), new MeshAsyncConsumeContext() {
                    @Override
                    public void commit(Action action) {
                        // nothing...
                    }
                });

            } else if (SUBSCRIBE_BYTEBUF.equals(header)) {
                long value = ((IntegerRedisMessage) children.get(2)).value();
                LOGGER.debug("Receive subscribe reply: [{}]", value);
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
