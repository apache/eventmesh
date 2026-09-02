# Apache EventMesh Client Guide

> **Audience:** application developers using the `eventmesh-sdk-java` `CloudEventsClient`
> and `A2AClient` to publish / subscribe / stream / dispatch A2A tasks.
>
> **Backend-agnostic.** This guide covers the full API surface and is the single
> source of truth for what the client SDK does. The capability status table in
> the project README is the source of truth for GA / Beta / Experimental / Legacy
> tags; backend-specific configuration is in
> [docs/eventmesh-configuration.md](eventmesh-configuration.md); architectural
> context is in [docs/eventmesh-architecture.md](eventmesh-architecture.md).

The new architecture ships with **one client SDK** that exposes two surface APIs:

* `CloudEventsClient` — HTTP + [CloudEvents 1.0](https://cloudevents.io) pub/sub,
  request-reply, SSE / WebSocket push, RocketMQ 5.x Lite Topic, LLM streaming.
  This is the **primary user path** and the recommended way to integrate with
  EventMesh.
* `A2AClient` — Agent-to-Agent task dispatch on top of the same Runtime
  ([docs/eventmesh-a2a-protocol.md](eventmesh-a2a-protocol.md)). Used by multi-agent systems that
  need a durable task lifecycle.

Both clients talk **only to the EventMesh Runtime** (HTTP). The underlying
storage backend — RocketMQ 4.x, RocketMQ 5.x, Kafka, or any other
`MeshStoragePlugin` implementation — is **completely transparent** to the client.
Switching backends is a server-side configuration change, not a client change.

> Legacy `EventMeshHttpClient` / `EventMeshTCPClient` are kept for protocol
> compatibility but are no longer extended. New integrations must use
> `CloudEventsClient` (or `A2AClient` for A2A workloads). See the
> [migration notes](#15-legacy-compatibility) at the end of this guide.

---

## Table of contents

1. [Quick orientation](#1-quick-orientation)
2. [`CloudEventsClient` API reference](#2-cloudeventsclient-api-reference)
3. [Builder and configuration](#3-builder-and-configuration)
4. [Publish / subscribe patterns](#4-publish--subscribe-patterns)
5. [Request-reply (synchronous RPC)](#5-request-reply-synchronous-rpc)
6. [Subscriber transports: long-poll / SSE / WebSocket](#6-subscriber-transports-long-poll--sse--websocket)
7. [RocketMQ 5.x Lite Topic](#7-rocketmq-5x-lite-topic)
8. [LLM streaming call (Mode 1 / Mode 2)](#8-llm-streaming-call-mode-1--mode-2)
9. [Security: tokens, signatures, the unified gate](#9-security-tokens-signatures-the-unified-gate)
10. [Reliability: ACK, retries, dead-letter, idempotency](#10-reliability-ack-retries-dead-letter-idempotency)
11. [`A2AClient` for agent workloads](#11-a2aclient-for-agent-workloads)
12. [Backend selection: RocketMQ 4.x / 5.x / Kafka](#12-backend-selection-rocketmq-4x--5x--kafka)
13. [End-to-end example](#13-end-to-end-example)
14. [Operational checklist](#14-operational-checklist)
15. [Legacy compatibility](#15-legacy-compatibility)
16. [Code locations](#16-code-locations)

---

## 1. Quick orientation

A typical client looks like this:

```java
CloudEventsClient client = CloudEventsClient.builder()
    .runtimeUrl("http://localhost:8080")          // EventMesh Runtime HTTP endpoint
    .clientId("order-svc")                         // unique per JVM
    .pollIntervalMs(500L)                          // long-poll cadence
    .build();

client.subscribe("orders", "BROADCAST", event -> {
    System.out.println("got " + event.getId() + " type=" + event.getType());
});

CloudEvent e = CloudEventsClient.event(
    "evt-1", "order-svc", "order.created", "{\"amt\":99}".getBytes(StandardCharsets.UTF_8));
client.publish("orders", e);
```

That's the entire model. The rest of this document covers edge cases and the
optional surfaces (A2A, streaming, security, Lite Topic).

---

## 2. `CloudEventsClient` API reference

```java
org.apache.eventmesh.client.cloudevents.CloudEventsClient
```

| Method | Returns | Notes |
| --- | --- | --- |
| `builder()` | `CloudEventsClientBuilder` | Entry point — see §3 |
| `publish(topic, CloudEvent)` | `boolean` | Single publish; 202 on success |
| `publish(topic, List<CloudEvent>)` | `boolean` | Batched publish (HTTP body batching) |
| `request(topic, CloudEvent, timeoutMs)` | `CloudEvent` | **Blocking** request-reply; `null` on timeout; late replies are dropped |
| `reply(correlationId, CloudEvent)` | `boolean` | Reply side of request-reply; uses the `emcorrelationid` extension |
| `subscribe(topic, mode, Consumer<CloudEvent>)` | — | Long-poll subscribe; **handler return ⇒ auto-ACK** |
| `subscribeWithAck(topic, mode, Predicate<CloudEvent>)` | — | Long-poll subscribe; `Predicate` returns `true` = ACK, `false` = no-ACK (at-least-once re-delivery on dispatcher timeout) |
| `subscribeSse(topic, mode, Consumer<CloudEvent>)` | — | SSE push subscribe; runs over HTTP on `/events/stream` |
| `subscribeWs(topic, mode, Consumer<CloudEvent>)` | — | WebSocket push subscribe; needs `wsUrl` (separate port) |
| `unsubscribe(topic)` | — | Unsubscribe one topic; stops the long-poll loop if no topics remain |
| `unsubscribe()` | — | Unsubscribe all; stop all loops / pushes |
| `createLiteTopic(parent, lite)` | `boolean` | **RocketMQ 5.x only.** Idempotent create of a Lite Topic (RIP-83) |
| `publishLite(parent, lite, CloudEvent)` | `boolean` | **5.x only.** Publish to LMQ inside the Lite Topic |
| `subscribeLite(parent, lite, Consumer<CloudEvent>)` | — | **5.x only.** Background poll loop; offset managed inside the storage plugin (no ACK / no DLQ) |
| `unsubscribeLite(parent, lite)` | — | **5.x only.** Stop the background poll loop for one Lite Topic |
| `streaming()` | `StreamingOperations` | Entry point for LLM streaming — see §8 |
| `shutdown()` | — | Stop everything: long-poll, SSE, WS, Lite loops, streaming sessions |
| `static event(id, source, type, byte[] data)` | `CloudEvent` | Convenience constructor |

### Subscriber mode (`DistributionMode`)

`mode` is one of the string constants from
`org.apache.eventmesh.runtime.subscription.DistributionMode`:

| Mode | Semantics |
| --- | --- |
| `BROADCAST` | Every subscriber gets every message |
| `LOAD_BALANCE` | Each message goes to exactly one subscriber in the group |
| `MULTICAST` | Multi-cast delivery |
| `LOAD_BALANCE_STICKY` | Hash by `partitionKey` extension for stable affinity (preserves per-key order) |

---

## 3. Builder and configuration

```java
CloudEventsClient.builder()
    .runtimeUrl("http://localhost:8080")     // required — Runtime HTTP endpoint
    .clientId("my-service")                  // required — unique per JVM
    .pollIntervalMs(500L)                    // long-poll cadence (default: builder default)
    .wsUrl("http://localhost:8082")          // optional — required for subscribeWs
    .build();
```

| Builder key | Required | Default | Notes |
| --- | --- | --- | --- |
| `runtimeUrl` | yes | — | HTTP(S) base URL of the Runtime |
| `clientId` | yes | — | Used in subscription registration, `SubscriptionStore`, quota key |
| `pollIntervalMs` | no | builder default | Long-poll cadence. Larger value = more idle time per round; smaller = more requests |
| `wsUrl` | no (yes for WS) | — | **WebSocket** endpoint. Runtime exposes WS on a separate port (configured at server start) |

Environment variables are honored via `System.getProperty` for tests:

```java
CloudEventsClient client = CloudEventsClient.builder()
    .runtimeUrl(System.getProperty("eventmesh.runtime.url", "http://localhost:8080"))
    .clientId("demo-" + System.currentTimeMillis())
    .build();
```

TLS / mTLS is configured at the **Runtime** (server side), not the client. The
client just talks to `https://...` once TLS is enabled. See
[docs/eventmesh-configuration.md](eventmesh-configuration.md#security).

---

## 4. Publish / subscribe patterns

### 4.1 Auto-ACK subscribe (the simple case)

```java
client.subscribe("orders", "BROADCAST", event -> {
    System.out.println("got " + event.getId());
    // any thrown exception still counts as ACK — use subscribeWithAck for at-least-once
});
```

### 4.2 Manual ACK for at-least-once

```java
client.subscribeWithAck("orders", "LOAD_BALANCE", event -> {
    try {
        process(event);          // your business logic
        return true;             // ACK → offset advances
    } catch (Exception ex) {
        return false;            // no-ACK → re-delivery on dispatcher timeout
    }
});
```

Business idempotency is your responsibility. EventMesh guarantees
**at-least-once**, not exactly-once.

### 4.3 Batch publish

```java
List<CloudEvent> batch = ...;
boolean ok = client.publish("orders", batch);
```

The Runtime splits the batch into per-partition writes inside the storage plugin.
A single failure inside the batch surfaces as `false` and the Runtime returns
`502` for that call.

---

## 5. Request-reply (synchronous RPC)

The request-reply pattern uses the **`emcorrelationid`** CloudEvents extension
(all-lowercase, no hyphens — CloudEvents disallows hyphens in extension names).

```java
// requester
CloudEvent req = CloudEventsClient.event("req-1", "caller", "query.price", payload);
CloudEvent reply = client.request("price-req", req, 10_000L);   // up to 10s
if (reply != null) { /* use reply */ }

// replier
responder.subscribe("price-req", "LOAD_BALANCE", event -> {
    Object corr = event.getExtension("emcorrelationid");
    if (corr != null) {
        CloudEvent r = CloudEventsClient.event("reply-1", "price-svc", "query.price.reply",
            priceJson(event).getBytes(StandardCharsets.UTF_8));
        responder.reply(corr.toString(), r);
    }
});
```

`request(...)` is **blocking** on the client thread. Late replies arriving after
`timeoutMs` are dropped at the Runtime. Use `subscribeSse` or `subscribeWs`
when you need to keep the channel open.

---

## 6. Subscriber transports: long-poll / SSE / WebSocket

All three transports produce the same `CloudEvent` payload to the handler; the
only difference is the **push direction**.

| Transport | Endpoint | Push direction | Port |
| --- | --- | --- | --- |
| Long-poll | `POST /events/subscribe` | client-driven | Runtime HTTP port (default 8080) |
| SSE | `GET /events/stream` (text/event-stream) | server push | Runtime HTTP port (default 8080) |
| WebSocket | runtime WS endpoint | server push, bi-directional | Runtime WS port (default 8082, configurable) |

WebSocket needs a separate port because the WS upgrade is a different protocol
negotiation than plain HTTP. The Runtime starts the WS server on its own port
(server-side configuration), and the client **must** configure `wsUrl`
explicitly. Pointing `wsUrl` at the HTTP port will fail the WS handshake.

```java
// SSE — same port as HTTP
client.subscribeSse("orders", "BROADCAST", event -> { /* server-push */ });

// WebSocket — separate port
CloudEventsClient wsClient = CloudEventsClient.builder()
    .runtimeUrl("http://localhost:8080")    // HTTP (publish / long-poll / SSE)
    .wsUrl("http://localhost:8082")         // WS push
    .clientId("ws-sub").build();
wsClient.subscribeWs("orders", "BROADCAST", event -> { /* WS push */ });
```

All three transports auto-ACK on handler return (like `subscribe`). Use the
manual-ACK variant only for the long-poll transport.

---

## 7. RocketMQ 5.x Lite Topic

Lite Topic (RIP-83) is RocketMQ 5.5+'s hierarchical message container. A Lite
Topic lives inside a normal parent topic; the parent must be declared `LITE`
type, then individual `lite` queues inside it share the parent's storage
budget. Useful for **session / sub-class** fan-out at very high cardinality.

> **Backend-only feature.** `createLiteTopic` / `publishLite` / `subscribeLite`
> return `false` (or no callbacks fire) on RocketMQ 4.x, Kafka, or any
> non-`LiteTopicCapable` storage backend. The Runtime returns `501 Not
> Implemented` for the corresponding endpoints.

```java
// 1. Declare — idempotent, call once at startup
client.createLiteTopic("orders", "user-42");

// 2. Subscribe — background poll loop, push-style callback (no ACK, no DLQ)
client.subscribeLite("orders", "user-42", event -> { /* process lite event */ });

// 3. Publish — routes to LMQ via __LITE_TOPIC property
client.publishLite("orders", "user-42",
    CloudEventsClient.event("lt-1", "order-svc", "order.lite", payload));
```

Differences from ordinary topic subscribe:

* **No ACK / no DLQ** — offset is managed inside the storage plugin. At-least-once
  is best-effort.
* **Client-driven polling** — `subscribeLite` runs a `GET /events/lite/poll` loop
  on the client side. Use `unsubscribeLite(parent, lite)` to stop one Lite
  subscription; `unsubscribe()` / `shutdown()` stop everything.

---

## 8. LLM streaming call (Mode 1 / Mode 2)

EventMesh provides two streaming patterns for LLM-style use cases (token
chunks flowing back, multi-turn conversation context).

| Mode | Use case | Direction | Entry |
| --- | --- | --- | --- |
| **Mode 1** — streaming call | client → agent (LLM), agent streams tokens back | request/response, push | `client.streaming().openSession(...)` |
| **Mode 2** — pub/sub on a session | producer writes chunks; consumer reads via SSE | publish/subscribe | `client.subscribeSession(sessionId)` / `client.openSessionPublisher(sessionId)` |

### 8.1 Mode 1 — single call

```java
CloudEventsClient client = CloudEventsClient.builder()
    .runtimeUrl("http://localhost:8080").clientId("my-app").build();

try (StreamingResponse r = client.streaming()
        .openSession(OpenSession.builder().clientId(client.clientId()).build())
        .call("Introduce EventMesh in three sentences")) {
    r.forEach(chunk -> System.out.print(chunk.getChunk())).join();
}
```

`forEach` fires once per token (or delta). `.join()` blocks until the stream
ends. Closing the `StreamingResponse` ends **one round**; it does **not** close
the session.

### 8.2 Mode 1 — multi-turn conversation

```java
StreamingSession session = client.streaming()
    .openSession(OpenSession.builder().clientId("my-app").build());
try {
    try (StreamingResponse r1 = session.call("I'm Zhang San, a Java engineer")) {
        r1.forEach(c -> System.out.print(c.getChunk())).join();
    }
    // session remembers the previous round
    try (StreamingResponse r2 = session.call("What's my name and job?")) {
        r2.forEach(c -> System.out.print(c.getChunk())).join();
    }
} finally {
    session.close();
}
```

Multi-turn context is owned by the agent's `ConversationStore`, keyed by
`sessionId`.

### 8.3 Mode 2 — pub/sub on a session

Useful when chunks need to be **persistent** (durable across process restarts)
or **fan-out** to multiple consumers. Internally uses the storage plugin's
Lite Topic.

```java
// consumer side
StreamingResponse sub = client.subscribeSession("my-session-id");
sub.forEach(chunk -> System.out.println("[" + chunk.getSeq() + "] " + chunk.getChunk())).join();
sub.close();

// producer side
SessionPublisher pub = client.openSessionPublisher("my-session-id");
pub.publish("Hello", false);   // non-terminal frame
pub.publish(" world", false);
pub.publish("", true);         // terminal frame — consumer's forEach completes
pub.close();
```

### 8.4 Implementing an agent

An agent that participates in Mode 1 follows a four-step contract:

1. parse `sessionId`, `prompt`, `replyTo` from the inbound CloudEvent
2. on each LLM token → emit a non-terminal frame `{chunk: token, done: false}`
3. on normal completion → emit a terminal frame `{chunk: "", done: true}`
4. on error → emit a terminal error frame `{chunk: "", done: true, error: "..."}`

Reference implementation: `eventmesh-agent/.../StreamingAgent.java`
(instantiate with an LLM client, an `agentParent` topic, the agent's `agentId`,
and a `ConversationStore`).

### 8.5 Server-side configuration for streaming

The Runtime pre-creates the agent / client parent topics. For Mode 2 also
pre-create `sessionStreamParent`. The 6-arg `SessionRouter` enables
`sessionTtlMs` + `sessionStreamParent`; the 4-arg variant is Mode 1 only.

---

## 9. Security: tokens, signatures, the unified gate

By default, the Runtime is open. Production deployments **must** enable the
unified security gate (issue #5304) on the server. From the client side, the
only practical change is that you may need to attach credentials as HTTP
headers / CloudEvents extensions:

* `Authorization: Bearer <token>` — picked up by the built-in `TokenAuthFilter`
  and recorded into the `RequestContext` as `principal` / `scopes`
* CloudEvents extension `emtenantid` — drives per-tenant quota in
  `TenantQuotaManager`
* CloudEvents extension `emcorrelationid` — request-reply correlation

The gate runs `FilterChain` (TokenAuth → SignatureVerifier → Acl) →
`QuotaManager` (per-`Resource` counter, default `UnlimitedQuotaManager`) →
`AuditSink` (default `LoggingAuditSink`) on every ingress. The
`Operation` enum is recorded in the context, so quota can distinguish a
`publish` from a `subscribe` from an A2A call.

For configuration and the three wiring points
(`UniHttpServer.withSecurityGate`, `A2AGatewayHttpHandler.withSecurityGate`,
`ConnectorScheduler.withSecurityGate`) see
[docs/eventmesh-configuration.md](eventmesh-configuration.md#security) and
[docs/eventmesh-architecture.md §4](eventmesh-architecture.md#4-security-gate-issue-5304).

> **The client SDK does not need to "know" about the gate.** A deployment that
> enables the gate is a server-side change. The client sends the same CloudEvent
> and the Runtime decides. If the Runtime requires auth, it returns `401` and
> your handler can re-authenticate and retry.

---

## 10. Reliability: ACK, retries, dead-letter, idempotency

| Concept | Where it lives | Client responsibility |
| --- | --- | --- |
| At-least-once | Runtime `DeliveryStateStore` | Use `subscribeWithAck` and return `true` only after success |
| Retries | Runtime retry policy | `false` from your predicate triggers a re-delivery after the dispatcher timeout |
| Dead-letter | Runtime `DeadLetterStore` | Inspect / replay via admin endpoints (see [eventmesh-configuration.md](eventmesh-configuration.md#admin)) |
| Idempotency | — | **You.** Use `event.getId()` as the dedup key. The Runtime does not deduplicate. |
| Offset | Runtime `OffsetStore` (L1) | — |
| Subscription state | Runtime `SubscriptionStore` (L2) | Survives Runtime restart via the meta store |
| Task state (A2A) | Runtime `TaskStore` (L3) | — |

For the storage-state taxonomy (L1 / L2 / L3) and the
`MeshStoragePlugin` / `MeshStoragePluginTCK` contract, see
[docs/eventmesh-architecture.md §3](eventmesh-architecture.md#3-control-plane--state-sessions-and-coordination).

Configuration knobs: see
[docs/eventmesh-configuration.md](eventmesh-configuration.md#deliverystate)
(`eventmesh.runtime.delivery.*`).

---

## 11. `A2AClient` for agent workloads

For multi-agent systems, the [A2A protocol](eventmesh-a2a-protocol.md) gives you a
durable task lifecycle (`submitted → working → completed | failed |
canceled`) on top of the same storage substrate. The client side is
`org.apache.eventmesh.protocol.a2a.A2AClient` (in the
`eventmesh-protocol-a2a` module).

### 11.1 Builder

```java
A2AClient client = A2AClient.builder()
    .gatewayUrl("http://localhost:8080")    // Runtime A2A gateway (port 8080 by default)
    .namespace("default")
    .agentName("order-agent")
    .heartbeatInterval(30_000L)
    .build();
```

| Builder key | Required | Notes |
| --- | --- | --- |
| `gatewayUrl` | yes | Runtime HTTP base URL (A2A is served on the same HTTP port) |
| `namespace` | recommended | A2A namespace for topic isolation |
| `agentName` | recommended | Local agent identity; used in topic factory and `AgentCard` |
| `heartbeatInterval` | no | Heartbeat to the Runtime; default 30s |
| `socketTimeoutMs` | no | Underlying HTTP client socket timeout |

### 11.2 Core operations

| Method | Returns | Notes |
| --- | --- | --- |
| `sendTask(task)` | `TaskResult` | Submit a task; returns immediately with `taskId` + initial state |
| `sendTaskSync(task, timeoutMs)` | `TaskResult` | Submit and block until terminal state (or timeout) |
| `sendTaskAsync(task, Consumer<TaskResult>)` | — | Submit and stream intermediate states via callback |
| `getTaskStatus(taskId)` | `TaskResult` | Re-query the current state of a task |
| `cancelTask(taskId)` | `boolean` | Request cancellation; the target agent stops work if it can |
| `streamTaskStatus(taskId, Consumer<TaskResult>)` | — | SSE push of state transitions until terminal |
| `listAgents()` | `List<AgentCard>` | Browse the agent registry |
| `registerAgentCard(AgentCard)` | `boolean` | Publish this agent's capability description |

`TaskResult` exposes `taskId`, `state`, `data`, `error`, and `targetAgent`.

### 11.3 Idempotency: `taskEpoch`

Each task has a `taskEpoch` field that is **set at creation and never reset**.
Stale writes with a `taskEpoch` different from the create value are rejected by
the Runtime. Use the same `taskEpoch` across retries so the same logical task
always lands in the same slot.

### 11.4 Server-side wiring

The A2A gateway is enabled at the Runtime by booting the `A2AGatewayServer`
(Netty) on a configurable port (defaults to the main HTTP port). The endpoint
surface is:

* `POST /a2a/tasks/send` — submit a task
* `POST /a2a/tasks/sync` — submit and block
* `GET /a2a/tasks/{id}` — query state
* `POST /a2a/tasks/{id}/cancel` — cancel
* `GET /a2a/tasks/{id}/stream` — SSE stream of state transitions
* `GET /a2a/agents` — list agents
* `POST /a2a/agents` — register an agent card

See [docs/eventmesh-a2a-protocol.md](eventmesh-a2a-protocol.md) for the wire contract and
[docs/eventmesh-architecture.md §5](eventmesh-architecture.md#5-agent-plane--a2a-on-the-same-substrate)
for the runtime architecture.

---

## 12. Backend selection: RocketMQ 4.x / RocketMQ 5.x / Kafka

**The client code is identical across backends.** Switching from one storage
backend to another is a **Runtime** configuration change; the same
`CloudEventsClient` (and `A2AClient`) bytes run unchanged.

### 12.1 Server-side configuration matrix

| | RocketMQ 4.x | RocketMQ 5.x | Kafka |
| --- | --- | --- | --- |
| Plugin SPI key | `rocketmq` | `rocketmq5` | `kafka` |
| Storage module | `eventmesh-storage-plugin/eventmesh-storage-rocketmq` | `eventmesh-storage-plugin/eventmesh-storage-rocketmq5` | `eventmesh-storage-plugin/eventmesh-storage-kafka` |
| Connection | `NettyRemotingClient` direct (no `rocketmq-client` JAR) | Same — pure 5.5 remoting | `kafka-clients` (assign+seek+poll, **no consumer group**; EventMesh owns offsets) |
| Auth | ACL (optional) | ACL (optional) | SASL/SSL pass-through (`security.protocol` / `sasl.mechanism` / `sasl.jaas.config` are passed verbatim to `kafka-clients`) |
| Lite Topic | — | yes (`LiteTopicCapable`) | — |

Per-backend keys are listed in
[docs/eventmesh-configuration.md](eventmesh-configuration.md#storage).
Pick one `eventmesh.storage.type` at Runtime startup:

```bash
# 4.x
EVENTMESH_STORAGE_TYPE=rocketmq EVENTMESH_ROCKETMQ_NAMESRV=127.0.0.1:9876 bin/start.sh

# 5.x
EVENTMESH_STORAGE_TYPE=rocketmq5 EVENTMESH_ROCKETMQ5_NAMESRV=127.0.0.1:9876 bin/start.sh

# Kafka
EVENTMESH_STORAGE_TYPE=kafka bin/start.sh     # eventmesh.properties has bootstrap + SASL
```

### 12.2 Client-visible behavioral differences

The HTTP contract is the same — but the storage-plugin choices have
client-visible consequences for **subscription semantics** under failure:

| Dimension | RocketMQ 4.x | RocketMQ 5.x | Kafka |
| --- | --- | --- | --- |
| Consumption model | Classic PULL (EventMesh owns offset + partition ownership) | **POP** (broker allocates queues + lease gate) | assign + seek + poll (no consumer group; EventMesh owns offset) |
| Multi-instance de-dup | EventMesh `PartitionOwnership` | broker POP + lease | EventMesh `PartitionOwnership` (Kafka assign) |
| Offset ACK semantics | offset advances only on ACK | same | same (Kafka offset not committed; EventMesh-managed) |
| `publish` / `subscribe` / `request` / `reply` | consistent | consistent | consistent |
| Lite Topic | not supported | supported | not supported |

The `publish` / `subscribe` / `subscribeWithAck` / `request` / `reply` API
contract is identical across all three backends — that is the point of the
abstraction.

### 12.3 Kafka + SASL example

For SASL-enabled Kafka clusters (e.g. wemq-kafka), set in `eventmesh.properties`:

```properties
eventMesh.server.kafka.namesrvAddr=127.0.0.1:9094
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="<user>" password="<pass>";
```

The `KafkaMeshStoragePlugin` forwards `security.*` / `sasl.*` / `ssl.*` keys
verbatim to the underlying `KafkaProducer` / `KafkaConsumer` / `AdminClient`.
Plain-text Kafka clusters need none of these.

---

## 13. End-to-end example

```java
public class Demo {
    public static void main(String[] args) throws Exception {
        CloudEventsClient client = CloudEventsClient.builder()
            .runtimeUrl(System.getProperty("eventmesh.runtime.url", "http://localhost:8080"))
            .clientId("demo-" + System.currentTimeMillis())
            .pollIntervalMs(500L)
            .build();

        client.subscribeWithAck("demo-topic", "LOAD_BALANCE", event -> {
            System.out.println("processing: " + event.getId() + " type=" + event.getType());
            return true;     // ACK
        });

        for (int i = 0; i < 10; i++) {
            CloudEvent e = CloudEventsClient.event(
                "e" + i, "demo", "demo.tick",
                ("tick-" + i).getBytes(StandardCharsets.UTF_8));
            client.publish("demo-topic", e);
        }

        Thread.sleep(60_000L);
        client.shutdown();
    }
}
```

Switching the backend is a server-side change only:

```bash
# 4.x
EVENTMESH_STORAGE_TYPE=rocketmq EVENTMESH_ROCKETMQ_NAMESRV=127.0.0.1:9876 bin/start.sh

# 5.x (Lite Topic capable)
EVENTMESH_STORAGE_TYPE=rocketmq5 EVENTMESH_ROCKETMQ5_NAMESRV=127.0.0.1:9876 bin/start.sh

# Kafka (SASL in eventmesh.properties)
EVENTMESH_STORAGE_TYPE=kafka bin/start.sh
```

The same `Demo` class runs unchanged on all three.

---

## 14. Operational checklist

| Check | Where | What to look for |
| --- | --- | --- |
| Runtime reachable | client log | First `publish` returns `true`; first `subscribe` callback fires within `pollIntervalMs` |
| `clientId` uniqueness | Runtime log | A `clientId` collision prints a warning; use a different `clientId` per JVM |
| Backend connection | Runtime startup log | `[storage] connected to <backend>` line; otherwise no subscriptions will fire |
| Security gate | Runtime response | `401` on first request → auth header missing; `429` → quota exhausted; `403` → ACL denied |
| Quota exhaustion | Runtime metrics | `eventmesh_security_gate_quota_*` per-tenant counters |
| Dead-letter inspection | admin HTTP (port 8081) | `GET /admin/dlq?topic=<topic>` |
| A2A agent registry | `A2AClient.listAgents()` | Should return at least one `AgentCard` for `agentName` you registered |

See [docs/production-readiness.md](production-readiness.md) for SLOs and
runbooks.

---

## 15. Legacy compatibility

The legacy `EventMeshHttpClient` and `EventMeshTCPClient` continue to work
against the current Runtime, but they are **legacy-compatible** in the
capability status table and are not extended.

| Old client | New client | Migration |
| --- | --- | --- |
| `EventMeshHttpClient.publish(CloudEventMessage)` | `CloudEventsClient.publish(topic, CloudEvent)` | Switch the event from `CloudEventMessage` to `CloudEvent`; topic is a string |
| `EventMeshTCPClient.subscribe(topic, EventListener)` | `CloudEventsClient.subscribe(topic, mode, Consumer<CloudEvent>)` | Add a `mode`; switch the callback to `Consumer<CloudEvent>` |
| TCP subscribe with custom `Session` | WebSocket | WebSocket is the modern bi-directional transport |
| OpenMessaging SDK | CloudEventsClient | The OpenMessaging wire is not supported in the new Runtime; use the HTTP + CloudEvents path |

For TCP / gRPC SDK migration details, see the legacy-compat section of this guide (§15) — this document is the
authoritative home for the new client API; the old guide is preserved in git
history for the migration notes.

---

## 16. Code locations

* `CloudEventsClient` — `eventmesh-sdks/eventmesh-sdk-java/.../cloudevents/CloudEventsClient.java`
  + `CloudEventsClientBuilder`
* Streaming — `eventmesh-sdks/eventmesh-sdk-java/.../cloudevents/stream/` (operations,
  response, session, request, publisher, exception)
* `A2AClient` — `eventmesh-protocol-plugin/eventmesh-protocol-a2a/.../A2AClient.java`
* `A2ATopicFactory` — `eventmesh-protocol-plugin/eventmesh-protocol-a2a/.../A2ATopicFactory.java`
  (`agentInbox(agentId)`, `gatewayResponseTopic(ns, gw, taskId)`, `+` wildcard)
* Runtime HTTP entry — `eventmesh-runtime/.../http/UniHttpServer.java`
  (`/events/*` endpoints; `withSecurityGate(...)` wiring point)
* A2A HTTP handler — `eventmesh-runtime/.../a2a/A2AGatewayHttpHandler.java`
  (`/a2a/*` endpoints; `withSecurityGate(...)` wiring point)
* Streaming agent — `eventmesh-agent/.../StreamingAgent.java`
* Storage plugins —
  * `eventmesh-storage-plugin/eventmesh-storage-rocketmq/` (SPI key `rocketmq`)
  * `eventmesh-storage-plugin/eventmesh-storage-rocketmq5/` (SPI key `rocketmq5`,
    `LiteTopicCapable`)
  * `eventmesh-storage-plugin/eventmesh-storage-kafka/` (SPI key `kafka`,
    assign+seek+poll, SASL pass-through)
* Security gate — `eventmesh-runtime/.../security/gate/` (`SecurityGate`,
  `RequestContext`, `QuotaManager`, `AuditSink`, `GateDecision`)
* Architecture — `eventmesh-architecture-guard/.../guard/ArchitectureRules.java`
  (ArchUnit layered-architecture enforcement)

See also:

* [docs/eventmesh-architecture.md](eventmesh-architecture.md) — system
  architecture, control / data / agent planes
* [docs/eventmesh-features.md](eventmesh-features.md) — feature-by-feature
  guide
* [docs/eventmesh-configuration.md](eventmesh-configuration.md) — every
  runtime key
* [docs/eventmesh-getting-started.md](eventmesh-getting-started.md) —
  zero-to-running guide
* [docs/eventmesh-a2a-protocol.md](eventmesh-a2a-protocol.md) — A2A wire contract
* [docs/production-readiness.md](production-readiness.md) — SLOs, runbooks
