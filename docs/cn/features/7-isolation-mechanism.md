## 隔离机制

Producer在往Topic发送消息时，会按照MessageQueueSelector定义的选择策略，从Topic的所有MessageQueue中选择一个作为目标队列发送消息。
当队列发生熔断，或者Broker故障导致队列发送消息异常时，如果没有对这些队列进行特殊处理，下次再轮到发这个队列的时候仍然可能失败。

DeFiBus提供异常队列的隔离机制，当往某个队列发送消息失败时，将队列标记为隔离状态，在隔离过期之前将不再往这个队列发送消息，避免再次失败，降低失败概率。

异常队列隔离机制分为两步：  
**-发现并标记队列为隔离**  
在发送回调中更新发送队列的健康状态，如果执行的是onSuccess分支，则标记队列为健康，去除队列的隔离标记；如果执行的是onException分支，则标记队列为隔离状态。

**-不选择隔离中的队列发送消息**  
在MessageQueueSelector中实现隔离机制的过滤逻辑，每次进行队列的选择时，优先从没有标记为隔离的队列中选择。当所有队列都被标记为隔离时，则从所有队列中选择，保证每次都要选出一个队列。

---

#### Links:

* [架构介绍](../../../README.zh-CN.md)
* [Request-Reply调用](../features/1-request-response-call.md)
* [灰度发布](../features/2-dark-launch.md)
* [熔断机制](../features/3-circuit-break-mechanism.md)
* [服务就近](../features/4-invoke-service-nearby.md)
* [应用多活](../features/5-multi-active.md)
* [动态扩缩队列](../features/6-dynamic-adjust-queue.md)
* [容错机制](../features/8-fault-tolerant.md)