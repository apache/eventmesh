# Rust SDK 迁移指南：master → refactor/rust-sdk

本文面向已经使用 `master` 中 Rust SDK 的应用，说明迁移到当前分支需要修改的代码和行为。
这是一次不兼容的公共 API 重构：配置、消息、生产者、消费回调和订阅 API 都需要调整。

## 对比范围

本次对比使用 2026-10-15 本地仓库的两个提交，直接比较分支端点，而非只比较共同祖先之后的改动：

| 项目 | 旧版 | 新版 |
| --- | --- | --- |
| 分支 | `master` | `refactor/rust-sdk` |
| 提交 | `09f9f8d7ddcf30b1e862545f897e86abd61226ad` | `e8d9cd317b3f358bb00b6722a4c88da40feba2ea` |
| SDK `Cargo.toml` 版本 | `1.9.0` | `2.0.0` |
| Rust 版本声明 | `msrv = "1.75.0"` | Cargo 标准字段 `rust-version = "1.86.0"` |
| 协议实现 | gRPC | gRPC、HTTP、TCP |

范围限于 `eventmesh-sdks/eventmesh-sdk-rust/`。本地 `master` 与 `origin/master` 的引用在对比时相同；
本次没有更新远端引用。分支中的 `2.0.0` 是源码版本声明，不表示该版本已经发布到 crates.io，
也不表示需要一个版本号为 2 的 EventMesh Runtime。

在仓库根目录可复现对比：

```bash
git diff --stat 09f9f8d7ddcf30b1e862545f897e86abd61226ad e8d9cd317b3f358bb00b6722a4c88da40feba2ea -- eventmesh-sdks/eventmesh-sdk-rust
git diff master HEAD -- eventmesh-sdks/eventmesh-sdk-rust
```

第一条命令固定本文快照；第二条命令会随本地分支继续变化。本文的旧版行为依据旧提交源码，
新版行为依据当前公共 API 和传输实现；实时使用说明以 [README.md](README.md) 和 rustdoc 为准。

## 1. 先调整工具链和 Cargo features

升级到 Rust 1.86.0 或更高版本。启用 `grpc`、`full` 或 `e2e` 时还需要 `protoc` 3.15 或更高版本；
仅使用原生消息模型、HTTP 或 TCP 时不需要生成 gRPC 代码。

| 旧版 feature / 默认行为 | 新版 | 迁移动作 |
| --- | --- | --- |
| 默认启用 `grpc`、`eventmesh_message` | `default = []` | 显式启用应用需要的传输协议 |
| `eventmesh_message` | 已移除；原生消息始终可用 | 从依赖的 feature 列表删除 |
| `grpc` | 按需启用 gRPC 依赖和代码生成 | 原有 gRPC 应用保留此 feature |
| `cloud_events` | 保留，按需启用 CloudEvents 依赖和 `Message::CloudEvent` | 使用 CloudEvents 时显式保留 |
| `tls` | 已移除 | gRPC 当前只提供明文连接；HTTP TLS 使用 `HttpConfig::with_tls()` |
| `full` 仅含 gRPC 和消息模型 | 包含全部传输和 CloudEvents | 检查依赖体积；普通应用优先选择具体 feature |
| 无 HTTP/TCP features | `http-client`、`http-server`、`http`、`tcp` | 新增传输时按需启用 |
| 无现场集成测试 features | `e2e`、`interop_e2e` | 仅在相应验收阶段启用 |

`http-client` 提供发布、外部 webhook 注册和回调编解码，不引入 axum；`http-server` 增加 SDK 托管的回调服务器。
`http` 是同时启用这两者的聚合 feature，这种兼容别名针对 HTTP 实现的历史用法，旧 `master` 本身没有 HTTP SDK。

例如，先把应用依赖指向当前分支的本地检出，替换下面的路径：

```toml
[dependencies]
eventmesh = { path = "/path/to/eventmesh/eventmesh-sdks/eventmesh-sdk-rust", features = ["grpc"] }
tokio = { version = "1", features = ["macros", "rt-multi-thread", "signal"] }
```

使用 CloudEvents 时改成 `features = ["grpc", "cloud_events"]`；应用自己构造 CloudEvents 时，
还需直接声明 `cloudevents-sdk = "0.7"`。SDK 没有重新导出该依赖的 builder。

