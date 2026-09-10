# Publish & Subscribe

**Audience:** application developers publishing or subscribing via the HTTP
API or the Java SDK. Covers topics, the event format, distribution modes,
filtering, and batching. For the endpoint-by-endpoint reference see
[HTTP API](../reference/http-api.md); for delivery guarantees see
[Reliable delivery](delivery-reliability.md).

---

## The event format

Events are [CloudEvents 1.0](https://cloudevents.io) JSON. The runtime
accepts the structured content type on every publish endpoint:

```json
{
  "specversion": "1.0",
  "id": "89010a5a-3c6f-4a1e-9b2d-0f7c1f2e3a4b",
  "source": "/example/producer",
  "type": "com.example.order.created",
  "datacontenttype": "application/json",
  "data": {"orderId": 42, "amount": 99.5}
}
```

A `202 Accepted` means the event is durably in the backend WAL. The broker is
a pure write-ahead log — **all subscription semantics live in the EventMesh
runtime**, not the broker.

Internally the runtime converts every event into a single frame type
(`EventMeshFrame`, `eventmesh-common/.../wire/`) that flows through the
whole pipeline; CloudEvents is the recommended *external* format, and the
legacy MeshMessage / OpenMessaging formats are adapted onto the same frame.

## Publishing

Raw HTTP:

```shell
curl -X POST "http://localhost:8080/events/publish?topic=orders" \
  -H "Content-Type: application/cloudevents+json" \
  -d '{ ... event above ... }'
```

SDK:

```java
client.publish("orders", CloudEventBuilder.v1()
    .withId(UUID.randomUUID().toString())
    .withSource(URI.create("/order-svc"))
    .withType("com.example.order.created")
    .withData("application/json", "{\"orderId\":42}".getBytes(UTF_8))
    .build());
```

Batch publish (`POST /events/publish-batch`) sends a JSON array of events in
one HTTP round-trip — the SDK exposes it as
`client.publish(topic, List<CloudEvent>)`.

Payloads above the frame limit are rejected with `413` up front (no
auto-sharding — route large payloads through external storage and event the
reference).

## Subscribing

A subscription binds a **clientId** to a **topic** with a **distribution
mode**. There are **no consumer groups** — EventMesh tracks offsets per
(topic, clientId) itself.

```shell
curl -X POST http://localhost:8080/events/subscribe \
  -H "Content-Type: application/json" \
  -d '{"clientId":"order-svc","topic":"orders","mode":"LOAD_BALANCE"}'
# → {"subscriptionId":"...", "instanceUrl":"http://10.0.0.5:8080"}
```

The response's `instanceUrl` is the instance the subscriber should pin
subsequent polls to (load balancing); it is empty when no advertised address
is configured.

### Distribution modes

| Mode | Semantics | When to use |
| --- | --- | --- |
| `LOAD_BALANCE` | Each message goes to exactly **one** subscriber, round-robin | Queue-like work distribution |
| `BROADCAST` | **Every** subscriber receives every event | Cache invalidation, config fan-out |
| `MULTICAST` | Per-subscriber `CloudEventFilter` predicates decide delivery | Selective interest within one clientId group |

(Mode constants: `org.apache.eventmesh.runtime.subscription.DistributionMode`.)

### Filtering

A subscriber can attach a filter at subscribe time so the runtime only
buffers matching events (server-side, evaluated on the frame). In the SDK
this is `subscribeWithAck(topic, mode, predicate)` — the same predicate
mechanism that decides ACK on the long-poll transport. See
[Java client guide §4–5](../reference/client-java.md) for patterns.

## Receiving

| Transport | How | Trade-off |
| --- | --- | --- |
| **Long-poll** | `GET /events/poll?clientId=…&timeoutMs=…` | Simplest; NAT-friendly; per-pull batches |
| **SSE** | `GET /events/stream?clientId=…` | Server push, one-way; browser friendly |
| **WebSocket** | dedicated port (`-Deventmesh.ws.port`) | Bi-directional, lowest push latency |

All three deliver the same CloudEvent payloads; the SDK's `subscribe` /
`subscribeSse` / `subscribeWs` wrap them with identical handler semantics.
Details and code: [Streaming & push transports](streaming.md).

## Unsubscribing

`POST /events/unsubscribe` with `{clientId, topic?}` — topic present removes
that one subscription; absent removes **all** subscriptions of the client.
The SDK mirrors this as `unsubscribe(topic)` / `unsubscribe()`.

## Request-reply (RPC shape)

Synchronous request/reply rides the same pipe:
`POST /events/request` (blocking, returns the reply event or 408) and
`POST /events/reply` (correlates on the `emcorrelationid` extension). SDK:
`client.request(topic, event, timeoutMs)` / `client.reply(correlationId, event)`.
Late replies are dropped, so at-most-once per request — do not use it where
at-least-once is required.

## Where the code lives

| Piece | Location |
| --- | --- |
| HTTP entry | `eventmesh-runtime/.../http/UniHttpServer.java` |
| Ingress orchestrator | `eventmesh-runtime/.../ingress/UniIngressService.java` |
| Subscription manager | `eventmesh-runtime/.../subscription/SubscriptionManager.java` |
| Distribution modes / filter | `eventmesh-runtime/.../subscription/DistributionMode.java`, `CloudEventFilter.java` |
| Internal frame | `eventmesh-common/.../wire/EventMeshFrame.java` |
| SDK | `eventmesh-sdks/eventmesh-sdk-java/.../cloudevents/CloudEventsClient.java` |

## Configuration highlights

Keys live in `eventmesh-runtime/conf/eventmesh.properties`
(`-D` system properties override). The ones that shape pub/sub behavior:

| Key | Default | Effect |
| --- | --- | --- |
| `eventmesh.http.port` | `8080` | Traffic endpoints |
| `eventmesh.ws.port` | `-1` (off) | WebSocket push transport |
| `eventmesh.delivery.topology` | `LOCAL_STICKY_PULL` | Single- vs multi-instance polling (see [Control plane](../architecture/control-plane.md)) |

Full reference: [Configuration](../quickstart/configuration.md).
