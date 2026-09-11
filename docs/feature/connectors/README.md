# Connectors

**Audience:** operators and developers wiring EventMesh to external systems —
message queues, databases, chat platforms, AI services. Covers the connector
model, the 23 shipped plugins, how to configure and run them. For the API a
plugin implements see [Connector API split plan](../connector-api-split.md);
for runtime configuration of the connector scheduler see
[Configuration reference](../../quickstart/configuration.md).

---

## The connector model

A **connector** moves CloudEvents between EventMesh and an external system on
one direction:

- a **Source** pulls (or receives) data from the external system and the
  connector runtime publishes it into EventMesh over HTTP;
- a **Sink** long-polls EventMesh for deliveries and writes them into the
  external system.

The contract lives in `eventmesh-connector-api`:

| Method | Source | Sink |
| --- | --- | --- |
| `init(Properties)` | configure from `-D` flags | configure from `-D` flags |
| `resume(String)` / `poll()` | resume from an offset marker; pull the next batch | — |
| `put(List<CloudEvent>)` | — | write a batch (throw on failure → no ACK → redelivery) |
| `commit(...)` | checkpoint after EventMesh accepted the publish | checkpoint after the write |

Delivery semantics are **at-least-once**: sources checkpoint only after the
runtime accepted the publish, and a failing sink write leaves the delivery
un-ACKed so EventMesh redelivers (dedup by event id downstream).

## Running a connector

Each plugin ships inside the connector-runtime image; `bin/start-connector.sh`
starts a worker:

```bash
# CONNECTOR_OPTS carries the plugin class and every -D flag the plugin reads
CONNECTOR_OPTS="-Dconnector.class=org.apache.eventmesh.connector.kafka.source.KafkaSourceConnector \
  -Dconnector.mode=source \
  -Dconnector.topic=my-topic \
  -DbootstrapServers=broker:9092" \
bin/start-connector.sh
```

Common flags: `eventmesh.runtime.url` (default `http://localhost:8080`),
`connector.offset.mode` (`remote` | `rocksdb` | `inmemory`), and for multiple
connectors per process the numbered form `-Dconnector.1.class=...`,
`-Dconnector.2.class=...` (any `-Dconnector.N.*` key is passed through to the
plugin's `init`). The runtime can also schedule connectors dynamically via
`/admin/connectors` — see the [Admin API](../admin-api.md).

## Plugin catalog

**Message queues**

- [Kafka connector](kafka.md)
- [RocketMQ 4.x connector](rocketmq.md)
- [RabbitMQ connector](rabbitmq.md)
- [Pulsar connector](pulsar.md)
- [Pravega connector](pravega.md)

**Web & serverless**

- [HTTP connector](http.md)
- [Knative connector](knative.md)
- [OpenFunction connector](openfunction.md)

**Framework bridges**

- [Spring connector](spring.md)

**Databases**

- [JDBC connector](jdbc.md)
- [Canal (MySQL CDC) connector](canal.md)
- [MongoDB connector](mongodb.md)

**Storage**

- [Amazon S3 connector](s3.md)
- [File connector](file.md)
- [Redis connector](redis.md)

**Observability**

- [Prometheus connector](prometheus.md)

**AI & chat**

- [ChatGPT (OpenAI) connector](chatgpt.md)
- [MCP (Model Context Protocol) connector](mcp.md)

**Chat & IM**

- [DingTalk connector](dingtalk.md)
- [Lark / Feishu connector](lark.md)
- [Slack connector](slack.md)
- [WeChat Official Account connector](wechat.md)
- [WeCom (WeChat Work) connector](wecom.md)


## Plugin matrix

| Plugin | Source | Sink | Client dependency |
| --- | :-: | :-: | --- |
| [Kafka](kafka.md) | ✓ | ✓ | kafka-clients 3.9.0 |
| [RocketMQ 4.x](rocketmq.md) | ✓ | ✓ | rocketmq-client |
| [RabbitMQ](rabbitmq.md) | ✓ | ✓ | amqp-client 5.22.0 |
| [Pulsar](pulsar.md) | ✓ | ✓ | pulsar-client |
| [Pravega](pravega.md) | ✓ | ✓ | pravega-client 0.11.0 |
| [HTTP](http.md) | ✓ | ✓ | — |
| [Knative](knative.md) | ✓ | ✓ | — |
| [OpenFunction](openfunction.md) | ✓ | ✓ | — |
| [Spring](spring.md) | ✓ | ✓ | — |
| [JDBC](jdbc.md) | ✓ | ✓ | — |
| [Canal (MySQL CDC)](canal.md) | ✓ | ✓ | canal.client 1.1.7 |
| [MongoDB](mongodb.md) | ✓ | ✓ | mongodb-driver-sync 4.11.0 |
| [Amazon S3](s3.md) | ✓ | ✓ | aws-sdk-s3 |
| [File](file.md) | ✓ | ✓ | — |
| [Redis](redis.md) | ✓ | ✓ | redisson |
| [Prometheus](prometheus.md) | ✓ | ✓ | — |
| [ChatGPT (OpenAI)](chatgpt.md) | ✓ | ✓ | — |
| [MCP (Model Context Protocol)](mcp.md) | ✓ | ✓ | — |
| [DingTalk](dingtalk.md) | ✓ | ✓ | — |
| [Lark / Feishu](lark.md) | ✓ | ✓ | — |
| [Slack](slack.md) | ✓ | ✓ | — |
| [WeChat Official Account](wechat.md) | ✓ | ✓ | — |
| [WeCom (WeChat Work)](wecom.md) | ✓ | ✓ | — |

## Adding a plugin

Implement `SourceConnector` / `SinkConnector` from `eventmesh-connector-api`
in a new `eventmesh-connector-plugin/eventmesh-connector-<name>` module,
register the module in `settings.gradle`, and model the implementation on an
existing plugin of the same style (pull-based like kafka/jdbc, push-receiver
like http/dingtalk, or writer like rabbitmq/mongodb).
