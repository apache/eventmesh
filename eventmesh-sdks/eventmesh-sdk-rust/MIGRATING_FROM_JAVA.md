# Migrating from the Java SDK

This guide compares the Rust v2 SDK with `eventmesh-sdk-java` in this repository.
The protocols remain compatible, but copying Java callback, metadata, and lifecycle
patterns can produce different application behavior. The optional interoperability
tests separately use the published Java `1.12.0-release` artifact; that is not the
same as testing every source change in the repository's Java SDK.

## Changes that can affect an existing application

| Situation | Java SDK behavior | Rust behavior and migration action |
| --- | --- | --- |
| Forward a received CloudEvent to another client | The gRPC builder fills identity and credentials only when absent, so the event can retain the original sender's credentials, token, identity, and routing. | A new publish, broadcast, or request removes known transport and delivery attributes from the outbound copy. Authentication and identity come from the destination client. Configure that client explicitly if forwarding previously depended on per-event credentials. |
| Return a CloudEvent received from another request as a reply | gRPC merges request attributes first and reply attributes second; conflicting reply routing wins. TCP also preserves existing reply extensions. | The **current request** owns known internal delivery and routing attributes, including `correlation99id`, `reply99to99client`, cluster, and `req0*`/`rsp0*` keys. Reply business extensions still win. Do not use a handler reply to redirect the response to an unrelated request. |
| Return plain text or binary from a gRPC handler | `SubStreamHandler` sets the reply content type to `application/json` without converting its payload. | The declared content type and data variant are preserved. A `text/plain` reply containing `pong` stays text; binary stays binary. Consumers that assumed every reply was JSON must dispatch by content type. JSON replies remain JSON. |
| A TCP handler completes after its socket reconnects | Java TCP replies use the callback's channel, while ACKs use the client's current channel. The latter can acknowledge an old delivery on a new connection. | Each delivery carries a socket generation. Late results send neither reply nor ACK on a replacement session; an old handler failure also leaves that replacement session intact. Runtime/storage policy determines redelivery. Make handler side effects idempotent. |
| A TCP publisher push handler fails or panics | Netty can close the channel when the exception escapes the callback. There is no Rust-style task result to await. | Producer and consumer handlers use the same unwind/error boundary. No ACK is sent for a failed delivery; reconnect follows `ReconnectPolicy`. `TcpProducer::join()` reports a terminal failure. Recovery does not reset application state, and `panic = "abort"` cannot be caught. |
| Stop an HTTP consumer | Java `close()` closes local resources and shuts down the scheduler; it does not automatically unregister subscriptions or wait for a submitted heartbeat. | `shutdown()` stops local scheduling and `join()` waits. `close().await` waits for a submitted heartbeat, stops further scheduling, unregisters tracked topics, and joins local tasks. It can take longer and return cleanup errors. Registration changes after shutdown/closing return `Error::ChannelClosed`; create a new role to restart. |
| Construct gRPC configuration without credentials | Java defaults to `username` / `passwd`; Runtime validates non-empty credentials even when authentication is disabled. | `GrpcConfig::new` uses those same defaults. Explicit `with_credentials` replaces them. Generic `Credentials::default()`, HTTP, and TCP keep empty defaults. For an authenticated deployment, supply the deployment's credentials. |

These corrections deliberately avoid reproducing the Java metadata precedence,
JSON relabeling, and cross-session ACK behavior. Returning the current request as
an echo reply remains supported.

Relevant Java implementations:
[CloudEvent builder](../eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/grpc/util/EventMeshCloudEventBuilder.java),
[stream reply handler](../eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/grpc/consumer/SubStreamHandler.java),
[HTTP consumer](../eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/http/consumer/EventMeshHttpConsumer.java),
and [TCP client](../eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/tcp/common/TcpClient.java).

## Choose a transport and own its resources

The default Cargo feature set is empty:

