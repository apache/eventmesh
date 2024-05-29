package org.apache.eventmesh.common.remote.request;

import lombok.Getter;
import org.apache.eventmesh.common.remote.payload.IPayload;

import java.util.HashMap;
import java.util.Map;

@Getter
public abstract class BaseRemoteRequest implements IPayload {
    private Map<String, String> header = new HashMap<>();

    public void addHeader(String key, String value) {
        if (key == null || value == null) {
            return;
        }
        header.put(key,value);
    }

    public void addHeaders(Map<String,String> map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        map.forEach((k,v) -> {
            if (k == null || v == null) {
                return;
            }
            this.header.put(k,v);
        });
    }
}
