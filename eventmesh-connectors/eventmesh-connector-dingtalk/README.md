# eventmesh-connector-dingtalk

## DingtalkSinkConnector：从 eventmesh 到 dingtalk。

1. launch your eventmesh-runtime.
2. enable sinkConnector and check `sink-config.yml`.
3. send a message to eventmesh with the topic defined in `pubSubConfig.subject`
```yaml
pubSubConfig:
  # default port is 10000
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
  # Please refer to: https://open.dingtalk.com/document/orgapp/the-robot-sends-a-group-message
  appKey: dingTalkAppKey
  appSecret: dingTalkAppSecret
  openConversationId: dingTalkOpenConversationId
  robotCode: dingTalkRobotCode
```