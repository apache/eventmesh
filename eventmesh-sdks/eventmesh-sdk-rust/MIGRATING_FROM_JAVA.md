# 从 Java SDK 迁移到 Rust SDK

本文对照当前仓库中的 `eventmesh-sdk-java` 与 `eventmesh-sdk-rust` 源码，面向将 Java 应用迁移到 Rust 的开发者。
两者使用 EventMesh 的 gRPC、HTTP、TCP 协议，但公开 API、功能范围和部分处理语义不同，不能只替换类名。

Rust crate 当前声明版本为 `2.0.0`，最低 Rust 版本为 `1.86.0`；该声明不代表已发布到 crates.io，
也不要求 Runtime 的版本号为 2。安装与公共行为以 [README.md](README.md) 和 rustdoc 为准。
旧 Rust SDK 到当前 Rust SDK 的迁移见 [MIGRATION.md](MIGRATION.md)，与本文的 Java 迁移范围不同。

## 1. 先确认功能是否有对应实现

| Java SDK 用法 | Rust SDK 对应 API / 差异 |
| --- | --- |
| `EventMeshGrpcProducer.publish(message)` | `GrpcProducer::publish(Message).await` |
| gRPC `publish(List<T>)` | `GrpcProducer::publish_batch(Vec<Message>).await`；批次不能为空，也不能混用原生消息和 CloudEvents |
| gRPC `requestReply(message, timeout)` | `request_reply().await` 或 `request_reply_with_timeout(...).await` |
| `EventMeshGrpcConsumer` 的流订阅 | `GrpcStreamConsumer::open(channel, options, subscriptions, handler).await` |
| gRPC `subscribe(items, url)` | `GrpcWebhookConsumer::subscribe(items, url).await`；回调仍通过 HTTP 到达应用 |
| `EventMeshHttpProducer.publish(...)` | `HttpClient::producer(...)` 创建 `HttpProducer`，再调用 `publish().await` |
| HTTP `request(...)` / `RRCallback` | 没有对应的公开请求/响应 API；同步业务改用 gRPC 或 TCP |
| `EventMeshHttpConsumer.subscribe(items, url)` | 已有 HTTP 服务用 `webhook_registration(...)`；由 SDK 托管回调用 `consumer(...).await` |
| Java HTTP 的显式 `heartBeat(items, url)` | Rust HTTP 注册管理器自动维护心跳，无需单独启动 |
| `EventMeshTCPClientFactory`、`init()`、`listen()` | `TcpClient` 创建生产者/消费者时完成连接和握手；消费者还自动执行 LISTEN |
| TCP `publish`、`broadcast`、`rr` / `asyncRR` | `TcpProducer::{publish, broadcast, request_reply}`；通过 future 取得结果，不暴露 Java `Package` |
| TCP `registerBusiHandler(...)` | 消费者在 `consumer(options, handler)` 时传入；生产者推送回调用 `producer_with_handler(...)` |
| TCP `unsubscribe()` | `TcpConsumer::unsubscribe_all().await`；注销整个会话的订阅 |
| HTTP/TCP 的 OpenMessaging `Message` | 不支持；先转换为原生 `EventMeshMessage` 或 CloudEvent |
| `EventMeshCatalogClient`、`EventMeshWorkflowClient` | 没有对应客户端，需要另行实现或保留现有服务调用 |
| `Selector` / `SelectorFactory` 的注册中心发现 | 没有对应扩展点；gRPC/TCP 配置单端点，HTTP 配置显式端点集合 |

Rust 的 HTTP/TCP 生产者没有 gRPC `publish_batch` 的对应方法。不要把多次 `publish` 当作一个批量 RPC。
Java HTTP 虽然提供 `request` 方法，当前 Rust SDK 与 stock Runtime 并没有完整的 HTTP responder 路径；
Rust HTTP 注册 API 会拒绝 `DeliveryType::Sync`，托管回调也不会把 handler 返回的业务回复发回请求方。

`DeliveryMode::Broadcast` 是订阅分发模式；`TcpProducer::broadcast` 是专用 TCP 发送命令，迁移时分别选择。
后者等待本地 socket 写入完成，不等待 Runtime ACK，也不保证消息已被消费者处理。

## 2. 调整依赖和运行环境

Rust SDK 独立于 Gradle 构建，默认 Cargo features 为空，传输协议必须显式启用：

