# Apache EventMesh Architecture

> **Audience:** anyone who wants the structural view — the three planes,
> module decomposition, and the publish wire path. Code locations are
> relative to the module root.

---

Apache EventMesh is a **stateless application-layer event mesh** built around three
separation-of-concerns planes:

1. **Control plane** — subscription / session / state management, cluster coordination,
   routing decisions. Stored in a *pluggable meta store* (Nacos today; in-memory single-instance)
   and *pluggable storage SPI* (RocksDB / MQ-as-WAL / external KV).
2. **Data plane** — the wire path from a client SDK to a backend MQ, then to subscriber
   delivery transports (HTTP long-polling, SSE, WebSocket, request-reply). The
   EventMesh Runtime is the only place where delivery semantics live.
3. **Agent plane** — the A2A (Agent-to-Agent) gateway that maps synchronous
   tool-call / JSON-RPC semantics onto the same MQ-backed pub/sub substrate, so
   multi-agent workloads get durability, observability, and quota from the same
   pipeline as ordinary events.

This page gives the **structural** view. For end-to-end data flow diagrams see
`docs/architecture/redesign.md`. For the security / quota gate, see
the "Security gate" section below and the `#5304` reference docs; for the storage
capability contract, see the `eventmesh-storage-api` TCK.

---

## 1. Top-level decomposition

```
┌──────────────────────────────────────────────────────────────────────┐
│                        EventMesh Repository                          │
├──────────────────────────────────────────────────────────────────────┤
│  eventmesh-runtime          // the only deployable that ingests     │
│                              // and dispatches events                │
│  eventmesh-storage-plugin/  // pluggable MeshStoragePlugin SPI      │
│      eventmesh-storage-api  // interface + TCK (StorageCapabilities, │
│      │                       // LiteTopicCapable, MeshStoragePlugin) │
│      eventmesh-storage-*    // backends: RocketMQ 4.x/5.x, Kafka    │
│                              // (SPI: add more via MeshStoragePlugin)│
│  eventmesh-connector-plugin/ // 24+ source/sink connector plugins   │
│  eventmesh-connector-runtime // standalone process running them      │
│  eventmesh-protocol-plugin/  // eventmesh-protocol-api + cloudevents│
│                              // + meshmessage + grpc + a2a          │
│  eventmesh-spi               // ExtensionFactory, EventMeshSPI,     │
│                              // EventMeshExtensionType              │
│  eventmesh-common            // shared util / config / metrics      │
│  eventmesh-sdks              // client SDKs (Java, …)               │
│  eventmesh-examples          // runnable end-to-end examples        │
│  eventmesh-architecture-guard // ArchUnit rules + CI workflow       │
│  eventmesh-agent             // reference A2A client / agent core   │
└──────────────────────────────────────────────────────────────────────┘
```

* **One deployable process for the data plane**: `eventmesh-runtime`. It is
  *stateless with respect to delivery* (cluster state lives in the meta store and
  storage SPI), which means horizontal scale-out is a config change.
* **Storage is a contract, not a driver**: every backend — including the new
  RocksDB-local path — implements `MeshStoragePlugin` and passes
  `MeshStoragePluginTCK`. The runtime only knows the SPI; it does not import any
  vendor JAR.
* **Connectors run in a separate process**: `eventmesh-connector-runtime` (admin
  HTTP server + per-connector thread pool). They connect to the runtime over
  HTTP+CloudEvents, not in-process, so a misbehaving connector cannot poison the
  data plane.
* **Architecture is enforced, not aspirational**: `eventmesh-architecture-guard`
  is an ArchUnit test that fails CI if any module violates the layered rules
  (e.g. `eventmesh-common` may not depend on `eventmesh-runtime`). See
  `#5305` for the FAIL-mode workflow.

---

## 2. Runtime data plane — the wire path

A single publish, end to end:

