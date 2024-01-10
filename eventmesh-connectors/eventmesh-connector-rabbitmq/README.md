# RabbitMQ

## RabbitMQSinkConnector: From EventMesh to RabbitMQ

1. launch your RabbitMQ server and EventMesh Runtime.
2. enable sinkConnector and check `sink-config.yml`.
3. start your `RabbitMQConnectorServer`, it will subscribe to the topic defined in `pubSubConfig.subject` of EventMesh Runtime and send data to `connectorConfig.queueName` in your RabbitMQ.
4. send a message to EventMesh with the topic defined in `pubSubConfig.subject` and then you will receive the message in RabbitMQ.

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

## RabbitMQSourceConnector: From RabbitMQ to EventMesh

1. launch your RabbitMQ server and EventMesh Runtime.
2. enable sourceConnector and check `source-config.yml` (Basically the same as `sink-config.yml`)
3. start your `RabbitMQConnectorServer`, it will subscribe to the queue defined in `connectorConfig.queueName` in your RabbitMQ and send data to `pubSubConfig.subject` of EventMesh Runtime.
4. send a CloudEvent message to the queue and then you will receive the message in EventMesh.