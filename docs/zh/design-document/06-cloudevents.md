# CloudEvents 集成

## 介绍

[CloudEvents](https://github.com/cloudevents/spec) 是一种描述事件数据的格式规范，它提供了跨服务、平台与系统的互操作性。

截止至 2021 年 5 月，EventMesh 包含了以下主要组件：`eventmesh-runtime`, `eventmesh-sdk-java` 和 `eventmesh-connector-rocketmq`。

对于使用 EventMesh 的用户，`eventmesh-runtime` 可以被部署为微服务来在生产者和消费者间传输用户的事件。
用户的应用程序可以通过 `eventmesh-sdk-java` 来与 `eventmesh-runtime` 进行交互，即发布或订阅指定主题的事件。

EventMesh 的用户非常渴望能得到对 CloudEvents 的支持。有许多理由使得用户倾向于使用集成了 CloudEvents 支持的 SDK：

- CloudEvents 是一种更为广泛接受和支持的描述事件的方式。目前，`eventmesh-sdk-java` 使用的是 `LiteMessage` 结构
  来描述事件，其标准化程度较低。
- CloudEvents 的 Java SDK 有更广泛的分发方式。比如，目前 EventMesh 的用户需要使用 SDK 的 tar 包，或对每个 EventMesh 的
  发布版本从源码编译。有了 CloudEvents 的支持，用户可以更方便地通过 CloudEvents 的公开分发（比如，配置 Maven）来添加
  EventMesh SDK 依赖项。
- CloudEvents 的 SDK 支持多种语言。尽管目前 EventMesh 只提供了 Java SDK，但在未来，如果要为更多语言提供支持，将 Java SDK
  与 CloudEvents 绑定的经验将使工作变得容易。

## 需求

### 功能需求

| 需求 ID | 需求描述 | 备注 |
| ------ | ------- | --- |
| F-1    | EventMesh 用户应能使用公共 SDK 依赖项来发布或订阅 CloudEvents 格式的事件 | 功能性 |
| F-2    | EventMesh 用户应能在提供了 CloudEvents 支持的 SDK 中继续使用现有的 EventMesh 客户端功能（如负载均衡） | 功能等价 |
| F-3    | EventMesh 的开发者应不需要付出特别多努力/痛苦来在 `eventmesh-sdk-java` 和提供了 CloudEvents 支持的 SDK 之间同步 | 可维护性 |
| F-4    | EventMesh 支持可插拔的协议，以便开发者整合其他协议（例如：CloudEvents / EventMesh MessageOpenMessage / MQTT...） | 功能性 |
| F-5    | EventMesh 支持统一的 API 以供从/向事件库发布或订阅事件 | 功能性 |

### 性能需求

| 需求 ID | 需求描述 | 备注 |
| ------ | ------- | --- |
| P-1    | 提供了 CloudEvents 支持的 SDK 应具有与目前的 SDK 相近的客户端延迟 | |

## 设计细节

与 CloudEvents 的 Java SDK 绑定（这与 Kafka 已经完成的工作类似，请在附录中的参考资料了解更多细节）是达成上述需求的一种简单方法。

### 可插拔协议

![可插拔协议](/images/design-document/cloudevents-pluggable-protocols.png)

### EventMesh 集成 CloudEvents 进度表

#### TCP

##### SDK 端发布

- 在 `package` 首部中添加 CloudEvents 标识符
- 使用 `CloudEventBuilder` 构造 CloudEvent，并将其放入 `package` 体中

##### SDK 端订阅

- 在 `ReceiveMsgHook` 接口下添加 `convert` 函数，其用于将 `package` 体转换为具有 `package` 首部标识符的特定协议
- 不同协议应实现 `ReceiveMsgHook` 接口

##### 服务端发布

- 设计包含 `decodeMessage` 接口的协议转换 API，其可以把包体转换为 CloudEvent
- 更新 `MessageTransferTask` 下的 `Session.upstreamMsg()`，将入参 `Message` 改为 `CloudEvent`，这使用了
  上一步的 `decodeMessage` API 来进行对 CloudEvent 的转换
- 更新 `SessionSender.send()`，将入参 `Message` 改为 `CloudEvent`
- 更新 `MeshMQProducer` API，支持在运行时发送 `CloudEvents`
- 在 `connector-plugin` 中实现支持向 EventStore 中发送 `CloudEvents`

##### 服务端订阅

- 支持将连接器插件中的 `RocketMessage` 改为 `CloudEvent
- 重写 `AsyncMessageListener.consume()` 函数，将入参 `Message` 改为 `CloudEvent`
- 更新 `MeshMQPushConsumer.updateOffset()`，将入参 `Message` 改为 `CloudEvent`
- 更新 `DownStreamMsgContext`，将入参 `Message` 改为 `CloudEvent`，更新 `DownStreamMsgContext.ackMsg`

#### HTTP

##### SDK 端发布

- 支持 `LiteProducer.publish(cloudEvent)`
- 在 http 请求头中添加 CloudEvents 标识符

##### SDK 端订阅

##### 服务端发布

- 支持根据 `HttpCommand` 首部中的协议类型，通过可插拔的协议插件构造 `HttpCommand.body`
- 支持在消息处理器中发布 CloudEvent

##### 服务端订阅

- 更新 `EventMeshConsumer.subscribe()`
- 更新 `HandleMsgContext`， 将入参 `Message` 改为 `CloudEvent`
- 更新 `AsyncHttpPushRequest.tryHTTPRequest()`

## 附录

### 参考资料

- <https://cloudevents.github.io/sdk-java/kafka>
