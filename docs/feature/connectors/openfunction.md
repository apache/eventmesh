# OpenFunction connector

**Audience:** operators bridging EventMesh with OpenFunction. OpenFunction bridge. Source receives function output pushes (the function runtime POSTs its result to the connector endpoint); sink invokes a function's HTTP trigger with CloudEvent headers (`Ce-Id`/`Ce-Type`/`Ce-Source`).

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.openfunction.source.OpenfunctionSourceConnector` | JDK `HttpServer` on `connector.port`+`connector.path` receives function outputs (`X-Function-Name` header → CloudEvent subject); `poll()` drains. |
| Sink | `org.apache.eventmesh.connector.openfunction.sink.OpenfunctionSinkConnector` | POSTs each CloudEvent (data + Ce-* headers) to `connector.functionUrl`; non-2xx throws → no ACK → redelivery. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.port` | `8097` | Listen port for function outputs |
| `connector.path` | `/openfunction` | Listen path |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.functionUrl` | `http://localhost:8081/function` | Function HTTP trigger URL |
| `connector.timeoutMs` | `30000` | Connect/read timeout in ms |

## Running

```bash
bin/start-connector.sh with -Dconnector.class=...OpenfunctionSinkConnector -Dconnector.mode=sink -Dconnector.functionUrl=http://fn.default.svc.cluster.local:8080
```