| Feature | 用途 |
| --- | --- |
| `grpc` | gRPC 生产者、流消费者、外部 webhook 注册；构建需要 `protoc` 3.15 或更高版本 |
| `http-client` | HTTP 生产者、外部 webhook 注册/心跳、与框架无关的回调编解码；不引入 axum |
| `http-server` | 包含 `http-client`，增加 SDK 托管的 axum 回调服务 |
| `http` | 同时启用上述两个 HTTP features 的便利聚合项 |
| `tcp` | TCP 生产者、消费者、广播与重连 |
| `cloud_events` | 增加 `Message::CloudEvent(cloudevents::Event)` |
| `full` | 全部传输和 CloudEvents，适合本地验证 |

例如，先使用本地检出的 SDK；将路径替换为实际位置：

```toml
[dependencies]
eventmesh = { path = "/path/to/eventmesh/eventmesh-sdks/eventmesh-sdk-rust", features = ["grpc"] }
tokio = { version = "1", features = ["macros", "rt-multi-thread", "signal"] }
```

使用 CloudEvents 时增加 `cloud_events` feature。应用直接构造 CloudEvent 时还需声明
`cloudevents-sdk = "0.7"`，通过 `cloudevents::{EventBuilder, EventBuilderV10}` 导入 builder。
应用自行初始化日志，例如配置 `tracing-subscriber`。

网络调用使用 Tokio。连接、消费循环和心跳依赖运行中的 Tokio runtime：

- 在驱动连接的 runtime 内调用 `GrpcChannel::connect`；同一 runtime 内可克隆 channel 供多个角色共享。
  使用另一个 Tokio runtime 时，应在其中重新连接。
- 一个 `HttpClient` 的克隆、生产者和注册管理器共享连接池及端点选择状态。
  加权轮询会随所有角色的操作共同推进；需要独立选择状态时创建独立客户端。
- `TcpClient` 是配置与工厂句柄，每次创建生产者或消费者都会建立独立 TCP 会话。

`GrpcProducer` / `HttpProducer` 没有 Java `AutoCloseable.close()` 的对应方法。
它们持有共享连接句柄，释放一个角色不等于关闭其他角色使用的连接；消费者的后台任务需要另外关闭。

## 3. 拆分配置，显式保留部署身份

Java 通常把身份、认证、组名放在一个配置对象中；Rust 将其拆分为客户端配置与角色选项。

| Java 配置 | Rust 配置 |
| --- | --- |
| gRPC `serverAddr` / `serverPort`，TCP `host` / `port` | `Endpoint::new(host, port)?`，传入 `GrpcConfig::new` / `TcpConfig::new` |
| HTTP `liteEventMeshAddr` 的分号分隔地址 | `EndpointSet::new([...])?`，传入 `HttpConfig::new` |
| HTTP 地址中的权重、`loadBalanceType` | `Endpoint::with_weight(...)`、`HttpConfig::with_load_balance(...)` |
| `env` / `idc` / `sys`；TCP `UserAgent.subsystem` | `Identity::default().with_env(...).with_idc(...).with_system(...)` |
| HTTP `ip` / `pid`；TCP `UserAgent.host` / `pid` | `Identity::with_ip(...)` / `with_process_id(...)` |
| `userName` / `password`；TCP `UserAgent.token` | `Credentials::new().with_basic(...).with_token(...)` |
| `producerGroup` / TCP pub `UserAgent.group` | `ProducerOptions::new(group)` |
| `consumerGroup` / TCP sub `UserAgent.group` | `ConsumerOptions::new(group)`；gRPC 流用 `GrpcConsumerOptions::new(group)` |
| 请求超时毫秒数 | `ClientOptions::with_request_timeout(Duration)`，传入配置的 `with_options` |
| Java TCP 的 `reconnect()` 调用 | `TcpConfig::with_reconnect(ReconnectPolicy)` 配置后续断线恢复 |

`Endpoint` 的 host 不带 URL scheme、路径或内嵌端口，端口类型为 `u16` 且不能为零。
HTTP 端点集合不能为空。角色创建时要求组名非空，不再隐式使用 Java 默认组名。
TCP 的完整 `UserAgent` 不作为公共配置暴露，不能逐项照搬其 `path`、`version`、`purpose` 等字段。

### 默认值对照

