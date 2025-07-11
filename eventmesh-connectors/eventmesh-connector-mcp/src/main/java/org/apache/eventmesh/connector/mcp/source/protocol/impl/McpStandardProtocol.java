package org.apache.eventmesh.connector.mcp.source.protocol.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler;
import io.vertx.ext.web.Route;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.config.connector.mcp.McpSourceConfig;
import org.apache.eventmesh.common.config.connector.mcp.SourceConnectorConfig;
import org.apache.eventmesh.connector.mcp.source.data.McpRequest;
import org.apache.eventmesh.connector.mcp.source.handler.NettyMcpRequestHandler;
import org.apache.eventmesh.connector.mcp.source.protocol.Protocol;
import org.apache.eventmesh.connector.mcp.util.AIToolManager;
import org.apache.eventmesh.connector.mcp.session.MCPSession;
import org.apache.eventmesh.connector.mcp.session.SessionManager;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.eventmesh.connector.mcp.util.CloudEventsExtensionUtil;

import java.util.*;
import java.util.concurrent.BlockingQueue;

/**
 * MCP Standard Protocol Implementation (Netty-based)
 *
 * 职责：
 * 1. MCP协议的具体实现和规范遵循
 * 2. 请求/响应格式的解析和构造
 * 3. 数据转换和验证
 * 4. 协议级别的错误处理
 */
@Slf4j
public class McpStandardProtocol implements Protocol {

    public static final String PROTOCOL_NAME = "Mcp";
    public static final String PROTOCOL_VERSION = "2025-03-26";

    private SourceConnectorConfig sourceConnectorConfig;
    private BlockingQueue<Object> queue;
    // Getter methods
    @Getter
    private SessionManager sessionManager;
    @Getter
    private AIToolManager toolManager;
    @Getter
    private ObjectMapper objectMapper;

    /**
     * 初始化协议处理器
     */
    @Override
    public void initialize(SourceConnectorConfig sourceConnectorConfig) {
        this.sourceConnectorConfig = sourceConnectorConfig;
        this.sessionManager = new SessionManager();
        this.toolManager = new AIToolManager();
        this.objectMapper = new ObjectMapper();

        log.info("McpStandardProtocol initialized - version: {}", PROTOCOL_VERSION);
    }

    /**
     * 初始化协议处理器（带队列）
     */
    @Override
    public void initialize(SourceConnectorConfig sourceConnectorConfig, BlockingQueue<Object> queue) {
        this.queue = queue;
        initialize(sourceConnectorConfig);
    }

    /**
     * 创建 Netty 请求处理器
     */
    @Override
    public ChannelHandler createHandler(McpSourceConfig sourceConfig) {
        if (sessionManager == null || toolManager == null || objectMapper == null) {
            throw new IllegalStateException("Protocol not properly initialized");
        }
        return new NettyMcpRequestHandler(sourceConfig, queue, sessionManager, objectMapper, toolManager);
    }

    /**
     * 设置 Vert.x 路由处理器（已弃用，保留兼容性）
     */
    @Override
    @Deprecated
    public void setHandler(Route route, BlockingQueue<Object> queue) {
        log.warn("setHandler(Route, BlockingQueue) is deprecated in Netty implementation. Use createHandler(McpSourceConfig) instead.");
        throw new UnsupportedOperationException("Vert.x Route handling is not supported in Netty implementation");
    }

    /**
     * 转换消息为 ConnectRecord
     */
    @Override
    public ConnectRecord convertToConnectRecord(Object message) {
        if (!(message instanceof McpRequest)) {
            log.warn("Received non-McpRequest message: {}", message != null ? message.getClass().getSimpleName() : "null");
            return null;
        }

        McpRequest request = (McpRequest) message;

        try {
            // 确保会话存在
            ensureSessionExists(request);

            // 验证请求基本格式
            validateRequestFormat(request);

            // 提取数据
            Object data = extractDataFromRequest(request);

            // 创建 ConnectRecord
            ConnectRecord connectRecord = new ConnectRecord(
                    null, // partition
                    null, // offset
                    System.currentTimeMillis(), // timestamp
                    data  // data
            );

            // 添加协议相关扩展信息
            enrichConnectRecord(connectRecord, request);

            log.debug("Converted McpRequest to ConnectRecord: sessionId={}, recordId={}",
                    request.getSessionId(), connectRecord.getRecordId());

            return connectRecord;

        } catch (Exception e) {
            log.error("Failed to convert McpRequest to ConnectRecord for session: {}",
                    request.getSessionId(), e);
            return null;
        }
    }

