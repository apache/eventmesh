# Connectors

## Connector 
A connector is a bridge that interacts with a specific external service or underlying data source (e.g., Databases) on behalf of user applications. A connector is either a Source or a Sink.

## Source
A source connector obtains data from an underlying data producer, and delivers it to targets after original data has been transformed into CloudEvents. It doesn't limit the way how a source retrieves data. (e.g., A source may pull data from a message queue or act as an HTTP server waiting for data sent to it).

## Sink 
A sink connector receives CloudEvents and does some specific business logics. (e.g., A MySQL Sink extracts useful data from CloudEvents and writes them to a MySQL database).
CloudEvents - A specification for describing event data in common formats to provide interoperability across services, platforms and systems.

## Implements
Add a new connector by implementing the source/sink interface using :

[eventmesh-openconnect-java](https://github.com/apache/eventmesh/tree/master/eventmesh-openconnect/eventmesh-openconnect-java)

## Connector Status


|                  Connector Name                  |    Type     | Status  |
|:------------------------------------------------:|:-----------:|:-------:|
|     [RocketMQ](eventmesh-connector-rocketmq)     |   Source    |    ✅    |
|     [RocketMQ](eventmesh-connector-rocketmq)     |    Sink     |    ✅    |
|                     ChatGPT                      |   Source    |    ⬜    |
|                     ChatGPT                      |    Sink     |    ⬜    |
|                    ClickHouse                    |   Source    |    ⬜    |
|                    ClickHouse                    |    Sink     |    ⬜    |
|                     DingDing                     |   Source    |    ⬜    |
|     [Dingtalk](eventmesh-connector-dingtalk)     |    Sink     |    ✅    |
|                      Email                       |   Source    |    ⬜    |
|                      Email                       |    Sink     |    ⬜    |
|                      Lark                        |   Source    |    ⬜    |
|         [Lark](eventmesh-connector-lark)         |    Sink     |    ✅    |
|         [File](eventmesh-connector-file)         |   Source    |    ✅    |
|         [File](eventmesh-connector-file)         |    Sink     |    ✅    |
|                      Github                      |   Source    |    ⬜    |
|                      Github                      |    Sink     |    ⬜    |
|         [Http](eventmesh-connector-http)         |   Source    |    ✅    |
|                       Http                       |    Sink     |    ⬜    |
|                       Jdbc                       |   Source    |    ⬜    |
|         [Jdbc](eventmesh-connector-jdbc)         |    Sink     |    ✅    |
|        [Kafka](eventmesh-connector-kafka)        |   Source    |    ✅    |
|        [Kafka](eventmesh-connector-kafka)        |    Sink     |    ✅    |
|      [Knative](eventmesh-connector-knative)      |   Source    |    ✅    |
|      [Knative](eventmesh-connector-knative)      |    Sink     |    ✅    |
|      [MongoDB](eventmesh-connector-mongodb)      |   Source    |    ✅    |
|      [MongoDB](eventmesh-connector-mongodb)      |    Sink     |    ✅    |
| [OpenFunction](eventmesh-connector-openfunction) |   Source    |    ✅    |
| [OpenFunction](eventmesh-connector-openfunction) |    Sink     |    ✅    |
|      [Pravega](eventmesh-connector-pravega)      |   Source    |    ✅    |
|      [Pravega](eventmesh-connector-pravega)      |    Sink     |    ✅    |
|   [Promethues](eventmesh-connector-prometheus)   |   Source    |    ✅    |
|   [Promethues](eventmesh-connector-prometheus)   |    Sink     |    ⬜    |
|       [Pulsar](eventmesh-connector-pulsar)       |   Source    |    ✅    |
|       [Pulsar](eventmesh-connector-pulsar)       |    Sink     |    ✅    |
|     [Rabbitmq](eventmesh-connector-rabbitmq)     |   Source    |    ✅    |
|     [Rabbitmq](eventmesh-connector-rabbitmq)     |    Sink     |    ✅    |
|        [Redis](eventmesh-connector-redis)        |   Source    |    ✅    |
|        [Redis](eventmesh-connector-redis)        |    Sink     |    ✅    |
|                      S3File                      |   Source    |    ⬜    |
|         [S3File](eventmesh-connector-s3)         |    Sink     |    ✅    |
|        [Slack](eventmesh-connector-slack)        |   Source    |    ⬜    |
|        [Slack](eventmesh-connector-slack)        |    Sink     |    ✅    |
|       [Spring](eventmesh-connector-spring)       |   Source    |    ✅    |
|       [Spring](eventmesh-connector-spring)       |    Sink     |    ✅    |
|                      WeCom                       |   Source    |    ⬜    |
|        [WeCom](eventmesh-connector-wecom)        |    Sink     |    ✅    |
|                      WeChat                      |   Source    |    ⬜    |
|       [WeChat](eventmesh-connector-wechat)       |    Sink     |    ✅    |
|         More connectors will be added...         | Source/Sink |   N/A   |