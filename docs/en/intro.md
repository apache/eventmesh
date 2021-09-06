# Apache EventMesh (Incubating)
[![CI status](https://github.com/apache/incubator-eventmesh/actions/workflows/ci.yml/badge.svg)](https://github.com/apache/incubator-eventmesh/actions/workflows/ci.yml)
[![CodeCov](https://codecov.io/gh/apache/incubator-eventmesh/branch/develop/graph/badge.svg)](https://codecov.io/gh/apache/incubator-eventmesh)
[![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/apache/incubator-eventmesh.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/apache/incubator-eventmesh/context:java)
[![Total alerts](https://img.shields.io/lgtm/alerts/g/apache/incubator-eventmesh.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/apache/incubator-eventmesh/alerts/)
[![GitHub release](https://img.shields.io/badge/release-download-orange.svg)](https://github.com/apache/incubator-eventmesh/releases)
[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

[点我查看中文版](../cn/README.md)

## What is Event Mesh?

This figure shows the positioning of the event mesh relative to other similar technologies (such as service mesh) in the
application framework.

![architecture1](../images/eventmesh-define.png)

Event Mesh is a dynamic plug-in cloud-native basic service layer used to decouple the application and middleware layer.
It provides flexible, reliable and fast event distribution, and can be managed.

![architecture1](../images/eventmesh-runtime.png)

Cloud Native Event Mesh:

![architecture2](../images/eventmesh-panels.png)

The event mesh allows events from one application to be dynamically routed to any other application. General functions
of the event mesh:

* Event driven;
* Event governance;
* Dynamic routing;
* Cloud native

Dependent components:

* DeFiBus : a distributed messaging platform with low latency, high performance and reliability, flexible
  scalability. [DeFiBus](https://github.com/WeBankFinTech/DeFiBus)
* RocketMQ

Key components:

* eventmesh-runtime : an middleware to transmit events between event producers and consumers, support cloud native apps
  and microservices
* eventmesh-sdk-java : currently supports HTTP and TCP protocols, and will support gRPC in the future

## RoadMap

| version | feature |
| ----    | ----    |
| v1.0.0  |Support DeFiBus as eventstore, support pub/sub, http api, java-sdk|
| v1.1.0  |Support rocketmq as eventstore|
| v1.2.0  |Support Plug-in architecture, support http sub|
| v1.3.0 |Support cloud event protocal|
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
|         |Support c/go/python/nodejs SDK|

## Quick Start

1. Build and deploy event-store([DeFiBus](https://github.com/WeBankFinTech/DeFiBus)), see
   instruction ['event-store quickstart'](instructions/eventmesh-store-quickstart.md).
2. Build and deploy eventmesh-runtime, see
   instruction ['eventmesh-runtime quickstart'](instructions/eventmesh-runtime-quickstart.md).
3. Run eventmesh-sdk-java demo, see
   instruction ['eventmesh-sdk-java quickstart'](instructions/eventmesh-sdk-java-quickstart.md).

## Contributing

Contributions are always welcomed! Please see [CONTRIBUTING](../../CONTRIBUTING.md) for detailed guidelines

You can start with the issues labeled with good first issue.
[GitHub Issues](https://github.com/apache/incubator-eventmesh/issues)

## License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) Copyright (C) Apache Software Foundation

## Contacts

WeChat group：

![wechat_qr](../images/mesh-helper.png)