| 项目 | Java SDK | Rust SDK |
| --- | --- | --- |
| 端点 | gRPC `localhost:10205`；HTTP `localhost:10105`；TCP 由调用方提供 | 所有传输显式提供；Runtime 通常使用 gRPC 10205、HTTP 10105、TCP 10000 |
| gRPC `env` / `idc` / `sys` | `env` / `default` / `sys123` | `env` / `default` / `sys` |
| HTTP `env` / `idc` / `sys` | 均为空 | `env` / `default` / `sys` |
| HTTP `ip` / `pid` | `localhost` / 空字符串 | 本机 IPv4 / 当前进程 ID |
| 语言标识 | `JAVA` | `RUST` |
| gRPC 用户名 / 密码 | `username` / `passwd` | `GrpcConfig::new` 使用相同默认值 |
| HTTP/TCP 认证 | HTTP 默认空；TCP 由 `UserAgent` 提供 | 默认空 |
| 生产组 / 消费组 | gRPC/HTTP 默认 `DefaultProducerGroup` / `DefaultConsumerGroup` | 必须在对应角色 options 中提供 |
| HTTP 负载均衡 | 默认随机 | 默认 `LoadBalance::Random`；另有 `WeightedRandom`、`WeightedRoundRobin` |

不要依赖默认身份保持不变，尤其是 gRPC 的 `sys123` 与 HTTP 的空身份字段。
按原部署填写 `Identity`，保持 topic、消费者组、订阅模式和鉴权身份一致。

`with_credentials` 替换整个认证对象，不会与 gRPC 默认值合并。
通用 `Credentials::default()` 本身仍为空；默认用户名/密码只由 `GrpcConfig::new` 提供。
Runtime 即使关闭鉴权也验证 gRPC 用户名和密码非空；只设置 token 不满足此验证。
需要 token 时同时提供非空 basic 字段，例如 `Credentials::new().with_basic(user, password).with_token(token)`。
HTTP 使用 RocketMQ 存储时同样应显式配置非空 basic 凭据：存储客户端拒绝空用户属性，即使 Runtime 关闭鉴权。HTTP 默认值仍与 Java 一样为空，示例显式提供随附 Runtime 的凭据。

### 超时需要重新核对

| 操作 | Java SDK 源码行为 | Rust SDK |
| --- | --- | --- |
| gRPC publish / batch | 配置声明 `timeOut = 5000`，这些路径未使用它设置 deadline | 默认实际执行 5 秒超时 |
| gRPC 原生消息 request/reply | 使用调用参数设置毫秒 deadline | 默认 5 秒，可按客户端或单次调用覆盖 |
| gRPC CloudEvent request/reply | 接收 `timeout` 参数，但没有应用它 | 同样执行默认或单次指定超时 |
| HTTP 请求 | 通用默认超时 15 秒，request 可传入单次超时 | 默认 15 秒，通过 `ClientOptions` 覆盖 |
| TCP publish / request/reply | 调用方传入业务超时 | 默认 20 秒；request/reply 可按单次覆盖 |
| TCP connect / 控制命令 | connect 1 秒；HELLO、LISTEN、订阅等通常 20 秒 | 默认分别为 1 秒 / 20 秒，使用 `with_connect_timeout` / `with_control_timeout` 覆盖 |

gRPC 流建立另有等待响应头的 15 秒限制，不限制已建立流的存活时间。
业务消息 TTL 与请求超时是两个不同参数，不能互相替代。

Java gRPC 的 `useTls` 配置并未改变当前生产者/消费者的 `usePlaintext()` 路径；Rust gRPC 也只提供明文连接。
HTTP 的 HTTPS 使用 `HttpConfig::with_tls()`。Java 的 JKS 配置及连接池参数没有逐项对应的 Rust 公共 setter。
Rust HTTP 默认不读取环境代理，需通过 `with_proxy_from_env(true)` 显式启用。

## 4. 迁移消息构造和发布

Java 有两个同名的原生消息类：gRPC/HTTP 使用 `org.apache.eventmesh.common.EventMeshMessage`，
TCP 使用 `org.apache.eventmesh.common.protocol.tcp.EventMeshMessage`。
Rust 所有传输统一使用公共 `EventMeshMessage`，由传输层负责 wire 编码。

| Java 字段 / 访问方式 | Rust API |
| --- | --- |
| `topic`；TCP `topic` | `topic()` / builder `topic(...)` |
| `content`；TCP `body` | `content()` / builder `content(...)`，原生内容为 `String` |
| `bizSeqNo`；TCP properties 的 `seqnum` | `biz_seq_no()` / builder `biz_seq_no(...)` |
| `uniqueId`；TCP properties 的 `uniqueid` | `unique_id()` / builder `unique_id(...)` |
| `getProp("ttl")`；TCP properties 的 `ttl` | `ttl_millis()` / builder `ttl_millis(i64)` |
| `getProp("datacontenttype")`；TCP headers 的同名键 | `data_content_type()` / builder `data_content_type(...)` |
| 业务 `prop` / properties / headers | `properties()`、`get_prop(...)`、builder `prop(...)`、`set_prop(...)?` |
| 收到的协议描述、身份、内部路由属性 | `delivery_context()`，再读取 `protocol_description()` / `attribute(...)` |

