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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.apache.eventmesh.runtime.configuration.EventMeshAmqpConfiguration;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.MetaStore;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.codec.AmqpCodeDecoder;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.codec.AmqpCodeEncoder;
import org.apache.eventmesh.runtime.core.protocol.amqp.service.ExchangeService;
import org.apache.eventmesh.runtime.core.protocol.amqp.service.ExchangeServiceImpl;
import org.apache.eventmesh.runtime.core.protocol.amqp.service.QueueService;
import org.apache.eventmesh.runtime.core.protocol.amqp.service.QueueServiceImpl;
import org.apache.eventmesh.runtime.registry.Registry;

public class EventMeshAmqpServer extends AbstractRemotingServer {

    private EventMeshServer eventMeshServer;

    private EventMeshAmqpConfiguration eventMeshAmqpConfiguration;

    private Registry registry;

    private ExchangeService exchangeService;

    private QueueService queueService;

    private MetaStore metaStore;

    public EventMeshAmqpServer(EventMeshServer eventMeshServer,
                               EventMeshAmqpConfiguration eventMeshAmqpConfiguration, Registry registry) {
        super();
        this.eventMeshServer = eventMeshServer;
        this.eventMeshAmqpConfiguration = eventMeshAmqpConfiguration;
        this.registry = registry;
        this.metaStore=new MetaStore(this);
        this.exchangeService=new ExchangeServiceImpl(this,metaStore);
        this.queueService=new QueueServiceImpl(this,metaStore);
    }


    private void startServer() {
        Runnable r = () -> {
            ServerBootstrap bootstrap = new ServerBootstrap();
            ChannelInitializer channelInitializer = new AmqpChannelInitializer(this);

            bootstrap.group(bossGroup, ioGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .childOption(ChannelOption.SO_KEEPALIVE, false)
                    .childOption(ChannelOption.SO_LINGER, 0)
                    .childOption(ChannelOption.SO_TIMEOUT, 600000)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_SNDBUF, 65535 * 4)
                    .childOption(ChannelOption.SO_RCVBUF, 65535 * 4)
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(2048, 4096, 65536))
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childHandler(channelInitializer);

            try {
                int port = eventMeshAmqpConfiguration.eventMeshAmqpServerPort;
                ChannelFuture f = bootstrap.bind(port).sync();
                logger.info("EventMeshAmqpServer[port={}] started.....", port);
                f.channel().closeFuture().sync();
            } catch (Exception e) {
                logger.error("EventMeshAmqpServer RemotingServer Start Err!", e);
                try {
                    shutdown();
                } catch (Exception e1) {
                    logger.error("EventMeshAmqpServer RemotingServer shutdown Err!", e);
                }
            }
        };

        Thread t = new Thread(r, "eventMesh-amqp-server");
        t.start();
    }

    public void init() throws Exception {
        logger.info("==================EventMeshAmqpServer Initialing==================");

        logger.info("--------------------------EventMeshAmqpServer Inited");
    }

    @Override
    public void start() throws Exception {
        startServer();
        logger.info("--------------------------EventMeshAmqpServer Started");
    }

    @Override
    public void shutdown() throws Exception {
        logger.info("--------------------------EventMeshAmqpServer Shutdown");
    }

    public EventMeshServer getEventMeshServer() {
        return eventMeshServer;
    }

    public EventMeshAmqpConfiguration getEventMeshAmqpConfiguration() {
        return eventMeshAmqpConfiguration;
    }

    public ExchangeService getExchangeService() {
        return exchangeService;
    }

    public QueueService getQueueService() {
        return queueService;
    }

    /**
     * A channel initializer that initialize channels for amqp protocol.
     */
    class AmqpChannelInitializer extends ChannelInitializer<SocketChannel> {

        private final EventMeshAmqpServer amqpServer;

        public AmqpChannelInitializer(EventMeshAmqpServer amqpServer) {
            super();
            this.amqpServer = amqpServer;
        }

        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            //        ch.pipeline().addLast(new LengthFieldPrepender(4));
            //0      1         3         7                 size+7 size+8
            //+------+---------+---------+ +-------------+ +-----------+
            //| type | channel |    size | | payload     | | frame-end |
            //+------+---------+---------+ +-------------+ +-----------+
            // octet   short      long       'size' octets   octet

            ch.pipeline().addLast("frameEncoder",

                    new AmqpCodeEncoder());
            ch.pipeline().addLast("frameDecoder",
                    // TODO
                    new AmqpCodeDecoder());
            // TODO: 2022/9/27 add AmqpConnection
//            ch.pipeline().addLast("handler",
//                    new AmqpConnection(amqpServer));
        }

    }
}