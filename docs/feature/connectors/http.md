# HTTP connector

**Audience:** operators bridging EventMesh with HTTP. Generic HTTP bridge. Source is a webhook receiver: any system can POST events into EventMesh without an SDK. Sink forwards each CloudEvent to an HTTP endpoint.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.http.source.HttpSourceConnector` | JDK `HttpServer` (virtual threads) accepts POSTs on `connector.port`+`connector.path` into a buffer; `poll()` drains it as CloudEvents. |
| Sink | `org.apache.eventmesh.connector.http.sink.HttpSinkConnector` | POSTs each CloudEvent's data to `connector.url`. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.port` | `8082` | Listen port for the webhook |
| `connector.path` | `/webhook` | Listen path |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.url` | `http://localhost:9090/sink` | Target endpoint to POST into |

## Running

```bash
bin/start-connector.sh with -Dconnector.class=...HttpSourceConnector -Dconnector.mode=source -Dconnector.topic=incoming -Dconnector.port=8082
```
