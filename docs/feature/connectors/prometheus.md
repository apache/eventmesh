# Prometheus connector

**Audience:** operators bridging EventMesh with Prometheus. Metrics bridge. Source scrapes a Prometheus `/metrics` endpoint on each poll and emits the exposition text as a CloudEvent; sink pushes metric samples (text exposition format) to a Pushgateway.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.prometheus.source.PrometheusSourceConnector` | GETs `connector.metricsUrl` each poll; the response body becomes one CloudEvent. |
| Sink | `org.apache.eventmesh.connector.prometheus.sink.PrometheusSinkConnector` | Merges the batch's payloads into one text-exposition document and POSTs it to the Pushgateway; non-2xx throws → no ACK → redelivery. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.metricsUrl` | `http://localhost:9090/metrics` | Metrics endpoint to scrape |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.pushgatewayUrl` | `http://localhost:9091` | Pushgateway base URL |
| `connector.job` | `eventmesh` | Pushgateway job label |
| `connector.instance` | `connector-1` | Pushgateway instance label |
| `connector.timeoutMs` | `10000` | Push timeout in ms |

## Running

```bash
bin/start-connector.sh with -Dconnector.class=...PrometheusSinkConnector -Dconnector.mode=sink -Dconnector.pushgatewayUrl=http://pg:9091 -Dconnector.job=eventmesh
```
