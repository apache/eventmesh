# DingTalk

## DingtalkSinkConnector: From EventMesh to DingTalk

1. launch your EventMesh Runtime.
2. enable sinkConnector and check `sink-config.yml`.
3. send a message to EventMesh with the topic defined in `pubSubConfig.subject`

```yaml
pubSubConfig:
  # default port 10000
  meshAddress: your.eventmesh.server:10000
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

### CloudEvent Attributes

When using the eventmesh-connector-dingtalk sinking event, you need to add the corresponding extension filed in CloudEvent:

- When key=`dingtalkTemplateType`, value=`text`/`markdown`, indicating the text type of the event.