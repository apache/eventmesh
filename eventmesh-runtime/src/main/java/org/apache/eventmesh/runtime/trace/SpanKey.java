package org.apache.eventmesh.runtime.trace;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Makes span keys for specific instrumentation accessible to enrich and suppress spans. */
public final class SpanKey {
    // server span key
    public static final ContextKey<Span> SERVER_KEY =
            ContextKey.named("opentelemetry-traces-span-key-server");

    // client span keys
    public static final ContextKey<Span> HTTP_CLIENT_KEY =
            ContextKey.named("opentelemetry-traces-span-key-http");
    public static final ContextKey<Span> RPC_CLIENT_KEY =
            ContextKey.named("opentelemetry-traces-span-key-rpc");
    public static final ContextKey<Span> DB_CLIENT_KEY =
            ContextKey.named("opentelemetry-traces-span-key-db");

    // this is used instead of above, depending on the configuration value for
    // otel.instrumentation.experimental.outgoing-span-suppression-by-type
    public static final ContextKey<Span> CLIENT_KEY =
            ContextKey.named("opentelemetry-traces-span-key-client");

    // producer & consumer (messaging) span keys
    public static final ContextKey<Span> PRODUCER_KEY =
            ContextKey.named("opentelemetry-traces-span-key-producer");
    public static final ContextKey<Span> CONSUMER_RECEIVE_KEY =
            ContextKey.named("opentelemetry-traces-span-key-consumer-receive");
    public static final ContextKey<Span> CONSUMER_PROCESS_KEY =
            ContextKey.named("opentelemetry-traces-span-key-consumer-process");
}

