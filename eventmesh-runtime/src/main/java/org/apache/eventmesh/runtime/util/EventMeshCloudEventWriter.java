package org.apache.eventmesh.runtime.util;

import io.cloudevents.rw.CloudEventContextWriter;
import io.cloudevents.rw.CloudEventRWException;

import java.util.HashMap;
import java.util.Map;

public class EventMeshCloudEventWriter implements CloudEventContextWriter {
    private Map<String, Object> extensionMap = null;

    public EventMeshCloudEventWriter() {
        extensionMap = new HashMap<String, Object>();
    }

    @Override
    public CloudEventContextWriter withContextAttribute(String key, String value)
        throws CloudEventRWException {
        extensionMap.put(key, value);
        return this;
    }

    public Map<String, Object> getExtensionMap() {
        return extensionMap;
    }
}
