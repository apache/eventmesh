package org.apache.eventmesh.protocol.a2a;

import java.util.Map;

/**
 * A2A Message structure for agent-to-agent communication
 */
public class A2AMessage {
    private String messageType;
    private A2AProtocolAdaptor.AgentInfo sourceAgent;
    private A2AProtocolAdaptor.AgentInfo targetAgent;
    private Object payload;
    private MessageMetadata metadata;
    private long timestamp;

    public A2AMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public A2AProtocolAdaptor.AgentInfo getSourceAgent() {
        return sourceAgent;
    }

    public void setSourceAgent(A2AProtocolAdaptor.AgentInfo sourceAgent) {
        this.sourceAgent = sourceAgent;
    }

    public A2AProtocolAdaptor.AgentInfo getTargetAgent() {
        return targetAgent;
    }

    public void setTargetAgent(A2AProtocolAdaptor.AgentInfo targetAgent) {
        this.targetAgent = targetAgent;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public MessageMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(MessageMetadata metadata) {
        this.metadata = metadata;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}