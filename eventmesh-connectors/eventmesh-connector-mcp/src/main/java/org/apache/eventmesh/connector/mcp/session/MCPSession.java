package org.apache.eventmesh.connector.mcp.session;

import lombok.Getter;

import java.util.concurrent.ConcurrentHashMap;

public class MCPSession {
    @Getter
    private final String sessionId;
    private final ConcurrentHashMap<String, Object> context = new ConcurrentHashMap<>();
    @Getter
    private volatile long lastActivity;

    public MCPSession(String sessionId) {
        this.sessionId = sessionId;
        this.lastActivity = System.currentTimeMillis();
    }

    public void updateLastActivity() {
        this.lastActivity = System.currentTimeMillis();
    }

    public void setContext(String key, Object value) {
        context.put(key, value);
    }

    public Object getContext(String key) {
        return context.get(key);
    }

    public void clearContext() {
        context.clear();
    }
}