```
client SDK                                EventMesh Runtime                       storage
─────────                                 ─────────────────                      ───────
CloudEventsClient.publish(ce)             ┌─ UniHttpServer (Netty HTTP/S)        OffsetStore
   │   POST /events/publish  ───────────▶ │  • TlsContextFactory                 (local KV)
   │                                       │  • RequestContext (security gate)  ─┘
   │                                       │  • SecurityGate.check(ctx, frame) ───┐
   │                                       │      ├─ FilterChain (auth / acl)    │   ACL: AclFilter,
   │                                       │      │  (TokenAuth, Signature,      │   TokenAuthFilter,
   │                                       │      │   AclFilter)                 │   SignatureVerifierFilter
   │                                       │      ├─ QuotaManager (per-tenant)  │   Quota:
   │                                       │      └─ AuditSink                   │   TenantQuotaManager
   │                                       ▼                                     │
   │                                   UniIngressService.publish                │
   │                                       │                                     │
   │                                       │ EventMeshFrame (single protocol)    │
   │                                       ▼                                     │
   │                                   FilterChain.invoke(frame)                 │
   │                                       ▼                                     │
   │                                   Producer.send(StorageResourceService) ─▶│  ──▶ MQ-as-WAL
   │                                       │                                     │
   │                                       │ StorageClient.append(...)           │
   │                                       ▼                                     │
   │                                   SendCallback                             │
   │                                       ▼                                     │
   ◀──── HTTP 200 / 4xx (rejected by gate)  │  AuditSink.emit(...) ──▶ metrics     │
                                          └─────────────────────────────────────┘
```

A subscribe path mirrors this in reverse. A subscriber's `POST /events/subscribe`
registers a `Subscription` in `ClusterSubscriptionStore` (L2). The runtime's
`LocalDeliverer` polls the storage offset, applies the subscription filter, then
pushes the CloudEvent over the chosen transport (long-poll / SSE / WebSocket /
request-reply).

The legacy TCP / gRPC / OpenMessaging SDKs still work because the new
`EventMeshFrame` adaptor (`eventmesh-runtime/.../protocol/meshmessage`) and the
`UniTcpServer` (public/internal split per `#5297`) preserve the wire format and
semantics of the old `MeshMessage` / `OpenMessage` clients.

### Key classes

| Component | File | Role |
| --- | --- | --- |
| HTTP entry | `eventmesh-runtime/.../http/UniHttpServer.java` | Netty HTTP/S; entry of all `/events/*` + `/a2a/*` traffic; `withSecurityGate(...)` wiring point |
| WebSocket entry | `eventmesh-runtime/.../http/UniWsServer.java` | WebSocket transport for subscribers |
| Ingress orchestrator | `eventmesh-runtime/.../ingress/UniIngressService.java` | Frame-typed facade; single protocol path (`EventMeshFrame`) used by both HTTP and the legacy TCP adaptor |
| Security gate | `eventmesh-runtime/.../security/gate/SecurityGate.java` | Opt-in unified gate (see §4) |
| Filter chain | `eventmesh-runtime/.../security/FilterChain.java` | Auth + ACL filters executed before the gate |
| A2A HTTP handler | `eventmesh-runtime/.../a2a/A2AGatewayHttpHandler.java` | Netty handler for `/a2a/*`; `withSecurityGate(...)` wiring point |
| Connector scheduler | `eventmesh-runtime/.../connector/ConnectorScheduler.java` | Validates `ConnectorDef` against the gate; `withSecurityGate(...)` wiring point |
| Storage SPI | `eventmesh-storage-plugin/eventmesh-storage-api/.../storage/MeshStoragePlugin.java` | Capability-aware contract; backends: `eventmesh-storage-rocketmq`, `eventmesh-storage-rocketmq5`, `eventmesh-storage-kafka` |
| Offset store | `eventmesh-runtime/.../offset/` | Local offset tracking; survives restart via RocksDB or via the meta store |
| Delivery state | `eventmesh-runtime/.../state/` | `DeliveryStateStore` (RocksDB) for at-least-once, dead-letter handling |

---

## 3. Control plane — state, sessions, and coordination

The runtime keeps no delivery state in memory. Every piece of cluster-visible
state lives behind one of the storage SPI interfaces, each with a *clear level*
classification:

| Level | Purpose | Examples | Stores |
| --- | --- | --- | --- |
| **L1 — local-only** | per-instance delivery hints, cheap to recompute | `OffsetStore` (RocksDB or local KV) | Local file / memory |
| **L2 — cluster-shared** | subscriptions, sessions, agent cards | `SubscriptionStore`, `SessionStore`, `AgentCardRegistry` | Meta store (Nacos; Consul/ETCD/ZK are SPI work) |
| **L3 — durable-egress** | dead-letter, task records | `DeadLetterStore`, `TaskStore` | Meta store with CAS + fencing epoch |

The contract for each interface is the same:

* a `MeshStoragePlugin` implementation in `eventmesh-storage-plugin/*`,
* a thin runtime-side adapter (e.g. `ClusterSubscriptionStore`,
  `SessionRegistry`) that adds **fencing tokens / epochs** on top of the meta
  store, so split-brain does not corrupt state,
* a TCK (`MeshStoragePluginTCK`) that every backend must pass.

