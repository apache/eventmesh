# Apache EventMesh (Incubating)
[![Build Status](https://www.travis-ci.org/apache/incubator-eventmesh.svg?branch=develop)](https://www.travis-ci.org/github/apache/incubator-eventmesh.svg?branch=develop)
[![CodeCov](https://codecov.io/gh/apache/incubator-eventmesh/branch/develop/graph/badge.svg)](https://codecov.io/gh/apache/incubator-eventmesh)
[![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/apache/incubator-eventmesh.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/apache/incubator-eventmesh/context:java)
[![Total alerts](https://img.shields.io/lgtm/alerts/g/apache/incubator-eventmesh.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/apache/incubator-eventmesh/alerts/)
[![GitHub release](https://img.shields.io/badge/release-download-orange.svg)](https://github.com/apache/incubator-eventmesh/releases)
[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

[点我查看中文版](cn/README.md)

## What is Event Mesh?

EventMesh is a dynamic cloud-native eventing infrastruture used to decouple the application and backend middleware
layer, which supports a wide range of use cases that encompass complex multi-cloud, widely distributed topologies using
diverse technology stacks.
![architecture1](images/eventmesh-define.png)

**EventMesh Architecture:**

![architecture1](images/eventmesh-runtime.png)

**EventMesh Cloud Native Structure:**

![architecture2](images/eventmesh-panels.png)

The event mesh allows events from one application to be dynamically routed to any other application. General functions
of the event mesh:

* Event driven;
* Event governance;
* Dynamic routing;
* Cloud native

Dependent components:

* [RocketMQ](https://github.com/apache/rocketmq):RocketMQ is a distributed messaging and streaming platform with low
  latency, high performance and reliability, trillion-level capacity and flexible scalability.

Key components:

* eventmesh-runtime : an middleware to transmit events between event producers and consumers, support cloud native apps
  and microservices
* eventmesh-sdk-java : currently supports HTTP and TCP protocols, and will support gRPC in the future

## RoadMap

| version | feature |
| ----    | ----    |
| v1.0.0  |Support pub/sub, http api, java-sdk|
| v1.1.0  |Support rocketmq as eventstore|
| v1.2.0  |Support Plug-in architecture, support http sub，support cloud native deploy|
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

## Quick Start

1. Build and deploy event-store(RocketMQ), see [instruction](https://rocketmq.apache.org/docs/quick-start/).
2. Build and deploy eventmesh-runtime, see
   instruction ['eventmesh-runtime quickstart'](en/instructions/eventmesh-runtime-quickstart.md).
3. Run eventmesh-sdk-java demo, see
   instruction ['eventmesh-sdk-java quickstart'](en/instructions/eventmesh-sdk-java-quickstart.md).

## Contributing

Contributions are always welcomed! Please see [CONTRIBUTING](../CONTRIBUTING.md) for detailed guidelines

You can start with the issues labeled with good first issue.
[GitHub Issues](https://github.com/WeBankFinTech/EventMesh/issues)

## License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) Copyright (C) Apache Software Foundation

## Contacts

WeChat group：

![wechat_qr](images/mesh-helper.png)


