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
[![Slack Status](https://img.shields.io/badge/slack-join_chat-blue.svg?logo=slack&style=for-the-badge)](https://join.slack.com/t/apacheeventmesh/shared_invite/zt-1fal6hggw-PJ3~N6Js_ZFlEvPtpQR7jg)

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
- **eventmesh-sdk-java**: The Java SDK that supports HTTP, TCP, and [gRPC](https://grpc.io) protocols.
- **eventmesh-sdk-go**: The Golang SDK that supports HTTP, TCP, and [gRPC](https://grpc.io) protocols.
- **eventmesh-connector-plugin**: The collection of plugins that connects middlewares such as [Apache RocketMQ](https://rocketmq.apache.org)(implemented) [Apache Kafka](https://kafka.apache.org)(in progress), [Apache Pulsar](https://pulsar.apache.org/)(in progress), [Pravega](https://cncf.pravega.io/)(in progress), [Redis](https://redis.io) (in progress) and [RDMS](https://en.wikipedia.org/wiki/Relational_database) using [JDBC](https://en.wikipedia.org/wiki/Java_Database_Connectivity) (in progress).
- **eventmesh-registry-plugin**: The collection of plugins that integrate service registries such as [Consul](https://consulproject.org/en/), [Nacos](https://nacos.io) and [ETCD](https://etcd.io).
- **eventmesh-security-plugin**: The collection of plugins that implement security mechanisms, such as ACL (access control list), authentication, and authorization.
- **eventmesh-protocol-plugin**: The collection of plugins that implement messaging protocols, such as [CloudEvents](https://cloudevents.io), [AMQP](https://www.amqp.org/) and [MQTT](https://mqtt.org).
- **eventmesh-workflow-go**: The [Serverless workflow](https://serverlessworkflow.io/) engine implementation.
- **eventmesh-catalog-go**: The catalog implementation follow [AsyncAPI](https://www.asyncapi.com/).
- **eventmesh-admin**: The control plane that manages clients, topics, and subscriptions.

## Roadmap

Please go to the [roadmap](https://github.com/apache/incubator-eventmesh/blob/master/docs/en/roadmap.md) to get the release history and new features of Apache EventMesh (Incubating).

## Quick start
Here are the guidelines:

[Step 1: Deploy eventmesh-store](docs/en/instruction/01-store.md)

[Step 2: Start eventmesh-runtime](docs/en/instruction/02-runtime.md)

[Step 3: Run our demos](docs/en/instruction/03-demo.md)

Besides, we also provide the docker-version guidelines for you if you prefer Docker:

[Step 1: Deploy eventmesh-store using docker](docs/en/instruction/01-store-with-docker.md)

[Step 2: Start eventmesh-runtime using docker](docs/en/instruction/02-runtime-with-docker.md)

[Step 3: Run our demos](docs/en/instruction/03-demo.md)

## Contributing

Each contributor has played an important role in promoting the robust development of Apache EventMesh (Incubating). We sincerely appreciate all contributors who have contributed code and documents.

- [Contributing Guideline](https://github.com/apache/incubator-eventmesh/blob/master/docs/en/contribute/03-new-contributor-guidelines.md)
- [Good First Issues](https://github.com/apache/incubator-eventmesh/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22)

Here is the [List of Contributors](https://github.com/apache/incubator-eventmesh/graphs/contributors), thank you all! :)

<a href="https://github.com/apache/incubator-eventmesh/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=apache/incubator-eventmesh" />
</a>


## CNCF Landscape

<div align="center">

<img src="https://landscape.cncf.io/images/left-logo.svg" width="150"/>
<img src="https://landscape.cncf.io/images/right-logo.svg" width="200"/>

Apache EventMesh (Incubating) enriches the <a href="https://landscape.cncf.io/serverless?license=apache-license-2-0">CNCF Cloud Native Landscape.</a>

</div>

## License

Apache EventMesh (Incubating) is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).

## Community

|WeChat Assistant|WeChat Public Account|Slack|
|-|-|-|
|<img src="docs/images/contact/wechat-assistant.jpg" width="128"/>|<img src="docs/images/contact/wechat-official.jpg" width="128"/>|[Join Slack Chat](https://join.slack.com/t/apacheeventmesh/shared_invite/zt-1fal6hggw-PJ3~N6Js_ZFlEvPtpQR7jg)|

### Mailing List

|Name|Description|Subscribe|Unsubscribe|Archive
|-|-|-|-|-|
|Users|User discussion|[Subscribe](mailto:users-subscribe@eventmesh.incubator.apache.org)|[Unsubscribe](mailto:users-unsubscribe@eventmesh.incubator.apache.org)|[Mail Archives](https://lists.apache.org/list.html?users@eventmesh.apache.org)|
|Development|Development discussion (Design Documents, Issues, etc.)|[Subscribe](mailto:dev-subscribe@eventmesh.incubator.apache.org)|[Unsubscribe](mailto:dev-unsubscribe@eventmesh.incubator.apache.org)|[Mail Archives](https://lists.apache.org/list.html?dev@eventmesh.apache.org)|
|Commits|Commits to related repositories| [Subscribe](mailto:commits-subscribe@eventmesh.incubator.apache.org) |[Unsubscribe](mailto:commits-unsubscribe@eventmesh.incubator.apache.org) |[Mail Archives](https://lists.apache.org/list.html?commits@eventmesh.apache.org)|
