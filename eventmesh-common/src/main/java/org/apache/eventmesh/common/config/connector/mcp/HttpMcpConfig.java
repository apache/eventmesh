package org.apache.eventmesh.common.config.connector.mcp;

import lombok.Data;

/**
 * MCP Server 接入配置
 */
@Data
public class HttpMcpConfig {

    /**
     * 是否启用 MCP 服务
     */
    private boolean activate = false;

    /**
     * MCP Server 监听端口
     */
    private int port;

    // Path to display/export callback data
    private String exportPath = "/export";

    /**
     * Session 空闲超时时间（单位：毫秒）
     */
    private int sessionIdleTimeoutMs = 300_000;

    /**
     * 最大 Session 数（用于流式连接控制）
     */
    private int maxConcurrentSessions = 500;

    /**
     * 是否启用流式响应（Chunked Response）
     */
    private boolean enableStreaming = true;

    /**
     * 每个会话最大缓存消息数
     */
    private int maxBufferedMessagesPerSession = 100;

    /**
     * 启动时是否打印 MCP 请求日志
     */
    private boolean enableDebugLog = false;
}
