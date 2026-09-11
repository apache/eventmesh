# RocketMQ 4.x connector

**Audience:** operators bridging EventMesh with RocketMQ 4.x. Bridge EventMesh topics with an Apache RocketMQ 4.x cluster (nameserver-based). Source pulls from a consumer group; sink produces with a producer group.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.rocketmq.source.RocketmqSourceConnector` | Consumes a topic via `DefaultMQPushConsumer`; messages buffer into an internal queue that `poll()` drains. |
| Sink | `org.apache.eventmesh.connector.rocketmq.sink.RocketmqSinkConnector` | Sends each CloudEvent via `DefaultMQProducer` to the configured topic. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.namesrvAddr` | `localhost:9876` | Name server address |
| `connector.topic` | `source-topic` | Topic to consume |
| `connector.group` | `connector-source` | Consumer group name |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.namesrvAddr` | `localhost:9876` | Name server address |
| `connector.group` | `connector-sink` | Producer group name |
| `connector.topic` | `sink-topic` | Target topic (set via producer topic config) |

## Running

```bash
bin/start-connector.sh with -Dconnector.class=...RocketmqSourceConnector -Dconnector.mode=source -Dconnector.topic=my-topic -Dconnector.namesrvAddr=ns:9876
```
