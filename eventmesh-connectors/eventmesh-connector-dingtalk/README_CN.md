# 钉钉

## DingtalkSinkConnector：从 EventMesh 到钉钉

1. 启动你的 EventMesh Runtime。
2. 启用 sinkConnector 并检查 `sink-config.yml`。
3. 使用在 `pubSubConfig.subject` 中指定的 Topic，向 EventMesh 发送消息。

```yaml
pubSubConfig:
  # 默认端口 10000
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
  # 以下配置参考 https://open.dingtalk.com/document/orgapp/the-robot-sends-a-group-message
  appKey: dingTalkAppKey
  appSecret: dingTalkAppSecret
  openConversationId: dingTalkOpenConversationId
  robotCode: dingTalkRobotCode
```

### CloudEvent 属性

使用 eventmesh-connector-dingtalk 下沉事件时，需要在 CloudEvent 中添加对应的 extension filed：

- 当 key=`dingtalktemplatetype`时，value=`text`/`markdown`，表明该事件的文本类型。
- 当文本类型 `dingtalktemplatetype` 为 markdown 时，可以为文本设置标题。添加 extension：key=`dingtalkmarkdownmessagetitle`，value 为该事件的标题。