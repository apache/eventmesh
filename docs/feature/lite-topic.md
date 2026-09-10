# Lite Topic (RocketMQ 5.x)

**Audience:** RocketMQ 5.x users who need lightweight, hierarchical
messaging — many logical sub-topics inside one physical parent topic, with
server-managed offsets and no per-lite-queue consumer plumbing.

---

## What it is

Lite Topic is RocketMQ 5.5+'s hierarchical message container (RIP-83). A
Lite Topic lives inside a normal parent topic declared as `LITE` type;
individual **lite queues** inside it share the parent's storage, so creating
thousands of logical topics costs almost nothing.

EventMesh exposes Lite Topic over the same HTTP surface as regular topics:

| Operation | Endpoint | Notes |
| --- | --- | --- |
| Create (idempotent) | `POST /events/lite/create` | Parent must be LITE type |
| Publish | `POST /events/lite/publish` / `publish-bytes` | To a lite queue inside the parent |
| Poll | `GET /events/lite/poll` / `poll-bytes` | Batch poll like regular long-poll |

The `-bytes` variants carry raw binary payloads without JSON encoding.

## Semantics — and how they differ from regular topics

Lite Topic trades the runtime's full reliability machinery for RocketMQ-native
simplicity:

| Property | Regular topics | Lite Topic |
| --- | --- | --- |
| Offset ownership | EventMesh runtime (ACK-driven) | **Storage plugin** (managed inside the plugin) |
| ACK / DLQ | Explicit ACK, retries, DLQ | None — consumption is poll-and-forget |
| Ordering | Per partition (LOAD_BALANCE_STICKY per key) | Per lite queue |
| Backend | Kafka / RocketMQ 4.x / 5.x | **RocketMQ 5.x only** (`LiteTopicCapable`) |

Use Lite Topic for high-fanout, low-ceremony channels (presence, metrics,
chat rooms); use regular topics where unacked-redelivery and dead-lettering
matter.

## Checkpointing and crash replay

The storage plugin periodically persists each lite queue's pull offset to
disk (default every 5 s):

```properties
eventmesh.rocketmq5.lite.checkpoint.interval.ms=5000
```

- A JVM crash replays **at most one interval's worth** of messages.
- Shutdown always persists regardless of the interval.
- `<= 0` disables periodic checkpointing (persist on shutdown only).

## SDK

```java
client.createLiteTopic("chat", "room-42");           // idempotent
client.publishLite("chat", "room-42", event);
client.subscribeLite("chat", "room-42", event -> { ... }); // background poll loop
client.unsubscribeLite("chat", "room-42");
```

`subscribeLite` runs a background poll loop with plugin-managed offsets —
no ACK calls, no DLQ.

## Where the code lives

| Piece | Location |
| --- | --- |
| SPI capability marker | `eventmesh-storage-plugin/eventmesh-storage-api/.../storage/LiteTopicCapable.java` |
| RocketMQ 5.x implementation | `eventmesh-storage-plugin/eventmesh-storage-rocketmq5/` |
| HTTP endpoints | `eventmesh-runtime/.../http/UniHttpServer.java` (`liteCreate`, `litePublish`, `litePoll`) |
| SDK | `eventmesh-sdks/.../cloudevents/CloudEventsClient.java` (`*Lite` methods) |
| Tests | `RocketMQ5LiteHttpIntegrationTest`, `MeshStoragePluginTCK` (LiteTopic contract) |
