# MongoDB

## MongoDBSinkConnector：从 EventMesh 到 MongoDB

1. 启动你的 MongoDB 服务和 EventMesh Runtime。
2. 启用 sinkConnector 并检查 `sink-config.yml`。
3. 启动你的 MongoDBConnectorServer，它将订阅到 EventMesh Runtime 中 `pubSubConfig.subject` 中定义的主题，并将数据发送到 MongoDB 中的 `connectorConfig.collection`。
4. 使用在 `pubSubConfig.subject` 中指定的 Topic，向 EventMesh 发送消息，然后你将在 MongoDB 中接收到该消息。

```yaml
pubSubConfig:
  # 默认端口 10000
  meshAddress: your.eventmesh.server:10000
  subject: TopicTest  
  idc: FT  
  env: PRD  
  group: rabbitmqSink  
  appId: 5031  
  userName: rabbitmqSinkUser  
  passWord: rabbitmqPassWord  
connectorConfig:  
  connectorName: mongodbSink
  # 支持 REPLICA_SET 和 STANDALONE
  connectorType: STANDALONE
  # mongodb://root:root@127.0.0.1:27018,127.0.0.1:27019
  url: mongodb://127.0.0.1:27018
  database: yourDB
  collection: yourCol
```

## MongoDBSourceConnector：从 MongoDB 到 EventMesh

1. 启动你的 MongoDB 服务和 EventMesh Runtime。
2. 启用 sourceConnector 并检查 `source-config.yml`（与 sink-config.yml 基本相同）。
3. 启动你的 MongoDBSourceConnector，它将订阅到 MongoDB 中的 `connectorConfig.collection`，并将数据发送到 EventMesh Runtime 中的 `pubSubConfig.subject`。
4. 向 MongoDB 中 `yourDB` 的 `yourCol` 写入一个 CloudEvent 消息，然后你将在 EventMesh 中接收到该消息。