`#[eventmesh::main]` 和 `eventmesh::eventmesh` 的 Tokio 重新导出已移除，改用直接依赖的 `#[tokio::main]`。
`eventmesh::log::init_logger()` 也已移除，由应用初始化 `tracing` 日志订阅者。
gRPC 依赖由 tonic 0.10 / prost 0.12 升到 tonic 0.12 / prost 0.13；应用应移除对 SDK 内部 protobuf 类型的依赖。

## 2. 公共 API 对照

| 旧版 API / 路径 | 新版 API / 迁移方式 |
| --- | --- |
| `config::EventMeshGrpcClientConfig` | `config::{Endpoint, GrpcConfig, Identity, Credentials, ClientOptions}`，角色分组单独配置 |
| `grpc::GrpcEventMeshProducer<M>` | `GrpcChannel` + 无消息类型参数的 `GrpcProducer` |
| `grpc::grpc_producer::EventMeshGrpcProducer` trait | 直接调用 `GrpcProducer` 的固有方法，无需导入生产者 trait |
| `grpc::grpc_consumer::EventMeshGrpcConsumer` | 流消费使用 `GrpcStreamConsumer<H>`；webhook 注册使用 `GrpcWebhookConsumer` |
| `common::ReceiveMessageListener` | crate 根的异步 `MessageHandler`，或满足约束的异步闭包 |
| `model::message::EventMeshMessage` | crate 根的 `EventMeshMessage` 或 `message::EventMeshMessage` |
| `model::subscription::SubscriptionItem` | crate 根的 `Subscription` 或 `subscription::Subscription` |
| `SubscriptionMode::{CLUSTERING, BROADCASTING}` | `DeliveryMode::{Cluster, Broadcast}` |
| `SubscriptionType::{ASYNC, SYNC}` | `DeliveryType::{Async, Sync}` |
| `SubscriptionReply`、`HeartbeatItem` | 不再是公共业务模型；回复通过 handler 返回值，心跳由角色管理 |
| `EventMeshResponse` | 发布成功返回 `PublishReceipt`；订阅控制方法通常返回 `Result<()>` |
| `eventmesh::Result<T> = anyhow::Result<T>` | `Result<T, eventmesh::Error>` |
| `model::EventMeshProtocolType` | 用 `Message` / `MessageKind` 区分公开消息类型；协议编码由 SDK 管理 |
| `common::ProtocolKey`、`EventMeshCloudEventUtils`、`proto_cloud_event::*` | 已取消公共访问；使用消息字段、配置和 `DeliveryContext` |

`common`、`model`、生成的 protobuf 和传输实现现在是私有模块。即使内部仍有类似类型名，也不能继续从旧路径导入。

## 3. 拆分连接配置与角色配置

旧配置把服务器、身份、认证及生产/消费组放在一个对象里，新版分别由以下对象持有：

- `GrpcConfig`：端点、身份、认证、请求选项。
- `GrpcChannel`：在当前 Tokio runtime 内异步建立的连接。
- `ProducerOptions`、`ConsumerOptions`、`GrpcConsumerOptions`：对应角色的组及消费选项。

| 旧配置调用 | 新配置调用 |
| --- | --- |
| `set_server_addr(host)` + `set_server_port(port)` | `Endpoint::new(host, port)?`，端口类型由 `u32` 改为 `u16` |
| `set_env` / `set_idc` / `set_sys` | `Identity::default().with_env(...).with_idc(...).with_system(...)`，再传给 `with_identity` |
| `set_user_name` / `set_password` | `Credentials::new().with_basic(user, password)`，再传给 `with_credentials` |
| `set_producer_group` | `ProducerOptions::new(group)` |
| `set_consumer_group` | 流消费用 `GrpcConsumerOptions::new(group)`；webhook 用 `ConsumerOptions::new(group)` |
| `set_time_out(milliseconds)` | `ClientOptions::default().with_request_timeout(Duration::from_millis(milliseconds))`，再传给 `with_options` |
| `set_use_tls` | 无 gRPC 对应项；当前 `GrpcChannel` 使用明文 HTTP/2 |
| `set_language` | 无公开 setter；新版身份默认语言为 `RUST` |

