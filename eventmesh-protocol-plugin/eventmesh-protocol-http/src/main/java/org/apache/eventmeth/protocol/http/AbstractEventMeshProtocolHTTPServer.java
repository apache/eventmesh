package org.apache.eventmeth.protocol.http;

import com.google.common.base.Preconditions;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.multipart.DiskAttribute;
import io.netty.handler.ssl.SslHandler;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.ThreadUtil;
import org.apache.eventmesh.protocol.api.EventMeshProtocolServer;
import org.apache.eventmesh.protocol.api.exception.EventMeshProtocolException;
import org.apache.eventmeth.protocol.http.config.EventMeshHTTPConfiguration;
import org.apache.eventmeth.protocol.http.factory.SSLContextFactory;
import org.apache.eventmeth.protocol.http.handler.HttpChannelHandler;
import org.apache.eventmeth.protocol.http.handler.HttpConnectionHandler;
import org.apache.eventmeth.protocol.http.metrics.HTTPMetricsServer;
import org.apache.eventmeth.protocol.http.processor.AdminMetricsProcessor;
import org.apache.eventmeth.protocol.http.processor.BatchSendMessageProcessor;
import org.apache.eventmeth.protocol.http.processor.BatchSendMessageV2Processor;
import org.apache.eventmeth.protocol.http.processor.HeartBeatProcessor;
import org.apache.eventmeth.protocol.http.processor.HttpRequestProcessor;
import org.apache.eventmeth.protocol.http.processor.ReplyMessageProcessor;
import org.apache.eventmeth.protocol.http.processor.SendAsyncMessageProcessor;
import org.apache.eventmeth.protocol.http.processor.SendSyncMessageProcessor;
import org.apache.eventmeth.protocol.http.processor.SubscribeProcessor;
import org.apache.eventmeth.protocol.http.processor.UnSubscribeProcessor;
import org.apache.eventmeth.protocol.http.retry.HttpRetryer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractEventMeshProtocolHTTPServer implements EventMeshProtocolServer {

    public final Logger logger = LoggerFactory.getLogger(AbstractEventMeshProtocolHTTPServer.class);

    public EventLoopGroup bossGroup;

    public EventLoopGroup ioGroup;

    public EventLoopGroup workerGroup;

    protected ThreadPoolExecutor batchMsgExecutor;

    protected ThreadPoolExecutor sendMsgExecutor;

    protected ThreadPoolExecutor replyMsgExecutor;

    protected ThreadPoolExecutor pushMsgExecutor;

    protected ThreadPoolExecutor clientManageExecutor;

    protected ThreadPoolExecutor adminExecutor;

    protected HttpRetryer httpRetryer;

    private final HttpChannelHandler httpChannelHandler;

    private HTTPMetricsServer metrics;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private final int port;

    private final boolean useTLS;

    static {
        DiskAttribute.deleteOnExitTemporaryFile = false;
    }

    public AbstractEventMeshProtocolHTTPServer(int port, boolean useTLS) {
        this.port = port;
        this.useTLS = useTLS;
        this.httpChannelHandler = new HttpChannelHandler(started, metrics);
    }

    @Override
    public void init() throws EventMeshProtocolException {
        try {
            metrics = new HTTPMetricsServer((EventMeshProtocolHTTPServer) this);
            metrics.init();

            httpRetryer = new HttpRetryer();
            httpRetryer.init();

            initThreadPool();
            bossGroup = new NioEventLoopGroup(
                    1,
                    ThreadUtil.createThreadFactory(true, "eventMesh-http-boss-")
            );
            ioGroup = new NioEventLoopGroup(
                    Runtime.getRuntime().availableProcessors(),
                    ThreadUtil.createThreadFactory(false, "eventMesh-http-io-")
            );
            workerGroup = new NioEventLoopGroup(
                    Runtime.getRuntime().availableProcessors(),
                    ThreadUtil.createThreadFactory(false, "eventMesh-http-worker-")
            );
            registerHTTPRequestProcessor();
        } catch (Exception ex) {
            throw new EventMeshProtocolException(ex);
        }
    }

    @Override
    public void start() throws EventMeshProtocolException {
        if (!started.compareAndSet(false, true)) {
            logger.warn("EventMeshHTTPServer is already started");
            return;
        }
        try {
            metrics.start();
            httpRetryer.start();

            Runnable r = () -> {
                ServerBootstrap b = new ServerBootstrap();
                b.group(this.bossGroup, this.workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childOption(ChannelOption.SO_KEEPALIVE, Boolean.TRUE)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel channel) {
                                initNettyChannel(channel);
                            }
                        });
                try {
                    logger.info("EventMeshHTTPServer[port={}] started......", this.port);
                    ChannelFuture future = b.bind(this.port).sync();
                    future.channel().closeFuture().sync();
                } catch (Exception e) {
                    logger.error("EventMeshHTTPServer start Err!", e);
                    try {
                        shutdown();
                    } catch (Exception e1) {
                        logger.error("HTTPServer shutdown Err!", e);
                    }
                }
            };
            Thread t = new Thread(r, "eventMesh-http-server");
            t.start();
        } catch (Exception ex) {
            started.set(false);
            throw new EventMeshProtocolException(ex);
        }
    }

    @Override
    public void shutdown() throws EventMeshProtocolException {
        try {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
                logger.info("shutdown bossGroup");
            }

            ThreadUtil.randomSleep(30);

            if (ioGroup != null) {
                ioGroup.shutdownGracefully();
                logger.info("shutdown ioGroup");
            }

            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
                logger.info("shutdown workerGroup");
            }
            httpRetryer.shutdown();

            metrics.shutdown();

            batchMsgExecutor.shutdown();
            adminExecutor.shutdown();
            clientManageExecutor.shutdown();
            sendMsgExecutor.shutdown();
            pushMsgExecutor.shutdown();
            replyMsgExecutor.shutdown();
        } catch (Exception ex) {
            throw new EventMeshProtocolException(ex);
        }
    }

    public void registerProcessor(Integer requestCode, HttpRequestProcessor httpRequestProcessor, ThreadPoolExecutor threadPoolExecutor) {
        Preconditions.checkState(ObjectUtils.allNotNull(requestCode), "requestCode can't be null");
        Preconditions.checkState(ObjectUtils.allNotNull(httpRequestProcessor), "processor can't be null");
        Preconditions.checkState(ObjectUtils.allNotNull(threadPoolExecutor), "executor can't be null");
        httpChannelHandler.registerProcessor(String.valueOf(requestCode), httpRequestProcessor, threadPoolExecutor);
    }

    public HttpRetryer getHttpRetryer() {
        return httpRetryer;
    }

    public ThreadPoolExecutor getBatchMsgExecutor() {
        return batchMsgExecutor;
    }

    public ThreadPoolExecutor getSendMsgExecutor() {
        return sendMsgExecutor;
    }

    public ThreadPoolExecutor getReplyMsgExecutor() {
        return replyMsgExecutor;
    }

    public ThreadPoolExecutor getPushMsgExecutor() {
        return pushMsgExecutor;
    }

    public ThreadPoolExecutor getClientManageExecutor() {
        return clientManageExecutor;
    }

    public ThreadPoolExecutor getAdminExecutor() {
        return adminExecutor;
    }

    public HTTPMetricsServer getMetricsServer() {
        return metrics;
    }

    private void initNettyChannel(SocketChannel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        if (useTLS) {
            SSLContext sslContext = SSLContextFactory.getSslContext();
            SSLEngine sslEngine = sslContext.createSSLEngine();
            sslEngine.setUseClientMode(false);
            pipeline.addFirst("ssl", new SslHandler(sslEngine));
        }
        pipeline.addLast(
                new HttpRequestDecoder(),
                new HttpResponseEncoder(),
                new HttpConnectionHandler(),
                new HttpObjectAggregator(Integer.MAX_VALUE),
                httpChannelHandler
        );
    }

    private void initThreadPool() {

        BlockingQueue<Runnable> batchMsgThreadPoolQueue = new LinkedBlockingQueue<>(EventMeshHTTPConfiguration.eventMeshServerBatchBlockQSize);
        batchMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(EventMeshHTTPConfiguration.eventMeshServerBatchMsgThreadNum,
                EventMeshHTTPConfiguration.eventMeshServerBatchMsgThreadNum, batchMsgThreadPoolQueue, "eventMesh-batchMsg-", true);

        BlockingQueue<Runnable> sendMsgThreadPoolQueue = new LinkedBlockingQueue<>(EventMeshHTTPConfiguration.eventMeshServerSendMsgBlockQSize);
        sendMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(EventMeshHTTPConfiguration.eventMeshServerSendMsgThreadNum,
                EventMeshHTTPConfiguration.eventMeshServerSendMsgThreadNum, sendMsgThreadPoolQueue, "eventMesh-sendMsg-", true);

        BlockingQueue<Runnable> pushMsgThreadPoolQueue = new LinkedBlockingQueue<>(EventMeshHTTPConfiguration.eventMeshServerPushMsgBlockQSize);
        pushMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(EventMeshHTTPConfiguration.eventMeshServerPushMsgThreadNum,
                EventMeshHTTPConfiguration.eventMeshServerPushMsgThreadNum, pushMsgThreadPoolQueue, "eventMesh-pushMsg-", true);

        BlockingQueue<Runnable> clientManageThreadPoolQueue = new LinkedBlockingQueue<>(EventMeshHTTPConfiguration.eventMeshServerClientManageBlockQSize);
        clientManageExecutor = ThreadPoolFactory.createThreadPoolExecutor(EventMeshHTTPConfiguration.eventMeshServerClientManageThreadNum,
                EventMeshHTTPConfiguration.eventMeshServerClientManageThreadNum, clientManageThreadPoolQueue, "eventMesh-clientManage-", true);

        BlockingQueue<Runnable> adminThreadPoolQueue = new LinkedBlockingQueue<>(50);
        adminExecutor = ThreadPoolFactory.createThreadPoolExecutor(EventMeshHTTPConfiguration.eventMeshServerAdminThreadNum,
                EventMeshHTTPConfiguration.eventMeshServerAdminThreadNum, adminThreadPoolQueue, "eventMesh-admin-", true);

        BlockingQueue<Runnable> replyMessageThreadPoolQueue = new LinkedBlockingQueue<Runnable>(100);
        replyMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(EventMeshHTTPConfiguration.eventMeshServerReplyMsgThreadNum,
                EventMeshHTTPConfiguration.eventMeshServerReplyMsgThreadNum, replyMessageThreadPoolQueue, "eventMesh-replyMsg-", true);
    }

    private void registerHTTPRequestProcessor() {
        registerProcessor(RequestCode.MSG_BATCH_SEND.getRequestCode(), new BatchSendMessageProcessor((EventMeshProtocolHTTPServer) this), batchMsgExecutor);
        registerProcessor(RequestCode.MSG_BATCH_SEND_V2.getRequestCode(), new BatchSendMessageV2Processor((EventMeshProtocolHTTPServer) this), batchMsgExecutor);
        registerProcessor(RequestCode.MSG_SEND_SYNC.getRequestCode(), new SendSyncMessageProcessor((EventMeshProtocolHTTPServer) this), sendMsgExecutor);
        registerProcessor(RequestCode.MSG_SEND_ASYNC.getRequestCode(), new SendAsyncMessageProcessor((EventMeshProtocolHTTPServer) this), sendMsgExecutor);
        registerProcessor(RequestCode.ADMIN_METRICS.getRequestCode(), new AdminMetricsProcessor((EventMeshProtocolHTTPServer) this), adminExecutor);
        registerProcessor(RequestCode.HEARTBEAT.getRequestCode(), new HeartBeatProcessor((EventMeshProtocolHTTPServer) this), clientManageExecutor);
        registerProcessor(RequestCode.SUBSCRIBE.getRequestCode(), new SubscribeProcessor((EventMeshProtocolHTTPServer) this), clientManageExecutor);
        registerProcessor(RequestCode.UNSUBSCRIBE.getRequestCode(), new UnSubscribeProcessor((EventMeshProtocolHTTPServer) this), clientManageExecutor);
        registerProcessor(RequestCode.REPLY_MESSAGE.getRequestCode(), new ReplyMessageProcessor((EventMeshProtocolHTTPServer) this), replyMsgExecutor);
    }


}
