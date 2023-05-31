#RabbitMQ Source Connector
从RabbitMQ中获取数据/事件，将其发布到EventMesh。
通过配置文件来定义对RabbitMQ的消费行为，来指定要发布到的EventMesh目标。

##Connector配置项

针对EventMesh目标的配置全部以`pubSubConfig`为前缀。

针对RabbitMQ的配置全部以`connectorConfig`为前缀。

| 名称                | 描述                                                                              | 可选值        | 默认值     |
|-------------------|---------------------------------------------------------------------------------|------------|---------|
| connectorName     | RabbitMQ Source Connector的名称。                                                   |            |         |
| address           | RabbitMQ Server的地址，格式为`host:port`，集群地址格式为`host1:port1,host2:port2,host3:port3`。 |            |         |
| userName          | 连接RabbitMQ Server的用户名。                                                          |            |         |
| passWord          | 连接RabbitMQ Server的密码。                                                           |            |         |
| virtualHost       | 连接到RabbitMQ的虚拟机。                                                                |            | /       |
| connectionTimeout | 与RabbitMQ建立连接的超时时间。                                                             |            | 10000ms |
| queues            | 需要消费的队列名称，以列表形式进行配置。                                                            |            |         |
| consumeMode       | 消费模式：推模式或拉模式。                                                                   | push,pull  | push    |
| qosEnable         | 当消费模式为push时，是否开启RabbitMQ的qos功能。                                                 | true,false | false   |
| prefetchCount     | 当开启qos功能时，RabbitMQ Server每次推送多少条消息。                                             |            | 1       |
| autoAck           | 消费消息后是否自动ack。                                                                   | true,false | true    |
