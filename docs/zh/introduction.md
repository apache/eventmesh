# Apache EventMesh (Incubating)

[![CI status](https://github.com/apache/incubator-eventmesh/actions/workflows/ci.yml/badge.svg)](https://github.com/apache/incubator-eventmesh/actions/workflows/ci.yml)
[![CodeCov](https://codecov.io/gh/apache/incubator-eventmesh/branch/develop/graph/badge.svg)](https://codecov.io/gh/apache/incubator-eventmesh)
[![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/apache/incubator-eventmesh.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/apache/incubator-eventmesh/context:java)
[![Total alerts](https://img.shields.io/lgtm/alerts/g/apache/incubator-eventmesh.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/apache/incubator-eventmesh/alerts/)
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

部件：

* eventmesh-runtime：一种中间件，用于在事件生产者和消费者之间传输事件，支持云原生应用程序和微服务
* eventmesh-sdk-java：当前支持HTTP、HHTTP、TCP和 [gRPC](https://grpc.io) 协议


## 快速开始

1. 构建并部署event-store(RocketMQ), 请参见[说明](https://rocketmq.apache.org/docs/quick-start/)
2. 构建并部署eventmesh-runtime，请参见[说明](installation/eventmesh-runtime-quickstart.zh-CN.md)
3. 运行eventmesh-sdk-java演示，请参见[说明](installation/eventmesh-sdk-java-quickstart.zh-CN.md)

## 贡献

永远欢迎参与共建, 请参阅[贡献](../../03-new-contributor-guidelines.md)了解详细指南

您可以从发现和解决问题开始～
[GitHub Issues](https://github.com/apache/incubator-eventmesh/issues)