`Endpoint` 要求非空 host 和非零端口，host 不应带 `http://`、路径或内嵌端口。
角色创建时验证组名非空，请求超时和 gRPC handler 并发上限必须大于零。

### 默认值不是原样沿用

| 配置 | 旧版默认值 | 新版默认值 / 要求 |
| --- | --- | --- |
| 端点 | `localhost:10205` | 必须显式传入 `Endpoint` |
| 环境 | `dev` | `env` |
| 系统 | `evetmesh`（旧代码原值） | `sys` |
| IDC | `default` | `default` |
| 语言 | `Rust` | `RUST` |
| gRPC 用户名 / 密码 | `username` / `password` | `username` / `passwd` |
| 生产组 / 消费组 | `DefaultProducerGroup` / `DefaultConsumerGroup` | 必须通过对应角色 options 显式传入 |
| gRPC 发布请求超时 | 配置存有 `5000` ms，但旧 publish/batch 路径没有应用它 | publish/batch/request-reply 默认实际使用 5 秒 |

如果部署依赖旧环境、系统、分组或密码，迁移时显式填入原值。`with_credentials` 会替换整个认证对象，
不会补上默认的用户名和密码。Runtime 即使关闭鉴权也验证 gRPC 用户名和密码非空；
使用 token 时同时配置非空 basic 字段，不能只传 `Credentials::new().with_token(...)`。
HTTP、TCP 以及通用 `Credentials::default()` 的认证默认值为空。

## 4. 迁移发布代码与消息构造

旧版代码形态如下（仅供对照，不能在新版编译）：

```rust,ignore
use eventmesh::config::EventMeshGrpcClientConfig;
use eventmesh::grpc::{grpc_producer::EventMeshGrpcProducer, GrpcEventMeshProducer};
use eventmesh::model::message::EventMeshMessage;

let config = EventMeshGrpcClientConfig::new()
    .set_server_addr("127.0.0.1".into())
    .set_producer_group("orders-producer".into());
let mut producer = GrpcEventMeshProducer::new(config);
let message = EventMeshMessage::default()
    .with_topic("orders.created")
    .with_content(r#"{"id":42}"#)
    .with_biz_seq_no("order-42")
    .with_unique_id("event-42");
let response = producer.publish(message).await?;
```

新版可以写成下面的完整程序。身份和认证配置显式展示了如何保留旧默认值，实际迁移时替换为部署现用值：

