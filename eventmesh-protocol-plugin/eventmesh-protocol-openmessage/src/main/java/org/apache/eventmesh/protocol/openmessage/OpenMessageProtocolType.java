package org.apache.eventmesh.protocol.openmessage;

import org.apache.eventmesh.protocol.api.ProtocolType;

public class OpenMessageProtocolType implements ProtocolType {

    private static final String name = "OpenMessage";

    private static final OpenMessageProtocolType instance = new OpenMessageProtocolType();

    private OpenMessageProtocolType() {
    }


    public static ProtocolType getInstance() {
        return instance;
    }

    @Override
    public String getName() {
        return name;
    }
}
