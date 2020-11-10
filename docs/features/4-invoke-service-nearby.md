## 4.服务就近
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
为了保证高可用，服务的部署通常分布在多个机房、区域。我们希望服务之间能够就近调用，减少跨机房跨区域网络访问的时延问题。对此，DeFiBus在Broker和客户端上都增加了区域的属性来标识实例属于哪个区域。对于Producer，消息会优先发往同区域内的Broker集群上；对于Consumer，则优先监听同区域内的Queue；当一个区域内没有Consumer实例监听时，则由其他区域的Consumer实例跨区域监听。

### 就近发送
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
在创建Producer时，通过设置```DeFiBusClientConfig.setClusterPrefix("your region")```来标识Producer实例所在的区域。Producer在每次发送消息会先选则一个Queue来作为发送的目标队列。当启用就近发送时，Producer优先选择与自己同区域内的Queue，当本区域内没有可用Queue时，则选择其他区域的Queue。
<div align=center>
<img src="/images/features/invoke_nearby-p1.png" width="600" />
</div>

### 就近监听
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
就近监听指的是Consumer在做负载均衡分配Queue的时候，每个区域内的Queue只由该区域内的Consumer监听和消费，当且仅当一个区域内没有订阅该Topic的Consumer时，由其他区域订阅了该Topic的Consumer跨区域监听和消费这些Queue。虽然Consumer是在同区域内就近消费，但仍通过心跳维持跨区域的连接，以保证能够随时跨区域接管消费。

<div align=center>
<img src="/images/features/subscribe-nearby-p1.png" width="600" />
</div>

---
#### Links:

* [Request-Reply调用](cn/features/1-request-response-call.md)
* [灰度发布](cn/features/2-dark-launch.md)
* [熔断机制](cn/features/3-circuit-break-mechanism.md)
* [服务就近](cn/features/4-invoke-service-nearby.md)
* [应用多活](cn/features/5-multi-active.md)
* [动态扩缩队列](cn/features/6-dynamic-adjust-queue.md)
* [容错机制](cn/features/8-fault-tolerant.md)