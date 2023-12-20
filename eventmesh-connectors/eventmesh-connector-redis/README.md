# Redis

Connector runs as a standalone service by `main()` [eventmesh-connectors#connector](https://github.com/apache/eventmesh/tree/master/eventmesh-connectors#connector)

## RedisSinkConnector: From EventMesh to Redis topic queue

1. start your redis instance if needed and EventMesh Runtime.
2. enable sinkConnector and check `sink-config.yml`.
3. start your `RedisConnectServer`, it will subscribe to the topic defined in `pubSubConfig.subject` of EventMesh Runtime and send data to `connectorConfig.topic` in your redis.
4. send a message to EventMesh with the topic defined in `pubSubConfig.subject` and then you will receive the message in redis.

```yaml
pubSubConfig:  
  # default port 10000
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
  # the topic in redis
  topic: SinkTopic
```

## RedisSourceConnector: From Redis topic queue to EventMesh

1. start your redis instance if needed and EventMesh Runtime.
2. enable sourceConnector and check `source-config.yml` (Basically the same as `sink-config.yml`)
3. start your `RedisConnectServer`, it will subscribe to the topic defined in `connectorConfig.topic` in your redis and send data to `pubSubConfig.subject` of EventMesh Runtime.
4. send a CloudEvent message to the topic in redis, and you will receive the message in EventMesh.