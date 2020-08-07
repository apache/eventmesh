[![Build Status](https://www.travis-ci.org/WeBankFinTech/DeFiBus.svg?branch=master)](https://www.travis-ci.org/WeBankFinTech/EventMesh) [![Coverage Status](https://coveralls.io/repos/github/WeBankFinTech/DeFiBus/badge.svg?branch=master)](https://coveralls.io/github/WeBankFinTech/EventMesh?branch=master)

## What is Event Mesh?
This figure shows the positioning of the event mesh relative to other similar technologies (such as service mesh) in the application framework
![architecture1](docs/images/eventmesh-define.png)

Event grid is a dynamic plug-in basic service layer used to distribute events among decoupled applications, cloud services and devices. It enables the communication of events to be supervised and governed, and the communication of events is flexible, reliable and fast.
## What are the core capabilities of an event mesh?
This diagram shows the architecture of EventMesh:
![architecture2](docs/images/eventmesh-arch.png)

The event grid allows events from one application to be dynamically routed to any other application.
General functions of the event grid:
* Essentially "event driven";
* Event can be governanced;
* Dynamic routing;
* Cloud native

Key components:
* eventmesh-emesher : an middleware to transmit events between event producers and consumers, support cloud native apps and microservices
* eventmesh-sdk-java : support for popular open standard protocols and APIs, including REST/HTTP, AMQP, MQTT, Websocket and JMS, gRPC etc.
* eventmesh-registry : automatically routes events between applications and services connected to seperate event meshers
* eventmesh-governance : governace layer for event producers and consumers
* eventmesh-acl : security at various level of authentication, authorization and topic/channel access control
* eventmesh-store : the store layer of Event-Mesh which implemented with [DeFiBus](https://github.com/WeBankFinTech/DeFiBus)(based on RocketMQ in financial scenario) or RocketMQ by default. We wish the store layeris a general solution and can use any store implement such as kafka, redis ,blockchain etc.

## Quick Start
1. Build eventmesh-store to your own local maven repository first, because eventmesh depends on defibus as store layer by default. 
2. Build eventmesh-emesher and start it by sh bin/start.sh.
3. Run eventmesh-sdk-java demo. 

## Contributing
Contributions are always welcomed! Please see [CONTRIBUTING](CONTRIBUTING.md) for detailed guidelines.

You can start with the issues labeled with good first issue.

## License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) Copyright (C) Apache Software Foundation

## Contacts
WeChat group：

![wechat_qr](./docs/images/mesh-helper.png)


