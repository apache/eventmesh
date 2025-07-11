package org.apache.eventmesh.connector.mcp.session;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SessionManager {
    private final ConcurrentHashMap<String, MCPSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public SessionManager() {
        // 定时清理过期会话
        scheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 5, 5, TimeUnit.MINUTES);
    }

    public MCPSession createSession() {
        String sessionId = UUID.randomUUID().toString();
        MCPSession session = new MCPSession(sessionId);
        sessions.put(sessionId, session);
        return session;
    }

    public MCPSession createSessionWithId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return createSession();
        }

        // 检查会话ID是否已存在
        if (sessions.containsKey(sessionId)) {
            return sessions.get(sessionId);
        }

        MCPSession session = new MCPSession(sessionId);
        sessions.put(sessionId, session);
        return session;
    }

    public void putSession(MCPSession session) {
        if (session != null) {
            sessions.put(session.getSessionId(), session);
        }
    }

    public MCPSession getSession(String sessionId) {
        MCPSession session = sessions.get(sessionId);
        if (session != null) {
            session.updateLastActivity();
        }
        return session;
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public void cleanupExpiredSessions(long currentTime) {
        sessions.entrySet().removeIf(entry -> {
            MCPSession session = entry.getValue();
            return currentTime - session.getLastActivity() > 30 * 60 * 1000; // 30分钟超时
        });
    }

    private void cleanupExpiredSessions() {
        cleanupExpiredSessions(System.currentTimeMillis());
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}