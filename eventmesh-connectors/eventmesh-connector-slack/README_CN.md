# Slack

## SlackSinkConnector：从 eventmesh 到 Slack

1. 启动你的 eventmesh-runtime。
2. 启用 sinkConnector 并检查 `sink-config.yml`。
3. 向 eventmesh 发送带有在 `pubSubConfig.subject` 中定义的主题消息。

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