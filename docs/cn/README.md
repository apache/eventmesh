# Apache EventMesh (Incubating)
[![Build Status](https://www.travis-ci.org/apache/incubator-eventmesh.svg?branch=develop)](https://www.travis-ci.org/github/apache/incubator-eventmesh.svg?branch=develop)
[![GitHub release](https://img.shields.io/badge/release-download-orange.svg)](https://github.com/apache/incubator-eventmesh/releases)
[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

## 什么是Event Mesh？

EventMesh是一个动态的云原生事件驱动架构基础设施，用于分离应用程序和后端中间件层，它支持广泛的用例，包括复杂的混合云、使用了不同技术栈的分布式架构。

![architecture1](../images/eventmesh-define.png)

**EventMesh架构：**

![architecture1](../images/eventmesh-runtime.png)

**EventMesh云原生结构：**

![architecture2](../images/eventmesh-panels.png)

Event Mesh允许将来自一个应用程序的事件动态路由到任何其他应用程序. Event Mesh的一般功能:

* 事件驱动;
* 事件治理;
* 动态路由;
* 云原生

依赖部件：

* [RocketMQ](https://github.com/apache/rocketmq)：RocketMQ是一个分布式消息流平台，具有低延迟、高性能和可靠性、万亿级容量和灵活的可伸缩性。

关键部件：

* eventmesh-runtime：一种中间件，用于在事件产生者和使用者之间传输事件，支持云原生应用程序和微服务
* eventmesh-sdk-java：当前支持HTTP和TCP协议，未来会支持gRPC等

## RoadMap

| version | feature |
| ----    | ----    |
| v1.0.0  |Support pub/sub, http api, java-sdk|
| v1.1.0  |Support rocketmq as eventstore|
| v1.2.0  |Support Plug-in architecture, support http sub|
| V1.3.0 |Support CloudEvents protocol|
|   |Support transaction event|
|         |Support Event Sourcing|
|         |Support Event orchestration|
|         |Support Dashboard|
|         |Support Event governance|
|         |Support Nacos as an event router|
|         |Support Promethus|
|         |Support Skywalking|
|         |Support Spiffe|
|         |Support gRPC|
|         |Support c/go/python/NodeJs/wasm SDK|

## 快速开始

1. 构建并部署event-store(RocketMQ), 请参见[说明](https://rocketmq.apache.org/docs/quick-start/)
2. 构建并部署eventmesh-runtime，请参见说明['eventmesh-runtime quickstart'](instructions/eventmesh-runtime-quickstart.zh-CN.md)
3. 运行eventmesh-sdk-java演示，请参见说明['eventmesh-sdk-java quickstart'](instructions/eventmesh-sdk-java-quickstart.zh-CN.md)

## 贡献

永远欢迎参与共建, 请参阅[贡献](../../CONTRIBUTING.zh-CN.md)了解详细指南

您可以从问题开始.
[GitHub Issues](https://github.com/apache/incubator-eventmesh/issues)

## License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) Copyright (C) Apache Software Foundation

## 联系人

微信群:

![wechat_qr](../images/mesh-helper.png)
