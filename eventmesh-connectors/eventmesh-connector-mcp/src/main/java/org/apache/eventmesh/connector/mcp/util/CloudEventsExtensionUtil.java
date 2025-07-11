package org.apache.eventmesh.connector.mcp.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * CloudEvents扩展属性工具类
 *
 * 根据CloudEvents规范处理扩展属性：
 * - 扩展名称必须是小写字母、数字和短横线的组合
 * - 必须以字母开头
 * - 长度在1-20个字符之间
 * - 不能包含点号、下划线等特殊字符
 */
@Slf4j
public class CloudEventsExtensionUtil {

    // CloudEvents扩展名称的正则表达式 - 只允许小写字母和数字
    private static final Pattern VALID_EXTENSION_NAME = Pattern.compile("^[a-z][a-z0-9]{0,19}$");

    /**
     * 安全地添加扩展属性到ConnectRecord
     *
     * @param record ConnectRecord对象
     * @param name 扩展属性名称
     * @param value 扩展属性值
     */
    public static void addExtensionSafely(ConnectRecord record, String name, Object value) {
        if (record == null || name == null) {
            return;
        }

        String safeName = normalizeExtensionName(name);
        String safeValue = normalizeExtensionValue(value);

        try {
            record.addExtension(safeName, safeValue);
            log.debug("Added extension: {} = {}", safeName, safeValue);
        } catch (Exception e) {
            log.warn("Failed to add extension {}: {}", safeName, e.getMessage());
        }
    }

    /**
     * 批量添加扩展属性
     *
     * @param record ConnectRecord对象
     * @param extensions 扩展属性映射
     * @param prefix 前缀
     */
    public static void addExtensionsBatch(ConnectRecord record, Map<String, Object> extensions, String prefix) {
        if (record == null || extensions == null) {
            return;
        }

        String safePrefix = prefix != null ? normalizeExtensionName(prefix) : "";

        extensions.forEach((key, value) -> {
            String fullName = safePrefix + normalizeExtensionName(key);
            addExtensionSafely(record, fullName, value);
        });
    }

    /**
     * 规范化扩展名称以符合CloudEvents规范
     *
     * CloudEvents扩展名称规则（更严格）：
     * - 只能包含小写字母(a-z)和数字(0-9)
     * - 必须以小写字母开头
     * - 长度在1-20个字符之间
     * - 不能包含短横线、下划线、点号等特殊字符
     *
     * @param name 原始名称
     * @return 规范化后的名称
     */
    public static String normalizeExtensionName(String name) {
        if (name == null || name.isEmpty()) {
            return "unknown";
        }

        // 转换为小写
        String normalized = name.toLowerCase();

        // 移除所有非字母数字字符
        normalized = normalized.replaceAll("[^a-z0-9]", "");

        // 确保以字母开头
        if (normalized.isEmpty() || !Character.isLetter(normalized.charAt(0))) {
            normalized = "ext" + normalized;
        }

        // 限制长度为20个字符
        if (normalized.length() > 20) {
            normalized = normalized.substring(0, 20);
        }

        // 最终检查，确保不为空且有效
        if (normalized.isEmpty() || !isValidExtensionName(normalized)) {
            normalized = "ext";
        }

        return normalized;
    }

    /**
     * 规范化扩展值
     *
     * @param value 原始值
     * @return 规范化后的字符串值
     */
    public static String normalizeExtensionValue(Object value) {
        if (value == null) {
            return "";
        }

        String stringValue = value.toString();

        // CloudEvents扩展值必须是字符串，且不能包含控制字符
        return stringValue.replaceAll("[\\p{Cntrl}]", " ").trim();
    }

    /**
     * 验证扩展名称是否符合CloudEvents规范
     *
     * @param name 扩展名称
     * @return 是否有效
     */
    public static boolean isValidExtensionName(String name) {
        return name != null && VALID_EXTENSION_NAME.matcher(name).matches();
    }

    /**
     * 创建MCP特定的扩展属性前缀
     *
     * @param category 类别
     * @return 规范化的前缀
     */
    public static String createMcpPrefix(String category) {
        return normalizeExtensionName("mcp" + category);
    }

    /**
     * 为MCP协议添加标准扩展属性
     *
     * @param record ConnectRecord对象
     * @param sessionId 会话ID
     * @param protocolVersion 协议版本
     * @param isStreaming 是否流式
     */
    public static void addMcpStandardExtensions(ConnectRecord record, String sessionId,
                                                String protocolVersion, boolean isStreaming) {
        addExtensionSafely(record, "mcpprotocol", "mcp");
        addExtensionSafely(record, "mcpversion", protocolVersion);
        addExtensionSafely(record, "mcpsession", sessionId);
        addExtensionSafely(record, "mcpstreaming", String.valueOf(isStreaming));
        addExtensionSafely(record, "mcptimestamp", String.valueOf(System.currentTimeMillis()));
    }

    /**
     * 为请求头添加扩展属性
     *
     * @param record ConnectRecord对象
     * @param headers 请求头映射
     */
    public static void addHeaderExtensions(ConnectRecord record, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }

        headers.forEach((key, value) -> {
            String extensionName = "header" + normalizeExtensionName(key);
            addExtensionSafely(record, extensionName, value);
        });
    }

    /**
     * 为元数据添加扩展属性
     *
     * @param record ConnectRecord对象
     * @param metadata 元数据映射
     */
    public static void addMetadataExtensions(ConnectRecord record, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }

        metadata.forEach((key, value) -> {
            String extensionName = "meta" + normalizeExtensionName(key);
            addExtensionSafely(record, extensionName, value);
        });
    }

    /**
     * 获取所有有效的扩展名称示例（用于测试和文档）
     *
     * @return 有效扩展名称示例列表
     */
    public static String[] getValidExtensionExamples() {
        return new String[]{
                "mcpprotocol",
                "mcpversion",
                "mcpsession",
                "mcpstreaming",
                "headercontenttype",
                "metauserid",
                "requesttimestamp",
                "processinghint"
        };
    }
}