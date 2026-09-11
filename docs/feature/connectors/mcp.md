# MCP (Model Context Protocol) connector

**Audience:** operators bridging EventMesh with MCP (Model Context Protocol). MCP server bridge over JSON-RPC 2.0. Source receives server-initiated notifications (e.g. resource updates) on an HTTP endpoint; sink forwards each CloudEvent to an MCP server as a JSON-RPC request.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.mcp.source.McpSourceConnector` | JDK `HttpServer` on `connector.port`+`connector.path` accepts JSON-RPC notifications; each becomes a CloudEvent (`type` = `mcp.<method>`) and `poll()` drains them. |
| Sink | `org.apache.eventmesh.connector.mcp.sink.McpSinkConnector` | POSTs a JSON-RPC 2.0 request per CloudEvent (`method` = `connector.method`, `params` = event payload) to `connector.mcpServerUrl`; non-2xx throws → no ACK → redelivery. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.port` | `8096` | HTTP listen port |
| `connector.path` | `/mcp` | HTTP listen path |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.mcpServerUrl` | `http://localhost:3333/mcp` | MCP server endpoint |
| `connector.method` | `eventmesh.notify` | JSON-RPC method to invoke |
| `connector.timeoutMs` | `10000` | Request timeout in ms |

## Running

```bash
bin/start-connector.sh with -Dconnector.class=...McpSinkConnector -Dconnector.mode=sink -Dconnector.mcpServerUrl=http://mcp:3333/mcp
```