```rust,ignore
use std::time::Duration;

use eventmesh::{
    config::{ClientOptions, Credentials, Endpoint, GrpcConfig, Identity, ProducerOptions},
    EventMeshMessage, GrpcChannel, GrpcProducer, Message,
};

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    let config = GrpcConfig::new(Endpoint::new("127.0.0.1", 10_205)?)
        .with_identity(Identity::default().with_env("dev").with_system("evetmesh"))
        .with_credentials(Credentials::new().with_basic("username", "password"))
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

`GrpcChannel::connect(...).await?` 在连接阶段报告错误，替代旧构造函数内部的 lazy connect 和 `unwrap()`。
在使用它的 Tokio runtime 中创建 channel；同一 runtime 内用 `channel.clone()` 给多个角色共享连接。
另一个 Tokio runtime 应重新连接。生产者方法使用 `&self`，不再要求 `mut producer`。

### 原生消息模型变化

| 旧用法 | 新用法 / 影响 |
| --- | --- |
| `EventMeshMessage::default().with_topic(...).with_content(...)` | `EventMeshMessage::new(topic, content)?` 或 `builder().topic(...).content(...).build()?` |
| 六参数 `EventMeshMessage::new(...)` | 使用 builder 显式设置业务 ID、内容和业务属性 |
| `with_biz_seq_no` / `with_unique_id` | builder 的 `biz_seq_no` / `unique_id` |
| `with_create_time` | 无对应公开 setter；创建时间由 SDK 自动填入，单位为 epoch 毫秒 |
| `add_prop(key, value)` | builder 的 `prop`，或 `message.with_property(key, value)?` / `message.set_prop(key, value)?` |
| `get_prop(...) -> Option<&String>` | `get_prop(...) -> Option<&str>` |
| `remove_prop_if_present` | 无对应方法；需要删除属性时复制并修改 `properties()`，再用 builder 重建业务消息 |
| 直接 serde 序列化/反序列化 `EventMeshMessage` | 不再实现 serde；持久化或接口交换使用应用自己的 DTO，协议序列化交给 SDK |
| 未填业务 ID | 仍可省略，由传输编码补齐；需要稳定幂等键时继续显式传入 ID |

topic 不能为空白，builder 必须提供 content，但构造阶段允许空字符串。
新原生发布、广播和请求在发送阶段进一步验证：gRPC 拒绝空内容，TCP 拒绝空白内容，HTTP 允许空内容。
这些发布限制不重新施加在 handler 回复上。

### TTL、内容类型和投递上下文分开处理

旧 gRPC 解码会把收到的协议属性一起放入 `prop`，编码又允许属性覆盖配置字段。
新版 `properties()` 只保存业务扩展，迁移时按下面的归属拆分：

| 旧属性访问 | 新访问 / 设置方式 |
| --- | --- |
| `get_prop("ttl")` / `add_prop("ttl", ...)` | `ttl_millis()` / builder `ttl_millis(i64)` |
| `get_prop("seqnum")` | `biz_seq_no()` |
| `get_prop("uniqueid")` | `unique_id()` |
| `get_prop("datacontenttype")` | `data_content_type()` / builder `data_content_type(...)` |
| `get_prop("protocoldesc")` | `delivery_context().and_then(|c| c.protocol_description())` |
| 收到的 `cluster`、`req0*` / `rsp0*` 等已知路由字段 | `delivery_context().and_then(|c| c.attribute("cluster"))` 等只读访问 |
| 每条消息设置 `sys`、`username`、`passwd`、`token` | 在目标客户端的 `Identity` / `Credentials` 中配置 |

保留字段名称不能作为原生业务属性注入：builder 的 `prop` / `props` 在 `build()` 时报错，
`set_prop` / `with_property` 立即返回 `Error::InvalidArgument`，调用处需要处理 `Result`。
完整保留名称分类见 [common/metadata.rs](src/common/metadata.rs)。

本地构造的消息没有 `DeliveryContext`。重新发布收到的原生消息时，SDK 忽略该上下文，
使用目标客户端身份和认证重建协议属性；回复则自动使用当前请求的路由，不必把收到的属性复制进回复。

原生消息 TTL 的单位是毫秒。接收时保留能解析为 `i64` 的数值；新发布、广播和请求要求
`1..=i32::MAX`，`0` 或负数不表示永不过期。未设置时，gRPC/HTTP 使用 4000 ms，TCP 交由 Runtime 决定。
重新发布带有超范围 TTL 的接收消息时，应重建为有效的发送 TTL。

## 5. 迁移消费回调和订阅

旧 `ReceiveMessageListener` 是同步 trait，使用关联消息类型和 `Box<dyn ...>`。
新版 `MessageHandler` 接收统一的 `Message`，返回异步结果，要求 handler 为 `Send + Sync + 'static`，
返回的 future 也必须是 `Send`。用具体 handler 类型或异步闭包替代旧 boxed listener；
包含阻塞 I/O 的旧处理逻辑应迁移到异步 API，或在适当位置使用 `spawn_blocking`。

下面是完整的异步原生消费示例，收到 Ctrl-C 时停止并等待后台任务：

