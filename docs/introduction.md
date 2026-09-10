# Introduction

**Audience:** anyone evaluating EventMesh — what it is, what problems it
solves, and how its pieces fit together. Everything here is elaborated in the
[documentation map](index.md).

---

## What is EventMesh?

Apache EventMesh is an **application-layer event mesh** that sits between
your applications and your messaging backends (Apache RocketMQ, Apache
Kafka, …). Applications talk to EventMesh over plain **HTTP using the
[CloudEvents 1.0](https://cloudevents.io) format**; EventMesh talks to the
broker on their behalf.

The defining design decision: **the broker is used purely as a write-ahead
log (WAL)**. EventMesh itself owns every delivery semantic — subscriptions,
offsets, retries, dead-lettering, load balancing. This inversion is what
makes the rest of the properties possible:

| Property | How it follows |
| --- | --- |
| **Client simplicity** | An app only needs an HTTP client and CloudEvents. No MQ client libraries, consumer groups, or broker credentials in application space. |
| **Backend freedom** | Switching RocketMQ ↔ Kafka is a runtime configuration change; the client API and semantics do not move. |
| **Elastic scale** | The runtime is stateless with respect to delivery — cluster state lives in a meta store and local RocksDB, so scaling is adding instances. |
| **Uniform reliability** | ACK-tracked at-least-once delivery, exponential-backoff retries, and a dead-letter queue — identical across every backend. |
| **Event-driven agents** | The A2A protocol turns the same substrate into an agent collaboration bus with durable tasks and streamed replies. |

## The three planes

1. **Data plane** — the wire path: publish, subscribe, poll, ACK, push
   (long-poll / SSE / WebSocket), request-reply. Implemented in the
   `eventmesh-runtime` module around a single internal frame type
   (`EventMeshFrame`). See [Architecture overview](architecture/overview.md).

2. **Control plane** — everything stateful: subscriptions, sessions,
   offsets, dead-letter and A2A task records, each behind a typed store
   (L1 local / L2 cluster-shared / L3 durable). Multi-instance coordination
   runs over a meta store (Nacos today). See
   [Control plane](architecture/control-plane.md).

3. **Agent plane** — the A2A gateway: a task lifecycle
   (`submitted → working → completed | failed | canceled`) mapped onto the
   same pub/sub substrate, so agent workloads inherit durability, quota, and
   observability. See [A2A protocol](feature/a2a.md)
   *(Experimental)*.

## What a deployment looks like

```
                     ┌─────────────────────────────┐
  publishers /       │   EventMesh Runtime(s)      │        storage backend
  subscribers ─HTTP─▶│  8080 traffic   8081 admin  │─WAL──▶ RocketMQ / Kafka
  (SDK or curl)      │  8082 WebSocket (opt-in)    │        (pluggable SPI)
                     └──────────────┬──────────────┘
                                    │ meta (optional, multi-instance)
                                    ▼
                              Nacos meta store
```

- **One process per instance**, launched by `bin/start.sh` or the Docker
  image; traffic and admin HTTP are on separate ports so data and management
  never interfere.
- **Multi-instance is opt-in**: point every instance at the same meta store
  and partitions are owned by exactly one instance (CAS + fencing), with no
  duplicate consumption.
- **Connectors** (Kafka, JDBC, Slack, ChatGPT, …) run in a *separate*
  process (`eventmesh-connector-runtime`) and talk to the runtime over the
  same HTTP + CloudEvents contract — a misbehaving connector cannot poison
  the data plane.

## Feature highlights

| Feature | One-liner | Detail |
| --- | --- | --- |
| CloudEvents pub/sub | Vendor-neutral events over HTTP; the runtime owns semantics | [Publish & subscribe](feature/pubsub.md) |
| Delivery reliability | At-least-once with ACK tracking, retries, DLQ, crash recovery | [Reliable delivery](feature/delivery-reliability.md) |
| Multiple transports | Long-poll, SSE, WebSocket, request-reply | [Streaming](feature/streaming.md) |
| LLM streaming | Mode-1 streaming calls and Mode-2 pub/sub sessions | [Streaming](feature/streaming.md) |
| Lite Topic | RocketMQ 5.x hierarchical messaging | [Lite Topic](feature/lite-topic.md) |
| A2A protocol | Durable agent-to-agent tasks bridging MCP / JSON-RPC | [A2A](feature/a2a.md) |
| Security gate | Auth filters + per-tenant quota + audit at every ingress | [Security](architecture/security.md) |
| Observability | Prometheus metrics, traces, health, SLOs/alerts | [Observability](reference/observability.md) |
| Pluggable storage | `MeshStoragePlugin` SPI with a TCK per backend | [Storage SPI](reference/storage-spi.md) |
| Connectors | 23 source/sink plugins in a separate process | [Deployment](reference/deployment.md#connectors) |

## Try it in five minutes

```shell
docker run -d --name eventmesh \
  -e EVENTMESH_STORAGE_TYPE=kafka \
  -e EVENTMESH_KAFKA_NAMESRV=YOUR_KAFKA:9092 \
  -p 8080:8080 -p 8081:8081 \
  apache/eventmesh:latest

curl -X POST "http://localhost:8080/events/publish?topic=hello" \
  -H "Content-Type: application/cloudevents+json" \
  -d '{"specversion":"1.0","id":"1","source":"/demo","type":"demo.hello","data":"world"}'
```

Continue with the [Quickstart](quickstart/getting-started.md).

## Subprojects

The EventMesh ecosystem also maintains separate repositories:
[EventMesh-workflow](https://github.com/apache/eventmesh-workflow)
(serverless workflow orchestration),
[EventMesh-dashboard](https://github.com/apache/eventmesh-dashboard)
(operations console),
[EventMesh-catalog](https://github.com/apache/eventmesh-catalog)
(event schema catalog using AsyncAPI), and language SDKs under
`eventmesh-sdks/` (Java, Go, C, Rust).
