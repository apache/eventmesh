## 什么是Event Mesh？
EventMesh是一个动态的云原生事件驱动架构基础设施，用于分离应用程序和后端中间件层，它支持广泛的用例，包括复杂的混合云、使用了不同技术栈的分布式架构。
![architecture1](images/eventmesh-define.png)

**EventMesh架构：**

![architecture1](images/eventmesh-runtime.png)

**EventMesh云原生结构：**

![architecture2](images/eventmesh-panels.png)

EventMesh允许将来自一个应用程序的事件动态路由到任何其他应用程序.
EventMesh的一般功能:

* 事件驱动;
* 事件治理;
* 动态路由;
* 云原生;

依赖部件：
* [RocketMQ](https://github.com/apache/rocketmq)：RocketMQ是一个分布式消息流平台，具有低延迟、高性能和可靠性、万亿级容量和灵活的可伸缩性。

关键部件：
* eventmesh-runtime：一种中间件，用于在事件产生者和使用者之间传输事件，支持云原生应用程序和微服务
* eventmesh-sdk-java：当前支持HTTP和TCP协议，未来会支持gRPC等
* eventmesh-registry：自动在连接到事件网格的应用程序和服务之间路由事件, 管理EventMesh


## 开源地址
* https://github.com/WeBankFinTech/DeFiBus
* https://github.com/WeBankFinTech/EventMesh
* https://gitee.com/WeBank/DeFiBus
* https://gitee.com/WeBank/EventMesh

欢迎加入社区进行交流