State changes propagate through a **prefix-watch** pattern on the meta store
(see `eventmesh-runtime/.../cluster/DynamicConfigWatcher.java`). A
`SubscriptionStore.subscribe(...)` call writes to the meta store; every runtime
instance receives the change and re-evaluates its local delivery set. That keeps
the cluster eventually consistent without inventing a Raft implementation.

For partition-owned delivery, the runtime implements the
`PARTITION_OWNED_PULL` topology (`#5309`): each topic partition is owned by
exactly one runtime, the owner drives the offset, and ownership transfers
are epoch-protected. The owner drives the offset because the storage SPI
itself does not know what is consumed.

---

## 4. Security gate (issue #5304)

Until 2026 the runtime had no consistent multi-tenant gate. ACL was per-filter
and easy to miss, rate limits were topic-only, and there was no audit trail.
Issue **#5304** added an **opt-in unified gate** that you can wire to the three
ingress points (HTTP, A2A HTTP, Connector admin).

```
   ingress request
        │
        ▼
   SecurityGate.check(RequestContext, EventMeshFrame)
        │
        ├─ 1. FilterChain.invoke(frame)            ──  TokenAuthFilter → SignatureVerifierFilter → AclFilter
        │     produces verdict (ALLOW / DENY)
        │
        ├─ 2. if ALLOW → QuotaManager.acquire(ctx, Resource)
        │     resources: CONNECTIONS | SUBSCRIPTIONS | THROUGHPUT | BACKLOG
        │     per-tenant counter; default = UnlimitedQuotaManager (no enforcement)
        │
        └─ 3. AuditSink.emit(decision, ctx, frame) ──  LoggingAuditSink (default) or custom sink
                                                       metrics already cover the aggregation view
```

Key design points:

* **Opt-in, not opt-out.** A `null` gate is a legacy behaviour. New deployments
  are expected to install a `TenantQuotaManager` and a custom `AuditSink`.
* **One `RequestContext` per request.** It is immutable, builder-built, and
  carries `tenantId`, `principal`, `roles`, `scopes`, `credential`,
  `remoteAddress`, `source`, `traceContext`, `quotaKey`, and an `Operation`
  enum (`PUBLISH` / `SUBSCRIBE` / `ACK` / `CONNECTOR` / `A2A` / `ADMIN`).
* **`FilterChain` is reused** for the actual auth/ACL verdict, so the gate does
  not duplicate policy; it composes it. New filters drop in without touching
  the gate.
* **Quota is keyed by `RequestContext.quotaKey()`** (defaults to `tenantId`),
  and uses the `Resource` enum so a backend can add custom resources via
  extension.

### Wiring

* `UniHttpServer#withSecurityGate(SecurityGate)` — gates every `/events/*`
  endpoint, including the SSE and WebSocket upgrades.
* `A2AGatewayHttpHandler#withSecurityGate(SecurityGate)` — gates every A2A
  endpoint; the A2A operation is recorded in the context so the quota can
  distinguish a `tasks/send` from a `tasks/get`.
* `ConnectorScheduler#withSecurityGate(SecurityGate)` — gates
  `createConnector(...)` and raises `ConnectorAccessDeniedException` (which
  the admin server maps to HTTP 403).

The **10 unit tests** in `SecurityGateTest` cover each Resource, both
allow/deny verdicts, every Operation, and a smoke test that the gate
short-circuits before the downstream SPI is touched.

---

## 5. Agent plane — A2A on the same substrate

The A2A (Agent-to-Agent) protocol is an HTTP/JSON contract for invoking a
remote agent as if it were a local function, with a **task** lifecycle
(`submitted → working → completed | failed | canceled`) that maps cleanly onto
a durable `TaskStore`. EventMesh implements the gateway side on top of the
data plane:

```
  agent A (or client)                     EventMesh Runtime
  ────────────────                        ─────────────────
  POST /a2a/tasks/send  ───────────────▶  A2AGatewayHttpHandler
                                              │
                                              │  SecurityGate.check(ctx, frame)   (Operation.A2A)
                                              ▼
                                          A2AGatewayService.submit(task)
                                              │
                                              │  TaskStore.create(task, taskEpoch)  ─▶ L3 (meta, CAS)
                                              │
                                              │  EventMeshA2ATransport.send(target, task) ─▶ topic
                                              │      topic = A2ATopicFactory.agentInbox(target)
                                              ▼
                                          async fan-out to target agent's listener
                                              │
                                              ▼
                                          agent B (or its runtime) processes task
                                              │
                                              │  POST /a2a/tasks/{id}/reply  ─▶ TaskStore.complete(...)
                                              ▼
                                          completion event published on
                                          A2ATopicFactory.gatewayResponseTopic(...)
                                              │
                                              ▼
  ◀───  Agent A subscribes (SSE on /a2a/tasks/{id}/stream)  ◀── emit
```

