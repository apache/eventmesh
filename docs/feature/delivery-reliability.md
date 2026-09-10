# Reliable Delivery

**Audience:** application developers and operators who need to know exactly
what delivery guarantees EventMesh provides — ACK tracking, retries, the
dead-letter queue, and what happens across crashes and restarts.

---

## The contract: at-least-once

Every delivery is tracked by the runtime until the subscriber acknowledges
it:

1. The runtime dispatches an event to a subscriber and records an
   **in-flight delivery** (persisted in a `DeliveryStateStore`, RocksDB by
   default).
2. The subscriber processes it, then **ACKs** (`POST /events/ack` with the
   `deliveryId`, or auto-ACK by returning from an SDK handler).
3. The ACK advances the subscriber's offset — **the only thing that ever
   advances it**.
4. No ACK within the timeout → the runtime **retries** with exponential
   backoff (+ jitter); after the attempt budget the event goes to the
   **dead-letter queue (DLQ)**.

Because an unacknowledged event is re-delivered, each event is delivered
*at least once*; subscribers must be idempotent (dedupe on event id, or use
`LOAD_BALANCE_STICKY`-style ordering per key and business-level idempotency
keys).

## ACK semantics

- `POST /events/ack` body `{"deliveryId": "..."}` → `200 {"status":"acked"}`
  or `404` for an unknown/already-acked id. (The Java SDK long-poll handler
  auto-ACKs on return; `subscribeWithAck(topic, mode, Predicate)` ACKs iff
  the predicate returns `true`.)
- The offset advances on ACK **exactly once**; double-ACK is a no-op.
- The backend broker is ACKed only after the subscriber ACKs
  (RocketMQ 5.x POP "deferred broker ACK"), so a crash between poll and ACK
  is covered by broker-side redelivery — not message loss.

## Retries and dead-letter

Retry policy per delivery (defaults in `ReliableDispatcher`):

- **Backoff**: exponential from 1 s, capped at 16 s, ±20 % jitter to spread
  retry storms (`DEFAULT_JITTER_RATIO`).
- **Attempt budget**: 6 attempts (initial + 5 retries) by default.
- **Terminal failure** → the event is written to the DLQ *ledger*
  (`DeadLetterStore`, meta-backed when clustered) and the delivery retired.

Operators inspect and replay the DLQ through the admin plane:
`GET /admin/dlq/browse?topic=…&max=…`, `POST /admin/dlq/replay?topic=…&max=…`
(see [Admin API](admin-api.md)).

## Crash recovery — what restarts guarantee

On boot the runtime replays the persisted in-flight records
(`UniRuntime.alignPullOffsetsToAck` + `ReliableDispatcher.recover()`):

- A record with a decodable event is **re-dispatched** to the subscriber's
  poll buffer with its attempt counter preserved — the client sees the event
  again (at-least-once), and the offset still only moves on a real ACK.
- A record that cannot be decoded (legacy/corrupt) is retired **without**
  advancing the offset; backend redelivery bounds it.
- Recovery is idempotent — records already live on this dispatcher are
  skipped, so calling it twice is safe.

The one thing recovery never does is *pretend the absent client ACKed*:
that would convert an unacknowledged delivery into acknowledged progress and
skip the event (the bug class fixed by issue #5379).

## Multi-instance fencing

When several instances run with a meta store, each topic partition is owned
by exactly one instance (`PARTITION_OWNED_PULL`). If ownership moves while a
delivery is in flight, the old owner's ACK is fenced: it raises
`StaleOwnerException`, writes nothing, and the broker redelivers to the new
owner. Details: [Control plane → delivery topology](control-plane.md#1-deliverytopology).

## Delivery failure matrix (per backend)

| Scenario | Behavior | Visible signal | Recovery |
| --- | --- | --- | --- |
| Local disk full (RocksDB offset/state write fails) | write returns `false` / throws | `offsetWriteFailures` counter; `pendingDeliveries` stops dropping | Free disk; deliveries stay in flight and retry |
| Meta unreachable during DLQ record | store throws; dispatcher keeps delivery in flight | WARN logs | Retry succeeds once Meta heals; records are idempotent |
| Push write failure (SSE/WS) | connection pump **nacks** the dispatcher | re-delivery counter | Event re-dispatched immediately |
| Process kill -9 | WAL + persisted delivery state survive | — | Boot recovery re-dispatches in-flight records |

Full per-store failure behavior:
[Control plane → state store failure matrix](control-plane.md#state-store-failure-matrix).

## Where the code lives

| Piece | Location |
| --- | --- |
| Dispatcher (ACK / retry / DLQ) | `eventmesh-runtime/.../delivery/ReliableDispatcher.java` |
| Delivery state (RocksDB) | `eventmesh-runtime/.../state/RocksDBDeliveryStateStore.java` |
| DLQ ledger (meta, clustered) | `eventmesh-runtime/.../state/MetaBackedDeadLetterStore.java` |
| Offset store | `eventmesh-runtime/.../offset/RocksDBOffsetStore.java` |
| Push transports (nack on failure) | `eventmesh-runtime/.../push/ConnectionPushPump.java` |
| Tests | `RecoveryRedispatchTest`, `DeliveryRecoveryTest`, `CrossStoreFaultInjectionTest`, `AckTimeoutRedeliveryIntegrationTest` |
