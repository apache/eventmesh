# ChatGPT (OpenAI) connector

**Audience:** operators bridging EventMesh with ChatGPT (OpenAI). OpenAI bridge. Source exposes an HTTP prompt endpoint: a client POSTs a prompt, the connector optionally completes it through the OpenAI REST API and emits both as a CloudEvent. Sink forwards events to a webhook (generic).

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.chatgpt.source.ChatgptSourceConnector` | JDK `HttpServer` on `connector.port`+`connector.path` accepts `{"prompt": ...}`; with `connector.openaiApiKey` set it calls chat-completions (`connector.model`) and emits `{prompt, answer}`; without a key it emits the prompt only. `poll()` drains the buffer. |
| Sink | `org.apache.eventmesh.connector.chatgpt.sink.ChatgptSinkConnector` | POSTs each CloudEvent's payload to `connector.webhookUrl`. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.port` | `8090` | HTTP listen port |
| `connector.path` | `/chatgpt` | HTTP listen path |
| `connector.openaiApiKey` | `` | OpenAI API key (empty = no completion) |
| `connector.model` | `gpt-3.5-turbo` | Chat-completion model |
| `connector.pollTimeoutMs` | `1000` | Buffer drain wait in ms |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.webhookUrl` | `` | Webhook to POST events into |

## Running

```bash
curl -X POST http://worker:8090/chatgpt -d '{"prompt":"hi"}' then bin/start-connector.sh with -Dconnector.class=...ChatgptSourceConnector -Dconnector.mode=source -Dconnector.openaiApiKey=sk-...
```
