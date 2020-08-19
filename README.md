[![Build Status](https://www.travis-ci.org/WeBankFinTech/DeFiBus.svg?branch=master)](https://www.travis-ci.org/WeBankFinTech/EventMesh)
[![Coverage Status](https://coveralls.io/repos/github/WeBankFinTech/DeFiBus/badge.svg?branch=master)](https://coveralls.io/github/WeBankFinTech/EventMesh?branch=master)
[![GitHub release](https://img.shields.io/badge/release-download-orange.svg)](https://github.com/WeBankFinTech/EventMesh/releases)
[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

[点我查看中文版](README.zh-CN.md)

## What is Event Mesh?
This figure shows the positioning of the event mesh relative to other similar technologies (such as service mesh) in the application framework.
![architecture1](docs/images/eventmesh-define.png)

Overview of EventMesh:
![architecture1](docs/images/eventmesh-overview.png)

Landscape of EventMesh:
![architecture1](docs/images/eventmesh-landscape.png)

Event Mesh is a dynamic plug-in cloud-native basic service layer used to decouple the application and middleware layer. It provides flexible, reliable and fast event distribution, and can be managed.
This diagram shows the architecture of EventMesh:
![architecture2](docs/images/eventmesh-arch.png)

The event mesh allows events from one application to be dynamically routed to any other application.
General functions of the event grid:
* Event driven;
* Event governance;
* Dynamic routing;
* Cloud native

Dependent components:
* DeFiBus : a distributed messaging platform with low latency, high performance and reliability, flexible scalability. [DeFiBus](https://github.com/WeBankFinTech/DeFiBus)

Key components:
* eventmesh-emesher : an middleware to transmit events between event producers and consumers, support cloud native apps and microservices
* eventmesh-sdk-java : currently supports HTTP and TCP protocols, and will support AMQP, MQTT, Websocket and JMS, gRPC etc. in the future
* eventmesh-registry : automatically routes events between applications and services connected to seperate event meshers, manage eventmesh-emesher

## Quick Start
1. Build and deploy event-store([DeFiBus](https://github.com/WeBankFinTech/DeFiBus)), 
   see instruction ['event-store quickstart'](docs/en/instructions/eventmesh-store-quickstart.md).
2. Build and deploy eventmesh-emesher, see instruction ['eventmesh-emesher quickstart'](docs/en/instructions/eventmesh-emesher-quickstart.md).
3. Run eventmesh-sdk-java demo, see instruction ['eventmesh-sdk-java quickstart'](docs/en/instructions/eventmesh-sdk-java-quickstart.md). 

## Contributing
Contributions are always welcomed! Please see [CONTRIBUTING](CONTRIBUTING.md) for detailed guidelines

You can start with the issues labeled with good first issue. 
[GitHub Issues](https://github.com/WeBankFinTech/EventMesh/issues)

## License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) Copyright (C) Apache Software Foundation

## Contacts
WeChat group：

![wechat_qr](docs/images/mesh-helper.png)