    /**
     * 确保会话存在
     */
    private void ensureSessionExists(McpRequest request) {
        String sessionId = request.getSessionId();
        MCPSession session = request.getSession();

        // 如果请求中已经有会话对象，直接使用
        if (session != null) {
            sessionManager.putSession(session);
            return;
        }

        // 如果有会话ID，尝试获取或创建会话
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            session = sessionManager.getSession(sessionId);
            if (session == null) {
                session = sessionManager.createSessionWithId(sessionId);
                log.info("Created new session with provided ID: {}", sessionId);
            }
            request.setSession(session);
        } else {
            // 如果没有会话ID，创建新会话
            session = sessionManager.createSession();
            request.setSessionId(session.getSessionId());
            request.setSession(session);
            log.info("Created new session: {}", session.getSessionId());
        }
    }

    /**
     * 验证请求格式
     */
    private void validateRequestFormat(McpRequest request) {
        if (request.getPayloadMap() == null) {
            throw new IllegalArgumentException("Request payload cannot be null");
        }

        // 验证协议版本（可选）
        String protocolVersion = request.getProtocolVersion();
        if (protocolVersion != null && !PROTOCOL_VERSION.equals(protocolVersion)) {
            log.warn("Protocol version mismatch: expected {}, got {}", PROTOCOL_VERSION, protocolVersion);
        }

        // 验证基本字段
        Map<String, Object> payload = request.getPayloadMap();
        if (!payload.containsKey("inputs") && !payload.containsKey("tools")) {
            log.warn("Request payload missing expected fields (inputs or tools)");
        }
    }

    /**
     * 从请求中提取数据
     */
    private Object extractDataFromRequest(McpRequest request) {
        Map<String, Object> payload = request.getPayloadMap();

        // 优先使用 inputs 作为主要数据
        Object inputs = payload.get("inputs");
        if (inputs != null) {
            return inputs;
        }

        // 如果没有 inputs，检查是否有 tools 字段
        Object tools = payload.get("tools");
        if (tools != null) {
            return tools;
        }

        // 如果都没有，使用整个 payload
        return payload;
    }

    /**
     * 丰富 ConnectRecord 的扩展信息（使用CloudEvents兼容的方式）
     */
    private void enrichConnectRecord(ConnectRecord connectRecord, McpRequest request) {
        // 添加MCP标准扩展属性
        CloudEventsExtensionUtil.addMcpStandardExtensions(
                connectRecord,
                request.getSessionId(),
                PROTOCOL_VERSION,
                request.isStreaming()
        );

        // 添加基本信息
        CloudEventsExtensionUtil.addExtensionSafely(connectRecord, "source", "mcpconnector");
        CloudEventsExtensionUtil.addExtensionSafely(connectRecord, "requesturi", request.getAbsoluteURI());

        // 添加会话信息
        MCPSession session = request.getSession();
        if (session != null) {
            CloudEventsExtensionUtil.addExtensionSafely(connectRecord, "sessioncreated", String.valueOf(session.getLastActivity()));
            Object context = session.getContext("context");
            if (context != null) {
                CloudEventsExtensionUtil.addExtensionSafely(connectRecord, "sessioncontext", context.toString());
            }
        }

        // 添加请求头信息
        Map<String, String> headers = request.getHeaderMap();
        if (headers != null) {
            CloudEventsExtensionUtil.addHeaderExtensions(connectRecord, headers);
        }

        // 添加Payload元数据
        Map<String, Object> payload = request.getPayloadMap();
        if (payload != null) {
            // 添加context信息
            Object context = payload.get("context");
            if (context != null) {
                CloudEventsExtensionUtil.addExtensionSafely(connectRecord, "payloadcontext", context.toString());
            }

            // 添加metadata信息
            Object metadataObj = payload.get("metadata");
            if (metadataObj instanceof Map) {
                Map<String, Object> metadata = (Map<String, Object>) metadataObj;
                CloudEventsExtensionUtil.addMetadataExtensions(connectRecord, metadata);
            }

            // 添加流式标识
            Object stream = payload.get("stream");
            if (stream != null) {
                CloudEventsExtensionUtil.addExtensionSafely(connectRecord, "streamrequest", stream.toString());
            }
        }

        // 生成唯一记录ID
        String recordUniqueId = generateRecordUniqueId(request);
        CloudEventsExtensionUtil.addExtensionSafely(connectRecord, "recordid", recordUniqueId);

        // 添加处理提示
        String processingHint = getProcessingHint(request);
        CloudEventsExtensionUtil.addExtensionSafely(connectRecord, "processhint", processingHint);

        log.debug("Enriched ConnectRecord with CloudEvents-compatible extensions for session: {}", request.getSessionId());
    }

    /**
     * 生成唯一记录ID
     */
    private String generateRecordUniqueId(McpRequest request) {
        return String.format("%s_%s_%d",
                PROTOCOL_NAME,
                request.getSessionId(),
                System.currentTimeMillis());
    }

    /**
     * 获取处理提示
     */
    private String getProcessingHint(McpRequest request) {
        if (request.isStreaming()) {
            return "streaming";
        }

        Map<String, Object> payload = request.getPayloadMap();
        if (payload != null && payload.containsKey("metadata")) {
            Object metadataObj = payload.get("metadata");
            if (metadataObj instanceof Map) {
                Map<String, Object> metadata = (Map<String, Object>) metadataObj;
                Object priority = metadata.get("priority");
                if (priority != null) {
                    return "priority:" + priority;
                }
            }
        }

        return "normal";
    }

    /**
     * 提交记录
     */
    @Override
    public void commit(ConnectRecord record) {
        try {
            String sessionId = (String) record.getExtensionObj("sessionId");
            if (sessionId != null && sessionManager != null) {
                MCPSession session = sessionManager.getSession(sessionId);
                if (session != null) {
                    session.updateLastActivity();
                }
            }

            log.debug("Committed record: {}", record.getRecordId());

        } catch (Exception e) {
            log.error("Failed to commit record: {}", record.getRecordId(), e);
        }
    }

    /**
     * 处理异常
     */
    @Override
    public void onException(ConnectRecord record) {
        try {
            String sessionId = (String) record.getExtensionObj("sessionId");
            log.error("Exception occurred for record: {}, sessionId: {}", record.getRecordId(), sessionId);

            // 记录错误统计或执行恢复逻辑

        } catch (Exception e) {
            log.error("Failed to handle exception for record: {}", record.getRecordId(), e);
        }
    }

    /**
     * 关闭协议处理器
     */
    @Override
    public void shutdown() {
        try {
            log.info("Shutting down McpStandardProtocol...");

            // 关闭会话管理器
            if (sessionManager != null) {
                sessionManager.shutdown();
            }

            log.info("McpStandardProtocol shutdown completed");

        } catch (Exception e) {
            log.error("Error during protocol shutdown", e);
        }
    }

    /**
     * 获取协议统计信息
     */
    public Map<String, Object> getProtocolStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("protocolName", PROTOCOL_NAME);
        stats.put("protocolVersion", PROTOCOL_VERSION);

        // 添加会话统计
        if (sessionManager != null) {
            // 可以添加会话数量等统计信息
            stats.put("sessionManagerInitialized", true);
        }

        return stats;
    }

    /**
     * 创建成功响应
     */
    public String createSuccessResponse(Object data, String sessionId) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("session_id", sessionId);
            response.put("outputs", data);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("timestamp", System.currentTimeMillis());
            metadata.put("version", PROTOCOL_VERSION);
            metadata.put("status", "success");
            response.put("metadata", metadata);

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            log.error("Failed to create success response", e);
            return createErrorResponse("Failed to create response", sessionId);
        }
    }

    /**
     * 创建错误响应
     */
    public String createErrorResponse(String errorMessage, String sessionId) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("session_id", sessionId);
            response.put("error", Map.of(
                    "message", errorMessage,
                    "code", "PROCESSING_ERROR"
            ));

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("timestamp", System.currentTimeMillis());
            metadata.put("version", PROTOCOL_VERSION);
            metadata.put("status", "error");
            response.put("metadata", metadata);

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            log.error("Failed to create error response", e);
            return "{\"error\":\"Internal server error\"}";
        }
    }

    @Override
    public String getProtocolName() {
        return PROTOCOL_NAME;
    }

    @Override
    public String getProtocolVersion() {
        return PROTOCOL_VERSION;
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public boolean supportsCompression() {
        return false;
    }

    @Override
    public Map<String, Object> getProtocolConfig() {
        return getProtocolStats();
    }

    @Override
    public boolean validateConfig(SourceConnectorConfig config) {
        return config != null;
    }
}