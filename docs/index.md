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
| Use the Java SDK in my application | [Java client guide](reference/client-java.md) |
| Understand the architecture | [Architecture overview](architecture/overview.md) |
| Operate a deployment | [Deployment & operations](reference/deployment.md) |

## Documentation map

### Quickstart

- [Getting started](quickstart/getting-started.md) — zero to a running runtime
  (Docker or source), first publish, subscribe and receive.
- [Configuration reference](quickstart/configuration.md) — every runtime key:
  storage backends, ports, security, connector scheduling.

### Feature guides

One page per capability — what it does, why it matters, how to use it, where
the code lives.

- [Publish & subscribe](feature/pubsub.md) — topics, distribution modes
  (LOAD_BALANCE / BROADCAST / MULTICAST), filtering, batching.
- [Reliable delivery](feature/delivery-reliability.md) — the at-least-once
  model: ACK tracking, retries, dead-letter queue, crash recovery.
- [Streaming & push transports](feature/streaming.md) — SSE, WebSocket, LLM
  streaming sessions (Mode 1 / Mode 2).
- [Lite Topic (RocketMQ 5.x)](feature/lite-topic.md) — hierarchical messaging
  inside a parent topic; publish/poll semantics and checkpoints.
- [A2A — Agent-to-Agent](feature/a2a.md) — the agent collaboration
  protocol: task lifecycle, JSON-RPC/MCP bridge, REST + SDK usage
  *(Experimental)*.

### Architecture

- [Architecture overview](architecture/overview.md) — the three planes
  (data / control / agent), module decomposition, the publish wire path.
- [Control plane](architecture/control-plane.md) — delivery topology,
  the unified state control plane (L1/L2/L3 stores), failure behavior.
- [Security](architecture/security.md) — the unified SecurityGate:
  auth filters, per-tenant quota, audit; TLS/mTLS; admin token guard.

### Reference

- [Java client guide](reference/client-java.md) — the complete
  `CloudEventsClient` / `A2AClient` API surface: builders, pub/sub patterns,
  request-reply, transports, security, reliability, backends.
- [HTTP API](reference/http-api.md) — every traffic endpoint on port 8080:
  methods, request/response shapes, error codes.
- [Admin API](reference/admin-api.md) — the operational surface on port 8081,
  including the fail-closed bearer-token guard.
- [Observability](reference/observability.md) — metrics (Prometheus + JSON),
  traces, health checks, alert rules, SLOs.
- [Deployment & operations](reference/deployment.md) — deployment modes,
  Docker, multi-instance coordination, runbooks, known limitations.
- [Protocols & SDKs](reference/protocols.md) — wire protocol inventory
  (CloudEvents, EventMeshFrame, A2A, TCP/gRPC/OpenMessaging) and SDK status
  per language.
- [Storage SPI](reference/storage-spi.md) — the `MeshStoragePlugin` contract,
  capability matrix, TCK.

### Feature & architecture records *(contributors)*

Historical design documents and review evidence. These explain *why* the
architecture looks the way it does; they are snapshots, not living docs.

- [Unified architecture redesign](architecture/redesign.md) — the original rewrite
  design (internal `EventMeshFrame`, MQ-as-WAL, self-managed offsets).
- [Offset / load-balancing / Frame design](architecture/offset-lb-frame.md)
- [Architecture guard](feature/architecture-guard.md) — the ArchUnit rulebook
  that enforces module boundaries in CI.
- [Connector API split plan](architecture/connector-api-split.md)
- [Architecture review evidence](architecture/review/evidence.md) —
  PR/test evidence behind every closed review issue.
- [Production HA acceptance plan](architecture/review/production-ha-plan.md)
- [A2A readiness decision](feature/a2a-readiness.md)

## Conventions

- File naming: lowercase, hyphen-separated (`delivery-reliability.md`);
  one topic per page; pages are written to render standalone as HTML
  (website conversion pulls this tree directly).
- Every page starts with a one-paragraph summary of *who it is for* and
  *what it covers*, followed by a `---` separator.
- Status banners reference the README capability table instead of restating
  maturity levels.
