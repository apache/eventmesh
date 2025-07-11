package org.apache.eventmesh.connector.mcp.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP响应格式验证器
 * 确保生成的响应完全符合MCP 2025-03-26规范
 */
@Slf4j
public class McpResponseValidator {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String PROTOCOL_VERSION = "2025-03-26";

    /**
     * 创建标准的MCP流式响应
     *
     * @param sessionId 会话ID
     * @param content 内容
     * @param chunkIndex 块索引
     * @param isComplete 是否完成
     * @param protocolVersion 协议版本
     * @return 格式化的响应JSON字符串
     */
    public static String createStreamingResponse(String sessionId, String content,
                                                 int chunkIndex, boolean isComplete,
                                                 String protocolVersion) {
        try {
            Map<String, Object> response = new LinkedHashMap<>();

            // outputs 数组 - 必须字段
            Map<String, Object> output = new HashMap<>();
            output.put("role", "assistant");
            output.put("content", sanitizeContent(content));
            response.put("outputs", Arrays.asList(output));

            // metadata 对象 - 必须字段
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("chunk_index", chunkIndex);
            metadata.put("streaming", true);
            metadata.put("is_complete", isComplete);
            metadata.put("version", protocolVersion != null ? protocolVersion : PROTOCOL_VERSION);
            metadata.put("timestamp", System.currentTimeMillis());
            response.put("metadata", metadata);

            // session_id - 必须字段
            response.put("session_id", sessionId);

            String jsonString = objectMapper.writeValueAsString(response);

            // 验证生成的JSON
            if (!isValidMcpResponse(jsonString)) {
                log.error("Generated invalid MCP response");
                return createFallbackResponse(sessionId, chunkIndex, isComplete);
            }

            return jsonString;

        } catch (Exception e) {
            log.error("Error creating streaming response", e);
            return createFallbackResponse(sessionId, chunkIndex, isComplete);
        }
    }

    /**
     * 创建标准的MCP错误响应
     *
     * @param sessionId 会话ID
     * @param errorMessage 错误消息
     * @param errorCode 错误代码
     * @param protocolVersion 协议版本
     * @return 格式化的错误响应JSON字符串
     */
    public static String createErrorResponse(String sessionId, String errorMessage,
                                             String errorCode, String protocolVersion) {
        try {
            Map<String, Object> response = new LinkedHashMap<>();

            // error 对象 - 必须字段
            Map<String, Object> error = new HashMap<>();
            error.put("message", sanitizeContent(errorMessage));
            error.put("code", errorCode != null ? errorCode : "UNKNOWN_ERROR");
            response.put("error", error);

            // metadata 对象
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("streaming", false);
            metadata.put("version", protocolVersion != null ? protocolVersion : PROTOCOL_VERSION);
            metadata.put("timestamp", System.currentTimeMillis());
            response.put("metadata", metadata);

            // session_id
            response.put("session_id", sessionId);

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            log.error("Error creating error response", e);
            return "{\"error\":{\"message\":\"Internal server error\",\"code\":\"INTERNAL_ERROR\"},\"session_id\":\"" + sessionId + "\"}";
        }
    }

    /**
     * 创建标准的MCP成功响应
     *
     * @param sessionId 会话ID
     * @param content 响应内容
     * @param protocolVersion 协议版本
     * @return 格式化的成功响应JSON字符串
     */
    public static String createSuccessResponse(String sessionId, String content, String protocolVersion) {
        try {
            Map<String, Object> response = new LinkedHashMap<>();

            // outputs 数组
            Map<String, Object> output = new HashMap<>();
            output.put("role", "assistant");
            output.put("content", sanitizeContent(content));
            response.put("outputs", Arrays.asList(output));

            // metadata 对象
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("streaming", false);
            metadata.put("version", protocolVersion != null ? protocolVersion : PROTOCOL_VERSION);
            metadata.put("timestamp", System.currentTimeMillis());
            response.put("metadata", metadata);

            // session_id
            response.put("session_id", sessionId);

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            log.error("Error creating success response", e);
            return createErrorResponse(sessionId, "Failed to create response", "RESPONSE_ERROR", protocolVersion);
        }
    }

