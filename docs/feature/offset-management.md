# Offset Management

**Audience:** operators and contributors who need to know exactly how
EventMesh tracks consumption progress — the two offset layers, per-backend
mechanics, the deferred-ACK protocol, and takeover behavior. Part of the
offset / load-balancing / frame design trio; see also
[Load balancing](load-balancing.md) and
[Frame protocol conversion](frame-protocol.md).

---

## Architectural position

**EventMesh fully self-manages offsets: no `group.id`, no reporting to meta.**
The MQ (RocketMQ / Kafka) is used only as a durable FIFO log; its Consumer
Group semantics are never used. The current implementation status of each
capability is governed by the
[capability status table](../../README.md#capability-status).

## The two offset layers

| Layer | Meaning | Keyed as | Storage | On takeover |
| --- | --- | --- | --- | --- |
| **pull offset** (how far we pulled from the MQ) | `parent#lite@queue` (lite) / `topic#partition` (regular) | storage plugin-local (rocketmq5 = properties file, 5 s atomic checkpoint) | instance dies → new instance replays from head |
| **deliver/ack offset** (delivery + ACK progress) | `topic#clientId#partition` | `RocksDBOffsetStore` (local) | instance dies → new instance reads `-1` → pulls from head → in-flight re-delivered |

The two layers are **deliberately not merged** (different semantics,
decoupled): the pull layer lives inside the storage plugin (self-managed
cursor), the deliver/ack layer in the runtime's `OffsetStore`.
`pullAndDispatchPartition` passes `startOffset=-1` to `storage.poll` (the
plugin manages its own cursor) — the OffsetStore is not on the takeover
path.

## The normal path

```
storage.poll()
  → pull offset self-managed (no commitOffset submitted to the broker)
  → pullAndDispatchPartition (startOffset=-1, plugin-managed cursor)
  → ReliableDispatcher.deliver()
    → Delivery (carries event + mqAckCallback)
    → PushChannel.deliver() → delivered to the client
  → client ACK → ReliableDispatcher.ack()
    → offsetStore.writeOffset() (CAS max — offsets only move forward)
    → storage.ackPulledMessage(topic, popCk)   (RocketMQ 5.x: only now ACK the broker)
```

## Takeover (instance death)

```
instance dies
  → client reconnects to a new instance via /session/recommend (see load-balancing.md)
  → the new instance re-pulls from the business topic
      · lite:  pollLite replays from the lite head (single cursor, naturally replayable)
      · regular: poll starts from the earliest un-ACKed / head
  → in-flight un-ACKed messages → at-least-once re-delivery (business idempotency covers it)
  → no snapshots written, no offset state migration, no meta reporting
```

## Per-backend differences

| Backend | Pull mechanism | ACK mechanism | Restart recovery |
| --- | --- | --- | --- |
| **RocketMQ 5.x** | `POP_MESSAGE` (broker-managed assignment; `partitionCount=-1` → poll-all) | **Deferred ACK**: `poll()` does not immediately ACK the broker; `ackPulledMessage()` fires the broker ACK only after the client ACKs. Crashes lose nothing — the broker's 30 s invisibleTime expires and it re-delivers automatically. | pull offset resumed from the local properties file (5 s checkpoint) |
| **RocketMQ 4.x** | `PULL_MESSAGE` (self-managed cursor, fixed CONSUMER_GROUP, no commitOffset submitted) | No MQ ACK (PULL mode) | pull offset resumed from the local properties file |
| **Kafka** | `assign` + `seek` (no group.id, `ENABLE_AUTO_COMMIT=false`) | No MQ ACK | pull offset resumed from the local properties file |

## Deferred ACK in detail (RocketMQ 5.x, the P2 fix)

**The problem:** originally `poll()` immediately called
`ackNormal(brokerAddr, msg)` → the broker marked the message consumed → an
EventMesh crash lost the message (at-most-once).

**The fixed chain:**

```
① poll()
   → POP_MESSAGE fetches messages
   → does NOT ACK the broker now
   → stores a deferred ACK callback (key = PROPERTY_POP_CK)
   → the EventMeshFrame attribute "empopck" carries the POP check key

② pullAndDispatchPartition()
   → reads frame.attributes().get("empopck")
   → builds mqAck = () -> storage.ackPulledMessage(topic, popCk)
   → dispatcher.deliver(topic, partition, offset, frame, clientId, channel, mqAck)
   → the Delivery stores mqAckCallback

③ client ACK → ReliableDispatcher.ack(deliveryId)
   → offsetStore.writeOffset() (offset advances)
   → delivery.getMqAckCallback().run() → storage.ackPulledMessage(topic, popCk)
   → pendingPopAcks.remove(popCk) → executes the broker ACK_MESSAGE
   → the broker confirms consumption
```

**The at-least-once guarantee:** if EventMesh crashes between ① and ③, the
broker's invisibleTime (30 s) expires → automatic re-delivery → a new
instance pulls and processes again.

## Monotonic offset writes (the P4 fix)

**The problem:** `writeOffset` used a plain overwrite (`set(offset))` —
after a restart, a fast consumer group's offset could be dragged down by a
slow group's replayed messages.

**The fix:**

- `InMemoryOffsetStore`: `AtomicLong.accumulateAndGet(offset, Math::max)` —
  CAS-max.
- `RocksDBOffsetStore`: read current → skip if `offset <= current` → else
  put — a conditional write.

## Retired designs

- **MetaBackedOffsetStore** (reporting a million keys to meta every second)
  is off by default — opt-in via `-Deventmesh.offset.meta=true`; the class
  is kept in reserve.
- No `group.id`, ever — the EventMesh-owned-offset rule stands.

## Open item

- **RocketMQ 4.x ConsumeQueue cleanup** — `commitOffset` remains a no-op.
  Verified in practice as a false alarm for storage growth (the CommitLog is
  time-GC'd via `fileReservedTime` and ConsumeQueues are trimmed in
  lockstep), but worth re-checking per deployment.

## Where the code lives

| Piece | Location |
| --- | --- |
| Offset stores | `eventmesh-runtime/.../offset/RocksDBOffsetStore.java`, `InMemoryOffsetStore.java` |
| Deferred POP ACK | `eventmesh-storage-plugin/eventmesh-storage-rocketmq5/` (`pendingPopAcks`, `ackPulledMessage`) |
| ACK-driven advance | `eventmesh-runtime/.../delivery/ReliableDispatcher.java` (`ack()`) |
| Boot alignment | `UniRuntime.alignPullOffsetsToAck` |
| Tests | `OffsetMonotonicAndRecoveryTest`, `DeferredAckDispatcherTest`, `AckTimeoutRedeliveryIntegrationTest` |
