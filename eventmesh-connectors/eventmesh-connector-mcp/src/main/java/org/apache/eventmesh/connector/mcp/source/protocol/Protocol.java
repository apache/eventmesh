package org.apache.eventmesh.connector.mcp.source.protocol;

import io.netty.channel.ChannelHandler;
import io.vertx.ext.web.Route;
import org.apache.eventmesh.common.config.connector.mcp.McpSourceConfig;
import org.apache.eventmesh.common.config.connector.mcp.SourceConnectorConfig;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.concurrent.BlockingQueue;

/**
 * Protocol interface for MCP connectors
 *
 * 定义了协议处理器的基本接口，支持不同的传输层实现（Netty/Vert.x）
 */
public interface Protocol {

    /**
     * 初始化协议处理器
     *
     * @param sourceConnectorConfig 源连接器配置
     */
    void initialize(SourceConnectorConfig sourceConnectorConfig);

    /**
     * 初始化协议处理器（带队列）
     *
     * @param sourceConnectorConfig 源连接器配置
     * @param queue 请求队列
     */
    default void initialize(SourceConnectorConfig sourceConnectorConfig, BlockingQueue<Object> queue) {
        initialize(sourceConnectorConfig);
    }

    /**
     * 创建 Netty 请求处理器
     *
     * @param sourceConfig MCP源配置
     * @return Netty ChannelHandler
     */
    default ChannelHandler createHandler(McpSourceConfig sourceConfig) {
        throw new UnsupportedOperationException("Netty handler creation not supported by this protocol implementation");
    }

    /**
     * 设置 Vert.x 路由处理器
     *
     * @deprecated 推荐使用 createHandler(McpSourceConfig) 来创建 Netty 处理器
     * @param route Vert.x 路由
     * @param queue 请求队列
     */
    @Deprecated
    default void setHandler(Route route, BlockingQueue<Object> queue) {
        throw new UnsupportedOperationException("Vert.x route handling not supported by this protocol implementation");
    }

    /**
     * 将消息转换为 ConnectRecord
     *
     * @param message 原始消息
     * @return 转换后的 ConnectRecord
     */
    ConnectRecord convertToConnectRecord(Object message);

    /**
     * 提交记录处理结果
     *
     * @param record 要提交的记录
     */
    default void commit(ConnectRecord record) {
        // 默认实现为空，子类可以重写
    }

    /**
     * 处理记录异常
     *
     * @param record 发生异常的记录
     */
    default void onException(ConnectRecord record) {
        // 默认实现为空，子类可以重写
    }

    /**
     * 关闭协议处理器
     */
    default void shutdown() {
        // 默认实现为空，子类可以重写
    }

    /**
     * 获取协议名称
     *
     * @return 协议名称
     */
    default String getProtocolName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 获取协议版本
     *
     * @return 协议版本
     */
    default String getProtocolVersion() {
        return "1.0";
    }

    /**
     * 检查协议是否支持流式传输
     *
     * @return 是否支持流式传输
     */
    default boolean supportsStreaming() {
        return false;
    }

    /**
     * 检查协议是否支持压缩
     *
     * @return 是否支持压缩
     */
    default boolean supportsCompression() {
        return false;
    }

    /**
     * 获取协议配置信息
     *
     * @return 配置信息映射
     */
    default java.util.Map<String, Object> getProtocolConfig() {
        return java.util.Collections.emptyMap();
    }

    /**
     * 验证协议配置
     *
     * @param config 要验证的配置
     * @return 验证结果
     */
    default boolean validateConfig(SourceConnectorConfig config) {
        return true;
    }
}