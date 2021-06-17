package org.apache.eventmesh.connector.redis.common;

import java.nio.charset.StandardCharsets;

public enum Command {

    // Authentication
    AUTH,

    // Pub/Sub
    PSUBSCRIBE, PUBLISH, PUNSUBSCRIBE, SUBSCRIBE, UNSUBSCRIBE, PUBSUB,

    // Stream
    XACK, XADD, XAUTOCLAIM, XCLAIM, XDEL, XGROUP, XINFO, XLEN, XPENDING, XRANGE, XREVRANGE, XREAD, XREADGROUP, XTRIM;

    public final byte[] bytes;

    Command() {
        bytes = name().getBytes(StandardCharsets.US_ASCII);
    }

    public byte[] getBytes() {
        return bytes;
    }

    public String getCmdName() {
        return this.name();
    }
}
