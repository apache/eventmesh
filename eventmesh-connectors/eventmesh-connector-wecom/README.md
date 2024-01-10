# WeCom

## WecomSinkConnector: From EventMesh to WeCom

1. launch your EventMesh Runtime.
2. enable sinkConnector and check `sink-config.yml`.
3. send a message to EventMesh with the topic defined in `pubSubConfig.subject`

```yaml
pubSubConfig:
  # default port 10000
  meshAddress: your.eventmesh.server:10000
  subject: TEST-TOPIC-WECOM
  idc: FT
  env: PRD
  group: weComSink
  appId: 5034
  userName: weComSinkUser
  passWord: weComPassWord
sinkConnectorConfig:
  connectorName: weComSink
  # Please refer to: https://developer.work.weixin.qq.com/document/path/90236
  robotWebhookKey: weComRobotWebhookKey
```

### CloudEvent Attributes

When using the eventmesh-connector-wecom sinking event, you need to add the corresponding extension filed in CloudEvent:

- When key=`wecomtemplatetype`, value=`text`/`markdown`, indicating the text type of the event.