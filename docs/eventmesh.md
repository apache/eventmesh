## 什么是Event Mesh？
该图显示了Event Mesh相对于应用程序框架中其他类似技术(例如Service Mesh)的定位.
![architecture1](docs/images/eventmesh-define.png)

Event Mesh是一个动态的插件式云原生基础服务层，用于分离应用程序和中间件层。它提供了灵活，可靠和快速的事件分发，并且可以进行管理：
![architecture1](docs/images/eventmesher.png)

云原生Event Mesh：
![architecture2](docs/images/bus.png)

Event Mesh允许将来自一个应用程序的事件动态路由到任何其他应用程序.
Event Mesh的一般功能:
* 事件驱动;
* 事件治理;
* 动态路由;
* 云原生;

依赖部件：
* 可选1：DeFiBus：具有低延迟，高性能和可靠性，和灵活可伸缩性的分布式消息传递平台 [DeFiBus](https://github.com/WeBankFinTech/DeFiBus)
* 可选2：RocketMQ

关键部件：
* event mesher：一种中间件，用于在事件产生者和使用者之间传输事件，支持云原生应用程序和微服务
* sdk：当前支持HTTP和TCP协议，未来会支持gRPC等
* registry：自动在连接到事件网格的应用程序和服务之间路由事件, 管理event mesher


## 开源地址
* https://github.com/WeBankFinTech/DeFiBus
* https://github.com/WeBankFinTech/EventMesh
* https://gitee.com/WeBank/DeFiBus
* https://gitee.com/WeBank/EventMesh

欢迎加入社区进行交流
