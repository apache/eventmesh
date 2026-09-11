# DingTalk connector

**Audience:** operators bridging EventMesh with DingTalk. DingTalk bridge. Source receives robot outgoing-callback messages (with HMAC signature verification); sink posts events to a DingTalk group-robot webhook.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.dingtalk.source.DingtalkSourceConnector` | JDK `HttpServer` on `connector.port`+`connector.path`; verifies `timestamp`+`sign` headers as base64(HMAC-SHA256(`timestamp + "\n" + appSecret`)) when `connector.appSecret` is set; accepted pushes become CloudEvents and `poll()` drains them. |
| Sink | `org.apache.eventmesh.connector.dingtalk.sink.DingtalkSinkConnector` | POSTs each CloudEvent's payload to `connector.webhookUrl`. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.port` | `8091` | HTTP listen port |
| `connector.path` | `/dingtalk` | HTTP listen path |
| `connector.appSecret` | `` | Robot app secret for signature verification (empty = skip verify) |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.webhookUrl` | `` | DingTalk robot webhook URL |

## Running

```bash
Configure the robot's outgoing callback to http://worker:8091/dingtalk; bin/start-connector.sh with -Dconnector.class=...DingtalkSourceConnector -Dconnector.mode=source -Dconnector.appSecret=...
```
