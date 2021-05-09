## 1. Request-Reply同步调用

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; Request-Reply同步调用指的是请求方发出一条消息之后，需要响应方在消费完这条消息后回复一个响应结果。

<div align=center>

![RR](../../images/features/RR-call-p1.png)

</div>

整个调用过程包含了两个消息的产生和消费过程。  

**1.请求方产生请求消息，服务响应方消费这条请求消息**  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; 请求方根据服务提供方的协议将请求内容设置到消息体中，并将消息发送到Broker上。服务响应方订阅相应的Topic，从Broker上获取到请求消息，并消费。

**2.服务响应方产生响应消息，请求方接收这条响应消息**  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
服务响应方收到请求消息后，执行相应的处理，并将请求结果设置到响应消息的消息体中，将响应消息发送到Broker上。请求方接收响应消息的方式采用的是Broker推送的形式，而不是由Producer订阅的方式，从而使得响应消息能够精准回到发出请求消息的实例上。

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
DeFiBus在每条请求消息中增加REPLY_TO属性来唯一标识每一个请求方实例。在创建响应消息时将REPLY_TO属性透传到响应消息中。Broker收到响应消息后，根据REPLY_TO属性，查找出对应的请求方实例的连接，将响应消息推送给该请求方实例。


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