    /**
     * 验证MCP响应格式是否正确
     *
     * @param jsonString JSON字符串
     * @return 是否有效
     */
    public static boolean isValidMcpResponse(String jsonString) {
        try {
            JsonNode root = objectMapper.readTree(jsonString);

            // 检查必须字段
            if (!root.has("session_id")) {
                log.warn("Missing session_id field");
                return false;
            }

            // 检查是否有error或outputs
            boolean hasError = root.has("error");
            boolean hasOutputs = root.has("outputs");

            if (!hasError && !hasOutputs) {
                log.warn("Missing both error and outputs fields");
                return false;
            }

            // 检查metadata
            if (!root.has("metadata")) {
                log.warn("Missing metadata field");
                return false;
            }

            JsonNode metadata = root.get("metadata");
            if (!metadata.has("version") || !metadata.has("timestamp")) {
                log.warn("Missing required metadata fields");
                return false;
            }

            // 如果是流式响应，检查流式字段
            if (metadata.has("streaming") && metadata.get("streaming").asBoolean()) {
                if (!metadata.has("chunk_index") || !metadata.has("is_complete")) {
                    log.warn("Missing required streaming metadata fields");
                    return false;
                }
            }

            return true;

        } catch (Exception e) {
            log.warn("JSON parsing error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 清理内容，确保JSON安全
     *
     * @param content 原始内容
     * @return 清理后的内容
     */
    private static String sanitizeContent(String content) {
        if (content == null) {
            return "";
        }

        // 移除可能导致JSON问题的字符
        return content.trim()
                .replaceAll("[\r\n\t]", " ") // 替换换行和制表符
                .replaceAll("\\s+", " ")     // 合并多个空格
                .replaceAll("\"", "\\\"");   // 转义双引号
    }

    /**
     * 创建备用响应
     *
     * @param sessionId 会话ID
     * @param chunkIndex 块索引
     * @param isComplete 是否完成
     * @return 备用响应JSON字符串
     */
    private static String createFallbackResponse(String sessionId, int chunkIndex, boolean isComplete) {
        return String.format(
                "{\"outputs\":[{\"role\":\"assistant\",\"content\":\"Error processing content\"}]," +
                        "\"metadata\":{\"chunk_index\":%d,\"streaming\":true,\"is_complete\":%s,\"version\":\"%s\",\"timestamp\":%d}," +
                        "\"session_id\":\"%s\"}",
                chunkIndex, isComplete, PROTOCOL_VERSION, System.currentTimeMillis(), sessionId
        );
    }

    /**
     * 格式化JSON字符串（用于调试）
     *
     * @param jsonString JSON字符串
     * @return 格式化后的JSON字符串
     */
    public static String formatJson(String jsonString) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        } catch (Exception e) {
            log.warn("Failed to format JSON: {}", e.getMessage());
            return jsonString;
        }
    }

    /**
     * 验证并修复JSON字符串
     *
     * @param jsonString 原始JSON字符串
     * @param sessionId 会话ID（用于修复）
     * @return 修复后的JSON字符串
     */
    public static String validateAndFix(String jsonString, String sessionId) {
        if (isValidMcpResponse(jsonString)) {
            return jsonString;
        }

        log.warn("Invalid MCP response detected, attempting to fix");

        try {
            JsonNode root = objectMapper.readTree(jsonString);
            Map<String, Object> fixed = new LinkedHashMap<>();

            // 确保有session_id
            if (root.has("session_id")) {
                fixed.put("session_id", root.get("session_id").asText());
            } else {
                fixed.put("session_id", sessionId);
            }

            // 处理outputs或error
            if (root.has("outputs")) {
                fixed.put("outputs", objectMapper.convertValue(root.get("outputs"), Object.class));
            } else if (root.has("error")) {
                fixed.put("error", objectMapper.convertValue(root.get("error"), Object.class));
            } else {
                Map<String, Object> error = new HashMap<>();
                error.put("message", "Unknown error");
                error.put("code", "UNKNOWN_ERROR");
                fixed.put("error", error);
            }

            // 确保有完整的metadata
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (root.has("metadata")) {
                JsonNode metaNode = root.get("metadata");
                metadata.putAll(objectMapper.convertValue(metaNode, Map.class));
            }

            // 补充缺失的metadata字段
            if (!metadata.containsKey("version")) {
                metadata.put("version", PROTOCOL_VERSION);
            }
            if (!metadata.containsKey("timestamp")) {
                metadata.put("timestamp", System.currentTimeMillis());
            }

            fixed.put("metadata", metadata);

            return objectMapper.writeValueAsString(fixed);

        } catch (Exception e) {
            log.error("Failed to fix JSON: {}", e.getMessage());
            return createErrorResponse(sessionId, "JSON format error", "JSON_ERROR", PROTOCOL_VERSION);
        }
    }
}