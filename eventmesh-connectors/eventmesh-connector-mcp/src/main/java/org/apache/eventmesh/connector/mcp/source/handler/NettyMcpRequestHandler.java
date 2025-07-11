package org.apache.eventmesh.connector.mcp.source.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
        import io.netty.util.CharsetUtil;

import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.config.connector.mcp.McpSourceConfig;
import org.apache.eventmesh.connector.mcp.source.data.McpRequest;
import org.apache.eventmesh.connector.mcp.source.data.McpStreamingRequest;
import org.apache.eventmesh.connector.mcp.util.AIToolManager;
import org.apache.eventmesh.connector.mcp.source.data.MCPStreamingResponse;
import org.apache.eventmesh.connector.mcp.session.MCPSession;
import org.apache.eventmesh.connector.mcp.session.SessionManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.eventmesh.connector.mcp.util.McpResponseValidator;

import java.util.*;
        import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Netty MCP请求处理器
 */
@Slf4j
public class NettyMcpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final String PROTOCOL_NAME = "Mcp";
    private final McpSourceConfig sourceConfig;
    private final BlockingQueue<Object> queue;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final AIToolManager toolManager;

    public NettyMcpRequestHandler(McpSourceConfig sourceConfig, BlockingQueue<Object> queue,
                                  SessionManager sessionManager, ObjectMapper objectMapper,
                                  AIToolManager toolManager) {
        this.sourceConfig = sourceConfig;
        this.queue = queue;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
        this.toolManager = toolManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        // 处理CORS预检请求
        if (request.method() == HttpMethod.OPTIONS) {
            sendCorsResponse(ctx);
            return;
        }

        // 处理健康检查GET请求
        if (request.method() == HttpMethod.GET) {
            // 检查是否是SSE请求
            String accept = request.headers().get("Accept");
            if (accept != null && accept.contains("text/event-stream")) {
                handleSSERequest(ctx, request);
                return;
            } else {
                sendHealthCheckResponse(ctx);
                return;
            }
        }

        // 检查路径是否匹配
        String targetPath = sourceConfig.getConnectorConfig().getPath();
        if (!targetPath.equals(request.uri())) {
            sendNotFound(ctx);
            return;
        }

        // 只处理POST请求用于MCP调用
        if (request.method() != HttpMethod.POST) {
            sendMethodNotAllowed(ctx);
            return;
        }

        try {
            // 解析请求体
            String jsonStr = request.content().toString(CharsetUtil.UTF_8);
            Map<String, Object> payloadMap = objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});

            // 从请求头获取MCP相关信息
            String mcpProtocolVersion = request.headers().get("Mcp-Protocol-Version");
            String mcpSessionId = request.headers().get("Mcp-Session-Id");

            log.info("Received MCP request - Protocol: {}, Session: {}, URI: {}",
                    mcpProtocolVersion, mcpSessionId, request.uri());

            // 检查是否是流式请求
            Boolean isStreaming = (Boolean) payloadMap.get("stream");
            if (Boolean.TRUE.equals(isStreaming)) {
                handleStreamingRequest(ctx, request, payloadMap, mcpSessionId, mcpProtocolVersion);
            } else {
                handleNormalRequest(ctx, request, payloadMap, mcpSessionId, mcpProtocolVersion);
            }

        } catch (Exception e) {
            log.error("Error processing MCP request", e);
            sendError(ctx, "Invalid request format: " + e.getMessage());
        }
    }

    /**
     * 处理普通MCP请求
     */
    private void handleNormalRequest(ChannelHandlerContext ctx, FullHttpRequest request,
                                     Map<String, Object> payloadMap, String sessionId, String protocolVersion) {

        // 获取或创建会话
        MCPSession session = getOrCreateSession(sessionId);
        session.updateLastActivity();

        // 解析MCP请求结构
        List<Map<String, Object>> inputs = (List<Map<String, Object>>) payloadMap.get("inputs");
        Map<String, Object> context = (Map<String, Object>) payloadMap.get("context");
        Map<String, Object> metadata = (Map<String, Object>) payloadMap.get("metadata");

        // 创建MCP请求对象
        Map<String, String> headerMap = new HashMap<>();
        for (Map.Entry<String, String> entry : request.headers().entries()) {
            headerMap.put(entry.getKey(), entry.getValue());
        }

        McpRequest mcpRequest = new McpRequest(
                PROTOCOL_NAME,
                request.uri(),
                headerMap,
                false,
                payloadMap,
                null, // Netty没有RoutingContext
                sessionId,
                protocolVersion,
                session
        );

        // 添加到队列
        if (!queue.offer(mcpRequest)) {
            sendError(ctx, "Queue full, failed to process request");
            return;
        }

        // 如果启用了数据一致性，处理请求并返回响应
        if (sourceConfig.getConnectorConfig().isDataConsistencyEnabled()) {
            processNormalMcpRequest(ctx, inputs, context, metadata, session, sessionId, protocolVersion);
        }
    }

    /**
     * 处理流式MCP请求
     */
    private void handleStreamingRequest(ChannelHandlerContext ctx, FullHttpRequest request,
                                        Map<String, Object> payloadMap, String sessionId, String protocolVersion) {

        // 获取或创建会话
        MCPSession session = getOrCreateSession(sessionId);
        session.updateLastActivity();

        // 解析MCP请求结构
        List<Map<String, Object>> inputs = (List<Map<String, Object>>) payloadMap.get("inputs");
        Map<String, Object> context = (Map<String, Object>) payloadMap.get("context");
        Map<String, Object> metadata = (Map<String, Object>) payloadMap.get("metadata");

        // 设置流式响应头
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, OPTIONS");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization, Mcp-Protocol-Version, Mcp-Session-Id");

        if (protocolVersion != null) {
            response.headers().set("Mcp-Protocol-Version", protocolVersion);
        }
        if (sessionId != null) {
            response.headers().set("Mcp-Session-Id", sessionId);
        }

        // 发送响应头
        ctx.write(response);

        // 创建流式请求对象
        Map<String, String> headerMap = new HashMap<>();
        for (Map.Entry<String, String> entry : request.headers().entries()) {
            headerMap.put(entry.getKey(), entry.getValue());
        }

        McpStreamingRequest streamingRequest = new McpStreamingRequest(
                PROTOCOL_NAME,
                request.uri(),
                headerMap,
                true,
                payloadMap,
                null, // Netty没有RoutingContext
                sessionId,
                protocolVersion,
                session
        );

        // 添加到队列
        if (!queue.offer(streamingRequest)) {
            sendStreamingError(ctx, sessionId, "Queue full", protocolVersion);
            return;
        }

        // 异步处理流式响应
        processStreamingMcpRequest(ctx, inputs, context, metadata, session, sessionId, protocolVersion);
    }

    /**
     * 处理普通MCP请求
     */
    private void processNormalMcpRequest(ChannelHandlerContext ctx, List<Map<String, Object>> inputs,
                                         Map<String, Object> context, Map<String, Object> metadata,
                                         MCPSession session, String sessionId, String protocolVersion) {

        // 提取用户输入
        String userContent = extractUserContent(inputs);

        // 设置会话上下文
        if (context != null) {
            session.setContext("history", context.get("history"));
            session.setContext("context", context);
        }

        // 异步处理请求
        toolManager.processRequest(userContent, session, metadata)
                .thenAccept(result -> {
                    try {
                        // 构造MCP响应
                        Map<String, Object> responseMap = new HashMap<>();
                        responseMap.put("session_id", sessionId);

                        Map<String, Object> output = new HashMap<>();
                        output.put("role", "assistant");
                        output.put("content", result);
                        responseMap.put("outputs", Arrays.asList(output));

                        Map<String, Object> metadataMap = new HashMap<>();
                        metadataMap.put("timestamp", System.currentTimeMillis());
                        metadataMap.put("version", protocolVersion != null ? protocolVersion : "2025-03-26");
                        metadataMap.put("streaming", false);
                        responseMap.put("metadata", metadataMap);

                        String responseJson = objectMapper.writeValueAsString(responseMap);

                        // 发送响应
                        ByteBuf content = Unpooled.copiedBuffer(responseJson, CharsetUtil.UTF_8);
                        FullHttpResponse response = new DefaultFullHttpResponse(
                                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);

                        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
                        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
                        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

                        if (protocolVersion != null) {
                            response.headers().set("Mcp-Protocol-Version", protocolVersion);
                        }
                        if (sessionId != null) {
                            response.headers().set("Mcp-Session-Id", sessionId);
                        }

                        ctx.writeAndFlush(response);

                    } catch (Exception e) {
                        log.error("Error processing response", e);
                        sendError(ctx, "Error processing response: " + e.getMessage());
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Error processing request", throwable);
                    sendError(ctx, "Error processing request: " + throwable.getMessage());
                    return null;
                });
    }

    /**
     * 处理流式MCP请求
     */
    private void processStreamingMcpRequest(ChannelHandlerContext ctx, List<Map<String, Object>> inputs,
                                            Map<String, Object> context, Map<String, Object> metadata,
                                            MCPSession session, String sessionId, String protocolVersion) {

        // 提取用户输入
        String userContent = extractUserContent(inputs);

        // 设置会话上下文
        if (context != null) {
            session.setContext("history", context.get("history"));
            session.setContext("context", context);
        }

        // 异步处理流式请求
        toolManager.processStreamingRequest(userContent, session, metadata)
                .thenAccept(streamingResult -> {
                    startMcpStreaming(ctx, sessionId, streamingResult, protocolVersion);
                })
                .exceptionally(throwable -> {
                    log.error("Error processing streaming request", throwable);
                    sendStreamingError(ctx, sessionId, throwable.getMessage(), protocolVersion);
                    return null;
                });
    }

    /**
     * 开始MCP流式传输
     */
    private void startMcpStreaming(ChannelHandlerContext ctx, String sessionId,
                                   MCPStreamingResponse result, String protocolVersion) {

        // 使用Netty的事件循环定时器实现流式传输
        ctx.executor().scheduleAtFixedRate(() -> {
            if (!ctx.channel().isActive()) {
                return;
            }

            try {
                // 获取下一个数据块
                String chunk = result.getNextChunk();
                if (chunk != null) {
                    // 使用验证器创建标准的MCP响应
                    String responseJson = McpResponseValidator.createStreamingResponse(
                            sessionId,
                            chunk,
                            result.getCurrentIndex() - 1,
                            result.isComplete(),
                            protocolVersion
                    );

                    log.debug("Streaming chunk {}/{}: {}",
                            result.getCurrentIndex() - 1,
                            result.getTotalChunks(),
                            chunk.substring(0, Math.min(50, chunk.length())));

                    ByteBuf chunkBuf = Unpooled.copiedBuffer(responseJson + "\n", CharsetUtil.UTF_8);
                    ctx.writeAndFlush(new DefaultHttpContent(chunkBuf));

                    // 如果完成，结束流式传输
                    if (result.isComplete()) {
                        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                        log.info("Streaming completed for session: {} with {} chunks",
                                sessionId, result.getTotalChunks());
                        return;
                    }
                }
            } catch (Exception e) {
                log.error("Error in streaming for session: {}", sessionId, e);
                sendStreamingError(ctx, sessionId, e.getMessage(), protocolVersion);
            }
        }, 0, 300, TimeUnit.MILLISECONDS); // 稍微降低频率到300ms
    }


    /**
     * 提取用户输入内容
     */
    private String extractUserContent(List<Map<String, Object>> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return "";
        }

        // 查找用户角色的消息
        for (Map<String, Object> input : inputs) {
            if ("user".equals(input.get("role"))) {
                return (String) input.get("content");
            }
        }

        // 如果没有找到用户角色，返回第一个输入的内容
        return (String) inputs.get(0).get("content");
    }

    /**
     * 发送流式错误
     */
    private void sendStreamingError(ChannelHandlerContext ctx, String sessionId,
                                    String errorMessage, String protocolVersion) {
        try {
            // 使用验证器创建标准错误响应
            String errorJson = McpResponseValidator.createErrorResponse(
                    sessionId, errorMessage, "STREAMING_ERROR", protocolVersion);

            ByteBuf errorBuf = Unpooled.copiedBuffer(errorJson + "\n", CharsetUtil.UTF_8);
            ctx.writeAndFlush(new DefaultHttpContent(errorBuf));
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

            log.error("Sent streaming error for session {}: {}", sessionId, errorMessage);

        } catch (Exception e) {
            log.error("Error sending streaming error for session: {}", sessionId, e);
            // 最后的备用方案
            String fallbackError = "{\"error\":{\"message\":\"Internal error\",\"code\":\"INTERNAL_ERROR\"},\"session_id\":\"" + sessionId + "\"}\n";
            ByteBuf fallbackBuf = Unpooled.copiedBuffer(fallbackError, CharsetUtil.UTF_8);
            ctx.writeAndFlush(new DefaultHttpContent(fallbackBuf));
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        }
    }

    /**
     * 获取或创建会话
     */
    private MCPSession getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            // 如果没有提供会话ID，创建新会话
            MCPSession newSession = sessionManager.createSession();
            log.info("Created new session: {}", newSession.getSessionId());
            return newSession;
        }

        // 尝试获取现有会话
        MCPSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            // 如果会话不存在，使用提供的会话ID创建新会话
            session = sessionManager.createSessionWithId(sessionId);
            log.info("Created session with provided ID: {}", sessionId);
        } else {
            log.debug("Using existing session: {}", sessionId);
        }

        return session;
    }

    /**
     * 发送错误响应
     */
    private void sendError(ChannelHandlerContext ctx, String message) {
        try {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", Map.of(
                    "message", message,
                    "code", "INVALID_REQUEST"
            ));
            errorResponse.put("metadata", Map.of(
                    "timestamp", System.currentTimeMillis(),
                    "version", "2025-03-26"
            ));

            String json = objectMapper.writeValueAsString(errorResponse);
            ByteBuf content = Unpooled.copiedBuffer(json, CharsetUtil.UTF_8);

            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST, content);

            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

            ctx.writeAndFlush(response);
        } catch (Exception e) {
            log.error("Error sending error response", e);
        }
    }

    /**
     * 发送404响应
     */
    private void sendNotFound(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);

        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        ctx.writeAndFlush(response);
    }

    /**
     * 处理SSE请求
     */
    private void handleSSERequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        log.info("Handling SSE request from: {}", ctx.channel().remoteAddress());

        // 设置SSE响应头
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
        response.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Cache-Control");

        // 发送响应头
        ctx.write(response);

        // 发送初始连接事件
        sendSSEEvent(ctx, "connected", Map.of(
                "status", "connected",
                "server", "EventMesh MCP Server",
                "version", "2025-03-26",
                "timestamp", System.currentTimeMillis()
        ));

        // 设置定时心跳
        ctx.executor().scheduleAtFixedRate(() -> {
            if (ctx.channel().isActive()) {
                sendSSEEvent(ctx, "heartbeat", Map.of(
                        "timestamp", System.currentTimeMillis(),
                        "status", "alive"
                ));
            }
        }, 30, 30, java.util.concurrent.TimeUnit.SECONDS);

        // 发送服务器能力信息
        sendSSEEvent(ctx, "capabilities", Map.of(
                "tools", Arrays.asList("text-generator", "code-analyzer", "data-processor"),
                "streaming", true,
                "protocol_version", "2025-03-26"
        ));
    }

    /**
     * 发送SSE事件
     */
    private void sendSSEEvent(ChannelHandlerContext ctx, String eventType, Object data) {
        try {
            String eventData = objectMapper.writeValueAsString(data);
            String sseEvent = String.format("event: %s\ndata: %s\n\n", eventType, eventData);

            ByteBuf eventBuf = Unpooled.copiedBuffer(sseEvent, CharsetUtil.UTF_8);
            ctx.writeAndFlush(new DefaultHttpContent(eventBuf));

            log.debug("Sent SSE event: {} with data: {}", eventType, eventData);
        } catch (Exception e) {
            log.error("Error sending SSE event", e);
        }
    }

    /**
     * 发送CORS预检响应
     */
    private void sendCorsResponse(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, OPTIONS");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization, Mcp-Protocol-Version, Mcp-Session-Id");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE, "86400");

        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception caught in channel", cause);
        ctx.close();
    }

    /**
     * 发送健康检查响应
     */
    private void sendHealthCheckResponse(ChannelHandlerContext ctx) {
        try {
            Map<String, Object> healthResponse = new HashMap<>();
            healthResponse.put("status", "healthy");
            healthResponse.put("service", "EventMesh MCP Server");
            healthResponse.put("version", "2025-03-26");
            healthResponse.put("timestamp", System.currentTimeMillis());
            healthResponse.put("endpoints", Map.of(
                    "mcp", "POST /test",
                    "health", "GET /test",
                    "sse", "GET /test (with Accept: text/event-stream)"
            ));

            String json = objectMapper.writeValueAsString(healthResponse);
            ByteBuf content = Unpooled.copiedBuffer(json, CharsetUtil.UTF_8);

            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);

            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS");
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Mcp-Protocol-Version, Mcp-Session-Id, Accept");

            ctx.writeAndFlush(response);
            log.debug("Sent health check response");
        } catch (Exception e) {
            log.error("Error sending health check response", e);
            sendError(ctx, "Health check failed");
        }
    }

    /**
     * 发送405响应
     */
    private void sendMethodNotAllowed(ChannelHandlerContext ctx) {
        try {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Method not allowed");
            errorResponse.put("message", "This endpoint only accepts POST requests for MCP calls and GET requests for health checks");
            errorResponse.put("allowed_methods", Arrays.asList("GET", "POST", "OPTIONS"));

            String json = objectMapper.writeValueAsString(errorResponse);
            ByteBuf content = Unpooled.copiedBuffer(json, CharsetUtil.UTF_8);

            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.METHOD_NOT_ALLOWED, content);

            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            response.headers().set(HttpHeaderNames.ALLOW, "GET, POST, OPTIONS");

            ctx.writeAndFlush(response);
        } catch (Exception e) {
            log.error("Error sending method not allowed response", e);
        }
    }
}