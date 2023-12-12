# Connectors

## Connector

A connector is a bridge that interacts with a specific external service or underlying data source (e.g., Databases) on behalf of user applications. A connector is either a Source or a Sink.

## Source

A source connector obtains data from an underlying data producer, and delivers it to targets after original data has been transformed into CloudEvents. It doesn't limit the way how a source retrieves data. (e.g., A source may pull data from a message queue or act as an HTTP server waiting for data sent to it).

## Sink

A sink connector receives CloudEvents and does some specific business logics. (e.g., A MySQL Sink extracts useful data from CloudEvents and writes them to a MySQL database).
CloudEvents - A specification for describing event data in common formats to provide interoperability across services, platforms and systems.

## Implements

Add a new connector by implementing the source/sink interface using [eventmesh-openconnect-java](https://github.com/apache/eventmesh/tree/master/eventmesh-openconnect/eventmesh-openconnect-java).

## Connector Status

|                  Connector Name                  |   Source    |   Sink   |
|:------------------------------------------------:|:-----------:|:-------:|
|     [RocketMQ](eventmesh-connector-rocketmq)     |      ✅      |    ✅    |
|                     ChatGPT                      |      ⬜      |    ⬜    |
|                    ClickHouse                    |      ⬜      |    ⬜    |
|     [Dingtalk](eventmesh-connector-dingtalk)     |      ⬜      |    ✅    |
|                      Email                       |      ⬜      |    ⬜    |
|     [Feishu/Lark](eventmesh-connector-lark)      |      ⬜      |    ✅    |
|         [File](eventmesh-connector-file)         |      ✅      |    ✅    |
|                      Github                      |      ⬜      |    ⬜    |
|         [Http](eventmesh-connector-http)         |      ✅      |    ⬜    |
|         [Jdbc](eventmesh-connector-jdbc)         |      ⬜      |    ✅    |
|        [Kafka](eventmesh-connector-kafka)        |      ✅      |    ✅    |
|      [Knative](eventmesh-connector-knative)      |      ✅      |    ✅    |
|      [MongoDB](eventmesh-connector-mongodb)      |      ✅      |    ✅    |
| [OpenFunction](eventmesh-connector-openfunction) |      ✅      |    ✅    |
|      [Pravega](eventmesh-connector-pravega)      |      ✅      |    ✅    |
|   [Prometheus](eventmesh-connector-prometheus)   |      ✅      |    ⬜    |
|       [Pulsar](eventmesh-connector-pulsar)       |      ✅      |    ✅    |
|     [RabbitMQ](eventmesh-connector-rabbitmq)     |      ✅      |    ✅    |
|        [Redis](eventmesh-connector-redis)        |      ✅      |    ✅    |
|        [S3 File](eventmesh-connector-s3)         |      ⬜      |    ✅    |
|        [Slack](eventmesh-connector-slack)        |      ⬜      |    ✅    |
|       [Spring](eventmesh-connector-spring)       |      ✅      |    ✅    |
|        [WeCom](eventmesh-connector-wecom)        |      ⬜      |    ✅    |
|       [WeChat](eventmesh-connector-wechat)       |      ⬜      |    ✅    |
|         More connectors will be added...         |   N/A       |   N/A   |