```rust,ignore
use eventmesh::{
    config::{Credentials, Endpoint, GrpcConfig, GrpcConsumerOptions, Identity},
    GrpcChannel, GrpcStreamConsumer, Message, MessageHandler, Subscription,
};

struct OrdersHandler;

impl MessageHandler for OrdersHandler {
    async fn handle(&self, message: Message) -> eventmesh::Result<Option<Message>> {
        let message = message.into_event_mesh()?;
        println!("topic={}, content={}", message.topic(), message.content());
        Ok(None)
    }
}

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    let config = GrpcConfig::new(Endpoint::new("127.0.0.1", 10_205)?)
        .with_identity(Identity::default().with_env("dev").with_system("evetmesh"))
        .with_credentials(Credentials::new().with_basic("username", "password"));
    let consumer = GrpcStreamConsumer::open(
        GrpcChannel::connect(config).await?,
        GrpcConsumerOptions::new("orders-consumer"),
        [Subscription::new("orders.created")],
        OrdersHandler,
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

`GrpcStreamConsumer::open` 要求至少一个初始订阅；`Subscription::new(topic)` 默认是集群、异步消费。
新增或移除单个订阅分别使用 `subscribe(subscription).await?` 和 `unsubscribe(subscription).await?`，
不再传 `Vec<SubscriptionItem>`。广播和同步类型使用 `with_delivery_mode(DeliveryMode::Broadcast)`、
`with_delivery_type(DeliveryType::Sync)`。
旧 `UNRECOGNIZED` 枚举值已移除；解析未知值会失败。新版枚举带 `non_exhaustive`，应用的 `match` 需要兜底分支。
线上序列化值仍使用 `CLUSTERING` / `BROADCASTING` 和 `ASYNC` / `SYNC`。

### 不要把旧回调返回值原样搬过来

| handler 结果 | 旧 gRPC consumer 实际行为 | 新版约定 / gRPC 行为 |
| --- | --- | --- |
| `Ok(None)` | 不回发消息 | 异步处理成功；gRPC 不额外发送业务 reply，HTTP/TCP 按各自协议确认 |
| `Ok(Some(message))` | 只给原帧设置回复标记并清空 data，返回消息内容未使用 | 实际编码并发送返回的回复内容，用于同步 request/reply |
| `Err(error)` | 记录日志后继续接收 | gRPC 终止流并经 `join()` 报错；其他传输见第 9 节 |

如果旧 handler 返回 `Some(msg)` 只是为了表示“处理成功”，迁移异步消费时改成 `Ok(None)`。
同步订阅则返回 `Ok(Some(reply))`，SDK 自动恢复当前请求的关联和路由信息。
gRPC 回复保留声明的内容类型和数据，不再把所有返回内容统一标成 JSON。

gRPC handler 默认串行执行；用 `GrpcConsumerOptions::with_max_concurrent_handlers(n)` 启用有界并发时，
完成及回复顺序可能改变。HTTP 回调可并发执行，TCP 按角色串行分发，不应跨协议假定相同顺序。

### 当前 gRPC 流订阅限制

以下情况在本次重构后仍然存在，迁移不能把返回成功作为订阅已生效的证据：

- `open()` 建立流、`subscribe()` 入队请求，都不等待 Runtime 接受订阅；没有 `seqnum` 的拒绝控制帧会被忽略。
  Runtime 正常关闭流时 `join()` 也可能返回成功。排查 ACL 或校验拒绝需要查看 Runtime 日志。
- 在同一流订阅 A、B 后取消 A，Java Runtime 可能关闭两者共享的流，导致 B 也停止消费。
  SDK 不会自动重建流或重放 B；等待旧 consumer 结束并释放后，用剩余订阅显式 `open` 新 consumer。

流建立等待响应头最多 15 秒，这不是订阅成功确认，也不会把已建立流的寿命限制为 15 秒。
详见 [gRPC 限制及实现依据](ARCHITECTURE.md#grpc-stream-subscription-limitations)。

## 6. 批量发布、request/reply 与错误处理

`publish`、`publish_batch` 成功返回 `PublishReceipt { code, message, server_time_millis }`。
旧发布路径把 Runtime 返回包装成 `EventMeshResponse`，非成功业务码也可能处在 `Ok` 中；
新版发布路径要求明确成功状态，拒绝或不完整响应走错误路径，不应继续解析旧响应的 Display 字符串。

gRPC `publish_batch(Vec<Message>)` 要求非空且消息类型一致：全是原生消息或全是 CloudEvents。
空批次返回 `InvalidArgument`，混合两种类型返回 `Unsupported`。HTTP/TCP 没有对应的公开批量方法。

旧 `request_reply(message, timeout_ms)` 改为 `request_reply(message)` 使用客户端默认值，
或者 `request_reply_with_timeout(message, Duration)` 覆盖单次调用：

```rust,ignore
use std::time::Duration;

use eventmesh::{EventMeshMessage, GrpcProducer};

