package org.apache.eventmesh.common.remote.response;

import lombok.Getter;
import lombok.Setter;
import org.apache.eventmesh.common.remote.payload.IPayload;

import java.util.HashMap;
import java.util.Map;

@Getter
public abstract class BaseRemoteResponse implements IPayload {
    public static final int UNKNOWN = -1;
    @Setter
    private boolean success = true;
    @Setter
    private int errorCode;
    @Setter
    private String desc;

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
