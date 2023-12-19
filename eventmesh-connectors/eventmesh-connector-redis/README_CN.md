# Redis

## RedisSinkConnector：从 EventMesh 到 Redis 的消息队列

1. 启动你的 redis 实例和 EventMesh Runtime。
2. 启用 sinkConnector 并检查 `sink-config.yml`。
3. 使用在 `pubSubConfig.subject` 中指定的 Topic，向 EventMesh 发送消息。

```yaml
pubSubConfig:
  # 默认端口 10000
  meshAddress: your.eventmesh.server:10000
  subject: TopicTest  
  idc: FT  
  env: PRD
  group: redisSink
  appId: 5031
  userName: redisSinkUser
  passWord: redisPassWord
connectorConfig:
  connectorName: redisSink
  server: redis://127.0.0.1:6379
  # redis 中的主题
  topic: SinkTopic
```

## RedisSourceConnector：从 Redis 的消息队列 到 EventMesh

1. 启动你的 redis 实例和 EventMesh Runtime。 
2. 启用 sourceConnector 并检查 `source-config.yml`（与 sink-config.yml 基本相同）。 
3. 启动你的 RabbitMQConnectorServer，你会自动订阅 redis 中的主题。 
4. 向 redis 的主题发送一个 CloudEvent 消息，然后你将在 EventMesh 中接收到该消息。