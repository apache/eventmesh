package org.apache.eventmesh.connector.mcp.source;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.mcp.McpSourceConfig;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.connector.mcp.source.protocol.Protocol;
import org.apache.eventmesh.connector.mcp.source.protocol.ProtocolFactory;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * MCP Source Connector (Netty Implementation)
 *
 * 职责：
 * 1. 连接器生命周期管理（启动、停止）
 * 2. Netty服务器的创建和管理
 * 3. 请求队列的管理
 * 4. 数据轮询和批处理
 * 5. 连接器配置管理
 * 6. 错误处理和监控
 */
@Slf4j
public class McpSourceConnector implements Source, ConnectorCreateService<Source> {

    private McpSourceConfig sourceConfig;
    // Getter methods for testing and monitoring
    @Getter
    private BlockingQueue<Object> queue;
    private int batchSize;
    @Getter
    private Protocol protocol;

    // Netty 服务器组件
    @Getter
    private EventLoopGroup bossGroup;
    @Getter
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @Getter
    private volatile boolean started = false;

    @Getter
    private volatile boolean destroyed = false;

    @Override
    public Class<? extends Config> configClass() {
        return McpSourceConfig.class;
    }

    @Override
    public Source create() {
        return new McpSourceConnector();
    }

    @Override
    public void init(Config config) {
        this.sourceConfig = (McpSourceConfig) config;
        doInit();
    }

    @Override
    public void init(ConnectorContext connectorContext) {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.sourceConfig = (McpSourceConfig) sourceConnectorContext.getSourceConfig();
        doInit();
    }

    /**
     * 初始化连接器
     */
    private void doInit() {
        // 初始化队列
        int maxQueueSize = this.sourceConfig.getConnectorConfig().getMaxStorageSize();
        this.queue = new LinkedBlockingQueue<>(maxQueueSize);

        // 初始化批处理大小
        this.batchSize = this.sourceConfig.getConnectorConfig().getBatchSize();

        // 初始化协议处理器
        String protocolName = this.sourceConfig.getConnectorConfig().getProtocol();
        this.protocol = ProtocolFactory.getInstance(this.sourceConfig.connectorConfig, protocolName);

        // 初始化协议处理器（传入队列引用）
        this.protocol.initialize(this.sourceConfig.connectorConfig, this.queue);

        // 初始化 Netty 事件循环组
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup();

        log.info("McpSourceConnector initialized with protocol: {}, maxQueueSize: {}, batchSize: {}",
                protocolName, maxQueueSize, batchSize);
    }