| Feature | Use |
| --- | --- |
| `grpc` | Producer, stream consumer, and external webhook registration |
| `http-client` | Producer, external webhook registration/heartbeat, and framework-independent webhook codecs; does not enable axum |
| `http-server` | Everything in `http-client`, plus the SDK-managed axum callback server |
| `http` | Backward-compatible aggregate of `http-client` and `http-server` |
| `tcp` | Connected producer and consumer |
| `cloud_events` | Native CloudEvents in the public `Message` enum |

For gRPC, connect `GrpcChannel` inside the Tokio runtime that drives it, then
clone the channel into producer and consumer roles. For HTTP, create one
`HttpClient` and use its role factories. Client clones and their roles share one
connection pool and endpoint selector. Weighted round-robin advances across all
operations on that client, rather than restarting separately for each role.
Create separate clients when independent endpoint selection is required.

`TcpClient` is a configuration/factory handle. Each producer or consumer it
creates establishes its own TCP session. Keep the Tokio runtime running for all
background work; dropping a role aborts its remaining owned tasks.

Default request timeouts are 5 seconds for gRPC, 15 seconds for HTTP, and
20 seconds for TCP. TCP separately uses a 1-second connect timeout and a
20-second control timeout. Use `ClientOptions::with_request_timeout` and the
TCP-specific configuration methods instead of assuming Java timeout settings
carry over implicitly. Configure `Identity` explicitly when subsystem, zone,
environment, or advertised IP must match an existing deployment.

## Port callbacks to `MessageHandler`

The callback receives `Message::EventMesh` or, with `cloud_events`,
`Message::CloudEvent`. Rust does not silently flatten CloudEvents into native
messages; `into_event_mesh()` returns an error for that dialect.

```rust,ignore
impl eventmesh::MessageHandler for Handler {
    async fn handle(&self, message: eventmesh::Message)
        -> eventmesh::Result<Option<eventmesh::Message>>
    {
        // Inspect or process the message here.
        Ok(None) // Successful asynchronous processing.
    }
}
```

Return `Ok(Some(reply))` for a synchronous request/reply subscription. Return
`Err(_)` for application failure. HTTP has no complete request/reply responder
path in the stock Runtime and SDK; use gRPC or TCP for synchronous subscriptions.

TCP dispatch is serial per role, but socket I/O and reconnect can progress while
the handler awaits. gRPC stream dispatch defaults to one handler and can be
bounded with `GrpcConsumerOptions::with_max_concurrent_handlers`. HTTP requests
can invoke handlers concurrently. Review shared mutable state and ordering
assumptions when porting Java hooks.

For a gRPC text reply, declare text rather than relying on Java's JSON label:

```rust,ignore
use cloudevents::{EventBuilder, EventBuilderV10};

let reply = EventBuilderV10::new()
    .id("reply-id")
    .source("urn:orders:responder")
    .ty("orders.reply")
    .subject(topic)
    .data("text/plain", "pong")
    .build()?;
Ok(Some(eventmesh::Message::CloudEvent(reply)))
```

To send JSON, provide valid JSON and its content type explicitly. TCP CloudEvents
retain the Java Runtime constraint that `datacontenttype` must be
`application/cloudevents+json`; the gRPC text/binary behavior does not remove
that TCP constraint.

## Separate business data from delivery metadata

Java native message properties can contain both application data and transport
attributes. Rust gives native messages dedicated fields and read-only delivery
context:

| Java property/access pattern | Rust API |
| --- | --- |
| Topic and content | `topic()`, `content()` |
| `ttl` | `ttl_millis()` / builder `ttl_millis(...)` |
| `seqnum` / business sequence | `biz_seq_no()` / builder `biz_seq_no(...)` |
| `uniqueid` | `unique_id()` / builder `unique_id(...)` |
| `datacontenttype` | `data_content_type()` / builder `data_content_type(...)` |
| Application extensions | `properties()`, `get_prop(...)`, `set_prop(...)?` |
| Received protocol/identity/routing | `delivery_context()`, then `protocol_description()` or `attribute(...)` |

Reserved property names are rejected. Builder `prop`/`props` report the error
at `build()`; `set_prop` and `with_property` return `Result` immediately. Do not
copy routing context into reply properties: the transport restores the current
request's route automatically. Producers ignore a native message's delivery
context when starting a new operation.

