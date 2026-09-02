# EventMesh Getting Started

This guide takes you from zero to a running EventMesh Runtime with a working publisher and
subscriber, using the recommended **HTTP + CloudEvents** path. Configuration reference:
[`eventmesh-configuration.md`](eventmesh-configuration.md). SDK details:
[`eventmesh-client-guide.md`](eventmesh-client-guide.md).

> Capability maturity levels (GA / Beta / Experimental / Legacy) are defined in the
> [capability status table](../README.md#capability-status) in the main README.

---

## 1. Prerequisites

- JDK 21+ (Temurin recommended)
- Docker (for the container path), or a local install of one storage backend:
  - [Apache RocketMQ](https://rocketmq.apache.org) 4.x or 5.x, **or**
  - [Apache Kafka](https://kafka.apache.org) 2.8+ (3.x recommended)
- (SDK only) Java 11+ application with `eventmesh-sdk-java` on the classpath

## 2. Choose a storage backend

The EventMesh Runtime is stateless — it owns subscriptions, offsets and delivery, and uses the
MQ purely as a write-ahead log (WAL). Pick one backend per deployment; the client side never
changes.

| Backend | Type value | Notes |
|---|---|---|
| RocketMQ 4.x | `rocketmq` | classic PULL over remoting |
| RocketMQ 5.x | `rocketmq5` | 5.x POP + Lite Topic support |
| Kafka | `kafka` | assign+seek+poll (no consumer groups), SASL/SSL supported |

## 3. Run the Runtime

### Option A — Docker

```shell
sudo docker pull apache/eventmesh:latest
sudo docker run -d --name eventmesh \
  -e EVENTMESH_STORAGE_TYPE=kafka \
  -e EVENTMESH_KAFKA_NAMESRV=YOUR_KAFKA:9092 \
  -p 8080:8080 -p 8081:8081 \
  apache/eventmesh:latest
```

Ports: `8080` = traffic HTTP (`/events/*`), `8081` = admin HTTP (`/admin/*`). The WebSocket
push port (`8082`) and the connector runtime admin port (`8083`) are opt-in.

### Option B — From source

```shell
git clone https://github.com/apache/eventmesh.git
cd eventmesh

# pick your backend via EVENTMESH_STORAGE_TYPE (rocketmq | rocketmq5 | kafka)
export EVENTMESH_STORAGE_TYPE=kafka
export EVENTMESH_KAFKA_NAMESRV=localhost:9092

./gradlew :eventmesh-runtime:clean :eventmesh-runtime:dist
cd eventmesh-runtime/dist && bash bin/start.sh
```

Storage-specific keys (all overridable via `-D` system properties) are documented in
[`eventmesh-configuration.md`](eventmesh-configuration.md#storage-backends).

### Verify it is up

```shell
curl http://localhost:8081/admin/health
# {"status":"UP"}
```

## 4. Publish your first event

Applications send standard [CloudEvents](https://cloudevents.io) 1.0 over HTTP. `202 Accepted`
means the event is durably in the WAL:

```shell
curl -X POST "http://localhost:8080/events/publish?topic=orders" \
  -H "Content-Type: application/cloudevents+json" \
  -d '{
    "specversion": "1.0",
    "id": "89010a5a-3c6f-4a1e-9b2d-0f7c1f2e3a4b",
    "source": "/example/producer",
    "type": "com.example.order.created",
    "datacontenttype": "application/json",
    "data": {"orderId": 42, "amount": 99.5}
  }'
```

## 5. Subscribe and receive

Register a subscription (there are **no consumer groups** — EventMesh tracks offsets itself),
then receive via one of three transports:

```shell
# 1. register: clientId + topic + distribution mode
curl -X POST http://localhost:8080/events/subscribe \
  -H "Content-Type: application/json" \
  -d '{"clientId":"order-svc","topic":"orders","mode":"LOAD_BALANCE"}'

# 2a. HTTP long-polling
curl "http://localhost:8080/events/poll?clientId=order-svc&topics=orders&timeout=30000"

# 2b. after processing, acknowledge so the offset advances (at-least-once)
curl -X POST http://localhost:8080/events/ack \
  -H "Content-Type: application/json" \
  -d '{"clientId":"order-svc","deliveryIds":["..."]}'
```

Distribution modes:

| Mode | Semantics |
|---|---|
| `LOAD_BALANCE` | one subscriber among the group receives each event (partition-key sticky variant available) |
| `BROADCAST` | every subscriber receives every event |
| `MULTICAST` | subscriber-side predicate filters events per client |

SSE and WebSocket push are also available (`GET /events/stream`, `subscribeWs` in the SDK) —
see the client guide for the trade-offs.

## 6. Use the SDK instead of raw HTTP (recommended)

```java
CloudEventsClient client = CloudEventsClient.builder()
    .baseUrl("http://localhost:8080")
    .build();
client.init();

// publish
client.publish("orders", CloudEventBuilder.v1()
    .withId(UUID.randomUUID().toString())
    .withSource(URI.create("/order-svc"))
    .withType("com.example.order.created")
    .withData("application/json", "{\"orderId\":42}".getBytes(UTF_8))
    .build());

// subscribe — handler return implies auto-ACK
client.subscribe("orders", "LOAD_BALANCE", event -> {
    System.out.println("got " + event.getType());
});
```

Full API (request/reply, streaming sessions, lite topics, SSE/WS): see the
[client guide](eventmesh-client-guide.md).

## 7. Where to go next

- [Configuration reference](eventmesh-configuration.md) — every runtime key, per-backend settings
- [Client guide](eventmesh-client-guide.md) — complete SDK walkthrough
- [Production readiness](production-readiness.md) — verified capabilities, SLOs, runbooks
- [A2A gateway](a2a-protocol.md) — agent-to-agent messaging (Experimental)
- Admin API (`/admin/*`) quick reference: `metrics`, `subscriptions`, `offsets`, `clients`,
  `client/reject`, `dlq/replay`, `dlq/browse`, `ratelimit`, `health`, `connectors`,
  `connector-workers` on port 8081
