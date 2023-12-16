# RabbitMQ

## RabbitMQSinkConnector: From eventmesh to RabbitMQ

1. launch your RabbitMQ server and eventmesh-runtime.
2. enable sinkConnector and check `sink-config.yml`.
3. send a message to eventmesh with the topic defined in `pubSubConfig.subject`

```yaml
pubSubConfig:  
  # default port 10000
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
  # build-in exchangeName or name a new one after you create it in rabbitmq server.
  exchangeName: amq.topic  
  # rabbitmq server will create the routingKey and queueName automatically after you connect to it if they aren't exist before.
  routingKey: eventmesh  
  queueName: eventmesh  
  autoAck: true
```

## RabbitMQSourceConnector: From RabbitMQ to eventmesh

1. launch your rabbitmq server and eventmesh-runtime.
2. enable sourceConnector and check `source-config.yml` (Basically the same as `sink-config.yml`)
3. start your `RabbitMQConnectorServer` and you will find the channel in rabbitmq server.
4. send a CloudEvent message to the queue and then you will receive the message in eventmesh.