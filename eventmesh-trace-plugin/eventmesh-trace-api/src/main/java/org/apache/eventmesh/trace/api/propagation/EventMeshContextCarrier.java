package org.apache.eventmesh.trace.api.propagation;

import java.util.Map;

public interface EventMeshContextCarrier {
    void extractFromMap(Map<String, String> carrier);
    void injectIntoMap(Map<String, String> carrier);
    Map<String, String> getContext();
}
