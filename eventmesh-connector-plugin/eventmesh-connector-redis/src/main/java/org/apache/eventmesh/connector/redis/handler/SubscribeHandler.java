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


package org.apache.eventmesh.connector.redis.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.redis.ArrayRedisMessage;
import io.netty.handler.codec.redis.FullBulkStringRedisMessage;
import io.netty.handler.codec.redis.IntegerRedisMessage;
import io.netty.handler.codec.redis.RedisMessage;
import io.openmessaging.api.Action;
import io.openmessaging.api.AsyncConsumeContext;
import io.openmessaging.api.AsyncMessageListener;
import io.openmessaging.api.Message;
import io.openmessaging.api.exception.OMSRuntimeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * 处理PUBLISH的返回和SUBSCRIBE接收到的消息
 */
@ChannelHandler.Sharable
public class SubscribeHandler extends SimpleChannelInboundHandler<RedisMessage> {

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
     * deal message
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RedisMessage msg) throws Exception {
        if (!(msg instanceof ArrayRedisMessage) || ((ArrayRedisMessage) msg).children().size() != 3) {
            LOGGER.warn("Subscribe handler can't deal response from: [{}]", ctx.channel().remoteAddress());
            return;
        }

        List<RedisMessage> children = ((ArrayRedisMessage) msg).children();
        ByteBuf header = ((FullBulkStringRedisMessage) children.get(0)).content();
        String topic = new String(ByteBufUtil.getBytes(((FullBulkStringRedisMessage) children.get(1)).content()));

        if (MESSAGE_BYTEBUF.equals(header)) {
            AsyncMessageListener listener = subscribeTable.get(topic);
            if (listener == null) {
                throw new OMSRuntimeException(String.format("The topic %s isn't been subscribed by consumer", topic));
            }

            byte[] message = ByteBufUtil.getBytes(((FullBulkStringRedisMessage) children.get(2)).content());
            listener.consume(new Message(topic, null, null, message), new AsyncConsumeContext() {
                @Override
                public void commit(Action action) {
                    // do nothing...
                }
            });

        } else if (SUBSCRIBE_BYTEBUF.equals(header)) {
            long value = ((IntegerRedisMessage) children.get(2)).value();
            LOGGER.debug("Receive subscribe reply: [{}]", value);
        }
    }


    /**
     * 监听subscribe连接断线事件
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }
}