Java TCP 的业务 headers 读取时合并到 Rust 业务属性中，没有独立的公共 headers map；重新发送时按 Rust 的字段规则编码。
`EventMeshMessage` 不是可直接替换 Java JSON DTO 的稳定序列化契约，不要自行拼装它来代替传输层编码。

例如，Java gRPC 发布代码片段：

```java
EventMeshGrpcClientConfig config = EventMeshGrpcClientConfig.builder()
    .producerGroup("orders-producer")
    .build();
EventMeshMessage message = EventMeshMessage.builder()
    .topic("orders.created")
    .content("{\"id\":42}")
    .bizSeqNo("order-42")
    .uniqueId("event-42")
    .build();
message.addProp("ttl", "4000");
message.addProp("datacontenttype", "application/json");
message.addProp("tenant", "store-a");
try (EventMeshGrpcProducer producer = new EventMeshGrpcProducer(config)) {
    Response response = producer.publish(message);
    // 按 Java 调用路径检查 null、响应码或异常。
}
```

Rust 对应完整程序，显式保留 Java gRPC 默认的 `sys123`；实际迁移时替换为部署现用身份与认证：

```rust,ignore
use std::time::Duration;

use eventmesh::{
    config::{ClientOptions, Credentials, Endpoint, GrpcConfig, Identity, ProducerOptions},
    EventMeshMessage, GrpcChannel, GrpcProducer, Message,
};

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    let config = GrpcConfig::new(Endpoint::new("127.0.0.1", 10_205)?)
        .with_identity(Identity::default().with_system("sys123"))
        .with_credentials(Credentials::new().with_basic("username", "passwd"))
        .with_options(ClientOptions::default().with_request_timeout(Duration::from_secs(5)));
    let channel = GrpcChannel::connect(config).await?;
    let producer = GrpcProducer::new(channel, ProducerOptions::new("orders-producer"))?;
    let message = EventMeshMessage::builder()
        .topic("orders.created")
        .content(r#"{"id":42}"#)
        .biz_seq_no("order-42")
        .unique_id("event-42")
        .ttl_millis(4_000)
        .data_content_type("application/json")
        .prop("tenant", "store-a")
        .build()?;
    let receipt = producer.publish(Message::from(message)).await?;
    println!("code={}, message={:?}", receipt.code, receipt.message);
    Ok(())
}
```

Rust 通过 `Message` 显式区分原生消息和 CloudEvents。启用 `cloud_events` 后，可直接传入 `Message::CloudEvent(event)`。
收到 CloudEvent 时 `into_event_mesh()` 返回错误，不会自动丢掉 CloudEvents 属性并转换为原生消息。
如业务使用消息 ID 去重，显式保留旧 ID；不要依赖两种 SDK 自动生成 ID 的格式相同。

还有三处需要调整：

- **保留字段不再放进业务 properties。** `ttl`、`seqnum`、`uniqueid`、`datacontenttype` 使用专用字段；
  `username`、`cluster` 等使用客户端配置或只读 delivery context。
  builder 的 `prop` / `props` 在 `build()` 时拒绝保留键，`set_prop` / `with_property` 立即返回 `Result`。
- **构造成功不代表可发布。** 原生消息接收时保留能放入 `i64` 的数值 TTL，畸形值会解码失败；
  新发布、广播和发起请求要求 TTL 为 `1..=i32::MAX` 毫秒，零或负数不表示永不过期。
  gRPC/HTTP 未设置时发送 4000 ms；TCP 未设置时交给 Runtime。gRPC 拒绝空内容，TCP 还拒绝全空白内容。
  handler 回复不重新应用这些仅针对生产者的检查；TCP 原生回复保留空白内容，TTL 未设置时继承请求值。
- **HTTP 原生属性发送范围不同。** Java HTTP 原生 producer 只组装固定字段，不发送完整业务 `prop` map；
  Rust 会将业务属性和显式内容类型编码进 `extFields`，并在缺少业务 ID 时补齐。
  原先在 Java HTTP 中未发出的业务属性，迁移后可能开始到达消费者。

## 5. 将回调迁移为 `MessageHandler`

Java gRPC `ReceiveMsgHook<T>` 的 `Optional<T> handle(T)` 与 TCP `ReceiveMsgHook` 统一对应：

