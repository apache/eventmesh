package org.apache.eventmesh.registry;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;


public class RegisterServerInfo {
    // different implementations will have different formats
    @Getter
    @Setter
    private String serviceName;

    @Getter
    @Setter
    private String address;

    @Getter
    @Setter
    private boolean health;
    @Getter
    private Map<String,String> metadata = new HashMap<>();
    @Getter
    private Map<String, Object> extFields = new HashMap<>();

    public void setMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            this.metadata.clear();
            return;
        }

        this.metadata = metadata;
    }

    public void addMetadata(String key, String value) {
        this.metadata.put(key, value);
    }
}
