# WeCom (WeChat Work) connector

**Audience:** operators bridging EventMesh with WeCom (WeChat Work). WeCom bridge. Source receives WeCom callback events (plain-token verification mode: answers the GET echostr challenge, accepts POST JSON events); sink posts events to a WeCom group-robot webhook.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.wecom.source.WecomSourceConnector` | JDK `HttpServer` on `connector.port`+`connector.path`; GET echoes the `echostr` (URL challenge); POST parses the JSON event (`event_type`, `from.user_id`, `msgid`) into a CloudEvent and replies `{"errcode":0}`. |
| Sink | `org.apache.eventmesh.connector.wecom.sink.WecomSinkConnector` | POSTs each CloudEvent's payload to `connector.webhookUrl`. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.port` | `8095` | HTTP listen port |
| `connector.path` | `/wecom` | HTTP listen path |
| `connector.token` | `eventmesh` | Callback token configured in the WeCom admin console |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.webhookUrl` | `` | WeCom group-robot webhook URL |

## Running

```bash
Set the app's receive-message URL to http://worker:8095/wecom; bin/start-connector.sh with -Dconnector.class=...WecomSourceConnector -Dconnector.mode=source
```