Key classes:

* `A2ATopicFactory` — `agentInbox(agentId)`, `gatewayResponseTopic(ns, gw, taskId)`,
  and a `+` wildcard for in-process tests. The convention is the same as
  Pulsar / MQTT — **single-level** `+`, **not** globstar `*`.
* `A2AGatewayService` — durable task store facade. Every mutation is
  epoch-protected: a stale write with a `taskEpoch` different from the create
  value is rejected. This is the only safe way to handle out-of-order
  responses from multiple agents.
* `AgentCardRegistry` — declares what an agent can do (`/a2a/agent-card`).
  Two implementations: `InMemoryAgentCardRegistry` (default) and a
  Meta-backed adapter (D2 of the A2A rollout, see issue `#5302`).
* `EventMeshA2ATransport` — bridges the A2A `MessageTransport` SPI to the
  same `UniIngressService` the data plane uses, so reliability, quota, and
  observability are uniform.

The protocol is **Experimental** (see capability status table). The wire
contract is the JSON envelope from
[docs/feature/a2a.md](../feature/a2a.md); the runtime
implementation is documented in
[docs/architecture/redesign.md](redesign.md).

---

## 6. Connector plane — source / sink as a separate process

A connector is a long-running process that copies events between EventMesh and
an external system (Kafka topic, Slack channel, MySQL table, S3 bucket, HTTP
endpoint, MCP tool, ChatGPT prompt, …). The `eventmesh-connector-plugin` repo
contains 24 plugin implementations; `eventmesh-connector-runtime` is the
deployable that hosts them.

```
   external system                              EventMesh
   ──────────────                               ─────────
   source side:                                       ▲
   ┌──────────┐  poll / listen   ┌──────────────────┐ │
   │ Kafka    │ ───────────────▶ │ Sink side:       │ │
   │ MySQL    │                  │ SinkConnector    │ │
   │ S3       │                  │  (CloudEvent in) │ │
   │ file     │                  └────────┬─────────┘ │
   └──────────┘                           │           │
                                          ▼           │
                                    EventMesh HTTP    │
                                    /events/publish   │
                                                   ───┘
                                                          ▲
                                                          │
                                    EventMesh HTTP        │
                                    /events/subscribe  ───┘
                                          │
                                          ▼
                                    Source side:
                                    SourceConnector
                                          │
                                          ▼
                                    external write
                                    (HTTP / JDBC / gRPC / …)
```

* `ConnectorDef` is the immutable, validated description of one connector:
  `id`, `className`, `mode` (`source` / `sink` / `source-sink`),
  `topic`, `clientId`, `sinkClass`. New fields are added via the
  `ConnectorDef.Builder` — never by setter injection.
* `ConnectorScheduler` owns the lifecycle (`init → start → stop`) and
  registers each connector against the SecurityGate. A misconfigured
  connector is rejected **before** the JAR is loaded, so a plugin bug
  cannot take down the runtime.
* Connectors talk to the runtime over **HTTP + CloudEvents**, never
  in-process. They can be deployed, scaled, and restarted independently.

See `eventmesh-connector-runtime/.../ConnectorManager.java` for the
lifecycle and `eventmesh-connector-runtime/.../ConnectorAdminServer.java`
for the admin HTTP surface.

---

## 7. Module boundary rules (architecture-guard)

`eventmesh-architecture-guard` is an ArchUnit test suite that **fails the
build** if a module violates the layering rules. It runs in two places
(issue `#5305`):

* locally on `./gradlew :eventmesh-architecture-guard:check` so a developer
  gets a 30-second feedback loop, and
* on every PR via `.github/workflows/architecture-guard.yml` (Ubuntu-only)
  with a path filter to skip irrelevant pushes.

The current rule set covers:

* `eventmesh-common` may not depend on `eventmesh-runtime` (and vice versa
  for any runtime-only class).
* `eventmesh-storage-api` may not depend on any concrete backend.
* `eventmesh-protocol-plugin/*` is partitioned into `public` (SPI) and
  `internal` (implementation) — the `public → internal` direction is
  allowed; the reverse is not.
* `eventmesh-connector-plugin/*` plugins may not depend on
  `eventmesh-runtime`; they only see the CloudEvents HTTP contract.
