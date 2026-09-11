# Pravega connector

**Audience:** operators bridging EventMesh with Pravega. Move events between EventMesh and a Pravega stream. The source reads through a reader group (offsets managed natively by Pravega); the sink appends events to a stream, creating the scope/stream on first start.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.pravega.source.PravegaSourceConnector` | `EventStreamReader` reads up to the read-timeout each poll; a `ReinitializationRequiredException` (reader-group rebalance) closes the reader and recreates it on the next poll. |
| Sink | `org.apache.eventmesh.connector.pravega.sink.PravegaSinkConnector` | `EventStreamWriter.writeEvent(routingKey=id, bytes)` for each CloudEvent; the batch is joined before returning (throw on failure → no ACK → redelivery). |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.scope` | `scope` | Pravega scope |
| `connector.stream` | `stream` | Stream to read |
| `connector.controllerUri` | `tcp://localhost:9090` | Controller URI |
| `connector.readTimeoutMs` | `1000` | Per-poll read window in ms |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.scope` | `scope` | Pravega scope (created on start) |
| `connector.stream` | `stream` | Target stream (created on start) |
| `connector.controllerUri` | `tcp://localhost:9090` | Controller URI |
| `connector.txnTimeoutMs` | `30000` | Writer transaction timeout in ms |

## Running

```bash
bin/start-connector.sh with -Dconnector.class=...PravegaSourceConnector -Dconnector.mode=source -Dconnector.scope=my-scope -Dconnector.stream=my-stream
```
