# Lark / Feishu connector

**Audience:** operators bridging EventMesh with Lark / Feishu. Lark (Feishu) bridge. Source receives event-subscription callbacks (answering the `url_verification` challenge automatically); sink posts events to a Lark custom-bot webhook.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.lark.source.LarkSourceConnector` | JDK `HttpServer` on `connector.port`+`connector.path`; answers `url_verification` by echoing the challenge, verifies the callback `token` against `connector.verificationToken` when set, and emits event pushes as CloudEvents. |
| Sink | `org.apache.eventmesh.connector.lark.sink.LarkSinkConnector` | POSTs each CloudEvent's payload to `connector.webhookUrl`. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.port` | `8092` | HTTP listen port |
| `connector.path` | `/lark` | HTTP listen path |
| `connector.verificationToken` | `` | Event-subscription verification token (empty = skip verify) |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.webhookUrl` | `` | Lark custom-bot webhook URL |

## Running

```bash
Set the app event-subscription request URL to http://worker:8092/lark; bin/start-connector.sh with -Dconnector.class=...LarkSourceConnector -Dconnector.mode=source
```
