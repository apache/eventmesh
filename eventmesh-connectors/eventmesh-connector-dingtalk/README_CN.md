# 钉钉

## DingtalkSinkConnector：从 EventMesh 到钉钉

1. 启动你的 EventMesh Runtime。
2. 启用 sinkConnector 并检查 `sink-config.yml`。
3. 向 EventMesh 发送带有在 `pubSubConfig.subject` 中定义的主题消息。

```yaml
pubSubConfig:
  # 默认端口 10000
  meshAddress: 127.0.0.1:10000
  subject: TEST-TOPIC-DINGTALK
  idc: FT
  env: PRD
  group: dingTalkSink
  appId: 5034
  userName: dingTalkSinkUser
  passWord: dingTalkPassWord
sinkConnectorConfig:
  connectorName: dingTalkSink
  # 以下配置参考 https://open.dingtalk.com/document/orgapp/the-robot-sends-a-group-message
  appKey: dingTalkAppKey
  appSecret: dingTalkAppSecret
  openConversationId: dingTalkOpenConversationId
  robotCode: dingTalkRobotCode
```