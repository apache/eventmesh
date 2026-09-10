# EventMesh Architecture Simplification Redesign

> **Status note:** this is the historical design document for the
> architecture rewrite (a design record, not a living guide). The current
> implementation status and maturity of each capability are governed by the
> [capability status table](../../README.md#capability-status) in the main
> README. For the user-facing views see
> [Architecture overview](overview.md) and the
> [feature guides](../feature/).

> Branch: `refactor/unified-runtime-pipeline` — based on the direction
> defined in this document.
>
> **Core change:** *drop the MQ's Producer Group / Consumer Group semantics
> entirely — the MQ is used purely as a storage layer, and EventMesh
> maintains its own subscription and dispatch semantics. Internally
> everything is `EventMeshFrame` (CloudEvent is demoted to one of the
> external ingress formats); externally the multi-protocol surface
> (CloudEvents / MeshMessage / A2A) converts directly to Frame via the
> `FrameAdaptor` SPI, never through each other. The SDK is simplified to
> HTTP-only.*
>
> **v2.0 architecture deepening (2026-08-13, absorbed from
> `eventmesh-architecture-refinement.md`):**
>
> - `EventMeshFrame` end-to-end internally (storage SPI + dispatch pipeline
>   + carriers all Frame-typed).
> - External protocol conversion moved into the `FrameAdaptor` SPI
>   (CloudEvents / MeshMessage / A2A each an independent plugin, zero
>   CE-intermediary conversions).
> - Offset path-② (purely local, no meta reporting, no group.id; takeover
>   via MQ replay).
> - Full-stickiness load balancing (session-assignment-layer `recommend`,
>   self-reported instance load, no forwarding).

---

## Executive summary

The pre-rewrite architecture inherited Kafka/RocketMQ's Producer Group /
Consumer Group concepts, which caused:

- **SDK complexity** — clients had to understand Group, Topic, Tag and
  other MQ semantics; a steep learning curve.
- **Semantic confusion** — EventMesh's own subscription model
  (load-balanced / broadcast / multicast) was entangled with the MQ Group
  concept.
- **Multi-protocol burden** — TCP + HTTP + gRPC SDKs maintained in
  parallel; protocol adaptation code bloated.

**Redesign direction: EventMesh becomes a pure "CloudEvents over MQ" event
bus. The MQ is EventMesh's storage backend; EventMesh manages its own
subscription and dispatch logic; clients only need HTTP + CloudEvents.**

---

## 1. Core design principles

### 1.1 The iron rules

| Rule | Meaning |
|------|---------|
| **MQ without semantics** | Kafka/RocketMQ are used only as "a durable FIFO queue"; no Producer Group / Consumer Group / Tag concept is ever exposed to clients |
| **EventMesh-owned subscriptions** | All dispatch logic (who receives which message, under which policy) is maintained by EventMesh itself, never delegated to the MQ |
| **EventMesh-owned offsets** | EventMesh manages the consumption position of every subscription (topic#clientId#partition → offset), modeled on the RocketMQ client OffsetStore, persisted in RocksDB — independent of MQ Consumer Group offsets |
| **Minimal SDK** | One SDK (HTTP), two objects (CloudEvent + Subscription), three APIs (publish / subscribe / unsubscribe) |
| **EventMeshFrame internally** | Everything internal is `EventMeshFrame` (fixed header + KV attributes + raw data); CloudEvent is demoted to one external ingress format; external protocols (CloudEvents/MeshMessage/A2A) each convert directly to Frame via the `FrameAdaptor` SPI, never through each other |
| **Independently deployed connectors** | The Connector Runtime is a separate process talking to the EventMesh Runtime over HTTP + CloudEvents — no shared internals, independent lifecycles and OffsetStores |

> **Implementation status snapshot (v2.0 / 2026-08-13 audit):** of the iron
> rules, "EventMeshFrame internally / independent connectors / minimal
> HTTP-only SDK" have landed; "EventMesh-owned subscriptions / offsets" hold
> for single-instance (offset path-② purely local + MQ-replay takeover,
> meta reporting off by default). Multi-instance coordination moved to
> **full stickiness** (session-assignment-layer recommend, forwarding layer
> disabled). External protocols go through the `FrameAdaptor` SPI
> (CloudEvents as an independent plugin + MeshMessage + A2A each converting
> directly to Frame, zero CE-intermediary). See §19.

### 1.2 The essential difference from the previous design

```
                    previous design                        redesigned

  EventMesh SDK:
  ├─ TCP SDK       (Package protocol)          →   deleted entirely
  ├─ HTTP SDK      (EventMeshMessage)          →   simplified to CloudEvents-only
  └─ gRPC SDK      (proto CloudEvent)          →   deleted entirely

  MQ role:
  ├─ Group semantics exposed to clients  ───→   pure storage, no semantics
  ├─ MQ-side load balancing              ───→   EventMesh SubscriptionManager
  └─ Tag filtering (subExpression)       ───→   EventMesh MULTICAST CloudEvent filters
```

---

## 2. Target architecture overview

```
┌─────────────────────────────────────────────────────────────────┐
│                       EventMesh Client                           │
│                  (HTTP SDK, CloudEvents-only)                    │
│   cloudEventsClient.publish(CloudEvent)                          │
│   cloudEventsClient.subscribe(topic, handler)                    │
│   cloudEventsClient.unsubscribe(topic)                           │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP POST / GET (application/cloudevents+json)
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      EventMesh Runtime                           │
│                    (single process, unified)                     │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ HTTP endpoint (the only transport layer)                   │ │
│  │  POST /events/publish   → Ingress pipeline                 │ │
│  │  POST /events/subscribe → SubscriptionManager              │ │
│  │  POST /events/unsubscribe                                  │ │
│  │  GET  /events/poll      → PushService delivery             │ │
│  └──────────────────────────────┬─────────────────────────────┘ │
│                                 ▼                                │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Ingress pipeline                                           │ │
│  │  AuthFilter → RateLimitFilter → AclFilter                  │ │
│  │  → ProtocolFilter → TransformerEngine → RouterEngine       │ │
│  └──────────────────────────────┬─────────────────────────────┘ │
│                                 ▼  EventMeshFrame               │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Storage plugin (the MQ as a storage layer)                 │ │
│  │  · single Producer, EventMesh-managed                      │ │
│  │  · writes by topic partition                               │ │
│  │  · no Producer Group / clientId / producerGroup config     │ │
│  │  · single Consumer — pulls everything, dispatch per        │ │
│  │    EventMesh subscription rules, no Consumer Group         │ │
│  └──────────────────────────────┬─────────────────────────────┘ │
│                                 ▼                                │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ SubscriptionManager (the core)                             │ │
│  │  manages every client subscription; dispatches by policy:  │ │
│  │  · LOAD_BALANCE (round-robin / least-connections)          │ │
│  │  · BROADCAST (every subscriber)                            │ │
│  │  · MULTICAST (subject/type/header match)                   │ │
│  │  · own OffsetStore (RocksDB)                               │ │
│  └──────────────────────────────┬─────────────────────────────┘ │
│                                 ▼                                │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ PushService (long-poll / SSE / WebSocket transports)       │ │
│  └────────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │ Kafka / RocketMQ (pure storage)
                             ▼
                    ┌──────────────────────┐
                    │   Kafka / RocketMQ   │
                    │  (a distributed WAL) │
                    │  · no Producer Group │
                    │  · no Consumer Group │
                    │  · no Tag filtering  │
                    │  · EventMesh is the  │
                    │    only producer +   │
                    │    consumer          │
                    └──────────────────────┘
```

---

## 3. Storage plugin redesign: the MQ as stateless storage

**The problem being removed.** The old storage plugin exposed MQ semantics:

```java
// eventmesh-storage-rocketmq (old)
MeshMQProducer.java:
  producerGroup: String        // RocketMQ ProducerGroup — clients had to understand it
  createTransactionProducer()  // transactional messages EventMesh had to inherit

MeshMQConsumer.java:
  consumerGroup: String        // RocketMQ ConsumerGroup — the core problem
  subscribe(topic, subExpression)  // subExpression = RocketMQ Tag filtering
  push() / pull()              // two consumption modes mixed
```

Configuring `consumerGroup` through the EventMesh SDK means the client is
still using RocketMQ's Consumer Group semantics — EventMesh was not really
providing its own subscription/dispatch model.

**The target interface** (landed as `MeshStoragePlugin`): only two core
concepts — `send(frame)` and offset-scoped `poll`, plus
`assignPartitions` / `commitOffset` — with no producerGroup, consumerGroup,
or tag parameters. *(Status note: the Kafka implementation initially kept
an internal `group.id` (gap G7); assignPartitions/commitOffset landed for
Kafka first and were partially stubbed for RocketMQ.)*

---

## 5. SDK minimization: the HTTP-family CloudEvents SDK

The old SDK family (TCP / HTTP / gRPC) had three object models and API
styles in parallel. The redesign collapsed them into **one HTTP SDK** with
`CloudEventsClient` exposing publish / subscribe / unsubscribe (plus the
later additions: request-reply, streaming, lite topics — see the
[client guide](../feature/client-java.md)).

---

## 6. Runtime ingress redesign: the unified IngressHandler

All entry points (HTTP publish, A2A, legacy bridge) converge on one
frame-typed ingress path — today's `UniIngressService` — so filters,
quota, and rate limiting have exactly one place to live.

---

## 7. Runtime egress redesign: PushService replaces the Consumer family

The old consumer model (TCP Consumer with session maps, HTTP PushConsumer
with LRU caches, gRPC push) was replaced by a single `PushService` with
pluggable transports (long-poll / SSE / WebSocket) fed by the
`SubscriptionManager` — all frame-typed, all sharing ACK / retry / quota.

---

## 8. Connector Runtime: fully independent

The Connector Runtime and EventMesh Runtime are **two completely
independent processes** communicating over HTTP + CloudEvents, sharing no
internal components:

```
┌─────────────────────────────┐   HTTP / CloudEvents   ┌──────────────────────────────┐
│      Connector Runtime      │ ←────────────────────→ │       EventMesh Runtime      │
│  Source/Sink manager        │  POST /events/publish  │  ingress → storage (MQ)      │
│  · connector lifecycle      │                        │  SubscriptionManager         │
│  · external poll/write      │  GET /events/poll      │   · own offsets (RocksDB)    │
│  · independent offsets      │ ←───────────────────── │   · dispatch (LB/Bcast/Mcast)│
└─────────────────────────────┘                        └──────────────────────────────┘
```

Source connectors `poll()` external systems into CloudEvents; sink
connectors `put(CloudEvent)` out; each maintains its own offsets.

---

## 9–11. Entry-point unification, deletions, phased plan

A single launcher (`EventMeshApplication`) starts traffic HTTP + admin (+
opt-in WebSocket). The deletion list removed the TCP/gRPC server paths,
`eventmesh-registry`, and dead modules (~59% of the tree). The phased plan
tracked the migration; the evidence trail lives in
[architecture review evidence](review/evidence.md).

---

## 12. Key design decision discussions

- **MQ without semantics vs keeping MQ capabilities** — the user's explicit
  requirement: the MQ only stores. Partition load-balancing semantics →
  replaced by the EventMesh SubscriptionManager; Tag filtering → replaced
  by MULTICAST-mode CloudEvent filters; the MQ's durable WAL and offset
  storage are kept (EventMesh replay builds on them).
- **Long-polling vs WebSocket / SSE** — the first cut chose long-polling;
  decision §15.6 superseded it with **WebSocket default + SSE one-way
  streaming + long-polling fallback**, all three user-selectable.

---

## 15. User decision log (v1.2)

Foundational decisions, confirmed 2026-07-02. Later design and
implementation follow this section; changes must be appended as new
decisions noting what they supersede.

### 15.1 MQ semantic boundary: fully self-managed coordination

Strict adherence to the "MQ without semantics" rule — EventMesh implements
multi-instance consumption coordination **entirely itself**: a
self-written partition-assignment protocol + leases + centralized offset
storage + cluster-wide subscription sync. No compromise that reuses MQ
Consumer Group rebalancing internally. Highest development cost; strongest
autonomy.

*(v2.0 note: multi-instance coordination was later simplified to the
full-stickiness model — see [Load balancing](../feature/load-balancing.md)
— with partition ownership retained behind `PARTITION_OWNED_PULL`.)*

### 15.2 Delivery semantics: at-least-once + client idempotency

Client-facing delivery is **at-least-once**, with clients deduplicating on
the CloudEvents `id`. Exactly-once is *not* implemented for the delivery
side. ACK + retry + DLQ + STICKY ordering + client dedup all hold.
Exactly-once remains confined to connector offset management (see §16).

### 15.3 SDK deletion scope: remove TCP + gRPC entirely, HTTP only

Keep a single HTTP-only CloudEvents SDK; **delete** the TCP and gRPC SDKs.
This is an **irreversible action** — all existing TCP/gRPC clients must
migrate to the HTTP SDK. Low-latency needs are covered by the optional
WebSocket/SSE transports. *(The shipped runtime keeps the legacy TCP path
as a compatibility bridge; see [Protocols & SDKs](../feature/protocols.md).)*

### 15.4 Landing baseline

The code baseline is the then-current workspace (which had already
absorbed meta / openconnect / A2A / CloudEvents / retry modules), not a
clean rewrite on Apache develop. Customizations (the masa submodules:
connector-wemq, trace-*, registry-*, security-acl) are plugin-shaped and
were slated for rewrite against the new SPIs.

### 15.5 Global control plane: the Meta registry (not the Admin Server)

The global control plane belongs to the **Meta registry** (instance
heartbeats, partition assignments, clientId routing, cluster subscription
view, offset replicas, dynamic rules); the Admin Server only manages
(Jobs, DLQ browse/replay, ops commands, metric aggregation) and never
performs strongly-consistent coordination. Degradation: Meta down →
instances self-assign; Admin down → ops impaired, Runtime fine. The dual
registry abstraction (`eventmesh-registry`) was dropped in favor of the
single Meta path.

### 15.6 Transport: the HTTP family, WebSocket default

- **WebSocket** (default): persistent bidirectional subscription streams,
  high throughput, control commands on the same connection.
- **SSE**: one-way streaming output (LLM token streams, A2A streamed
  replies); traverses firewalls well.
- **Long-polling**: the fallback when WS is blocked.
- **No gRPC**: WebSocket delivers the same low latency + throughput while
  staying in the HTTP family (no proto files, no separate SDK).

### 15.7 request-reply added

A synchronous `request()` API over HTTP request-response (suspended
request, correlationId matching, Meta routing for cross-instance replies).
**Timeout = failure; late replies are dropped by default** (clear
semantics, no DLQ). request-reply is RPC semantics — no re-delivery, no
DLQ — deliberately isolated from the at-least-once pub/sub path.

### 15.8 Storage: S3Stream as an additional backend + Java 21

The implementation targets **Java 21** (virtual threads for suspended
push channels — near-zero cost per parked connection). Storage keeps
multiple backends with **S3Stream added** alongside Kafka/RocketMQ
(integration posture A: a minimal `S3StreamStoragePlugin`; EventMesh still
fully self-coordinates partitions; S3Stream is a data WAL only).
RocksDB's clarified position: the **local full copy + Meta write-offload +
degraded fallback** for offsets — not redundant with the Meta tier.

---

## 16. Delivery semantics boundary: at-least-once vs exactly-once

Two different layers, often confused:

```
Layer A: connector-internal progress (offset persistence)
  Scope: Source pull offset / Sink write-confirm offset
  Where: inside the Connector Runtime; no external clients involved
  Semantics: exactly-once (local RocksDB + remote Admin double-write)
  Purpose: a connector restart neither loses nor double-processes external data

Layer B: client-facing delivery (subscribers receiving messages)
  Scope: EventMesh → SDK subscriber (post-publish delivery)
  Where: SubscriptionManager → PushService → client poll/ACK
  Semantics: at-least-once + client idempotency (decision 15.2)
  Purpose: no message loss; duplicates possible, deduped by id at the client
```

Exactly-once is *feasible* for layer A (self-contained offset state) and
*prohibitively expensive* for layer B (transactional delivery + a global
dedup store) — hence the split.

---

## 17. request-reply (v1.3)

```
requester: POST /events/request
           body: CloudEvent { ..., x-em-reply-to: "reply.<reqId>", x-em-correlation-id: reqId }
           (the HTTP request suspends, blocking for a reply or timeout)
               ↓ EventMesh routes like a publish (ingress → storage.send)
responder: subscribed to the request topic, processes, then
           POST /events/reply
           body: CloudEvent { x-em-correlation-id: reqId, data: reply }
               ↓ EventMesh matches correlationId to the suspended request
requester: the suspended request returns the reply ← unblocked
           timeout → the request fails (same semantics as the old TCP sync call)
```

Key points: a correlationId→AsyncContext matching table with timeout
sweep; cross-instance replies route via the Meta routing table; late
replies are dropped; never re-delivered and never dead-lettered (RPC side
effects must not repeat). *(Gap G10: self-addressed cross-instance reply
routing was later covered by the sticky-instance model.)*

---

## 18–21. Test design, streaming, and the SDK streaming API

The end-to-end test-case design (§18) mapped every guarantee above to a
named test; the current test inventory and per-PR evidence live in
[review evidence](review/evidence.md).

The streaming design (§20–21, integrated from the streaming-session,
sdk-streaming-call, and lite-streaming-call designs) defines the two
orthogonal streaming modes — **Mode 1** (client → agent streaming call;
the runtime mediates, channels multiplex with client affinity; sessionId
format `<agentId>:<uuid>` routes with zero lookups on the `:` prefix) and
**Mode 2** (session pub/sub with deterministic lite naming, no agent, no
matchmaking) — both carried by `EventMeshFrame` STREAM_REQ /
STREAM_CHUNK messages. The SDK surface is `client.streaming()`:
`openSession → call → forEach` and `subscribeSession` /
`openSessionPublisher`. See
[Streaming & push transports](../feature/streaming.md) for the current
user-facing guide.

---

## Appendices (historical)

Appendices A–F of the original document carried the configuration key
table, the full CloudEvents extension field set, deployment topology
examples, the develop-code migration mapping, the customization (masa)
migration inventory, and the v1.11 implementation-gap analysis. The
still-current content from those appendices lives in the feature and
reference pages of this documentation tree (configuration, protocols,
offset management, load balancing, frame protocol); the point-in-time
gap/migration tables are superseded by the
[architecture review evidence](review/evidence.md) and are intentionally
not reproduced.

*Document version: v2.0 (English rewrite of the Chinese original) ·
integrated 2026-08-13 · related: [offset management](../feature/offset-management.md),
[load balancing](../feature/load-balancing.md),
[frame protocol](../feature/frame-protocol.md).*
