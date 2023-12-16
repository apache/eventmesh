# Slack

## SlackSinkConnector: From EventMesh to Slack

1. launch your EventMesh Runtime.
2. enable sinkConnector and check `sink-config.yml`.
3. send a message to EventMesh with the topic defined in `pubSubConfig.subject`

```yaml
pubSubConfig:
  # default port 10000
  meshAddress: 127.0.0.1:10000
  subject: TEST-TOPIC-SLACK
  idc: FT
  env: PRD
  group: slackSink
  appId: 5034
  userName: slackSinkUser
  passWord: slackPassWord
sinkConnectorConfig:
  connectorName: slackSink
  # Please refer to: https://api.slack.com/messaging/sending
  appToken: slackAppToken
  channelId: slackChannelId
```