| Java 回调结果 | Rust handler 结果 | 含义 |
| --- | --- | --- |
| `Optional.empty()` | `Ok(None)` | 异步业务处理成功，无业务回复 |
| `Optional.of(reply)` | `Ok(Some(Message::from(reply)))` 或 `Ok(Some(Message::CloudEvent(reply)))` | 返回同步请求的业务回复 |
| 抛出异常 | `Err(eventmesh::Error::...)` | 将失败交给当前传输处理 |

Java gRPC 的 `getProtocolType()` 不再有对应方法，由收到的 `Message` variant 表达消息类型。
handler 必须满足 `Send + Sync + 'static`，也可以使用符合约束的异步闭包。

异步原生消息消费者可以写成下面的完整程序。它同时等待消费任务结束与 Ctrl-C，避免后台已经失败却仍只等退出信号：

```rust,ignore
use eventmesh::{
    config::{Endpoint, GrpcConfig, GrpcConsumerOptions, Identity},
    GrpcChannel, GrpcStreamConsumer, Message, MessageHandler, Subscription,
};

struct Orders;

impl MessageHandler for Orders {
    async fn handle(&self, message: Message) -> eventmesh::Result<Option<Message>> {
        let message = message.into_event_mesh()?;
        println!("received topic={}", message.topic());
        Ok(None)
    }
}

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    let config = GrpcConfig::new(Endpoint::new("127.0.0.1", 10_205)?)
        .with_identity(Identity::default().with_system("sys123"));
    let channel = GrpcChannel::connect(config).await?;
    let consumer = GrpcStreamConsumer::open(
        channel,
        GrpcConsumerOptions::new("orders-consumer"),
        [Subscription::new("orders.created")],
        Orders,
    )
    .await?;

    tokio::select! {
        result = consumer.join() => result,
        signal = tokio::signal::ctrl_c() => {
            signal?;
            consumer.shutdown();
            consumer.join().await
        }
    }
}
```

### 订阅与同步回复

| Java | Rust |
| --- | --- |
| `SubscriptionItem` | `Subscription::new(topic)`，默认 Cluster + Async |
| `SubscriptionMode.CLUSTERING` / `BROADCASTING` | `DeliveryMode::Cluster` / `Broadcast`，通过 `with_delivery_mode` 设置 |
| `SubscriptionType.ASYNC` / `SYNC` | `DeliveryType::Async` / `Sync`，通过 `with_delivery_type` 设置 |

同步业务同时需要 `DeliveryType::Sync` 与返回 `Ok(Some(reply))` 的 handler。
`Ok(None)` 不会生成空回复，请求方仍可能等待到超时。下面的函数可作为 gRPC/TCP 原生请求处理器：

```rust,ignore
use eventmesh::{DeliveryType, EventMeshMessage, Message, Subscription};

fn subscription() -> Subscription {
    Subscription::new("orders.query").with_delivery_type(DeliveryType::Sync)
}

async fn reply(request: Message) -> eventmesh::Result<Option<Message>> {
    let request = request.into_event_mesh()?;
    let response = EventMeshMessage::new(request.topic(), r#"{"status":"ok"}"#)?;
    Ok(Some(Message::from(response)))
}
```

请求方用 `request_reply(message).await`，或 `request_reply_with_timeout(message, Duration::from_secs(10)).await`。
完整工作流见 [gRPC request/reply](examples/grpc/request_reply.rs) 与 [TCP request/reply](examples/tcp/request_reply.rs)。
返回新业务消息即可，SDK 自动恢复当前请求的回复路由，不需要应用手工复制 correlation 属性。

### 并发和错误处理并不相同

| 传输 | Rust 调度 / 成功结果 | handler 失败后的行为 |
| --- | --- | --- |
| gRPC 流 | 默认串行；`with_max_concurrent_handlers(n)` 可增加并发并改变回复顺序；`Ok(None)` 不发送独立业务 ACK 帧 | 返回错误或任务 panic 会结束当前消费流，由 `join()` 报告；不会自动重建流 |
| TCP | 每个角色串行调用 handler；成功后发送对应 TCP ACK | 返回错误或 unwind panic 不发送回复/ACK，关闭该投递所属会话，按重连策略恢复 |
| SDK 托管 HTTP | HTTP 请求可并发调用 handler；成功返回 `WebhookReply::ok()` | 返回错误时发送 `WebhookReply::retry(...)`；由 Runtime 决定重投 |

