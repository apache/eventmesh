# Apache EventMesh Documentation

Welcome to the Apache EventMesh documentation. EventMesh is a **stateless
application-layer event mesh** for cloud-native, serverless, and AI-agent
workloads: it connects applications to messaging backends (RocketMQ, Kafka,
…) through an HTTP + [CloudEvents](https://cloudevents.io) API while owning
subscription, offset, and delivery semantics itself — the broker is used
purely as a write-ahead log.

> Maturity tags (**GA / Beta / Experimental / Legacy**) for every capability
> live in the [capability status table](../README.md#capability-status) in the
> project README — that table is the single source of truth.

## Where to start

| I want to… | Read |
| --- | --- |
| Understand what EventMesh is and when to use it | [Introduction](introduction.md) |
| Get a runtime running and send my first event | [Quickstart](quickstart/getting-started.md) |
| Use the Java SDK in my application | [Java client guide](feature/client-java.md) |
| Understand the architecture | [Architecture overview](architecture/overview.md) |
| Operate a deployment | [Deployment & operations](feature/deployment.md) |

## Documentation map

### Quickstart

- [Getting started](quickstart/getting-started.md) — zero to a running runtime
  (Docker or source), first publish, subscribe and receive.
- [Configuration reference](quickstart/configuration.md) — every runtime key:
  storage backends, ports, security, connector scheduling.

### Feature guides

One page per capability — what it does, why it matters, how to use it, where
the code lives. Reference material (APIs, protocols, operations) lives here
too.

**Core features**

- [Publish & subscribe](feature/pubsub.md) — topics, distribution modes
  (LOAD_BALANCE / BROADCAST / MULTICAST), filtering, batching, request-reply.
- [Reliable delivery](feature/delivery-reliability.md) — the at-least-once
  model: ACK tracking, retries, dead-letter queue, crash recovery, fencing.
- [Streaming & push transports](feature/streaming.md) — long-poll, SSE,
  WebSocket, LLM streaming sessions (Mode 1 / Mode 2).
- [Lite Topic (RocketMQ 5.x)](feature/lite-topic.md) — hierarchical messaging
  inside a parent topic; publish/poll semantics and checkpoints.
- [A2A — Agent-to-Agent](feature/a2a.md) — the agent collaboration protocol:
  task lifecycle, JSON-RPC/MCP bridge, REST + SDK usage
  *(Experimental)*; [readiness decision](feature/a2a-readiness.md).

**Internals & mechanisms**

- [Offset management](feature/offset-management.md) — the two offset layers,
  per-backend mechanics, deferred ACK, monotonic writes, takeover.
- [Load balancing](feature/load-balancing.md) — full-stickiness model,
  self-reported instance load, the `recommend` loop, trade-offs.
- [Frame protocol conversion](feature/frame-protocol.md) — `EventMeshFrame`
  wire format, the `FrameAdaptor` / `WireCodec` SPIs, per-protocol
  conversion chains.
- [Control plane](feature/control-plane.md) — delivery topology, the
  unified state control plane (L1/L2/L3 stores), and the per-backend
  state store failure matrix.
- [Architecture guard](feature/architecture-guard.md) — the ArchUnit rulebook
  keeping module boundaries enforced in CI *(contributors)*.
- [Connector API split plan](feature/connector-api-split.md) — the
  `eventmesh-connector-api` module split design *(contributors)*.

**Reference**

- [Java client guide](feature/client-java.md) — the complete
  `CloudEventsClient` / `A2AClient` API surface: builders, pub/sub patterns,
  request-reply, transports, security, reliability, backends.
- [HTTP API](feature/http-api.md) — every traffic endpoint on port 8080:
  methods, request/response shapes, error codes.
- [Admin API](feature/admin-api.md) — the operational surface on port 8081,
  including the fail-closed bearer-token guard.
- [Observability](feature/observability.md) — metrics (Prometheus + JSON),
  traces, health checks, SLOs and alert rules.
- [Deployment & operations](feature/deployment.md) — deployment modes,
  Docker, multi-instance coordination, runbooks, known limitations.
- [Protocols & SDKs](feature/protocols.md) — wire protocol inventory
  (CloudEvents, EventMeshFrame, A2A, TCP/gRPC/OpenMessaging) and SDK status
  per language.
- [Storage SPI](feature/storage-spi.md) — the `MeshStoragePlugin` contract,
  capability matrix, TCK.

### Architecture

- [Architecture overview](architecture/overview.md) — the three planes
  (data / control / agent), module decomposition, the publish wire path.
- [Security](architecture/security.md) — the unified SecurityGate:
  auth filters, per-tenant quota, audit; TLS/mTLS; admin token guard.
- [Unified architecture redesign](architecture/redesign.md) — the original
  rewrite design record (English rewrite): iron rules, target architecture,
  decision log, semantics boundaries.
- [Architecture review evidence](architecture/review/evidence.md) —
  PR/test evidence behind every closed review issue.
- [Production HA acceptance plan](architecture/review/production-ha-plan.md)

## Conventions

- File naming: lowercase, hyphen-separated (`delivery-reliability.md`);
  one topic per page; pages are written to render standalone as HTML
  (website conversion pulls this tree directly).
- Every page starts with a one-paragraph summary of *who it is for* and
  *what it covers*, followed by a `---` separator.
- Status banners reference the README capability table instead of restating
  maturity levels.
