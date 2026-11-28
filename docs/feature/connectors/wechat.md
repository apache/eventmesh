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

## CloudEvent mapping (source)

Each POSTed XML message becomes one CloudEvent:

| CloudEvent attribute | Value |
| --- | --- |
| `id` | `wechat-` + `<MsgId>` |
| `source` | `wechat` |
| `type` | `wechat.` + `<MsgType>` (e.g. `wechat.text`, `wechat.image`, `wechat.event`) |
| `subject` | `<FromUserName>` (the follower's OpenID) |
| `datacontenttype` | `application/xml` |
| `data` | the raw XML body, unchanged |

## Precautions

**Source**

- Only the GET verification request is signature-checked. POSTed messages are
  accepted without checking `signature`/`timestamp`/`nonce`, so do not expose
  the listen port directly; put it behind a reverse proxy or firewall that only
  admits WeChat traffic.
- Only plaintext messages are understood. Set the account's message
  encryption mode to plaintext (or compatible) mode; the `<Encrypt>` payload of
  safe mode is not decrypted.
- Change `connector.token` from the default `eventmesh` and use the same value
  in the WeChat admin console, otherwise verification fails (the connector
  then answers `verification failed`).
- WeChat only calls server URLs on port 80 or 443. Map that port to
  `connector.port` with a reverse proxy or port forwarding.
- The HTTP server starts on the first `poll()`, i.e. once the worker is
  running — not during `init`.
- Event pushes (`MsgType` = `event`, such as `subscribe`) carry no `MsgId`, so
  they all get the id `wechat-`. Downstream consumers that deduplicate by event
  id will drop all but one of them.
- The connector replies `success` right away and never sends a passive reply
  message. Received messages wait in an in-memory buffer until published and
  `commit` is a no-op, so messages still buffered when the worker stops are
  lost; WeChat does not retry them because it already got `success`.

**Sink**

- The sink is a plain webhook client: it POSTs the event's `data` bytes as-is
  with `Content-Type: application/json` and no extra authentication. The event
  data must already be the JSON body the target API expects.
- To send WeChat customer-service messages, point `connector.webhookUrl` at the
  `message/custom/send` API including `?access_token=...`. The connector does
  not obtain or refresh access tokens (they expire after 2 hours), so a proxy
  that injects a fresh token is usually needed.
- Failures are logged at WARN and swallowed, and the HTTP response code is not
  checked, so a failed write is still ACKed and **not** redelivered.
- With `connector.webhookUrl` unset every write fails with a WARN log.
- `WeChatSinkConfig` (`appId`/`appSecret`) in `eventmesh-common` is not read by
  this connector; only the `-D` keys above are used.

## Running

Set the account's server URL to `http://<public-host>/wechat` (forwarded to
`worker:8094`) with the matching token, then start the source:

```bash
CONNECTOR_OPTS="-Dconnector.class=org.apache.eventmesh.connector.wechat.source.WechatSourceConnector \
  -Dconnector.mode=source \
  -Dconnector.topic=wechat-messages \
  -Dconnector.port=8094 \
  -Dconnector.path=/wechat \
  -Dconnector.token=<your-token>" \
bin/start-connector.sh
```

## References

- [WeChat Official Account: access overview (server configuration and verification)](https://developers.weixin.qq.com/doc/offiaccount/en/Basic_Information/Access_Overview.html)
- [Receiving standard messages](https://developers.weixin.qq.com/doc/offiaccount/en/Message_Management/Receiving_standard_messages.html)
- [Receiving event pushes](https://developers.weixin.qq.com/doc/offiaccount/en/Message_Management/Receiving_event_pushes.html)
- [Getting an access token](https://developers.weixin.qq.com/doc/offiaccount/en/Basic_Information/Get_access_token.html)
