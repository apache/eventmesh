package org.apache.eventmesh.common.protocol.mcp;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.*;

public class McpEventWrapper implements ProtocolTransportObject {

    private transient Map<String, Object> headerMap = new HashMap<>();

    private transient Map<String, Object> sysHeaderMap = new HashMap<>();

    private byte[] body;

    private long reqTime;

    private long resTime;

    public McpEventWrapper() {
        this.reqTime = System.currentTimeMillis();
    }

    public Map<String, Object> getHeaderMap() {
        return headerMap;
    }

    public void setHeaderMap(Map<String, Object> headerMap) {
        this.headerMap = headerMap;
    }

    public Map<String, Object> getSysHeaderMap() {
        return sysHeaderMap;
    }

    public void setSysHeaderMap(Map<String, Object> sysHeaderMap) {
        this.sysHeaderMap = sysHeaderMap;
    }

    public byte[] getBody() {
        int len = body.length;
        byte[] b = new byte[len];
        System.arraycopy(body, 0, b, 0, len);
        return b;
    }

    public void setBody(byte[] newBody) {
        if (newBody == null || newBody.length == 0) {
            return;
        }
        this.body = Arrays.copyOf(newBody, newBody.length);
    }

    public long getReqTime() {
        return reqTime;
    }

    public void setReqTime(long reqTime) {
        this.reqTime = reqTime;
    }

    public long getResTime() {
        return resTime;
    }

    public void setResTime(long resTime) {
        this.resTime = resTime;
    }

    public void buildSysHeaderForClient() {
        sysHeaderMap.put(ProtocolKey.PROTOCOL_TYPE, "mcp");
        sysHeaderMap.put(ProtocolKey.PROTOCOL_DESC, "mcp");

        for (ProtocolKey.ClientInstanceKey key : ProtocolKey.ClientInstanceKey.values()) {
            if (key == ProtocolKey.ClientInstanceKey.BIZSEQNO || key == ProtocolKey.ClientInstanceKey.UNIQUEID) {
                continue;
            }
            sysHeaderMap.put(key.getKey(), headerMap.getOrDefault(key.getKey(), key.getValue()));
        }
    }

    public void buildSysHeaderForCE() {
        sysHeaderMap.put(ProtocolKey.CloudEventsKey.ID, UUID.randomUUID().toString());
        sysHeaderMap.put(ProtocolKey.CloudEventsKey.SOURCE, headerMap.getOrDefault("source", URI.create("/")));
        sysHeaderMap.put(ProtocolKey.CloudEventsKey.TYPE, headerMap.getOrDefault("type", "mcp_request"));

        String topic = headerMap.getOrDefault("subject", "").toString();
        if (StringUtils.isEmpty(topic)) {
            topic = "TEST-MCP-TOPIC";
        }
        sysHeaderMap.put(ProtocolKey.CloudEventsKey.SUBJECT, topic);
    }
}