async fn request_order(producer: &GrpcProducer) -> eventmesh::Result<EventMeshMessage> {
    let request = EventMeshMessage::new("orders.query", "order-42")?;
    let reply = producer
        .request_reply_with_timeout(request.into(), Duration::from_secs(1))
        .await?;
    reply.into_event_mesh()
}
```

先启动 `DeliveryType::Sync` 的订阅者，handler 返回 `Ok(Some(reply))`；订阅路由就绪后再请求。
上述辅助函数限定原生消息，CloudEvents 应直接处理 `Message::CloudEvent`。
gRPC 发布、批量和 request/reply 使用每次 RPC 的 deadline，超时返回 `Error::Timeout`，
不会给长连接订阅统一套上业务请求超时。gRPC 连接建立另有 10 秒连接超时。

SDK 的 `Result` 不再自动接收任意 `anyhow` 或第三方错误。应用顶层可以继续使用自己的 `anyhow::Result`；
实现 `MessageHandler` 时，把业务错误显式映射为适当的 `eventmesh::Error`，已有 `From` 实现的错误仍可使用 `?`。

```rust
use eventmesh::Error;

fn report_error(error: &Error) {
    match error {
        Error::Timeout(duration) => eprintln!("timeout after {duration:?}"),
        Error::Server { code, message } => eprintln!("server rejected: {code}, {message}"),
        Error::InvalidArgument(message) | Error::InvalidMessage(message) => {
            eprintln!("invalid input: {message}");
        }
        Error::ChannelClosed(message) => eprintln!("consumer/channel stopped: {message}"),
        other => eprintln!("eventmesh failure: {other}"),
    }
}
```

`Error` 带 `non_exhaustive`，保留兜底分支。`Error::Grpc` 的状态码和消息是结构化字符串，
不再要求应用向下转换旧的私有 `EventMeshError::GRpcStatus`。

## 7. 保留 CloudEvents 类型

旧生产者用泛型和运行时类型判断选取编码，旧 consumer 固定输出原生 `EventMeshMessage`。
新版生产者和 handler 统一使用 `Message`：

- 原生消息：`Message::EventMesh(message)` 或 `Message::from(message)`。
- 启用 `cloud_events` 后：`Message::CloudEvent(event)` 或 `Message::from(event)`。
- 接收端匹配实际 variant；`Message::into_event_mesh()` 对 CloudEvent 返回 `Error::Unsupported`，不会隐式丢失其语义。

例如，启用 `grpc,cloud_events` 并直接依赖 `cloudevents-sdk` 后：

```rust,ignore
use cloudevents::{EventBuilder, EventBuilderV10};
use eventmesh::{Error, GrpcProducer, Message, PublishReceipt};