Java gRPC `SubStreamHandler` 捕获 handler 异常后记录日志并继续；Rust 的终止行为需要应用明确处理。
不要把所有 handler 失败都理解为自动重试：TCP 的恢复不恢复业务状态，HTTP/gRPC 的处理方式也不同。
TCP 的 panic 捕获仅适用于 unwind，`panic = "abort"` 无法捕获；应用副作用仍需幂等。

## 6. 迁移 HTTP 回调与 TCP 初始化

### HTTP：保留已有服务，或交给 SDK 托管

Java HTTP consumer 本身负责注册 URL，不为应用启动回调服务器。迁移时有两种选择：

- 已有 Web 服务：使用 `HttpClient::webhook_registration` 管理订阅和心跳，`http-client` feature 即可。
  用 `eventmesh::http::codec::parse_push_body(body)?.to_message(&headers)?` 解码，传入请求的 `http::HeaderMap`。
  成功/失败分别返回 JSON 格式的 `WebhookReply::ok()` / `WebhookReply::retry(...)`。
  不能只解析 `content` 后一律构造成原生消息，否则会丢失 CloudEvents 方言。
- 新建托管回调：使用 `HttpClient::consumer(options, webhook, subscriptions, handler).await`，启用 `http-server`。
  SDK 先绑定回调 socket，再注册订阅，并持有 server、心跳和订阅生命周期。

外部回调注册的完整客户端程序如下；运行前，应用须已提供对应回调 URL：

```rust,ignore
use eventmesh::{
    config::{ConsumerOptions, Endpoint, EndpointSet, HttpConfig},
    HttpClient, Subscription,
};

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    let endpoints = EndpointSet::new([Endpoint::new("127.0.0.1", 10_105)?])?;
    let client = HttpClient::new(HttpConfig::new(endpoints))?;
    let registration = client.webhook_registration(ConsumerOptions::new("orders-consumer"))?;
    registration
        .subscribe(
            Subscription::new("orders.created"),
            "http://127.0.0.1:8081/eventmesh/callback",
        )
        .await?;
    tokio::signal::ctrl_c().await?;
    registration.close().await
}
```

回调 URL 必须从 Runtime 所在网络可达；容器中的 `127.0.0.1` 不指向宿主机应用。
托管方式用 `WebhookOptions::with_advertise_url` 设置可达地址。
完整实现见 [托管 HTTP consumer](examples/http/consumer_server.rs) 与 [应用自托管回调](examples/http/consumer_custom.rs)。

gRPC webhook 注册同样需要一个 HTTP 回调服务。仅启用 `grpc` 不会启动服务器；
若使用 SDK 的 `webhook::WebhookServer`，还需启用 `http-server`。

### TCP：由角色创建完成握手和接收准备

Java 的 `init → registerBusiHandler → subscribe → listen` 改为
`TcpClient::consumer(options, handler).await → consumer.subscribe(subscription).await`。
创建消费者时已完成 HELLO 和 LISTEN；不再手工调用 `init`、`listen` 或维护心跳。
生产者也在 `client.producer(options).await` 时连接并握手。

Rust 默认启用断线重连，重试次数不设上限，指数退避从 1 秒增长到最多 30 秒。
消费者重连后重放已确认订阅并再次 LISTEN，再恢复投递；重放或 LISTEN 失败会终止消费者。
Java 的自动重连由心跳检查连接状态触发，部分订阅重放失败只被吞掉，不能照搬其错误观察方式。

`unsubscribe_all()` 对应 Java 无参数 `unsubscribe()`，清空当前会话的全部订阅；无已跟踪订阅时返回 `Error::InvalidArgument`。
如需保留部分 topic，应重新规划会话，而不是尝试向 TCP unsubscribe 传入子集。
参考 [TCP consumer](examples/tcp/consumer.rs)、[生产者](examples/tcp/producer.rs) 与 [广播](examples/tcp/broadcast.rs)。

## 7. 明确元数据、内容类型和连接归属

以下是有意保留的 Java/Rust 行为差异：

| 场景 | Java SDK 源码行为 | Rust 行为与迁移动作 |
| --- | --- | --- |
| 转发收到的 CloudEvent | gRPC 只在扩展缺失时补身份/认证，可能沿用原发送方的凭据和路由 | 新发布、广播、请求从副本中过滤已知传输/投递属性，使用目标客户端配置；需要另一身份时配置另一客户端 |
| 将别处收到的消息作为当前请求回复 | gRPC 先合并请求属性，再用回复属性覆盖；TCP 保留已有回复扩展 | 已知内部路由以当前请求为准，显式回复业务扩展优先；不要靠外来 correlation 信息重定向回复 |
| gRPC 回复纯文本或二进制 | `SubStreamHandler` 无条件标为 `application/json`，并未转换内容 | 保留声明的内容类型与 payload，按 Runtime 规则选择 protobuf oneof；Rust `Data` variant 可能归一化 |
| TCP handler 跨过一次重连后完成 | 回复使用回调 channel，ACK 使用客户端当前 channel，可能跨会话确认旧投递 | 投递绑定 socket generation，旧结果不会在新连接发送回复/ACK；旧 handler 失败也不关闭新的健康连接 |

