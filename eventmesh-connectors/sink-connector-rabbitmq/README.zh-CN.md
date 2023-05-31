#RabbitMQ Sink Connector
将从EventMesh订阅到的数据/事件，发送到RabbitMQ。
通过配置文件指定要从EventMesh订阅的内容，指定要发送到的RabbitMQ目标。

##Connector配置项

针对EventMesh目标的配置全部以`pubSubConfig`为前缀。

针对RabbitMQ的配置全部以`connectorConfig`为前缀。

| 名称                                                                                                                      | 描述                                                                                                                                        | 可选值        | 默认值        |
|-------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|------------|------------|
| connectorName                                                                                                           | RabbitMQ Sink Connector的名称。                                                                                                               |            |            |
| address                                                                                                                 | RabbitMQ Server的地址，格式为`host:port`，集群地址格式为`host1:port1,host2:port2,host3:port3`。                                                           |            |            |
| userName                                                                                                                | 连接RabbitMQ Server的用户名。                                                                                                                    |            |            |
| passWord                                                                                                                | 连接RabbitMQ Server的密码。                                                                                                                     |            |            |
| virtualHost                                                                                                             | 连接到RabbitMQ的虚拟机。                                                                                                                          |            | /          |
| connectionTimeout                                                                                                       | 与RabbitMQ建立连接的超时时间。                                                                                                                       |            | 10000ms    |
| confirmListener                                                                                                         | 是否开启当消息无法被RabbitMQ处理时的回调                                                                                                                  | true,false | false      |
| returnListener                                                                                                          | 是否开启当消息无法被RabbitMQ路由时的回调                                                                                                                  | true,false | false      |
| passive                                                                                                                 | 当指定的RabbitMQ目标不存在时，是否创建目标                                                                                                                 | true,false | true：表示不创建 |
| destinations<table><tr><td>exchange</td></tr><tr><td>routingKey</td></tr><tr><td>exchangeType</td></tr></tbody></table> | 指定消息要发送到的RabbitMQ目标，以列表形式配置多个目标。<table><tr><td>交换机名称</td></tr><tr><td>路由键</td></tr><tr><td>交换机类型，当passive配置为false时该配置生效</td></tr></table> |            |            |