Native decoders preserve numeric TTL values that fit `i64`. New native publishes,
broadcasts, and outgoing requests require `1..=i32::MAX` milliseconds; zero and negative TTL are not
interpreted as never-expire. HTTP/gRPC use a 4000 ms default when TTL is absent;
TCP leaves an absent TTL to the Runtime. Re-publishing a received message with
an out-of-range TTL therefore requires choosing a valid outbound TTL.
Handler replies do not reapply these producer-only checks. TCP native replies
preserve the callback's content, including whitespace, and inherit the request's
TTL when the reply leaves it unset.

CloudEvents keep their standard attributes, data, and business extensions on
receive. Outbound filtering applies to a copy: it does not alter the original
event. CloudEvent `id`, source, type, time, and data remain application-owned;
existing EventMesh business IDs and TTL are preserved, and missing required
fields are generated/defaulted by the transport. Known transport/delivery keys
are classified by exact name, not a broad prefix: a business `requestid` remains
a business extension.

Use a separately configured destination client for a different identity or
credential. An event's `username`, `passwd`, or `token` extension is no longer an
implicit way to override outbound authentication. For replies, business
extensions explicitly supplied by the handler win over the request's business
extensions; missing ones may be inherited. This rule does not transfer the
request's standard CloudEvent attributes onto a new reply.

`with_credentials` replaces the entire credential value; it does not merge with
gRPC defaults. For token-based authentication on the stock Runtime, also supply
non-empty basic fields, for example
`Credentials::new().with_basic(user, password).with_token(token)`. A token alone
does not satisfy the Runtime's unconditional username/password validation.

## Close and observe failures explicitly

- Consumers: `shutdown()` signals local shutdown; `join().await` waits and
  reports failures. Cancelling a `join()` wait retains tasks and pending results
  for the next wait.
- HTTP: prefer `close().await` when remote registrations should be removed.
  It serializes heartbeat/registration changes, drains a submitted heartbeat
  before final unsubscribe, and attempts cleanup of every tracked URL. Failed
  unregisters remain tracked so another `close()` can retry. Transport timeouts
  can still leave remote acceptance uncertain, as with any timed-out RPC.
- gRPC webhook registration: explicitly `unsubscribe(...).await` before
  `shutdown()` and `join()`. There is no automatic remote cleanup.
- TCP producers: `shutdown().await` stops socket and push-handler work;
  `join().await` observes terminal transport/handler errors even after shutdown.
  Explicit producer shutdown cancels an in-flight push handler. A successful
  reconnect recovers the role without surfacing a terminal error.

HTTP `close()` does not reproduce Java's local-only `close()`. If local-only
shutdown is intended, use `shutdown()` followed by `join()`. Cancelling managed
HTTP consumer startup stops local tasks but does not undo a subscription already
accepted remotely. After shutdown, further subscription changes are rejected.

Rust operations return structured `Error` values. Handle timeouts, server
rejections, and channel closure explicitly instead of porting Java call sites
that rely on logging, `void`, or a `null` result for failure.

## Protocol constraints that still apply

- TCP unsubscribe is session-wide in the Runtime. Rust exposes
  `unsubscribe_all()`; passing a subset cannot keep the other topics active.
  Calling it with no tracked subscriptions returns `Error::InvalidArgument`.
- gRPC stream subscription rejection is still not reliably propagated:
  `open()` or `subscribe()` success does not prove Runtime acceptance.
- A gRPC stream unsubscribe can close the shared emitter for every topic.
  There is no automatic stream recreation. Open a new consumer for remaining
  topics after the old consumer finishes.
- TCP broadcast waits for the local socket write, not a Runtime acknowledgement.
  Failure recovery and missed ACKs do not provide exactly-once processing.

See the [transport guide](README.md#transport-guide),
[gRPC limitations](ARCHITECTURE.md#grpc-stream-subscription-limitations), and
[runnable examples](examples/README.md) for the remaining lifecycle details.
