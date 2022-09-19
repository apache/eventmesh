package org.apache.eventmesh.runtime.boot;

import com.google.common.util.concurrent.RateLimiter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.eventmesh.api.registry.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.tcp.codec.Codec;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.metrics.api.MetricsPluginFactory;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.runtime.admin.controller.ClientManageController;
import org.apache.eventmesh.runtime.configuration.EventMeshAmqpConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.amqp.processor.AmqpConnection;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.codec.AmqpCodeDecoder;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.codec.AmqpCodeEncoder;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcpConnectionHandler;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcpExceptionHandler;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcpMessageDispatcher;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.rebalance.EventMeshRebalanceService;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.rebalance.EventmeshRebalanceImpl;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.retry.EventMeshTcpRetryer;
import org.apache.eventmesh.runtime.metrics.tcp.EventMeshTcpMonitor;
import org.apache.eventmesh.runtime.registry.Registry;
import org.apache.eventmesh.webhook.admin.AdminWebHookConfigOperationManage;
import org.assertj.core.util.Lists;

import java.util.List;
import java.util.Optional;

public class EventMeshAmqpServer extends AbstractRemotingServer{

    private EventMeshServer eventMeshServer;

    private EventMeshAmqpConfiguration eventMeshAmqpConfiguration;

    private Registry registry;

    private EventMeshTcpMonitor eventMeshTcpMonitor;


    public EventMeshAmqpServer(EventMeshServer eventMeshServer,
                               EventMeshAmqpConfiguration eventMeshAmqpConfiguration, Registry registry) {
        super();
        this.eventMeshServer = eventMeshServer;
        this.eventMeshAmqpConfiguration = eventMeshAmqpConfiguration;
        this.registry = registry;
    }


    private void startServer() {
        Runnable r = () -> {
            ServerBootstrap bootstrap = new ServerBootstrap();
            ChannelInitializer channelInitializer =new AmqpChannelInitializer(this);

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
                // TODO
                //int port = eventMeshAmqpConfiguration.eventMeshTcpServerPort;
                ChannelFuture f = bootstrap.bind(port).sync();
                logger.info("EventMeshTCPServer[port={}] started.....", port);
                f.channel().closeFuture().sync();
            } catch (Exception e) {
                logger.error("EventMeshTCPServer RemotingServer Start Err!", e);
                try {
                    shutdown();
                } catch (Exception e1) {
                    logger.error("EventMeshTCPServer RemotingServer shutdown Err!", e);
                }
            }
        };

        Thread t = new Thread(r, "eventMesh-tcp-server");
        t.start();
    }

    public void init() throws Exception {
        logger.info("==================EventMeshAmqpServer Initialing==================");

        logger.info("--------------------------EventMeshAmqpServer Inited");
    }

    @Override
    public void start() throws Exception {
        startServer();
        logger.info("--------------------------EventMeshTCPServer Started");
    }

    @Override
    public void shutdown() throws Exception {
        logger.info("--------------------------EventMeshTCPServer Shutdown");
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
                    new AmqpCodeDecoder(0));
            ch.pipeline().addLast("handler",
                new AmqpConnection( amqpServer));
        }

    }
}
