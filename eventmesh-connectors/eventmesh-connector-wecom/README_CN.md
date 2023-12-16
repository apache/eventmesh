# 企业微信

## WecomSinkConnector：从 EventMesh 到企业微信

1. 启动你的 EventMesh Runtime。
2. 启用 sinkConnector 并检查 `sink-config.yml`。
3. 使用在 `pubSubConfig.subject` 中指定的 Topic，向 EventMesh 发送消息。

```yaml
pubSubConfig:
  # 默认端口 10000
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

### CloudEvent 属性

使用 eventmesh-connector-wecom 下沉事件时，需要在 CloudEvent 中添加对应的 extension filed：

- 当 key=`wecomTemplateType`时，value=`text`/`markdown`，表明该事件的文本类型。