async fn publish_cloud_event(producer: &GrpcProducer) -> eventmesh::Result<PublishReceipt> {
    let event = EventBuilderV10::new()
        .id("event-42")
        .source("urn:orders:service")
        .ty("orders.created")
        .subject("orders.created")
        .data("application/json", r#"{"id":42}"#)
        .build()
        .map_err(|error| Error::InvalidArgument(format!("invalid CloudEvent: {error}")))?;
    producer.publish(Message::CloudEvent(event)).await
}
```

`subject` 提供目标 topic。接收时保留 CloudEvent 标准属性、业务扩展和数据类型；
新发布会从发送副本过滤已知传输/投递扩展，身份和认证来自目标客户端，不能依靠事件里的认证扩展覆盖配置。
回复的已知路由属性来自当前请求，业务扩展和内容类型由回复保留。
原生消息的保留属性禁写规则与 CloudEvent 标准属性/扩展是两个层次，不要把整个 CloudEvent 拆进原生 `props`。

TCP CloudEvents 仍受 Runtime 编解码约束：使用 `protocoltype=cloudevents`、
`datacontenttype=application/cloudevents+json` 和原始 CloudEvent JSON；
参见 [TCP CloudEvents 示例](examples/tcp/producer_cloud_events.rs)。

## 8. 持有角色并显式关闭

旧 consumer 的接收与心跳任务在创建/订阅时启动，调用者没有公开的 shutdown/join 接口。
新版角色拥有后台任务，需要在整个消费期间保留角色对象；丢弃对象会终止其未完成的任务。

| 角色 | 关闭方式 | 迁移注意事项 |
| --- | --- | --- |
| `GrpcStreamConsumer` | `shutdown()`，然后 `join().await` | shutdown 只发停止信号；join 等待包括已开始 handler 在内的任务并报告失败 |
| `GrpcWebhookConsumer` | 先 `unsubscribe(subscriptions, url).await`，再 shutdown/join | 不会自动取消远端 webhook 注册；保存订阅列表与 URL |
| `HttpConsumer` / `WebhookRegistration` | 需要远端清理时 `close().await` | 等待已提交心跳，停止调度，取消已跟踪注册，再等待本地任务；失败的取消可再次 close 重试 |
| `TcpConsumer` | `shutdown()`，然后 `join().await` | 需要先取消全部订阅时调用 `unsubscribe_all().await` |
| `TcpProducer` | `shutdown().await`，然后 `join().await` | shutdown 本身是异步方法，join 仍用于观察终止失败 |

旧 `subscribe_webhook(items, url)` 迁移到 `GrpcWebhookConsumer::new(channel, ConsumerOptions::new(group)).await?`
再调用 `subscribe(items, url).await?`。该角色只管理注册和心跳，不会自动启动 HTTP 回调端点；
先启动应用自己的服务器，或启用 `http-server` 使用 `webhook::WebhookServer`。
参见 [完整 gRPC webhook 注册及清理示例](examples/grpc/webhook_consumer.rs)。

取消一次 `join()` 等待不会丢失角色持有的任务或待报告错误，可以再次等待。
HTTP 的 `shutdown()` / `join()` 仅停止本地工作，与执行远端清理的 `close()` 不同；
HTTP 关闭开始后继续修改订阅会返回 `ChannelClosed`。
HTTP consumer 启动 future 被取消时会清理本地服务器和心跳，但不会撤销 Runtime 已接受的订阅。

## 9. HTTP、TCP 是新增选择，先确认能力差异

从旧 `master` 迁移的 gRPC 应用可以继续使用 gRPC，不需要同时切换传输。
需要新增协议时，复用 `Message`、`Subscription` 和角色 options，连接及生命周期仍按协议分别管理：

| 能力 | gRPC | HTTP | TCP |
| --- | --- | --- | --- |
| 入口与默认服务端口 | `GrpcChannel::connect`，10205 | `HttpClient::new`，10105 | `TcpClient::new`，10000 |
| 角色创建 | `GrpcProducer::new` / `GrpcStreamConsumer::open` | `client.producer` / `client.consumer` / `client.webhook_registration` | 异步 `client.producer` / `client.consumer` |
| 原生消息和 CloudEvents | 支持；CloudEvents 需 feature | 支持；CloudEvents 需 feature | 支持；CloudEvents 需 feature |
| 批量发布 | 支持同类型批次 | 无公开方法 | 无公开方法 |
| request/reply | 支持 | 无完整公开 responder 路径 | 支持 |
| broadcast 操作 | 无公开方法 | 无公开方法 | 支持；等待本地 socket 写完，不等待 Runtime ACK |
| 默认业务请求超时 | 5 秒 | 15 秒 | 20 秒；另有连接 1 秒、控制操作 20 秒 |
| 连接与重连 | channel 克隆共享连接；流结束不自动重建消费 | client 克隆及角色共享连接池和端点选择器 | 每个角色建立独立 session；自动重连并重放已确认订阅 |
| 取消订阅 | 流有上述关闭整个 emitter 的限制 | 按 topic / webhook URL 管理 | Runtime 是 session 级取消，仅公开 `unsubscribe_all()` |

HTTP 使用非空 `EndpointSet`，支持权重、TLS 和代理配置。托管 consumer 需要 Runtime 能访问的 callback URL，
应用自己承接 HTTP 回调时可用 `http-client` 的 `parse_push_body(body)?.to_message(&headers)?` 和 `WebhookReply`。
HTTP 发布/订阅使用 `/` 上的 code-header 路由与 form body，不应把原生业务消息直接序列化成旧 JSON DTO 发送。

TCP 默认启用自动重连、重试次数上限为 `usize::MAX`，指数退避从 1 秒到最多 30 秒；
通过 `TcpConfig::with_reconnect(ReconnectPolicy::...)` 调整。handler 返回错误或发生可展开 panic 时，
SDK 不给该投递发送 ACK/reply，并关闭失败 session；重连成功后重放确认过的订阅并恢复 LISTEN。
重试禁用/耗尽或订阅重放失败时角色停止，终止错误通过 `join()` 报告。
跨重连完成的旧 handler 不会向新 session 写入 ACK/reply；实际重投由 Runtime/存储及 TTL 决定，业务仍需幂等处理。
HTTP handler 的错误通过回调失败响应反映；不能把 TCP 的恢复行为推广到 gRPC 或 HTTP。

## 10. 建议迁移与验收顺序

1. 升级 Rust，按协议启用 features，删除 `eventmesh_message` / `tls`，直接依赖 Tokio。
2. 替换旧模块导入；拆分 endpoint、身份、认证和角色配置，显式核对旧默认值。
3. 把消息改成 builder / `new(topic, content)?`；TTL、内容类型与路由属性按归属迁移。
4. 用 `GrpcChannel` 和具体 producer 替换泛型生产者；修改 receipt、单次超时及错误处理。
5. 把同步 listener 改成异步 handler；确认 ASYNC 返回 None、SYNC 返回实际回复；迁移订阅枚举。
6. 持有 consumer，接入停止信号并等待 `join()`；webhook 保存并显式取消远端注册。
7. 在现有 Runtime 上验证发布/接收、业务属性、认证、超时、request/reply、停机及重启订阅。
   CloudEvents 应同时核对 topic、标准属性、业务扩展、内容类型和实际数据。

在应用目录先运行 `cargo check`、应用自己的测试及实际选择的 feature 组合。
在 SDK 目录可检查示例与全功能测试：

```bash
cd eventmesh-sdks/eventmesh-sdk-rust
cargo check --examples --features full
cargo test --features full
cargo doc --features full --no-deps
```

现场集成与 Java 互通测试是独立的验收步骤：

```bash
cargo test --features e2e
cargo test --features interop_e2e --test e2e interop
```

测试 harness 默认使用 SDK 自带 compose 的 RocketMQ profile，Runtime 镜像固定为 `apache/eventmesh:v1.12.0`；
Java peer 使用已发布的 `eventmesh-sdk-java:1.12.0-release`，不代表当前仓库所有 Java 改动都已覆盖。
standalone 存储不实现 request/reply，不适合作为同步消息迁移的完整验收环境。
测试前提和外部 Runtime 配置参见 [CONTRIBUTING.md](CONTRIBUTING.md#end-to-end-tests)。

本次文档验证使用 Rust 1.86.0：从本文提取的 5 段新版 Rust 示例编译通过，SDK 的格式检查、
无默认 feature / `full` 的 Clippy 检查、`full` 测试和 API 文档构建均通过。
网络示例只做编译校验，未执行 `e2e` 或 `interop_e2e` 现场测试；上面的现场命令供应用迁移验收时使用。

## 源码与示例索引

- 依赖、版本、features：[Cargo.toml](Cargo.toml)、[build.rs](build.rs)。
- 公共入口、配置、错误：[lib.rs](src/lib.rs)、[config/client.rs](src/config/client.rs)、[error.rs](src/error.rs)。
- 消息与上下文：[message.rs](src/message.rs)、[model/message.rs](src/model/message.rs)、[model/delivery.rs](src/model/delivery.rs)。
- 消费与订阅：[handler.rs](src/handler.rs)、[subscription.rs](src/subscription.rs)、[grpc.rs](src/grpc.rs)。
- 旧版对照：用 `git show master:eventmesh-sdks/eventmesh-sdk-rust/<path>` 查看
  `src/config/grpc_config.rs`、`src/grpc/impl/grpc_producer_impl.rs`、`src/grpc/grpc_consumer.rs`
  和 `src/common/grpc_eventmesh_message_utils.rs`。
- 可运行程序及各自的 feature 命令：[examples/README.md](examples/README.md)。
- 从 Java SDK 迁移的独立说明：[MIGRATING_FROM_JAVA.md](MIGRATING_FROM_JAVA.md)。
