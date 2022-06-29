<div align="center">

<br /><br />
<img src="docs/images/logo.png" width="256">
<br />

[![CI status](https://img.shields.io/github/workflow/status/apache/incubator-eventmesh/Continuous%20Integration?logo=github&style=for-the-badge)](https://github.com/apache/incubator-eventmesh/actions/workflows/ci.yml)
[![CodeCov](https://img.shields.io/codecov/c/gh/apache/incubator-eventmesh/master?logo=codecov&style=for-the-badge)](https://codecov.io/gh/apache/incubator-eventmesh)
[![Code Quality: Java](https://img.shields.io/lgtm/grade/java/g/apache/incubator-eventmesh.svg?logo=lgtm&logoWidth=18&style=for-the-badge)](https://lgtm.com/projects/g/apache/incubator-eventmesh/context:java)
[![Total Alerts](https://img.shields.io/lgtm/alerts/g/apache/incubator-eventmesh.svg?logo=lgtm&logoWidth=18&style=for-the-badge)](https://lgtm.com/projects/g/apache/incubator-eventmesh/alerts/)

[![License](https://img.shields.io/github/license/apache/incubator-eventmesh?style=for-the-badge)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![GitHub Release](https://img.shields.io/github/v/release/apache/eventmesh?style=for-the-badge)](https://github.com/apache/incubator-eventmesh/releases)
[![Slack Status](https://img.shields.io/badge/slack-join_chat-blue.svg?logo=slack&style=for-the-badge)](https://join.slack.com/t/apacheeventmesh/shared_invite/zt-1blhcbedu-9b7yvwAQcDs3fddZxnZXag)

[📦 Documentation](https://eventmesh.apache.org/docs/introduction) |
[📔 Examples](https://github.com/apache/incubator-eventmesh/tree/master/eventmesh-examples) |
[⚙️ Roadmap](https://eventmesh.apache.org/docs/roadmap) |
[🌐 简体中文](README.zh-CN.md)
</div>

# Apache EventMesh (Incubating)

**Apache EventMesh (Incubating)** is a dynamic [event-driven](https://en.wikipedia.org/wiki/Event-driven_architecture) application multi-runtime used to decouple the application and backend middleware layer, which supports a wide range of use cases that encompass complex multi-cloud, widely distributed topologies using diverse technology stacks.

## Features

### Multi-Runtime Architecture

![EventMesh Architecture](docs/images/eventmesh-architecture.png)

### Orchestration

![EventMesh Orchestration](docs/images/eventmesh-orchestration.png)

### Data Mesh

![EventMesh Data Mesh](docs/images/eventmesh-bridge.png)

## Components

Apache EventMesh (Incubating) consists of multiple components that integrate different middlewares and messaging protocols to enhance the functionalities of the application runtime.

- **eventmesh-runtime**: The middleware that transmits events between producers and consumers, which supports cloud-native apps and microservices.
- **eventmesh-sdk-java**: The Java SDK that supports HTTP, HTTPS, TCP, and [gRPC](https://grpc.io) protocols.
- **eventmesh-connector-plugin**: The collection of plugins that connects middlewares such as [Apache Kafka](https://kafka.apache.org), [Apache RocketMQ](https://rocketmq.apache.org), [Apache Pulsar](https://pulsar.apache.org/), [DeFiBus](https://github.com/webankfintech/DeFiBus) and [Redis](https://redis.io).
- **eventmesh-registry-plugin**: The collection of plugins that integrate service registries such as [Nacos](https://nacos.io) and [etcd](https://etcd.io).
- **eventmesh-security-plugin**: The collection of plugins that implement security mechanisms, such as ACL (access control list), authentication, and authorization.
- **eventmesh-protocol-plugin**: The collection of plugins that implement messaging protocols, such as [CloudEvents](https://cloudevents.io) and [MQTT](https://mqtt.org).
- **eventmesh-admin**: The control plane that manages clients, topics, and subscriptions.

## Downloads

Please go to the [release page](https://eventmesh.apache.org/download) to get the release of Apache EventMesh (Incubating).

## Compiling
You can use below command to compile EventMesh
```shell
./gradlew clean dist
```
All distribution are under dist/

## Quick start

[Http pub/sub](https://github.com/apache/incubator-eventmesh/blob/master/docs/en/sdk-java/02-http.md#using-curl-command)

## Contributing

Each contributor has played an important role in promoting the robust development of Apache EventMesh (Incubating). We sincerely appreciate all contributors who have contributed code and documents.

- [Contributing Guideline](https://github.com/apache/incubator-eventmesh/blob/master/docs/en/contribute/03-new-contributor-guidelines.md)
- [List of Contributors](https://github.com/apache/incubator-eventmesh/graphs/contributors)
- [Good First Issues](https://github.com/apache/incubator-eventmesh/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22)

## CNCF Landscape

<div align="center">

<img src="https://landscape.cncf.io/images/left-logo.svg" width="150"/>
<img src="https://landscape.cncf.io/images/right-logo.svg" width="200"/>

Apache EventMesh (Incubating) enriches the <a href="https://landscape.cncf.io/serverless?license=apache-license-2-0">CNCF Cloud Native Landscape.</a>

</div>

## License

Apache EventMesh (Incubating) is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).

## Community

|WeChat Assistant|WeChat Official Account|Slack|
|-|-|-|
|<img src="docs/images/contact/wechat-assistant.jpg" width="128"/>|<img src="docs/images/contact/wechat-official.jpg" width="128"/>|[Join Slack Chat](https://join.slack.com/t/apacheeventmesh/shared_invite/zt-1blhcbedu-9b7yvwAQcDs3fddZxnZXag)|

### Mailing List

|Name|Description|Subscribe|Unsubscribe|Archive
|-|-|-|-|-|
|Users|User discussion|[Subscribe](mailto:users-subscribe@eventmesh.incubator.apache.org)|[Unsubscribe](mailto:users-unsubscribe@eventmesh.incubator.apache.org)|[Mail Archives](https://lists.apache.org/list.html?users@eventmesh.apache.org)|
|Development|Development discussion (Design Documents, Issues, etc.)|[Subscribe](mailto:dev-subscribe@eventmesh.incubator.apache.org)|[Unsubscribe](mailto:dev-unsubscribe@eventmesh.incubator.apache.org)|[Mail Archives](https://lists.apache.org/list.html?dev@eventmesh.apache.org)|
|Commits|Commits to related repositories| [Subscribe](mailto:commits-subscribe@eventmesh.incubator.apache.org) |[Unsubscribe](mailto:commits-unsubscribe@eventmesh.incubator.apache.org) |[Mail Archives](https://lists.apache.org/list.html?commits@eventmesh.apache.org)|