原生消息的 `DeliveryContext` 只读，生产者开启新操作时忽略它。不要把它复制到业务 properties；
回复路由由 SDK 从当前请求恢复，ACK 仍关联原接收帧。保留键按确切名称分类，不是按前缀过滤，
例如业务属性 `requestid` 不会因为以 `req` 开头而被删除。

CloudEvents 接收时保留标准属性、数据和业务扩展；发送过滤不修改原始事件。
CloudEvent 的 `id`、source、type、time、data 仍由应用拥有，已有 EventMesh 业务 ID 和 TTL 保留，
传输层补齐缺少的必要字段。回复的显式业务扩展优先，缺少的可从请求继承；
新回复不会自动继承请求的全部标准 CloudEvent 属性。直接返回当前请求作为 echo reply 仍可使用。

gRPC handler 应声明真实的内容类型：文本用 `text/plain`，JSON 提供合法 JSON 数据与对应类型。
消费者不能再假设所有 gRPC 回复都标记为 JSON。gRPC 按声明的内容类型选择 protobuf 数据字段，UTF-8 字节可以作为文本/JSON 发送；`application/protobuf` 的数据必须是完整序列化的 `google.protobuf.Any`。非法 UTF-8 文本和非法 Any 在本地返回错误。

**TCP CloudEvents 仍受 Java Runtime codec 限制：** `datacontenttype` 必须为 `application/cloudevents+json`。
Rust 会在发送前验证；通常意义上的 `application/json` 或 `text/plain` 不能替代此值。
这项 TCP 限制不因 gRPC 支持文本/二进制回复而消失。

## 8. 显式关闭资源并观察失败

| Rust 角色 | 推荐结束方式 | 远端订阅处理 |
| --- | --- | --- |
| `GrpcStreamConsumer` | `shutdown()`，再 `join().await` | 停止流和心跳，不调用逐项 unsubscribe RPC；流限制见下一节 |
| `GrpcWebhookConsumer` | 先 `unsubscribe(items, url).await`，再 `shutdown()` / `join().await` | 必须显式注销，单独停止本地任务不注销远端 |
| `HttpConsumer` / `WebhookRegistration` | `close().await` | 等待已提交心跳，停止后续调度，注销跟踪的订阅，再等待本地任务 |
| `TcpConsumer` | 按需 `unsubscribe_all().await`，再 `shutdown()` / `join().await` | unsubscribe 是会话级操作 |
| `TcpProducer` | `shutdown().await`，再 `join().await` | 没有消费订阅；join 仍用于观察终止的连接/推送 handler 错误 |

Java HTTP `close()` 只关闭本地资源和调度器，不自动注销订阅，也不等待已提交心跳。
Rust HTTP `close()` 做远端清理，可能耗时更久并返回错误；失败的注销仍被跟踪，可再次 `close()` 重试。
若只想停止本地 HTTP 任务，使用 `shutdown()` 后 `join()`；关闭开始后或 shutdown 后不能再修改订阅，
会返回 `Error::ChannelClosed`，重启须创建新角色。网络超时仍可能使远端是否已接受操作不确定。

`shutdown()` 是停止信号，`join()` 才等待任务并报告错误。取消一次 `join()` 等待不会丢掉任务或待报告结果，
可以再次等待；drop 会中止剩余任务，不能替代需要等待或远端清理的关闭流程。
gRPC 优雅关闭会等待已开始的 handler 完成；TCP producer 的显式 shutdown 会取消进行中的推送 handler。
取消 HTTP 托管消费者的启动 future 会停止本地 server 与心跳，但不能撤销 Runtime 已接受的注册。

Rust 网络操作返回结构化 `Error`：区分 `Config` / `InvalidArgument`、`InvalidMessage`、`Timeout`、
`Server { code, message }`、`ChannelClosed` 和传输错误。发布成功返回 `PublishReceipt`，不表示消费者业务已完成。
Java 的不同调用路径可能抛异常、返回 `null`、返回非成功响应码或只记录日志；迁移时逐个替换这些分支。

## 9. 仍需遵守的协议限制

