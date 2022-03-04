package org.apache.eventmesh.trace.zipkin;

import org.apache.eventmesh.trace.api.propagation.EventMeshContextCarrier;

import java.util.HashMap;
import java.util.Map;

public class ZipkinEventMeshContextCarrier implements EventMeshContextCarrier {
    private HashMap<String, String> context = new HashMap<>();

    @Override
    public void extractFromMap(Map<String, String> carrier) {

    }

    @Override
    public void injectIntoMap(Map<String, String> carrier) {

    }

    @Override
    public Map<String, String> getContext() {
        return this.context;
    }
}
