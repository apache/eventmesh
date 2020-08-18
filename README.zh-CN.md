[![Build Status](https://www.travis-ci.org/WeBankFinTech/DeFiBus.svg?branch=master)](https://www.travis-ci.org/WeBankFinTech/EventMesh)
[![Coverage Status](https://coveralls.io/repos/github/WeBankFinTech/DeFiBus/badge.svg?branch=master)](https://coveralls.io/github/WeBankFinTech/EventMesh?branch=master)
[![GitHub release](https://img.shields.io/badge/release-download-orange.svg)](https://github.com/WeBankFinTech/EventMesh/releases)
[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

## 什么是Event Mesh？
该图显示了Event Mesh相对于应用程序框架中其他类似技术(例如Service Mesh)的定位.
![architecture1](docs/images/eventmesh-define.png)

Event Mesh是一个动态的插件式云原生基础服务层，用于分离应用程序和中间件层。它提供了灵活，可靠和快速的事件分发，并且可以进行管理
下图显示了Event Mesh的体系结构:
![architecture2](docs/images/eventmesh-arch.png)

Event Mesh允许将来自一个应用程序的事件动态路由到任何其他应用程序.
Event Mesh的一般功能:
* 事件驱动;
* 事件治理;
* 动态路由;
* 云原生

依赖部件：
* defibus：具有低延迟，高性能和可靠性，和灵活可伸缩性的分布式消息传递平台 [DefiBus](https://github.com/WeBankFinTech/DeFiBus)

关键部件：
* eventmesh-emesher：一种中间件，用于在事件产生者和使用者之间传输事件，支持云原生应用程序和微服务
* eventmesh-sdk-java：支持流行的开放标准协议和API，包括REST / HTTP，AMQP，MQTT，Websocket和JMS，gRPC等
* eventmesh-registry：自动在连接到单独事件网格器的应用程序和服务之间路由事件, 管理emesher

## 快速开始
1.构建并部署eventmesh-emesher，请参见说明['eventmesh-emesher quickstart'](docs/cn/instructions/eventmesh-emesher-quickstart.zh-CN.md)
2.运行eventmesh-sdk-java演示，请参见说明['eventmesh-sdk-java quickstart'](docs/cn/instructions/eventmesh-sdk-java-quickstart.zh-CN.md)

## 贡献
永远欢迎捐款！请参阅[贡献](CONTRIBUTING.zh-CN.md)了解详细指南

您可以从问题开始. 
[GitHub Issues](https://github.com/WeBankFinTech/EventMesh/issues)

## License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) Copyright (C) Apache Software Foundation

## 联系人
微信群:
![wechat_qr](docs/images/mesh-helper.png)