#RabbitMQ Source Connector
Retrieve data/events from RabbitMQ and publish them to EventMesh.
Configuration file: define the consumption behavior for RabbitMQ, specify the EventMesh destination to publish to.

##Connector configuration items

All configurations for the EventMesh destination are prefixed with `pubSubConfig`.

All configurations for RabbitMQ are prefixed with `connectorConfig`.

| Name              | Description                                                                                               | Optional   | Default |
|-------------------|-----------------------------------------------------------------------------------------------------------|------------|---------|
| connectorName     | Name of RabbitMQ Source Connector                                                                         |            |         |
| address           | Address of RabbitMQ Server. Format is `host:port`，Cluster format is `host1:port1,host2:port2,host3:port3` |            |         |
| userName          | Username to connect to RabbitMQ server.                                                                   |            |         |
| passWord          | Password to connect to RabbitMQ server.                                                                   |            |         |
| virtualHost       | The virtual host RabbitMQ to connect.                                                                     |            | /       |
| connectionTimeout | The timeout for establishing a connection with RabbitMQ.                                                  |            | 10000ms |
| queues            | The list of queue names to consume from.                                                                  |            |         |
| consumeMode       | The consumption mode: push or pull.                                                                       | push,pull  | push    |
| qosEnable         | Whether to enable the QoS feature of RabbitMQ when the consumption mode is push.                          | true,false | false   |
| prefetchCount     | The number of messages pushed by RabbitMQ server at a time when QoS is enabled.                           |            | 1       |
| autoAck           | Whether to automatically acknowledge the consumed message.                                                | true,false | true    |
