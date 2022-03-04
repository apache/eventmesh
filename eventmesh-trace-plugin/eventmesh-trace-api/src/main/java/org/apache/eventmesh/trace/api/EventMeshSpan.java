package org.apache.eventmesh.trace.api;

import org.apache.eventmesh.trace.api.common.ProtocolType;

public interface EventMeshSpan {
    void start();
    void start(long timestamp);
    void finish();
    void finish(long timestamp);
    void setOperationName(String operationName);
    void setRemoteAddress(String remoteAddress);
    void setProtocolType(ProtocolType type);
    void addTag(String key, String value);
    void addError(Throwable throwable);
    void setAsync();
    void setComponent(String componentName);
    boolean isAsync();
}
