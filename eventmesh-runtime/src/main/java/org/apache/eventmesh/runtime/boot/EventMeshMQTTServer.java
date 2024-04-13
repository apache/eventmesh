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

package org.apache.eventmesh.runtime.boot;


import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.configuration.EventMeshMQTTConfiguration;
import org.apache.eventmesh.runtime.core.protocol.mqtt.exception.MqttException;
import org.apache.eventmesh.runtime.core.protocol.mqtt.processor.ClientConnectProcessor;
import org.apache.eventmesh.runtime.core.protocol.mqtt.processor.ClientDisConnectProcessor;
import org.apache.eventmesh.runtime.core.protocol.mqtt.processor.HealthCheckProcessor;
import org.apache.eventmesh.runtime.core.protocol.mqtt.processor.MqttProcessor;
import org.apache.eventmesh.runtime.core.protocol.mqtt.processor.PublishProcessor;
import org.apache.eventmesh.runtime.core.protocol.mqtt.processor.SubscrubeProcessor;
import org.apache.eventmesh.runtime.core.protocol.mqtt.processor.UnSubscrubeProcessor;
import org.apache.eventmesh.runtime.meta.MetaStorage;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.mqtt.MqttConnAckVariableHeader;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttIdentifierRejectedException;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageFactory;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttUnacceptableProtocolVersionException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshMQTTServer extends AbstractRemotingServer {

    private final EventMeshMQTTConfiguration eventMeshMQTTConfiguration;

    private final EventMeshServer eventMeshServer;

    private final MetaStorage metaStorage;

    private final Acl acl;


    protected final Map<MqttMessageType, MqttProcessor> processorTable =
        new ConcurrentHashMap<>(64);

    private final AtomicBoolean started = new AtomicBoolean(false);


    public EventMeshMQTTServer(final EventMeshServer eventMeshServer, final EventMeshMQTTConfiguration eventMeshMQTTConfiguration) {
        this.eventMeshServer = eventMeshServer;
        this.eventMeshMQTTConfiguration = eventMeshMQTTConfiguration;
        this.metaStorage = eventMeshServer.getMetaStorage();
        this.acl = eventMeshServer.getAcl();
    }

    @Override
    public void init() throws Exception {
        log.info("==================EventMeshMQTTServer Initialing==================");
        super.init("eventMesh-mqtt");
        registerMQTTProcessor();

    }

    private void registerMQTTProcessor() {
        processorTable.putIfAbsent(MqttMessageType.CONNECT, new ClientConnectProcessor(this, getWorkerGroup()));
        processorTable.putIfAbsent(MqttMessageType.DISCONNECT, new ClientDisConnectProcessor(this, getWorkerGroup()));
        processorTable.putIfAbsent(MqttMessageType.PINGREQ, new HealthCheckProcessor(this, getWorkerGroup()));
        processorTable.putIfAbsent(MqttMessageType.SUBSCRIBE, new SubscrubeProcessor(this, getWorkerGroup()));
        processorTable.putIfAbsent(MqttMessageType.UNSUBSCRIBE, new UnSubscrubeProcessor(this, getWorkerGroup()));
        processorTable.putIfAbsent(MqttMessageType.PUBLISH, new PublishProcessor(this, getWorkerGroup()));
    }


    @Override
    public void start() throws Exception {
        Thread thread = new Thread(() -> {
            final ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(this.getBossGroup(), this.getIoGroup())
                .channel(useEpoll() ? EpollServerSocketChannel.class : NioServerSocketChannel.class);
            bootstrap.option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.SO_RCVBUF, 10485760);

            bootstrap.childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
            bootstrap.childHandler(new MQTTServerInitializer());

            try {
                int port = eventMeshMQTTConfiguration.getEventMeshTcpServerPort();
                ChannelFuture f = bootstrap.bind(port).sync();
                log.info("EventMeshMQTTServer[port={}] started.....", port);
                f.channel().closeFuture().sync();
            } catch (Exception e) {
                log.error("EventMeshMQTTServer RemotingServer Start Err!", e);
                try {
                    shutdown();
                } catch (Exception ex) {
                    log.error("EventMeshMQTTServer RemotingServer shutdown Err!", ex);
                }
                System.exit(-1);
            }
        }, "eventMesh-mqtt-server");
        thread.start();

        started.compareAndSet(false, true);

    }

    @Override
    public CommonConfiguration getConfiguration() {
        return eventMeshMQTTConfiguration;
    }

    private class MQTTServerInitializer extends ChannelInitializer<SocketChannel> {


        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline channelPipeline = ch.pipeline();
            channelPipeline.addLast(getWorkerGroup(), MqttEncoder.INSTANCE);
            channelPipeline.addLast(getWorkerGroup(), new MqttDecoder());
            channelPipeline.addLast(getWorkerGroup(), new EventMeshMqttChannelInboundHandler());
        }
    }

    @Sharable
    private class EventMeshMqttChannelInboundHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof MqttMessage) {
                MqttMessage mqttMessage = (MqttMessage) msg;
                if (mqttMessage.decoderResult().isFailure()) {
                    Throwable cause = mqttMessage.decoderResult().cause();
                    if (cause instanceof MqttUnacceptableProtocolVersionException) {
                        ctx.writeAndFlush(MqttMessageFactory.newMessage(
                            new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION, false),
                            null));
                    } else if (cause instanceof MqttIdentifierRejectedException) {
                        ctx.writeAndFlush(MqttMessageFactory.newMessage(
                            new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, false),
                            null));
                    }
                    ctx.close();
                    return;
                }
                MqttFixedHeader mqttFixedHeader = mqttMessage.fixedHeader();
                MqttMessageType mqttMessageType = mqttFixedHeader.messageType();
                MqttProcessor mqttProcessor = processorTable.get(mqttMessageType);
                if (!Objects.isNull(mqttProcessor)) {
                    Executor executor = mqttProcessor.executor();
                    if (Objects.isNull(executor)) {
                        mqttProcessor.process(ctx, mqttMessage);
                    } else {
                        executor.execute(() -> {
                            try {
                                mqttProcessor.process(ctx, mqttMessage);
                            } catch (MqttException e) {
                                log.error("[mqtt Processor error]", e);
                                ctx.close();
                            }
                        });
                    }
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (cause instanceof IOException) {
                ctx.close();
            } else {
                super.exceptionCaught(ctx, cause);
            }
        }
    }

    public Acl getAcl() {
        return acl;
    }
}