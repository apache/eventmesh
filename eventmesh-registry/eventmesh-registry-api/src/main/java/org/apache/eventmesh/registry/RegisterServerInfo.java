package org.apache.eventmesh.registry;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;

@ToString
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

    public void setExtFields(Map<String, Object> extFields) {
        if (extFields == null) {
            this.extFields.clear();
            return;
        }

        this.extFields = extFields;
    }

    public void addExtFields(String key, Object value) {
        this.extFields.put(key, value);
    }
}
