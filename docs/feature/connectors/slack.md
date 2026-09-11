# Slack connector

**Audience:** operators bridging EventMesh with Slack. Slack bridge. Source receives Events API callbacks (verifying the v0 HMAC request signature and answering the challenge); sink posts events to a Slack incoming webhook.

---

## Classes

| Direction | Class | Behavior |
| --- | --- | --- |
| Source | `org.apache.eventmesh.connector.slack.source.SlackSourceConnector` | JDK `HttpServer` on `connector.port`+`connector.path`; when `connector.signingSecret` is set, verifies `X-Slack-Signature` (v0 = HMAC-SHA256 of `v0:timestamp:body`) + timestamp; answers `url_verification` challenges; event pushes become CloudEvents. |
| Sink | `org.apache.eventmesh.connector.slack.sink.SlackSinkConnector` | POSTs each CloudEvent's payload to `connector.webhookUrl`. |

## Source configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.port` | `8093` | HTTP listen port |
| `connector.path` | `/slack` | HTTP listen path |
| `connector.signingSecret` | `` | Slack app signing secret (empty = skip verify) |

## Sink configuration

| Key | Default | Description |
| --- | --- | --- |
| `connector.webhookUrl` | `` | Slack incoming webhook URL |

## Running

```bash
Point the Slack app's Events API request URL at http://worker:8093/slack; bin/start-connector.sh with -Dconnector.class=...SlackSourceConnector -Dconnector.mode=source -Dconnector.signingSecret=...
```