* `eventmesh-agent` is allowed to depend on `eventmesh-sdks` and
  `eventmesh-protocol-api` only.

If a future change needs a cross-module dependency, the rule must be
**amended in code review**, not bypassed. The 30-second local loop and the
PR workflow are both in place to make this discipline cheap.

---

## 8. Cross-cutting concerns

### Metrics and observability

* Prometheus metrics under `org.apache.eventmesh.metrics.*` cover the
  gate (allowed/denied counts per Operation, per Resource), the runtime
  (publish/subscribe rate, queue depth), and the connectors (per-connector
  throughput, error rate).
* Trace context (`RequestContext.traceContext`) propagates through every
  plane so a single `traceId` follows an event from the client SDK to
  the storage backend and back out to the subscriber.
* The audit sink is the place to add custom per-tenant audit
  destinations (Kafka, S3, …).

### Configuration

* Runtime configuration is documented in
  [docs/quickstart/configuration.md](../quickstart/configuration.md). New
  keys are added behind the existing prefixes (`eventmesh.runtime.*`,
  `eventmesh.security.gate.*`, `eventmesh.storage.*`,
  `eventmesh.connector.*`) — never under a global `eventmesh.*` root.
* Per-backend settings are isolated (e.g.
  `eventmesh.storage.rocketmq.namesrvAddr` is **not** visible to a
  Kafka backend). The runtime boots a backend only if its class is on
  the classpath.

### Build & test

* Gradle multi-module; `:eventmesh-runtime:assemble` produces the
  runnable distribution.
* `./gradlew :eventmesh-architecture-guard:check` runs the ArchUnit
  rules before unit tests.
* Integration tests use Testcontainers (Nacos, RocketMQ) — not
  embedded servers — to catch backend-version drift.

---

## 9. Protocols and SDKs

EventMesh supports several wire protocols and ships client SDKs in
multiple languages. The canonical inventory - including GA / Beta /
Experimental / Legacy status for each protocol, the server-side
protocol plugin that handles it, and the corresponding client SDK
surface - lives in [docs/feature/protocols.md](../feature/protocols.md). The policy
summarized there:

* The modern **HTTP + CloudEvents + EventMeshFrame** path is the
  only path that downstream code should depend on. It is GA.
* Legacy **TCP** (MeshMessage, OpenMessaging) and the legacy
  HTTP codec are still supported for backward compatibility but
  are explicitly marked Legacy and receive only critical bug
  fixes. See `docs/feature/protocols.md` for the migration path.
* gRPC framing is **Beta**: stable, but the API surface may shift.
* A2A is **Experimental** until the readiness checks in #5340
  pass.

The Java SDK (`eventmesh-sdk-java`) demotes the OpenMessaging API
dependency from `api` to `implementation`, so a modern user who
depends only on `org.apache.eventmesh.client.cloudevents.*` does
not see OMA types on their classpath. The `eventmesh-architecture-guard`
ArchUnit rules + CI workflow verify that the runtime's modern
ingress path does not import legacy wire types.

---

## Documentation

The [documentation map](../index.md) organizes everything by audience. Highlights:

| Page | What it covers |
| --- | --- |
| [Introduction](../introduction.md) | What EventMesh is; the three planes; deployment shape |
| [Getting started](../quickstart/getting-started.md) | Zero-to-running runtime; per-backend quickstarts |
| [Configuration](../quickstart/configuration.md) | Every runtime key, security & quota, per-backend overrides |
| [Java client guide](../feature/client-java.md) | `CloudEventsClient` walkthrough: pub/sub, request-reply, SSE, WebSocket, lite topics |
| [Pub/sub](../feature/pubsub.md) | Topics, distribution modes, filtering |
| [Reliable delivery](../feature/delivery-reliability.md) | ACK tracking, retries, DLQ, crash recovery |
| [A2A protocol](../feature/a2a.md) | A2A wire contract and task lifecycle |
| [Security](security.md) | The unified gate, TLS/mTLS, admin token |
| [HTTP API](../feature/http-api.md) | Traffic endpoints |
| [Admin API](../feature/admin-api.md) | Operational surface + token guard |
| [Protocols & SDKs](../feature/protocols.md) | Wire protocol inventory and per-language SDK status |
| [Storage SPI](../feature/storage-spi.md) | `MeshStoragePlugin` contract and capability matrix |
| [Deployment](../feature/deployment.md) | Modes, Docker, multi-instance, runbooks |
| [Design records](../index.md#design-records-contributors) | Redesign rationale, evidence, plans |
