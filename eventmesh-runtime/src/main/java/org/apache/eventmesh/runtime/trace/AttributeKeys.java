package org.apache.eventmesh.runtime.trace;

import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;

/**
 * keys
 */
public final class AttributeKeys {
    public static final AttributeKey<Context> WRITE_CONTEXT =
            AttributeKey.valueOf(AttributeKeys.class, "passed-context");

    // this is the context that has the server span
    //
    // note: this attribute key is also used by ratpack instrumentation
    public static final AttributeKey<Context> SERVER_CONTEXT =
            AttributeKey.valueOf(AttributeKeys.class, "server-span");

    public static final AttributeKey<Context> CLIENT_CONTEXT =
            AttributeKey.valueOf(AttributeKeys.class, "client-context");

    public static final AttributeKey<Context> CLIENT_PARENT_CONTEXT =
            AttributeKey.valueOf(AttributeKeys.class, "client-parent-context");

    private AttributeKeys() {}
}
