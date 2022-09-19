package org.apache.eventmesh.runtime.core.protocol.amqp.remoting;

import io.netty.buffer.ByteBuf;

import java.io.Serializable;

public interface AMQData extends Serializable {

    void encode(ByteBuf buf);

    void recycle();
}
