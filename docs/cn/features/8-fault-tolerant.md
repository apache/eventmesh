## 8.容错机制

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; 在金融场景下，对可用性和稳定性的要求非常高，中间件对机器故障、网络故障、应用故障以及中间件本身的故障等常见故障场景需要有容错能力，降低故障带来的影响。

### 隔离机制

##### 1. Producer端的隔离

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
Producer在往Topic发送消息时，会按照MessageQueueSelector定义的选择策略，从Topic的所有MessageQueue中选择一个作为目标队列发送消息。
当队列发生熔断，或者Broker故障导致队列发送消息异常时，如果没有对这些队列进行特殊处理，下次再轮到发这个队列的时候仍然可能失败。

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; DeFiBus提供异常队列的隔离机制，当往某个队列发送消息失败时，将队列标记为隔离状态，在隔离过期之前将不再往这个队列发送消息，避免再次失败，降低失败概率。

异常队列隔离机制分为两步：  
**-发现并标记队列为隔离**  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
在发送回调中更新发送队列的健康状态，如果执行的是onSuccess分支，则标记队列为健康，去除队列的隔离标记；如果执行的是onException分支，则标记队列为隔离状态。

**-不选择隔离中的队列发送消息**  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
在MessageQueueSelector中实现隔离机制的过滤逻辑，每次进行队列的选择时，优先从没有标记为隔离的队列中选择。当所有队列都被标记为隔离时，则从所有队列中选择，保证每次都要选出一个队列。

##### 2. Consumer端的隔离

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
Consumer由拉消息线程只负责把拉消息请求以异步发送的形式发送出去。在正常情况下，每次拉消息请求的执行都很快，不会有卡顿。一旦有Broker故障导致PullRequest的执行发生了卡顿，则该Consumer监听的所有Queue都会因为PullRequest执行的延迟而出现消息消费延迟。对于RR同步请求的场景，这种是不能够接受的。

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
创建连接采用的是同步建立连接的策略，线程执行创建新连接时必须等待连接创建完成或者连接超时。当有Broker故障连不上时，就算是异步发送，也会因为同步等待连接建立而阻塞。此时就会出现一个Broker的故障导致其他健康Broker的消息消费出现延迟。

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
DeFiBus在Consumer拉消息的过程中增加了对拉消息任务的隔离，此处的隔离指的是将疑似有问题的任务隔离到另外的线程中执行，保证拉消息线程能够正常处理其他正常的任务。当发现执行拉消息耗时超过设定的阈值时，将该拉消息任务对应的Broker列入“隔离名单”中，在隔离过期之前，隔离Broker的拉消息请求都转交给另外线程执行，避免阻塞拉消息主线程，从而避免故障的Broker影响健康Broker的消息消费时效。

### 连接空闲机制

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; 当连接的读或者写空闲超过60秒时，将主动断开连接。


---

#### Links:

* [架构介绍](../../../README.md)
* [Request-Reply调用](docs/cn/features/1-request-response-call.md)
* [灰度发布](docs/cn/features/2-dark-launch.md)
* [熔断机制](docs/cn/features/3-circuit-break-mechanism.md)
* [服务就近](docs/cn/features/4-invoke-service-nearby.md)
* [应用多活](docs/cn/features/5-multi-active.md)
* [动态扩缩队列](docs/cn/features/6-dynamic-adjust-queue.md)
* [容错机制](docs/cn/features/8-fault-tolerant.md)