- **gRPC 流订阅成功返回不证明已接受。** `open()` 只建立流，`subscribe()` 只排队请求；
  当前接收循环忽略无 `seqnum` 的控制帧，其中可能包含 ACL/参数拒绝。
  Runtime 正常结束流后 `join()` 仍可返回 `Ok(())`，应结合实际收消息和 Runtime 日志核实。
  仓库内 Java 流消费者也没有向调用方传播这些拒绝状态。
- **gRPC 取消一个 topic 可能结束整个流。** A、B 共用流时，取消 A 可使 Runtime 关闭共享 emitter，
  Rust 的接收循环与心跳结束，B 也停止接收。当前 SDK 不自动重建流或重放 B；
  等待旧消费者结束并 drop，再显式打开包含剩余订阅的新消费者。
  Java 同样不重建已关闭的流，而且其心跳可能继续，不能用心跳存活证明投递正常。
- **恢复连接不等于保证重投或恰好处理一次。** TCP 未 ACK 的消息是否重投取决于 Runtime、存储、
  订阅模式、可用会话和 TTL。重连、超时或广播完成都不能作为业务处理恰好一次的依据。

实现依据与运行约束见 [ARCHITECTURE.md](ARCHITECTURE.md#grpc-stream-subscription-limitations) 和
[README 的传输说明](README.md#transport-guide)。

## 10. 迁移验证与源码入口

迁移时至少核对：原部署的身份/组名、异步消费、同步回复、业务扩展与内容类型、超时处理、
断线后的幂等行为，以及退出时是否完成预期的远端注销。

在 Rust SDK 目录运行本地检查与现场测试：

```bash
# 本地测试，不要求 Runtime
cargo test --features full
cargo check --examples --features full

# 需要 Docker 或可达的外部 Runtime
cargo test --features e2e

# 还需要 Java 和 Maven
cargo test --features interop_e2e --test e2e interop
```

互操作测试覆盖 gRPC、HTTP、TCP 原生消息的双向发布/消费，Java peer 固定依赖 Maven artifact
`org.apache.eventmesh:eventmesh-sdk-java:1.12.0-release`，并非编译本仓库 Java SDK 源码。
它不能替代当前 Java 源码、CloudEvents、request/reply 和全部生命周期场景的验证。
现场测试环境、固定 Runtime 镜像、standalone 存储限制及 TTL 回归条件见
[CONTRIBUTING.md](CONTRIBUTING.md#end-to-end-tests)。

以下入口可用于复核本文对照，链接均指向本仓库源码：

| 对照项 | Java | Rust |
| --- | --- | --- |
| 公开配置与角色 | [gRPC 配置](../eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/grpc/config/EventMeshGrpcClientConfig.java)、[HTTP 配置](../eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/http/conf/EventMeshHttpClientConfig.java) | [config](src/config/client.rs)、[gRPC](src/grpc.rs)、[HTTP](src/http.rs)、[TCP](src/tcp.rs) |
| 发布与超时 | [gRPC 原生 producer](../eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/grpc/producer/EventMeshMessageProducer.java)、[CloudEvent producer](../eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/grpc/producer/CloudEventProducer.java)、[HTTP 原生 producer](../eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/http/producer/EventMeshMessageProducer.java) | [gRPC client](src/transport/grpc/client.rs)、[HTTP codec](src/transport/http/codec.rs) |
| 消息字段与元数据 | [gRPC CloudEvent builder](../eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/grpc/util/EventMeshCloudEventBuilder.java) | [消息模型](src/model/message.rs)、[保留键](src/common/metadata.rs)、[元数据策略](src/transport/metadata.rs) |
| 回调与回复 | [gRPC stream handler](../eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/grpc/consumer/SubStreamHandler.java)、[TCP handler](../eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/tcp/impl/AbstractEventMeshTCPSubHandler.java) | [MessageHandler](src/handler.rs)、[gRPC consumer](src/transport/grpc/consumer.rs)、[TCP delivery](src/transport/tcp/delivery.rs) |
| HTTP 生命周期 | [HTTP consumer](../eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/http/consumer/EventMeshHttpConsumer.java) | [HTTP registration](src/transport/http/consumer.rs) |
| TCP 连接与恢复 | [TcpClient](../eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/tcp/common/TcpClient.java) | [连接管理](src/transport/tcp/connection.rs)、[consumer](src/transport/tcp/consumer.rs) |
| 互操作范围 | [Java peer 依赖](interop/java-peer/pom.xml) | [双向测试](tests/e2e/interop.rs)、[运行示例](examples/README.md) |
