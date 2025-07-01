package org.apache.eventmesh.connector.mcp.source.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter.Feature;
import java.util.Map;

/**
 * Webhook response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpResponse implements Serializable {
    private static final long serialVersionUID = 8616938575207104455L;

    private Object outputs;

    private String sessionId;

    private Map<String, String> metadata;

    private String finishReason;

    private LocalDateTime handleTime;

    public String toJsonStr() {
        return JSON.toJSONString(this, Feature.WriteMapNullValue);
    }

    public static McpResponse success(Object outputs, String sessionId) {
        return new McpResponse(outputs, sessionId, null, "done", LocalDateTime.now());
    }


    public static McpResponse base(Object outputs, String sessionId, String finishReason) {
        return new McpResponse(outputs, sessionId, null, finishReason, LocalDateTime.now());
    }
}
