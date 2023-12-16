# Slack

## SlackSinkConnector：从 EventMesh 到 Slack

1. 启动你的 EventMesh Runtime。
2. 启用 sinkConnector 并检查 `sink-config.yml`。
3. 使用在 `pubSubConfig.subject` 中指定的 Topic，向 EventMesh 发送消息。

```yaml
pubSubConfig:
  # 默认端口 10000
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
  # 以下配置请参考文档：https://api.slack.com/messaging/sending
  appToken: slackAppToken
  channelId: slackChannelId
```