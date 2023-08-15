<div align="center">

<br /><br />
<img src="resources/logo.png" width="256">
<br />

[![CI status](https://img.shields.io/github/actions/workflow/status/apache/eventmesh/ci.yml?logo=github&style=for-the-badge)](https://github.com/apache/eventmesh/actions/workflows/ci.yml)
[![CodeCov](https://img.shields.io/codecov/c/gh/apache/eventmesh/master?logo=codecov&style=for-the-badge)](https://codecov.io/gh/apache/eventmesh)
[![Code Quality: Java](https://img.shields.io/lgtm/grade/java/g/apache/eventmesh.svg?logo=lgtm&logoWidth=18&style=for-the-badge)](https://lgtm.com/projects/g/apache/eventmesh/context:java)
[![Total Alerts](https://img.shields.io/lgtm/alerts/g/apache/eventmesh.svg?logo=lgtm&logoWidth=18&style=for-the-badge)](https://lgtm.com/projects/g/apache/eventmesh/alerts/)

[![License](https://img.shields.io/github/license/apache/eventmesh?style=for-the-badge)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![GitHub Release](https://img.shields.io/github/v/release/apache/eventmesh?style=for-the-badge)](https://github.com/apache/eventmesh/releases)
[![Slack Status](https://img.shields.io/badge/slack-join_chat-blue.svg?logo=slack&style=for-the-badge)](https://join.slack.com/t/the-asf/shared_invite/zt-1y375qcox-UW1898e4kZE_pqrNsrBM2g)
  

[📦 Documentation](https://eventmesh.apache.org/docs/introduction) |
[📔 Examples](https://github.com/apache/eventmesh/tree/master/eventmesh-examples) |
[⚙️ Roadmap](https://eventmesh.apache.org/docs/roadmap) |
[🌐 简体中文](README.zh-CN.md)
</div>


# Apache EventMesh

**Apache EventMesh** is a new generation serverless event middleware for building distributed [event-driven](https://en.wikipedia.org/wiki/Event-driven_architecture) applications.

### EventMesh Architecture

![EventMesh Architecture](resources/eventmesh-architecture-4.png)

### EventMesh Dashboard
![EventMesh Dashboard](resources/dashboard.png)

## Features

Apache EventMesh has a vast amount of features to help users achieve their goals. Let us share with you some of the key features EventMesh has to offer:

- Built around the [CloudEvents](https://cloudevents.io) specification.
- Rapidty extendsible interconnector layer [connectors](https://github.com/apache/eventmesh/tree/master/eventmesh-connectors) such as the source or sink of Saas, CloudService, and Database etc.
- Rapidty extendsible storage layer such as [Apache RocketMQ](https://rocketmq.apache.org), [Apache Kafka](https://kafka.apache.org), [Apache Pulsar](https://pulsar.apache.org), [RabbitMQ](https://rabbitmq.com), [Redis](https://redis.io), [Pravega](https://cncf.pravega.io), and [RDMS](https://en.wikipedia.org/wiki/Relational_database)(in progress) using [JDBC](https://en.wikipedia.org/wiki/Java_Database_Connectivity).
- Rapidty extendsible controller such as [Consul](https://consulproject.org/en/), [Nacos](https://nacos.io), [ETCD](https://etcd.io) and [Zookeeper](https://zookeeper.apache.org/).
- Guaranteed at-least-once delivery.
- Deliver events between multiple EventMesh deployments.
- Event schema management by catalog service.
- Powerful event orchestration by [Serverless workflow](https://serverlessworkflow.io/) engine.
- Powerful event filtering and transformation.
- Rapid, seamless scalability.
- Easy Function develop and framework integration.

## Roadmap
Please go to the [roadmap](https://eventmesh.apache.org/docs/roadmap) to get the release history and new features of Apache EventMesh.

## Subprojects
- [EventMesh-site](https://github.com/apache/eventmesh-site): Apache official website resources for EventMesh.
- [EventMesh-workflow](https://github.com/apache/eventmesh-workflow): Serverless workflow runtime for event Orchestration on EventMesh.
- [EventMesh-dashboard](https://github.com/apache/eventmesh-dashboard): Operation and maintenance console of EventMesh.
- [EventMesh-catalog](https://github.com/apache/eventmesh-catalog): Catalog service for event schema management using AsyncAPI.
- [EventMesh-go](https://github.com/apache/eventmesh-go): A go implementation for EventMesh runtime.

## Quick start
Here are the guidelines:

[Step 1: Deploy eventmesh-store](https://eventmesh.apache.org/docs/instruction/store)

[Step 2: Start eventmesh-runtime](https://eventmesh.apache.org/docs/instruction/runtime)

[Step 3: Run our demos](https://eventmesh.apache.org/docs/instruction/demo)

Besides, we also provide the docker-version guidelines for you if you prefer Docker:

[Step 1: Deploy eventmesh-store using docker](https://eventmesh.apache.org/docs/instruction/store-with-docker)

[Step 2: Start eventmesh-runtime using docker](https://eventmesh.apache.org/docs/instruction/runtime-with-docker)

[Step 3: Run our demos](https://eventmesh.apache.org/docs/instruction/demo)

## Contributing

Each contributor has played an important role in promoting the robust development of Apache EventMesh. We sincerely appreciate all contributors who have contributed code and documents.

- [Contributing Guideline](https://eventmesh.apache.org/community/contribute/contribute)
- [Good First Issues](https://github.com/apache/eventmesh/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22)

Here is the [List of Contributors](https://github.com/apache/eventmesh/graphs/contributors), thank you all! :)

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

Apache EventMesh is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).

## Community

|WeChat Assistant|WeChat Public Account|Slack|
|-|-|-|
|<img src="resources/wechat-assistant.jpg" width="128"/>|<img src="resources/wechat-official.jpg" width="128"/>|[Join Slack Chat](https://join.slack.com/t/the-asf/shared_invite/zt-1y375qcox-UW1898e4kZE_pqrNsrBM2g)(Please open an issue if this link is expired)|

Bi-weekly meeting : [#Tencent meeting](https://meeting.tencent.com/dm/wes6Erb9ioVV) : 346-6926-0133

Bi-weekly meeting record : [bilibili](https://space.bilibili.com/1057662180)

### Mailing List

|Name|Description|Subscribe|Unsubscribe|Archive
|-|-|-|-|-|
|Users|User discussion|[Subscribe](mailto:users-subscribe@eventmesh.apache.org)|[Unsubscribe](mailto:users-unsubscribe@eventmesh.apache.org)|[Mail Archives](https://lists.apache.org/list.html?users@eventmesh.apache.org)|
|Development|Development discussion (Design Documents, Issues, etc.)|[Subscribe](mailto:dev-subscribe@eventmesh.apache.org)|[Unsubscribe](mailto:dev-unsubscribe@eventmesh.apache.org)|[Mail Archives](https://lists.apache.org/list.html?dev@eventmesh.apache.org)|
|Commits|Commits to related repositories| [Subscribe](mailto:commits-subscribe@eventmesh.apache.org) |[Unsubscribe](mailto:commits-unsubscribe@eventmesh.apache.org) |[Mail Archives](https://lists.apache.org/list.html?commits@eventmesh.apache.org)|
|Issues|Issues or PRs comments and reviews| [Subscribe](mailto:issues-subscribe@eventmesh.apache.org) |[Unsubscribe](mailto:issues-unsubscribe@eventmesh.apache.org) |[Mail Archives](https://lists.apache.org/list.html?issues@eventmesh.apache.org)|
