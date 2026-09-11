# WeChat Official Account connector

**Audience:** operators bridging EventMesh with WeChat Official Account. WeChat Official Account bridge. Source handles the platform's server-to-server callback — the GET echostr verification (SHA-1 of token/timestamp/nonce) and POST XML message pushes; sink posts events to a webhook.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.wechat.source.WechatSourceConnector` | JDK `HttpServer` on `connector.port`+`connector.path`; GET answers the server verification (echostr when `sha1(sort(token,timestamp,nonce))` matches); POST parses the XML message (FromUserName/MsgType/MsgId) into a CloudEvent and replies `success`. |
| Sink | `org.apache.eventmesh.connector.wechat.sink.WechatSinkConnector` | POSTs each CloudEvent's payload to `connector.webhookUrl`. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.port` | `8094` | HTTP listen port |
| `connector.path` | `/wechat` | HTTP listen path |
| `connector.token` | `eventmesh` | Server-verification token configured in the WeChat admin console |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.webhookUrl` | `` | Webhook to POST events into |

## Running

```bash
Set the account's server URL to http://worker:8094/wechat with the matching token; bin/start-connector.sh with -Dconnector.class=...WechatSourceConnector -Dconnector.mode=source
```
