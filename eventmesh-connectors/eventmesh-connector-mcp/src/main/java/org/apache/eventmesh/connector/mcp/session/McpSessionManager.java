package org.apache.eventmesh.connector.mcp.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class McpSessionManager {

    private static final long SESSION_TIMEOUT_SECONDS = 300;

    private static final McpSessionManager INSTANCE = new McpSessionManager();

    private final Map<String, McpSession> sessionMap = new ConcurrentHashMap<>();

    private McpSessionManager() {
    }

    public static McpSessionManager getInstance() {
        return INSTANCE;
    }

    public McpSession getOrCreateSession(String sessionId, io.netty.channel.Channel channel) {
        return sessionMap.compute(sessionId, (id, existing) -> {
            if (existing != null) {
                existing.setChannel(channel); // 恢复连接
                return existing;
            } else {
                return new McpSession(sessionId, channel);
            }
        });
    }

    public McpSession getSession(String sessionId) {
        return sessionMap.get(sessionId);
    }

    public void closeSession(String sessionId) {
        McpSession session = sessionMap.remove(sessionId);
        if (session != null) {
            session.close();
        }
    }

    public void clearTimeoutSessions() {
        Instant now = Instant.now();
        for (Map.Entry<String, McpSession> entry : sessionMap.entrySet()) {
            if (Duration.between(entry.getValue().getLastActiveTime(), now).getSeconds() > SESSION_TIMEOUT_SECONDS) {
                closeSession(entry.getKey());
            }
        }
    }

    public int activeSessionCount() {
        return sessionMap.size();
    }
}

