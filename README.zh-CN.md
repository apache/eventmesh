<div align="center">


<br /><br />
<img src="resources/logo.svg" width="256">
<br />

[![CI status](https://img.shields.io/github/actions/workflow/status/apache/eventmesh/ci.yml?logo=github&style=for-the-badge)](https://github.com/apache/eventmesh/actions/workflows/ci.yml)
[![CodeCov](https://img.shields.io/codecov/c/gh/apache/eventmesh/master?logo=codecov&style=for-the-badge)](https://codecov.io/gh/apache/eventmesh)
[![Code Scanning](https://img.shields.io/github/actions/workflow/status/apache/eventmesh/code-scanning.yml?label=code%20scanning&logo=github&style=for-the-badge)](https://github.com/apache/eventmesh/actions/workflows/code-scanning.yml)

[![License](https://img.shields.io/github/license/apache/eventmesh?style=for-the-badge)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![GitHub Release](https://img.shields.io/github/v/release/apache/eventmesh?style=for-the-badge)](https://github.com/apache/eventmesh/releases)
[![Slack Status](https://img.shields.io/badge/slack-join_chat-blue.svg?logo=slack&style=for-the-badge)](https://join.slack.com/t/apacheeventmesh/shared_invite/zt-1t1816dli-I0t3OE~IpdYWrZbIWhMbXg)

[📦 文档(英文)](https://eventmesh.apache.org/docs/introduction) |
[📔 例子](https://github.com/apache/eventmesh/tree/master/eventmesh-examples) |
[⚙️ 路线图](https://eventmesh.apache.org/docs/roadmap) |
[🌐 英文版](README.zh-CN.md)
</div>


# Apache EventMesh

**Apache EventMesh** 是用于构建分布式[事件驱动](https://en.wikipedia.org/wiki/Event-driven_architecture)应用程序的新一代无服务器事件中间件。

### EventMesh 架构

![EventMesh Architecture](resources/eventmesh-architecture-7.png)

EventMesh 采用统一的 **CloudEvents-over-MQ** 架构。消息队列（MQ）仅作为持久化的预写日志（WAL）存储层——没有消费者组（consumer group）、没有 Tag，也不承担 Broker 端的订阅语义。所有投递逻辑由无状态的 EventMesh Runtime 掌控：其中的 **SubscriptionManager** 负责维护订阅注册表与位点（offset）跟踪，并以负载均衡（load-balance）、广播（broadcast）、多播（multicast）等语义分发事件。应用通过轻量的 **HTTP + CloudEvents 1.0** SDK（`publish` / `subscribe` / `unsubscribe`）接入，与外部系统的集成则通过 connector SPI 在独立进程的 Connector Runtime 中运行。


## 特性

Apache EventMesh 提供了丰富的能力，帮助用户轻松构建事件驱动应用。以下是让 EventMesh 与众不同的核心亮点：

**核心架构**

- **全链路 CloudEvents 原生** —— 完全基于 [CloudEvents](https://cloudevents.io) 1.0 规范构建，事件天然厂商中立、可移植。
- **轻量、语言无关的 SDK** —— 纯 HTTP 上仅需 `publish`、`subscribe`、`unsubscribe` 三个操作，无重客户端、无厂商锁定。
- **订阅与分发由 Runtime 掌控** —— 订阅状态与投递语义（负载均衡 / 广播 / 多播）由 EventMesh 自主管理，而非底层 MQ，无论后端是哪种存储，行为都保持一致。
- **MQ 仅作纯预写日志（WAL）** —— 仅追加写入，无消费者组、无 Tag；Broker 退化为纯持久化存储，大幅简化运维。
- **保证至少一次（at-least-once）投递** —— 可靠性由 EventMesh 通过自管 offset + 显式 ACK 掌控。
- **多种下发通道** —— 订阅者可自由选择 HTTP 长轮询、Server-Sent Events（SSE）或 WebSocket 推送，并支持请求-应答（request-reply）。
- **轻松水平扩展** —— 无状态 Runtime 实例可无缝扩容，无重平衡成本。

**扩展性与生态**

- **智能体协作（A2A）** —— 内置的 [A2A 协议](docs/eventmesh-a2a-protocol.md) 将 EventMesh 打造成智能体协作总线，打通同步的 MCP / JSON-RPC 2.0 工具调用与异步的事件驱动发布订阅，原生支撑大模型（LLM）与多智能体（Multi-Agent）场景。
- **可插拔存储层** —— [Apache RocketMQ](https://rocketmq.apache.org)、[Apache Kafka](https://kafka.apache.org)、[Apache Pulsar](https://pulsar.apache.org)、[RabbitMQ](https://rabbitmq.com)、[Redis](https://redis.io) 等。
- **可插拔互联层（Connector）** —— [connectors](https://github.com/apache/eventmesh/tree/develop/eventmesh-connector-plugin) 作为独立进程运行，可充当 SaaS、CloudService、数据库等的 source 或 sink。
- **可插拔元数据服务** —— [Consul](https://consulproject.org/en/)、[Nacos](https://nacos.io)、[ETCD](https://etcd.io) 和 [Zookeeper](https://zookeeper.apache.org/)。
- **事件模式管理** —— 通过目录（catalog）服务实现。
- **强大的事件编排** —— 基于 [Serverless workflow](https://serverlessworkflow.io/) 引擎。
- **强大的事件过滤与转换能力。**

## 能力状态（Capability Status）

每个 EventMesh 对外能力都有明确的状态定级。下表是**唯一的状态事实来源**——各模块文档链接到这里，
不再各自重复描述。各能力的详细指南见 [docs](docs/)。

| 能力 | 状态 | 建议 | 迁移目标 |
| --- | :---: | --- | --- |
| [HTTP + CloudEvents](docs/eventmesh-client-guide.md) | **GA 目标** | 推荐——主用户路径（`CloudEventsClient` + `/events/*`） | 主路径 |
| [Kafka / RocketMQ 存储](eventmesh-storage-plugin/)（4.x、5.x） | **GA 目标** | 推荐——可插拔 WAL 后端，TCK 覆盖（`MeshStoragePluginTCK`） | 主路径 |
| SSE / WebSocket 推送 | **Beta** | 可用——已有集成测试；统一 ACK/重投递语义仍在收敛 | 统一推送传输 |
| Connector Runtime | **Experimental** | 端到端可用，但 23 个插件中仅 4 个（file/kafka/pulsar/rocketmq）有单元测试，其余为模板实现；数据丢失加固已由 [#5328](https://github.com/apache/eventmesh/pull/5328) 落地 | SPI 拆分至 `eventmesh-connector-api`（#5328）；剩余插件测试与 GA 标准由 #5296 架构 review 跟踪 |
| [A2A / Agent 网关](docs/eventmesh-a2a-protocol.md) | **实验性** | 评估——TaskStore + Runtime 桥已落地（#5302/#5304）；reaper 与 Meta 化 AgentCard 待做 | 统一 Runtime A2A |
| TCP / gRPC / OpenMessaging SDK | **Legacy 兼容** | 仅存量用户——保持老客户端零改动运行；不再扩展 | [HTTP + CloudEvents](docs/eventmesh-client-guide.md) |

状态含义：

- **GA 目标** —— 当前架构下功能完整，已对真实 broker 做集成测试；可安全用于生产。
- **Beta** —— 功能可用且有测试，但语义或部署形态在次版本仍可能调整。
- **实验性** —— 迭代开发中；API 与存储布局可能破坏性变更；请先在开发集群试用。
- **Legacy 兼容** —— 仅为存量客户端零改动兼容而维护；只修缺陷、不加功能。新接入不要选这里。

> 正在从 TCP / gRPC SDK 迁移？Legacy 客户端在当前 Runtime 上继续可用；替代方案（HTTP +
> CloudEvents 的 `CloudEventsClient`）见[客户端指引](docs/eventmesh-client-guide.md)。

### 文档导航

- [快速上手（英文）](docs/eventmesh-getting-started.md) —— 零到运行
- [配置参考（英文）](docs/eventmesh-configuration.md) —— 每个运行时键、后端设置、安全 &amp; 配额
- [客户端指引（中文）](docs/eventmesh-client-guide.md) —— `CloudEventsClient` 完整用法
- [架构（英文）](docs/eventmesh-architecture.md) —— 控制面 / 数据面 / 智能体面；存储 SPI；安全闸门；A2A 协议栈
- [特性（英文）](docs/eventmesh-features.md) —— 逐特性说明（发布订阅、A2A、连接器、安全、可靠性）

## 子项目

- [EventMesh-site](https://github.com/apache/eventmesh-site): Apache EventMesh 的官方网站资源。
- [EventMesh-workflow](https://github.com/apache/eventmesh-workflow): 用于在 EventMesh 上进行事件编排的无服务器工作流运行时。
- [EventMesh-dashboard](https://github.com/apache/eventmesh-dashboard): EventMesh 的运维控制台。
- [EventMesh-catalog](https://github.com/apache/eventmesh-catalog): 使用 AsyncAPI 进行事件模式管理的目录服务。
- [EventMesh-go](https://github.com/apache/eventmesh-go): EventMesh 运行时的 Go 语言实现。

## 快速入门

完整的步骤引导——前置依赖、后端选择、通过 Docker 或源码启动、首个事件、三种接收传输、取消订阅以及 SDK 路径——均在
[快速上手](docs/eventmesh-getting-started.md)。该文档中的首事件示例可针对标准端口
（`8080` HTTP、`8081` 管理、`8082` WebSocket、`8083` 连接器管理）运行。

## 贡献

每个贡献者在推动 Apache EventMesh 的健康发展中都发挥了重要作用。我们真诚感谢所有为代码和文档作出贡献的贡献者。

- [贡献指南](https://eventmesh.apache.org/community/contribute/contribute)
- [Good First Issues](https://github.com/apache/eventmesh/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22)

这里是[贡献者列表](https://github.com/apache/eventmesh/graphs/contributors)，感谢大家！ :)

<a href="https://github.com/apache/eventmesh/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=apache/eventmesh" />
</a>


## CNCF Landscape

<div align="center">

<img src="https://landscape.cncf.io/images/left-logo.svg" width="150"/>
<img src="https://landscape.cncf.io/images/right-logo.svg" width="200"/>

Apache EventMesh enriches the <a href="https://landscape.cncf.io/serverless?license=apache-license-2-0">CNCF Cloud Native Landscape.</a>

</div>

## License

Apache EventMesh 的开源协议遵循 [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).

## Community

| 微信小助手                                                   | 微信公众号                                                  | Slack                                                                                                   |
|---------------------------------------------------------|--------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| <img src="resources/wechat-assistant.jpg" width="128"/> | <img src="resources/wechat-official.jpg" width="128"/> | [加入 Slack ](https://join.slack.com/t/apacheeventmesh/shared_invite/zt-1t1816dli-I0t3OE~IpdYWrZbIWhMbXg) |

双周会议 : [#Tencent meeting](https://meeting.tencent.com/dm/wes6Erb9ioVV) : 346-6926-0133

双周会议记录 : [bilibili](https://space.bilibili.com/1057662180)

### 邮件名单

| 名称      | 描述                       | 订阅                                                  | 取消订阅                                                    | 邮件列表存档                                                                  |
|---------|--------------------------|-----------------------------------------------------|---------------------------------------------------------|-------------------------------------------------------------------------|
| 用户      | 用户支持与用户问题                | [订阅](mailto:users-subscribe@eventmesh.apache.org)   | [取消订阅](mailto:users-unsubscribe@eventmesh.apache.org)   | [邮件存档](https://lists.apache.org/list.html?users@eventmesh.apache.org)   |
| 开发      | 开发相关 (设计文档， Issues等等.)   | [订阅](mailto:dev-subscribe@eventmesh.apache.org)     | [取消订阅](mailto:dev-unsubscribe@eventmesh.apache.org)     | [邮件存档](https://lists.apache.org/list.html?dev@eventmesh.apache.org)     |
| Commits | 所有与仓库相关的 commits 信息通知    | [订阅](mailto:commits-subscribe@eventmesh.apache.org) | [取消订阅](mailto:commits-unsubscribe@eventmesh.apache.org) | [邮件存档](https://lists.apache.org/list.html?commits@eventmesh.apache.org) |
| Issues  | Issues 或者 PR 提交和代码Review | [订阅](mailto:issues-subscribe@eventmesh.apache.org)  | [取消订阅](mailto:issues-unsubscribe@eventmesh.apache.org)  | [邮件存档](https://lists.apache.org/list.html?issues@eventmesh.apache.org)  |

