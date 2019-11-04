## DeFiBus (Decentralized Financial Message Bus)
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
**DeFiBus=RPC+MQ, a reliable distributed financial message bus which is build base on open sourcing message mid-ware. DeFiBus offers not only traditional feature of MQ system (event notification, multicast, broadcast etc.) but also synchronize call and high availability such as application multi active, service nearby, dark launch. The enhancement of fault tolerant enable DeFiBus running more reliable and offer an all days online service.**  


## Architecture  
<div align=center>

![architecture1](./docs/images/features/a-distributing-architecture-in-financial-EN.png)

</div>

![architecture2](./docs/images/features/architecture-p1.png)

DeFiBus includes the following components:
* **Broker**: Offering message store via the mechanism of Topic and queue. Broker registers to NameServer periodically. Brokers in the same cluster should register to the same NameServer to keep route info in all name server is consistent.

* **NameServer**: NameServer maintains route info of Topic and provide clients an interface to get route info of given Topic.

* **GSL**: GSL(Global Service Location) service provides service-level routing discovery. Services can be deployed in different areas (such as different data centers, logical partitions, etc.). When requesting a specific service, service requesters need not pay attention to the area of service username. GSL can automatically locate specific services according to service discovery rules and return service information to clients.

* **SGS**: SGS(Service Governance System) is responsible for the overall service management, including new service applying, service username, service offline. In DeFiBus, every service correspond to exactly one Topic. The creation, update and deletion of Topic are managed by SGS. After creating Topic in Broker cluster, SGS updates global service routing data for GSL.

* **Proxy**: Proxy provides TCP/HTTP access, and allows access to client of other languages, such as C/C++, Go, Python etc.


## Definition of Service & Topic
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
Each service in DeFiBus correspond to exactly a Topic. Topic is named according to the unique ID of the service and the username area of the service. Each service needs a unique identity, which can be represented by a digital ID or string. Each username area is represented by a string of length 3, which is consist of numbers and letters.  

Topic is named in the following format:
```
[区域代码]-[服务唯一ID]
``` 
For example, the service ID of the balance query service is 20190001, and it is deployed in the area of "A10". The Topic of such service in the area of A10 is named "A10-20190001". 

## 特性列表:
* [Request-Reply call:support sync invocation between systems](docs/cn/features/1-request-response-call.md)
* [Pub/Sub:message publish and subscribe](docs/cn/features/9-publish-type.md)
* [Dark launch: support dark launch at level of Topic](docs/cn/features/2-dark-launch.md)
* [Circuit break mechanism: circuit break at level of client instance](docs/cn/features/3-circuit-break-mechanism.md)
* [flow control: limit flow at the level of queue](docs/cn/features/10-flow-control.md)
* [Service nearby: invoke service nearby, reducing invocation cross region](docs/cn/features/4-invoke-service-nearby.md)
* [Multi active: multi active of application among multi region or idc](docs/cn/features/5-multi-active.md)
* [Dynamic expand/shrink queue: expand/shrink queue number according to alive instance adaptive](docs/cn/features/6-dynamic-adjust-queue.md)
* [Fault tolerant: fault isolation](docs/cn/features/8-fault-tolerant.md)
* GSL(under planning for open sourcing)
* Proxy(under planning open sourcing)
* SGS(under planning open sourcing)
* graceful upgrade(under planning open sourcing)

## Quick Start
We offer an instruction of quick start, read[ this ](docs/cn/quickstart.md) to get more information.   
Examples are also provide in DeFiBus-examples module, get more detail from [here](defibus-examples).

## License
DeFiBus is licensed under [Apache License](https://github.com/WeBankFinTech/DeFiBus/blob/master/LICENSE).

## Contacts
WeChat & QQ group：

![wechat_qr](./docs/images/wechat_helper.png)
![qqgroup_qr](./docs/images/qqgroup-crcode.png)

DingDing group: 23372793
