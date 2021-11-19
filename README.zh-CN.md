# Apache EventMesh (incubating)
[![CI status](https://github.com/apache/incubator-eventmesh/actions/workflows/ci.yml/badge.svg)](https://github.com/apache/incubator-eventmesh/actions/workflows/ci.yml)
[![CodeCov](https://codecov.io/gh/apache/incubator-eventmesh/branch/develop/graph/badge.svg)](https://codecov.io/gh/apache/incubator-eventmesh)
[![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/apache/incubator-eventmesh.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/apache/incubator-eventmesh/context:java)
[![Total alerts](https://img.shields.io/lgtm/alerts/g/apache/incubator-eventmesh.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/apache/incubator-eventmesh/alerts/)
[![GitHub release](https://img.shields.io/badge/release-download-orange.svg)](https://github.com/apache/incubator-eventmesh/releases)
[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

![logo](docs/images/logo2.png)
## 什么是Event Mesh？
EventMesh是一个动态的云原生事件驱动架构基础设施，用于分离应用程序和后端中间件层，它支持广泛的用例，包括复杂的混合云、使用了不同技术栈的分布式架构。

![architecture1](docs/images/eventmesh-multi-runtime.png)

**EventMesh生态:**
![architecture1](docs/images/eventmesh-define.png)

**EventMesh架构：**

![architecture1](docs/images/eventmesh-runtime.png)

**EventMesh云原生结构：**

![architecture2](docs/images/eventmesh-panels.png)

**支持连接的事件存储：**

* [RocketMQ](https://github.com/apache/rocketmq)：RocketMQ是一个分布式消息流平台，具有低延迟、高性能和可靠性、万亿级容量和灵活的可伸缩性。

**关键部件：**

* **eventmesh-runtime**：一种中间件，用于在事件产生者和使用者之间传输事件，支持云原生应用程序和微服务
* **eventmesh-sdk-java**：当前支持HTTP和TCP协议，未来会支持gRPC等
* **eventmesh-connector-api**：一个基于OpenMessaging api和SPI插件机制的接口层，可以有很多不同的事件存储的实现，比如IMDG，Messaging Engine和OSS等
* **eventmesh-connector-rocketmq** : 一种基于eventmesh-connector-api的实现，该实现支持将RocketMQ作为事件存储，实现事件的发布与订阅

**通信协议：**

eventmesh的通信协议更加简洁方便，详细内容，阅读更多[这里](docs/cn/instructions/eventmesh-runtime-protocol.md)

## RoadMap
| version | feature |
| ----    | ----    |
| v1.0.0  |Support java-sdk , tcp pub/sub, http pub|
| v1.1.0  |Support RocketMQ as eventstore|
| v1.1.1  |Support https|
| v1.2.0  |Support pluggable event store by OpenMessaging Pub/Sub API, http sub, docker|
| V1.3.0  |Support CloudEvents, event streaming|
|   WIP   |Support more pluggable event storage (Kafka, Pulsar, Redis, etc...)|
|   WIP   |Support Event schema|
|   WIP   |Support Event governance|
|   WIP   |Support Event function,triggers and bindings|
|   WIP   |Support Event orchestration, Servelss workflow|
|   WIP   |Support in-memory event store|
|   WIP   |Support Event transaction|
|   WIP   |Support Event security|
|   WIP   |Support multi language SDK(c\go\python\wasm)|
|   WIP   |Support metrics exporter|
|   WIP   |Support tracing exporter|
|   WIP   |Support at-least-once/at-most-once delivery guarantees|
|   WIP   |Support cold event storage (S3, Minio, SQL, key/value, etc...)|
|   WIP   |Support gRPC protocol|
|   WIP   |Support MQTT protocol|
|   WIP   |Support AsyncAPI|

## 快速开始
1. [event-store](https://rocketmq.apache.org/docs/quick-start/) (RocketMQ, ignore this step if use standalone).
2. [runtime quickstart](docs/en/instructions/eventmesh-runtime-quickstart.md) or [runtime quickstart with docker](docs/en/instructions/eventmesh-runtime-quickstart-with-docker.md).
3. [java examples ](docs/en/instructions/eventmesh-sdk-java-quickstart.md).

## 贡献
永远欢迎参与共建, 请参阅[贡献](CONTRIBUTING.zh-CN.md)了解详细指南

您可以从问题开始.
[GitHub Issues](https://github.com/apache/incubator-eventmesh/issues)

## Landscape
<p align="center">
<br/><br/>
<img src="https://landscape.cncf.io/images/left-logo.svg" width="150"/>&nbsp;&nbsp;<img src="https://landscape.cncf.io/images/right-logo.svg" width="200"/>
<br/><br/>
EventMesh enriches the <a href="https://landscape.cncf.io/serverless?license=apache-license-2-0">CNCF CLOUD NATIVE Landscape.</a>
</p>

## License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) Copyright (C) Apache Software Foundation

## 开发社区
微信群:

![wechat_qr](docs/images/mesh-helper.png)

Mailing Lists:

| 列表名称 | 描述 |订阅	|取消订阅|邮件列表存档
| ----    | ----    |----    | ----    | ----    |
|Users	|用户支持与用户问题|	[点击订阅](mailto:users-subscribe@eventmesh.incubator.apache.org)	|[点击取消订阅](mailto:users-unsubscribe@eventmesh.incubator.apache.org)	|[邮件列表存档](https://lists.apache.org/list.html?users@eventmesh.apache.org)|
|Development	|开发相关|	[点击订阅](mailto:dev-subscribe@eventmesh.incubator.apache.org)	|[点击取消订阅](mailto:dev-unsubscribe@eventmesh.incubator.apache.org)	|[邮件列表存档](https://lists.apache.org/list.html?dev@eventmesh.apache.org)|
|Commits	|所有与仓库相关的commits信息通知|	[点击订阅](mailto:commits-subscribe@eventmesh.incubator.apache.org)	|[点击取消订阅](mailto:commits-unsubscribe@eventmesh.incubator.apache.org)	|[邮件列表存档](https://lists.apache.org/list.html?commits@eventmesh.apache.org)|
