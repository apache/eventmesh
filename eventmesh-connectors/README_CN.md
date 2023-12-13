# 连接器简介

## 连接器类型

一个连接器就是一座桥梁，代表用户应用程序与特定的外部服务或底层数据源（例如数据库）进行交互。连接器的类型可以是源（Source）或汇（Sink）。

## 数据源（Source 端）

源连接器从底层数据生产者获取数据，并在原始数据被转换为 CloudEvents 后将其传递给目标。源连接器不限制源如何检索数据（例如，源可以从消息队列中获取数据，也可以充当等待接收数据的 HTTP 服务器）。

CloudEvents 是一种以通用格式描述事件数据的规范，以提供服务、平台和系统之间的互操作性。

## 数据汇（Sink 端）

汇连接器接收 CloudEvents 并执行特定的业务逻辑（例如，MySQL 的汇连接器从 CloudEvents 中提取有用的数据，并将其写入 MySQL 数据库）。

## 实现连接器

使用 [eventmesh-openconnect-java](https://github.com/apache/eventmesh/tree/master/eventmesh-openconnect/eventmesh-openconnect-java) 实现 Source/Sink 接口即可添加新的连接器。

## 连接器实现状态

|                      连接器名称                       |   源    |   汇   |
|:------------------------------------------------:|:-----------:|:-------:|
|     [RocketMQ](eventmesh-connector-rocketmq)     |      ✅      |    ✅    |
|                     ChatGPT                      |      ⬜      |    ⬜    |
|                    ClickHouse                    |      ⬜      |    ⬜    |
|        [钉钉](eventmesh-connector-dingtalk)        |      ⬜      |    ✅    |
|                      Email                       |      ⬜      |    ⬜    |
|       [飞书/Lark](eventmesh-connector-lark)        |      ⬜      |    ✅    |
|          [文件](eventmesh-connector-file)          |      ✅      |    ✅    |
|                      GitHub                      |      ⬜      |    ⬜    |
|         [HTTP](eventmesh-connector-http)         |      ✅      |    ⬜    |
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
|         [S3 存储](eventmesh-connector-s3)          |      ⬜      |    ✅    |
|        [Slack](eventmesh-connector-slack)        |      ⬜      |    ✅    |
|       [Spring](eventmesh-connector-spring)       |      ✅      |    ✅    |
|        [企业微信](eventmesh-connector-wecom)         |      ⬜      |    ✅    |
|         [微信](eventmesh-connector-wechat)         |      ⬜      |    ✅    |
|                  更多连接器正在计划中...                   |   N/A       |   N/A   |
