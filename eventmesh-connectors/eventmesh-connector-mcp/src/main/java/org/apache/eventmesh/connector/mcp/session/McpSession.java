package org.apache.eventmesh.connector.mcp.session;

import io.netty.channel.Channel;

import java.time.Instant;
import java.util.LinkedList;
import java.util.Queue;

public class McpSession {

    private final String sessionId;

    // 与客户端保持通信的通道
    private Channel channel;

    // 最近活跃时间，用于超时清理
    private Instant lastActiveTime;

    // 如果是 Stream 模式，可以缓存未发出的数据块
    private final Queue<Object> streamBuffer = new LinkedList<>();

    public McpSession(String sessionId, Channel channel) {
        this.sessionId = sessionId;
        this.channel = channel;
        this.lastActiveTime = Instant.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
        touch();
    }

    public void addToBuffer(Object data) {
        streamBuffer.add(data);
    }

    public Queue<Object> getStreamBuffer() {
        return streamBuffer;
    }

    public Instant getLastActiveTime() {
        return lastActiveTime;
    }

    public void touch() {
        this.lastActiveTime = Instant.now();
    }

    public void close() {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }
}
