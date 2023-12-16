# RabbitMQ

## RabbitMQSinkConnector：从 eventmesh 到 RabbitMQ

1. 启动你的 RabbitMQ 服务和 eventmesh-runtime。
2. 启用 sinkConnector 并检查 `sink-config.yml`。
3. 向 eventmesh 发送带有在 `pubSubConfig.subject` 中定义的主题消息。

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
  connectorName: rabbitmqSink  
  host: your.rabbitmq.server
  port: 5672  
  username: coyrqpyz  
  passwd: passwd 
  virtualHost: coyrqpyz  
  exchangeType: TOPIC  
  # 使用内置的 exchangeName 或在连接到 rabbitmq 服务后创建新的 exchangeName。
  exchangeName: amq.topic  
  # 如果在连接之前不存在，rabbitmq 服务将自动创建 routingKey 和 queueName。
  routingKey: eventmesh  
  queueName: eventmesh  
  autoAck: true
```

## RabbitMQSourceConnector：从 RabbitMQ 到 eventmesh

1. 启动你的 rabbitmq 服务和 eventmesh-runtime。 
2. 启用 sourceConnector 并检查 `source-config.yml`（与 sink-config.yml 基本相同）。 
3. 启动你的 RabbitMQConnectorServer，你会在 rabbitmq 服务中找到该 channel。 
4. 向队列发送一个 CloudEvent 消息，然后你将在 eventmesh 中接收到该消息。