    @Override
    public void start() {
        try {
            // 创建Netty服务器
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            // HTTP 编解码器
                            pipeline.addLast("httpCodec", new HttpServerCodec());

                            // HTTP 对象聚合器
                            pipeline.addLast("httpAggregator", new HttpObjectAggregator(
                                    sourceConfig.getConnectorConfig().getMaxFormAttributeSize()));

                            // 支持分块传输
                            pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());

                            // 协议处理器
                            pipeline.addLast("protocolHandler", protocol.createHandler(sourceConfig));
                        }
                    });

            // 绑定端口并启动服务器
            int port = sourceConfig.getConnectorConfig().getPort();
            ChannelFuture future = bootstrap.bind(port).sync();
            this.serverChannel = future.channel();
            this.started = true;

            log.info("Netty McpSourceConnector started successfully on port: {}", port);

            // 启动后台任务
            startBackgroundTasks();

        } catch (Exception e) {
            log.error("Failed to start Netty McpSourceConnector", e);
            throw new EventMeshException("Failed to start Netty server", e);
        }
    }

    /**
     * 启动后台任务
     */
    private void startBackgroundTasks() {
        // 启动队列监控任务
        workerGroup.scheduleAtFixedRate(() -> {
            try {
                monitorQueue();
            } catch (Exception e) {
                log.error("Error in queue monitoring", e);
            }
        }, 10, 30, TimeUnit.SECONDS);

        // 启动健康检查任务
        workerGroup.scheduleAtFixedRate(() -> {
            try {
                performHealthCheck();
            } catch (Exception e) {
                log.error("Error in health check", e);
            }
        }, 5, 60, TimeUnit.SECONDS);
    }

    /**
     * 监控队列状态
     */
    private void monitorQueue() {
        int queueSize = queue.size();
        int maxQueueSize = sourceConfig.getConnectorConfig().getMaxStorageSize();

        if (queueSize > maxQueueSize * 0.8) {
            log.warn("Queue usage is high: {}/{} ({}%)", queueSize, maxQueueSize,
                    (queueSize * 100 / maxQueueSize));
        }

        if (queueSize > 0) {
            log.debug("Queue status: {}/{} items", queueSize, maxQueueSize);
        }
    }

    /**
     * 执行健康检查
     */
    private void performHealthCheck() {
        boolean isHealthy = serverChannel != null && serverChannel.isActive() && started && !destroyed;

        if (!isHealthy) {
            log.warn("Health check failed: serverChannel.isActive={}, started={}, destroyed={}",
                    serverChannel != null ? serverChannel.isActive() : "null", started, destroyed);
        } else {
            log.debug("Health check passed");
        }
    }

    @Override
    public void commit(ConnectRecord record) {
        if (sourceConfig.getConnectorConfig().isDataConsistencyEnabled()) {
            log.debug("Committing record: {}", record.getRecordId());

            // 委托给协议处理器进行提交
            try {
                protocol.commit(record);
            } catch (Exception e) {
                log.error("Failed to commit record: {}", record.getRecordId(), e);
            }
        }
    }

    @Override
    public String name() {
        return this.sourceConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void onException(ConnectRecord record) {
        log.error("Exception occurred for record: {}", record.getRecordId());

        // 委托给协议处理器处理异常
        try {
            protocol.onException(record);
        } catch (Exception e) {
            log.error("Failed to handle exception for record: {}", record.getRecordId(), e);
        }
    }

    @Override
    public void stop() {
        log.info("Stopping McpSourceConnector...");

        try {
            // 停止协议处理器
            if (protocol != null) {
                protocol.shutdown();
            }

            // 关闭服务器通道
            if (serverChannel != null) {
                serverChannel.close().sync();
            }

            // 优雅关闭事件循环组
            if (bossGroup != null) {
                bossGroup.shutdownGracefully(2, 10, TimeUnit.SECONDS).sync();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully(2, 10, TimeUnit.SECONDS).sync();
            }

            this.destroyed = true;
            this.started = false;

            log.info("McpSourceConnector stopped successfully");

        } catch (Exception e) {
            log.error("Failed to stop McpSourceConnector gracefully", e);
            throw new EventMeshException("Failed to stop Netty server", e);
        }
    }

    @Override
    public List<ConnectRecord> poll() {
        long startTime = System.currentTimeMillis();
        long maxPollWaitTime = 5000;
        long remainingTime = maxPollWaitTime;

        List<ConnectRecord> connectRecords = new ArrayList<>(batchSize);

        try {
            for (int i = 0; i < batchSize; i++) {
                Object obj = queue.poll(remainingTime, TimeUnit.MILLISECONDS);
                if (obj == null) {
                    break;
                }

                // 委托给协议处理器转换记录
                ConnectRecord connectRecord = protocol.convertToConnectRecord(obj);
                if (connectRecord != null) {
                    connectRecords.add(connectRecord);
                }

                // 计算剩余时间
                long elapsedTime = System.currentTimeMillis() - startTime;
                remainingTime = Math.max(0, maxPollWaitTime - elapsedTime);
            }

            if (!connectRecords.isEmpty()) {
                log.debug("Polled {} records from queue", connectRecords.size());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Polling interrupted", e);
        } catch (Exception e) {
            log.error("Error during polling", e);
        }

        return connectRecords;
    }

    /**
     * 获取队列统计信息
     */
    public Map<String, Object> getQueueStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("queueSize", queue.size());
        stats.put("maxQueueSize", sourceConfig.getConnectorConfig().getMaxStorageSize());
        stats.put("batchSize", batchSize);
        stats.put("queueUtilization", (double) queue.size() / sourceConfig.getConnectorConfig().getMaxStorageSize());
        return stats;
    }

    /**
     * 获取服务器状态
     */
    public Map<String, Object> getServerStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("started", started);
        status.put("destroyed", destroyed);
        status.put("serverActive", serverChannel != null && serverChannel.isActive());
        status.put("port", sourceConfig.getConnectorConfig().getPort());
        status.put("connectorName", name());
        return status;
    }

    /**
     * 强制清空队列
     */
    public int clearQueue() {
        int clearedCount = queue.size();
        queue.clear();
        log.info("Cleared {} items from queue", clearedCount);
        return clearedCount;
    }

}