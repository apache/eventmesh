---
sidebar_position: 0
---

# Introduction to EventMesh

[![CI status](https://img.shields.io/github/workflow/status/apache/incubator-eventmesh/Continuous%20Integration?logo=github&style=for-the-badge)](https://github.com/apache/incubator-eventmesh/actions/workflows/ci.yml)
[![CodeCov](https://img.shields.io/codecov/c/gh/apache/incubator-eventmesh/master?logo=codecov&style=for-the-badge)](https://codecov.io/gh/apache/incubator-eventmesh)
[![Code Quality: Java](https://img.shields.io/lgtm/grade/java/g/apache/incubator-eventmesh.svg?logo=lgtm&logoWidth=18&style=for-the-badge)](https://lgtm.com/projects/g/apache/incubator-eventmesh/context:java)
[![Total Alerts](https://img.shields.io/lgtm/alerts/g/apache/incubator-eventmesh.svg?logo=lgtm&logoWidth=18&style=for-the-badge)](https://lgtm.com/projects/g/apache/incubator-eventmesh/alerts/)
[![License](https://img.shields.io/github/license/apache/incubator-eventmesh?style=for-the-badge)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![GitHub Release](https://img.shields.io/github/v/release/apache/eventmesh?style=for-the-badge)](https://github.com/apache/incubator-eventmesh/releases)
[![Slack Status](https://img.shields.io/badge/slack-join_chat-blue.svg?logo=slack&style=for-the-badge)](https://join.slack.com/t/apacheeventmesh/shared_invite/zt-16y1n77va-q~JepYy3RqpkygDYmQaQbw)

**Apache EventMesh (Incubating)** is a dynamic event-driven application runtime used to decouple the application and backend middleware layer, which supports a wide range of use cases that encompass complex multi-cloud, widely distributed topologies using diverse technology stacks.

## Features

- **Communication Protocol**: EventMesh could communicate with clients with TCP, HTTP, or gRPC.
- **CloudEvents**: EventMesh supports the [CloudEvents](https://cloudevents.io) specification as the format of the events. CloudEvents is a specification for describing event data in common formats to provide interoperability across services, platforms, and systems.
- **Schema Registry**: EventMesh implements a schema registry that receives and stores schemas from clients and provides an interface for other clients to retrieve schemas.
- **Observability**: EventMesh exposed a range of metrics, such as the average latency of the HTTP protocol and the number of delivered messages. The metrics could be collected and analyzed with Prometheus or OpenTelemetry.
- **Event Workflow Orchestration**: EventMesh Workflow could receive an event and decide which command to trigger next based on the workflow definitions and the current workflow state. The workflow definition could be written with the [Serverless Workflow](https://serverlessworkflow.io) DSL.

## Components

Apache EventMesh (Incubating) consists of multiple components that integrate different middlewares and messaging protocols to enhance the functionalities of the application runtime.

- **eventmesh-runtime**: The middleware that transmits events between producers and consumers, which supports cloud-native apps and microservices.
- **eventmesh-sdk-java**: The Java SDK that supports HTTP, TCP, and [gRPC](https://grpc.io) protocols.
- **eventmesh-connector-plugin**: The collection of plugins that connects middlewares such as [Apache Kafka](https://kafka.apache.org), [Apache RocketMQ](https://rocketmq.apache.org), [Apache Pulsar](https://pulsar.apache.org/), and [Redis](https://redis.io).
- **eventmesh-registry-plugin**: The collection of plugins that integrate service registries such as [Nacos](https://nacos.io) and [etcd](https://etcd.io).
- **eventmesh-security-plugin**: The collection of plugins that implement security mechanisms, such as ACL (access control list), authentication, and authorization.
- **eventmesh-protocol-plugin**: The collection of plugins that implement messaging protocols, such as [CloudEvents](https://cloudevents.io) and [MQTT](https://mqtt.org).
- **eventmesh-admin**: The control plane that manages clients, topics, and subscriptions.

## Contributors

Each contributor has played an important role in promoting the robust development of Apache EventMesh (Incubating). We sincerely appreciate all contributors who have contributed code and documents. The following is the list of contributors in EventMesh-related repositories.

- [apache/incubator-eventmesh](https://github.com/apache/incubator-eventmesh/graphs/contributors)
- [apache/incubator-eventmesh-site](https://github.com/apache/incubator-eventmesh-site/graphs/contributors)
