package org.apache.eventmesh.common.stubs;

import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.header.Header;
import org.apache.eventmesh.common.utils.HttpConvertsUtils;

import java.util.Map;

public class HeaderStub extends Header {

    public String code;
    public String eventmeshenv;

    @Override
    public Map<String, Object> toMap() {
        return new HttpConvertsUtils().httpMapConverts(this, new ProtocolKey(), new ProtocolKey.EventMeshInstanceKey());
    }
}
