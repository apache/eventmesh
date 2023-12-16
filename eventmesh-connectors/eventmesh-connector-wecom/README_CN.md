# eventmesh-connector-slack

## SlackSinkConnector：从 eventmesh 到 slack。

1. 启动你的 eventmesh-runtime。
2. 启用 sinkConnector 并检查 `sink-config.yml`。
3. 向 eventmesh 发送带有在 `pubSubConfig.subject` 中定义的主题消息。
```yaml
pubSubConfig:
  meshAddress: 127.0.0.1:10000
  subject: TEST-TOPIC-WECOM
  idc: FT
  env: PRD
  group: weComSink
  appId: 5034
  userName: weComSinkUser
  passWord: weComPassWord
sinkConnectorConfig:
  connectorName: weComSink
  # 以下配置请参考文档：https://developer.work.weixin.qq.com/document/path/90236
  robotWebhookKey: weComRobotWebhookKey
```