<div align="center">

<br /><br />
<img src="resources/logo.svg" width="256">
<br />

[![CI status](https://img.shields.io/github/actions/workflow/status/apache/eventmesh/ci.yml?logo=github&style=for-the-badge)](https://github.com/apache/eventmesh/actions/workflows/ci.yml)
[![CodeCov](https://img.shields.io/codecov/c/gh/apache/eventmesh/master?logo=codecov&style=for-the-badge)](https://codecov.io/gh/apache/eventmesh)
[![Code Scanning](https://img.shields.io/github/actions/workflow/status/apache/eventmesh/code-scanning.yml?label=code%20scanning&logo=github&style=for-the-badge)](https://github.com/apache/eventmesh/actions/workflows/code-scanning.yml)

[![License](https://img.shields.io/github/license/apache/eventmesh?style=for-the-badge)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![GitHub Release](https://img.shields.io/github/v/release/apache/eventmesh?style=for-the-badge)](https://github.com/apache/eventmesh/releases)
[![Slack Status](https://img.shields.io/badge/slack-join_chat-blue.svg?logo=slack&style=for-the-badge)](https://join.slack.com/t/the-asf/shared_invite/zt-1y375qcox-UW1898e4kZE_pqrNsrBM2g)
  

[📦 Documentation](https://eventmesh.apache.org/docs/introduction) |
[📔 Examples](https://github.com/apache/eventmesh/tree/master/eventmesh-examples) |
[⚙️ Roadmap](https://eventmesh.apache.org/docs/roadmap) |
[🌐 简体中文](README.zh-CN.md)
</div>


# Apache EventMesh

**Apache EventMesh** is a new generation serverless event middleware for building distributed [event-driven](https://en.wikipedia.org/wiki/Event-driven_architecture) applications.

### EventMesh Architecture

![EventMesh Architecture](resources/eventmesh-architecture-7.png)

EventMesh adopts a unified **CloudEvents-over-MQ** architecture. The message queue (MQ) acts as a pure write-ahead log (WAL) for durable storage only — there are no consumer groups, no tags, and no broker-side subscription semantics. Instead, the stateless EventMesh Runtime owns all delivery logic: its **SubscriptionManager** maintains the subscription registry and offset tracking, and dispatches events with load-balance, broadcast, and multicast semantics. Applications interact through a lightweight **HTTP + CloudEvents 1.0** SDK (`publish` / `subscribe` / `unsubscribe`), while integration with external systems runs in a standalone Connector Runtime via the connector SPI.

## Features

Apache EventMesh is packed with features that help users build event-driven applications with ease. Here are the highlights that set EventMesh apart:

**Core architecture**

- **CloudEvents-native, end to end** — built entirely around the [CloudEvents](https://cloudevents.io) 1.0 specification, so events stay vendor-neutral and portable.
- **Lightweight, language-agnostic SDK** — just three operations over plain HTTP (`publish`, `subscribe`, `unsubscribe`); no heavyweight client, no vendor lock-in.
- **Runtime-owned subscription & dispatch** — subscription state and delivery semantics (load-balance / broadcast / multicast) are managed by EventMesh itself, not the underlying MQ, giving you consistent behavior across any storage backend.
- **MQ as a pure write-ahead log (WAL)** — append-only, no consumer groups, no tags; the broker is reduced to durable storage, dramatically simplifying operations.
- **Guaranteed at-least-once delivery** — EventMesh owns reliability through self-managed offsets and explicit ACK.
- **Multiple delivery transports** — subscribers choose HTTP long-polling, Server-Sent Events (SSE), or WebSocket push, with request-reply support.
- **Effortless horizontal scaling** — stateless Runtime instances scale out seamlessly with no rebalancing cost.

**Extensibility & ecosystem**

- **Agent-to-Agent (A2A) collaboration** — a built-in [A2A protocol](docs/eventmesh-a2a-protocol.md) turns EventMesh into an agent collaboration bus, bridging synchronous MCP / JSON-RPC 2.0 tool calls and asynchronous event-driven pub/sub for LLM and multi-agent systems.
- **Pluggable storage layer** — [Apache RocketMQ](https://rocketmq.apache.org), [Apache Kafka](https://kafka.apache.org), [Apache Pulsar](https://pulsar.apache.org), [RabbitMQ](https://rabbitmq.com), [Redis](https://redis.io), and more.
- **Pluggable interconnector layer** — [connectors](https://github.com/apache/eventmesh/tree/develop/eventmesh-connector-plugin) run as standalone processes acting as the source or sink of SaaS, CloudService, Database, etc.
- **Pluggable meta service** — [Consul](https://consulproject.org/en/), [Nacos](https://nacos.io), [ETCD](https://etcd.io), and [Zookeeper](https://zookeeper.apache.org/).
- **Event schema management** via catalog service.
- **Powerful event orchestration** through the [Serverless workflow](https://serverlessworkflow.io/) engine.
- **Powerful event filtering and transformation.**

## Capability status

Each EventMesh surface carries an explicit maturity status. The table below is the
**single source of truth** — module-level docs link here instead of restating their
status. See [docs](docs/) for the per-capability guides.

| Capability | Status | Recommendation | Migration target |
| --- | :---: | --- | --- |
| [HTTP + CloudEvents](docs/eventmesh-client-guide.md) | **GA target** | Recommended — the primary user path (`CloudEventsClient` + `/events/*`) | Primary path |
| [Kafka / RocketMQ storage](eventmesh-storage-plugin/) (4.x, 5.x) | **GA target** | Recommended — pluggable WAL backends, TCK-covered (`MeshStoragePluginTCK`) | Primary path |
| SSE / WebSocket push | **Beta** | Usable — integration-tested; unified ACK/redelivery semantics still landing | Unified push transports |
| Connector Runtime | **Beta** | Usable — 24 connector plugins on the standalone runtime | New connector SPI migration |
| [A2A / Agent Gateway](docs/eventmesh-a2a-protocol.md) | **Experimental** | Evaluate — task store + runtime bridge landed (#5302/#5304); reaper & Meta-backed agent cards pending | Unified Runtime A2A |
| TCP / gRPC / OpenMessaging SDKs | **Legacy-compatible** | Existing users only — kept so old clients run unmodified; not extended | [HTTP + CloudEvents](docs/eventmesh-client-guide.md) |

Status meanings:

- **GA target** — feature-complete for the current architecture, integration-tested against real brokers; safe for production.
- **Beta** — functional and tested, but semantics or deployment shape may still shift in a minor release.
- **Experimental** — under active development; APIs and storage layouts may break; wire it up on dev clusters first.
- **Legacy-compatible** — maintained for zero-change compatibility with existing clients; receives fixes but no new features. New integrations should not start here.

> Migrating off TCP / gRPC SDKs? The legacy clients keep working against the current
> runtime; see the [client guide](docs/eventmesh-client-guide.md) for the
> HTTP + CloudEvents replacement (`CloudEventsClient`).

### Documentation

- [Getting started](docs/eventmesh-getting-started.md) — zero to running runtime in minutes
- [Configuration reference](docs/eventmesh-configuration.md) — every runtime key, per-backend settings, security &amp; quota
- [Client guide](docs/eventmesh-client-guide.md) — complete `CloudEventsClient` walkthrough (pub/sub, request-reply, streaming, lite topics)
- [Architecture](docs/eventmesh-architecture.md) — control / data / agent planes; storage SPI; security gate; A2A stack
- [Features](docs/eventmesh-features.md) — feature-by-feature guide (pub/sub, A2A, connectors, security, reliability)

## Subprojects

- [EventMesh-site](https://github.com/apache/eventmesh-site): Apache official website resources for EventMesh.
- [EventMesh-workflow](https://github.com/apache/eventmesh-workflow): Serverless workflow runtime for event Orchestration on EventMesh.
- [EventMesh-dashboard](https://github.com/apache/eventmesh-dashboard): Operation and maintenance console of EventMesh.
- [EventMesh-catalog](https://github.com/apache/eventmesh-catalog): Catalog service for event schema management using AsyncAPI.
- [EventMesh-go](https://github.com/apache/eventmesh-go): A go implementation for EventMesh runtime.

## Quick start

This section of the guide will show you the steps to deploy EventMesh from [Local](#run-eventmesh-runtime-locally), [Docker](#run-eventmesh-runtime-in-docker), [K8s](#run-eventmesh-runtime-in-kubernetes).  

This section guides the launch of EventMesh according to the default configuration, if you need more detailed EventMesh deployment steps, please visit the [EventMesh official document](https://eventmesh.apache.org/docs/introduction).

#### 1. Pull EventMesh Image

Use the following command line to download the latest version of [EventMesh](https://hub.docker.com/r/apache/eventmesh):

```shell
sudo docker pull apache/eventmesh:latest
```

#### 2. Run EventMesh Runtime

Use the following command to start the EventMesh container:

```shell
sudo docker run -d --name eventmesh -p 8080:8080 -p 8081:8081 -p 8082:8082 -t apache/eventmesh:latest
```

> Ports: `8080` = traffic HTTP (`/events/*` CloudEvents API), `8081` = admin HTTP (`/admin/*`), `8082` = WebSocket push (opt-in). The connector runtime image exposes `8083` for its optional admin port.
#### 3. Publishing a CloudEvent

Applications talk to EventMesh over the `/events/*` HTTP endpoints using standard [CloudEvents](https://cloudevents.io) 1.0. Publish an event with an HTTP POST (`202 Accepted` means the event was written to the WAL):

```shell
POST /events/publish HTTP/1.1
Host: localhost:8080
Content-Type: application/cloudevents+json

{
  "specversion": "1.0",
  "id": "89010a5a-3c6f-4a1e-9b2d-0f7c1f2e3a4b",
  "source": "/example/producer",
  "type": "com.example.order.created",
  "subject": "orders",
  "datacontenttype": "application/json",
  "data": {
    "content": "Hello, EventMesh!"
  }
}
```

#### 4. Subscribing to a Topic

Subscriptions are registered with EventMesh (there are no consumer groups or tags). Provide a `clientId`, the topic, and a distribution `mode` (`LOAD_BALANCE`, `BROADCAST`, `MULTICAST`, or `LOAD_BALANCE_STICKY`). The response returns a `subscriptionId`:

```shell
POST /events/subscribe HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "clientId": "order-svc",
  "topic": "orders",
  "mode": "LOAD_BALANCE"
}
```

#### 5. Receiving Events

Subscribers can receive dispatched events through three transports. Pick whichever fits your workload — all of them deliver the same CloudEvents and honor EventMesh's ACK / at-least-once semantics.

**a) HTTP Long-Polling** — pull events; the request blocks until events arrive or the timeout elapses:

```shell
GET /events/poll?clientId=order-svc&topics=orders&timeout=30000 HTTP/1.1
Host: localhost:8080
```

After processing the delivered events, acknowledge them so the offset advances (at-least-once delivery):

```shell
POST /events/ack HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "subId": "sub-123",
  "clientId": "order-svc",
  "topic": "orders",
  "partition": 0,
  "offset": 42
}
```

**b) Server-Sent Events (SSE)** — server push over a long-lived HTTP connection; the client does not poll:

```shell
GET /events/stream?clientId=order-svc&topics=orders HTTP/1.1
Host: localhost:8080
Accept: text/event-stream
```

**c) WebSocket** — full-duplex server push over a dedicated WebSocket port:

```shell
GET /events/stream HTTP/1.1
Host: localhost:8082
Upgrade: websocket
Connection: Upgrade
```

> Long-polling, SSE, and WebSocket are interchangeable delivery transports — a subscriber chooses one. SSE and WebSocket are pushed by the server (no polling loop), while long-polling is client-driven. Request-reply (`POST /events/request` + `POST /events/reply`) is also supported. The `CloudEventsClient` Java SDK wraps all of these (`subscribe` / `subscribeSse` / `subscribeWs`); see the [CloudEvents client guide](docs/eventmesh-client-guide.md).

#### 6. Unsubscribing

When you no longer need to receive events for a topic, unsubscribe by `clientId` (optionally with the `subscriptionId`):

```shell
POST /events/unsubscribe HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "clientId": "order-svc",
  "topic": "orders"
}
```
## Contributing

[![GitHub repo Good Issues for newbies](https://img.shields.io/github/issues/apache/eventmesh/good%20first%20issue?style=flat&logo=github&logoColor=green&label=Good%20First%20issues)](https://github.com/apache/eventmesh/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22) [![GitHub Help Wanted issues](https://img.shields.io/github/issues/apache/eventmesh/help%20wanted?style=flat&logo=github&logoColor=b545d1&label=%22Help%20Wanted%22%20issues)](https://github.com/apache/eventmesh/issues?q=is%3Aopen+is%3Aissue+label%3A%22help+wanted%22) [![GitHub Help Wanted PRs](https://img.shields.io/github/issues-pr/apache/eventmesh/help%20wanted?style=flat&logo=github&logoColor=b545d1&label=%22Help%20Wanted%22%20PRs)](https://github.com/apache/eventmesh/pulls?q=is%3Aopen+is%3Aissue+label%3A%22help+wanted%22) [![GitHub repo Issues](https://img.shields.io/github/issues/apache/eventmesh?style=flat&logo=github&logoColor=red&label=Issues)](https://github.com/apache/eventmesh/issues?q=is%3Aopen)

Each contributor has played an important role in promoting the robust development of Apache EventMesh. We sincerely appreciate all contributors who have contributed code and documents.

- [Contributing Guideline](https://eventmesh.apache.org/community/contribute/contribute)
- [Good First Issues](https://github.com/apache/eventmesh/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22)


## CNCF Landscape

<div align="center">

<img src="https://landscape.cncf.io/images/cncf-landscape-horizontal-color.svg" width="200"/>

Apache EventMesh enriches the <a href="https://landscape.cncf.io/embed/embed.html?base-path=&key=serverless--framework&headers=true&category-header=true&category-in-subcategory=false&title-uppercase=false&title-alignment=left&title-font-family=sans-serif&title-font-size=13&style=shadowed&bg-color=%233e79b0&fg-color=%23ffffff&item-modal=false&item-name=false&size=md&items-alignment=left" style="width:100%;height:100%;display:block;border:none;">CNCF Cloud Native Landscape.</a>

</div>

## License

Apache EventMesh is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).

## Community

| WeChat Assistant                                        | WeChat Public Account                                  | Slack                                                                                                                                               |
|---------------------------------------------------------|--------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| <img src="resources/wechat-assistant.jpg" width="128"/> | <img src="resources/wechat-official.jpg" width="128"/> | [Join Slack Chat](https://join.slack.com/t/the-asf/shared_invite/zt-1y375qcox-UW1898e4kZE_pqrNsrBM2g)(Please open an issue if this link is expired) |

Bi-weekly meeting : [#Tencent meeting](https://meeting.tencent.com/dm/wes6Erb9ioVV) : 346-6926-0133

Bi-weekly meeting record : [bilibili](https://space.bilibili.com/1057662180)

### Mailing List

| Name        | Description                                             | Subscribe                                                  | Unsubscribe                                                    | Archive                                                                          |
|-------------|---------------------------------------------------------|------------------------------------------------------------|----------------------------------------------------------------|----------------------------------------------------------------------------------|
| Users       | User discussion                                         | [Subscribe](mailto:users-subscribe@eventmesh.apache.org)   | [Unsubscribe](mailto:users-unsubscribe@eventmesh.apache.org)   | [Mail Archives](https://lists.apache.org/list.html?users@eventmesh.apache.org)   |
| Development | Development discussion (Design Documents, Issues, etc.) | [Subscribe](mailto:dev-subscribe@eventmesh.apache.org)     | [Unsubscribe](mailto:dev-unsubscribe@eventmesh.apache.org)     | [Mail Archives](https://lists.apache.org/list.html?dev@eventmesh.apache.org)     |
| Commits     | Commits to related repositories                         | [Subscribe](mailto:commits-subscribe@eventmesh.apache.org) | [Unsubscribe](mailto:commits-unsubscribe@eventmesh.apache.org) | [Mail Archives](https://lists.apache.org/list.html?commits@eventmesh.apache.org) |
| Issues      | Issues or PRs comments and reviews                      | [Subscribe](mailto:issues-subscribe@eventmesh.apache.org)  | [Unsubscribe](mailto:issues-unsubscribe@eventmesh.apache.org)  | [Mail Archives](https://lists.apache.org/list.html?issues@eventmesh.apache.org)  |
