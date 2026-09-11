# Knative connector

**Audience:** operators bridging EventMesh with Knative. Knative eventing bridge. Source receives CloudEvents pushed by a Knative broker (the source acts as the Knative subscriber); sink forwards events to a Knative service/endpoint.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.knative.source.KnativeSourceConnector` | JDK `HttpServer` on `connector.port`+`connector.path` buffers Knative-delivered CloudEvents; `poll()` drains the buffer. |
| Sink | `org.apache.eventmesh.connector.knative.sink.KnativeSinkConnector` | POSTs each CloudEvent to `connector.sinkUrl`. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.port` | `8080` | Listen port for broker deliveries |
| `connector.path` | `/` | Listen path |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.sinkUrl` | `http://localhost:8080/sink` | Knative service endpoint |

## Running

```bash
bin/start-connector.sh with -Dconnector.class=...KnativeSourceConnector -Dconnector.mode=source -Dconnector.port=8080
```
