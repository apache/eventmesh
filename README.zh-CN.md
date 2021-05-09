# Apache EventMesh (Incubating)
[![Build Status](https://www.travis-ci.org/apache/incubator-eventmesh.svg?branch=develop)](https://www.travis-ci.org/github/apache/incubator-eventmesh.svg?branch=develop)
[![GitHub release](https://img.shields.io/badge/release-download-orange.svg)](https://github.com/apache/incubator-eventmesh/releases)
[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

## 什么是Event Mesh？
EventMesh是一个动态的云原生事件驱动架构基础设施，用于分离应用程序和后端中间件层，它支持广泛的用例，包括复杂的混合云、使用了不同技术栈的分布式架构。
![architecture1](docs/images/eventmesh-multi-runtime.png)

**EventMesh生态:**
![architecture1](docs/images/eventmesh-define.png)

**EventMesh架构：**

![architecture1](docs/images/eventmesh-runtime.png)

**EventMesh云原生结构：**

![architecture2](docs/images/eventmesh-panels.png)

Event Mesh允许将来自一个应用程序的事件动态路由到任何其他应用程序.
Event Mesh的一般功能:
* 事件驱动;
* 事件治理;
* 动态路由;
* 云原生;
* 流控；
* 负载均衡

**支持连接的事件存储：**

* [RocketMQ](https://github.com/apache/rocketmq)：RocketMQ是一个分布式消息流平台，具有低延迟、高性能和可靠性、万亿级容量和灵活的可伸缩性。

**关键部件：**

* **eventmesh-runtime**：一种中间件，用于在事件产生者和使用者之间传输事件，支持云原生应用程序和微服务
* **eventmesh-sdk-java**：当前支持HTTP和TCP协议，未来会支持gRPC等
* **eventmesh-connector-rocketmq** : 一种基于OpenMessagingConnector 接口的实现，该实现支持将RocketMQ作为事件存储，实现事件的发布与订阅

**通信协议：**

eventmesh的通信协议更加简洁方便，详细内容，阅读更多[这里](docs/cn/instructions/eventmesh-runtime-protocol.zh-CN.md)

## RoadMap
| version | feature |
| ----    | ----    |
| v1.0.0  |Support java-sdk , tcp pub/sub, http pub|
| v1.1.0  |Support RocketMQ as eventstore|
| v1.1.1  |Support https|
| v1.2.0  |Support OpenMessaging API，support Plug-in architecture, support http sub, support cloud native deploy|
| V1.3.0  |Support CloudEvents, Event Streaming|
|         |Support Event function,triggers and bindings|
|         |Support Event orchestration, Servelss workflow|
|         |Support Event transaction|
|         |Support Event schema|
|         |Support Event governance, dashboard|
|         |Support Event security|
|         |Support multi language SDK(c\go\python\wasm)|
|         |Support Promethus as metrics|
|         |Support Skywalking as tracing|
|         |Support streaming event store|
|         |Support gRPC protocol|
|         |Support MQTT protocol|

## 快速开始
1. 构建并部署event-store(RocketMQ), 请参见[说明](https://rocketmq.apache.org/docs/quick-start/)
2. 构建并部署eventmesh-runtime，请参见说明['eventmesh-runtime quickstart.zh-CN'](docs/cn/instructions/eventmesh-runtime-quickstart.zh-CN.md)
3. 运行eventmesh-sdk-java演示，请参见说明['eventmesh-sdk-java quickstart.zh-CN'](docs/cn/instructions/eventmesh-sdk-java-quickstart.zh-CN.md)

## 贡献
永远欢迎参与共建, 请参阅[贡献](CONTRIBUTING.zh-CN.md)了解详细指南

您可以从问题开始.
[GitHub Issues](https://github.com/apache/incubator-eventmesh/issues)

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
