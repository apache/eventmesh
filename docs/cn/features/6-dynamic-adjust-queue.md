## 自动伸缩Queue
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
在同一个消费组内，每个队列只由一个实例消费。当队列数小于消费者实例数时，会有部分消费者实例分不到队列；反之，当队列数大于消费者实例数时，每个消费者需要消费多个队列。队列数不是消费者实例数的整数倍时，则会出现部分实例需要消费比同组内的其他实例更多的队列，出现负载不均衡问题。

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
DeFiBus提供了队列数量自动调整的特性。当有Consumer新注册或者去注册时，Broker触发队列的自动伸缩，根据当前在线的消费者实例个数，增加或者减少队列个数，使队列个数与消费者实例数保持一致。

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
当队列数需要增加时，首先调整Topic的ReadQueueNum，将可读的队列数扩增；10s之后，再调整Topic的WriteQueueNum，将可写的队列数扩增。这样使得新扩增的队列能够先被消费者感知并监听上，然后才让生产者感知到，往新队列上发送消息，是扩增操作更平滑。

<div align=center>
<img src="/images/features/adjust-queue-expand-p1.png" width="500" />
</div>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
当队列数需要减少时，首先调整Topic的WriteQueueNum，将可写的队列数缩减；5分钟（默认，可配置）后先检查即将被缩减的队列中是否有消息没有被消费完，如果有，则继续延迟缩减操作，使消费者能够继续消费完队列中的消息；如果没有，则调整ReadQueueNum，将可写的队列数缩减。

<div align=center>
<img src="/images/features/adjust-queue-shrink-p1.png" width="500" />
</div>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
对于多个消费组订阅相同Topic并且是集群消费模式时，在计算扩缩的队列个数时，以最大的消费组的消费者实例数为准，保证拥有最多实例数的消费组内每个消费者实例都能够分到Queue进行消费。


---
#### Links:

* [Request-Reply调用](cn/features/1-request-response-call.md)
* [灰度发布](cn/features/2-dark-launch.md)
* [熔断机制](cn/features/3-circuit-break-mechanism.md)
* [服务就近](cn/features/4-invoke-service-nearby.md)
* [应用多活](cn/features/5-multi-active.md)
* [动态扩缩队列](cn/features/6-dynamic-adjust-queue.md)
* [容错机制](cn/features/8-fault-tolerant.md)