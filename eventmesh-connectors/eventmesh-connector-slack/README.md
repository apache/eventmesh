# eventmesh-connector-slack

## SlackSinkConnector：from eventmesh to slack。

1. launch your eventmesh-runtime.
2. enable sinkConnector and check `sink-config.yml`.
3. send a message to eventmesh with the topic defined in `pubSubConfig.subject`
```yaml
pubSubConfig:
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