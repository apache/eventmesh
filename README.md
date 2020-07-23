[![Build Status](https://www.travis-ci.org/WeBankFinTech/DeFiBus.svg?branch=master)](https://www.travis-ci.org/WeBankFinTech/EventMesh) [![Coverage Status](https://coveralls.io/repos/github/WeBankFinTech/DeFiBus/badge.svg?branch=master)](https://coveralls.io/github/WeBankFinTech/EventMesh?branch=master)

## What is an Event Mesh?
This diagram shows where an event mesh fits in an application stack relative to other technologies such as service mesh:
![architecture1](docs/images/eventmesh-define.png)

An event mesh is a configurable and dynamic infrastructure layer for distributing events among decoupled applications, cloud services and devices. It enables event communications to be governed, flexible, reliable and fast. An event mesh is created and enabled through a network of interconnected event meshers.

## What are the core capabilities of an event mesh?
This diagram shows the architecture of EventMesh:
![architecture2](docs/images/eventmesh-arch.png)

An event mesh allows events from one application to be dynamically routed and received by any other application no matter where these applications are deployed (no cloud, private cloud, public cloud). 
The generic capabilities of an event mesh:
* inherently ‘event-driven;’
* created by connecting event meshers;
* environment agnostic (can be deployed anywhere); and,
* dynamic.

Key components:
* eventmesh-emesher : an middleware to transmit events between event producers and consumers, support cloud native apps and microservices
* eventmesh-sdk-java : support for popular open standard protocols and APIs, including REST/HTTP, AMQP, MQTT, Websocket and JMS, gRPC etc.
* eventmesh-registry : automatically routes events between applications and services connected to seperate event meshers
* eventmesh-governance : governace layer for event producers and consumers
* eventmesh-acl : security at various level of authentication, authorization and topic/channel access control
* eventmesh-store : the store layer of Event-Mesh which implemented with [DeFiBus](https://github.com/WeBankFinTech/DeFiBus)(based on RocketMQ in financial scenario) or RocketMQ by default. We wish the store layeris a general solution and can use any store implement such as kafka, redis ,blockchain etc.

## Quick Start
Coming soon...

## License
EventMesh is licensed under [Apache License](https://github.com/WeBankFinTech/DeFiBus/blob/master/LICENSE).

## Contacts
WeChat group：

![wechat_qr](./docs/images/wechat_helper.png)


