package org.apache.eventmesh.runtime.core.protocol.amqp.remoting.constants;

import io.netty.util.AttributeKey;

public class ProtocolKey {

    public static final AttributeKey<Integer> MAX_FRAME_SIZE = AttributeKey.valueOf("decoder.maxFrameSize");
}