# Pulsar connector

**Audience:** operators bridging EventMesh with Pulsar. Bridge EventMesh topics with an Apache Pulsar cluster. Source subscribes to a topic; sink produces to a persistent topic.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.pulsar.source.PulsarSourceConnector` | Subscribes via `PulsarClient` consumer (bytes schema) and buffers messages for `poll()`. |
| Sink | `org.apache.eventmesh.connector.pulsar.sink.PulsarSinkConnector` | Produces each CloudEvent's data bytes via a Pulsar bytes producer. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.serviceUrl` | `pulsar://localhost:6650` | Pulsar service URL |
| `connector.topic` | `persistent://public/default/source` | Topic to subscribe |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.serviceUrl` | `pulsar://localhost:6650` | Pulsar service URL |
| `connector.topic` | `persistent://public/default/sink` | Target topic |

## Running

```bash
bin/start-connector.sh with -Dconnector.class=...PulsarSourceConnector -Dconnector.mode=source -Dconnector.topic=persistent://public/default/my-topic
```
