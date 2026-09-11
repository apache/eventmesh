# Kafka connector

**Audience:** operators bridging EventMesh with Kafka. Move events between EventMesh topics and an Apache Kafka cluster. The source consumes a Kafka topic with a consumer group and commits offsets only after EventMesh accepted the publish; the sink writes CloudEvent payloads to a target topic.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.kafka.source.KafkaSourceConnector` | Polls a source topic via `KafkaConsumer` (manual offset commit); each record becomes a CloudEvent (`id = topic-partition-offset`). `commit()` runs `commitSync` after the runtime accepted the batch. |
| Sink | `org.apache.eventmesh.connector.kafka.sink.KafkaSinkConnector` | Produces each CloudEvent's data bytes to the target topic via `KafkaProducer`. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `bootstrapServers` | `localhost:9092` | Kafka bootstrap servers |
| `topic` | `source-topic` | Topic to consume |
| `groupId` | `eventmesh-connector-source` | Consumer group id |
| `pollTimeoutMs` | `1000` | Kafka poll timeout in ms |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `bootstrapServers` | `localhost:9092` | Kafka bootstrap servers |
| `topic` | `sink-topic` | Target topic to produce into |

## Running

```bash
bin/start-connector.sh with CONNECTOR_OPTS="-Dconnector.class=org.apache.eventmesh.connector.kafka.source.KafkaSourceConnector -Dconnector.mode=source -Dconnector.topic=my-topic -DbootstrapServers=broker:9092"
```
