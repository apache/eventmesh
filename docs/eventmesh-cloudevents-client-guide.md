# EventMesh CloudEvents 客户端使用指引（RocketMQ 4.x / 5.x / Kafka 后端通用）

## 1. 这是什么

新架构下 EventMesh 对外**只提供一套客户端 SDK**：`eventmesh-sdk-java` 的 `CloudEventsClient`。
它是 **HTTP + CloudEvents** 的极简客户端（无 TCP/gRPC、无 MQ 组语义），通过 EventMesh Runtime 的
`/events/*` HTTP 端点收发标准 [CloudEvents](https://cloudevents.io) 事件。

**关键点：客户端与 MQ 后端完全解耦。** 客户端只跟 EventMesh Runtime（HTTP）打交道，Runtime 后面接
RocketMQ 4.x、RocketMQ 5.x 还是 Kafka，对客户端代码**完全透明**——同一份客户端代码可以原样跑在任一
后端上。三种后端的区别只在 **Runtime（服务端）的部署配置**，不在客户端。

> 旧的 `EventMeshHttpClient` / `EventMeshTCPClient`（legacy）仍保留作老协议兼容；新接入请用 `CloudEventsClient`。

---

## 2. 客户端 API 总览

```java
org.apache.eventmesh.client.cloudevents.CloudEventsClient
```

| 方法 | 说明 |
|---|---|
| `builder()` | 构造器入口（见下） |
| `publish(topic, CloudEvent)` → `boolean` | 发布单条（202=成功） |
| `publish(topic, List<CloudEvent>)` → `boolean` | 批量发布 |
| `request(topic, CloudEvent, timeoutMs)` → `CloudEvent` | **阻塞式请求-应答**（等回复） |
| `reply(correlationId, CloudEvent)` → `boolean` | 应答方回送一个 reply |
| `subscribe(topic, mode, Consumer<CloudEvent>)` | 长轮询订阅，**handler 返回后自动 ACK** |
| `subscribeWithAck(topic, mode, Predicate<CloudEvent>)` | 长轮询订阅，**手动 ACK**：predicate 返回 `true`=已处理(ACK，offset 推进)；`false`=不 ACK（dispatcher 超时后重投，at-least-once） |
| `createLiteTopic(parent, lite)` → `boolean` | **（仅 5.x）** 建/声明 lite topic（确保 parent 为 LITE 类型） |
| `publishLite(parent, lite, CloudEvent)` → `boolean` | **（仅 5.x）** 发布到 lite topic（路由进 LMQ） |
| `subscribeLite(parent, lite, Consumer<CloudEvent>)` | **（仅 5.x）** 订阅 lite topic（后台循环拉 LMQ + handler 回调，push 风格，与 `subscribe` 一致；无 ACK，offset 自管） |
| `subscribeSse(topic, mode, Consumer<CloudEvent>)` | SSE 推送订阅（`/events/stream`） |
| `subscribeWs(topic, mode, Consumer<CloudEvent>)` | WebSocket 推送订阅 |
| `unsubscribe(topic)` | 退订单个 topic（服务端移除该 `{clientId, topic}` 订阅，其他保留；若无普通 topic 剩余则停长轮询循环） |
| `unsubscribeLite(parent, lite)` | **（仅 5.x）** 停某个 lite 订阅的后台拉取循环（lite 无服务端注册，纯客户端停循环） |
| `unsubscribe()` | 退订全部（服务端按 clientId 移除所有订阅 + 停所有循环/推送） |
| `shutdown()` | 关闭客户端（停轮询/SSE/WS） |
| `static event(id, source, type, byte[] data)` | 便捷构造一个 CloudEvent |

**Builder：**
```java
CloudEventsClient.builder()
    .runtimeUrl("http://localhost:8080")   // EventMesh Runtime 地址（必填）
    .clientId("my-service")                 // 客户端标识（必填）
    .pollIntervalMs(500L)                   // 长轮询间隔（默认由 builder 决定）
    .build();
```

**订阅模式 `mode`**（`org.apache.eventmesh.runtime.subscription.DistributionMode`，传字符串）：
- `BROADCAST` — 广播：每个订阅者都收到全量消息。
- `LOAD_BALANCE` — 负载均衡：同一消息只投递给组内一个订阅者。
- `MULTICAST` — 多播。
- `LOAD_BALANCE_STICKY` — 按 partition key 稳定哈希到同一个订阅者（保序）。

---

## 3. 快速上手

### 依赖
客户端 jar：`eventmesh-sdk-java`（Maven 坐标 `org.apache.eventmesh:eventmesh-sdk-java`，或直接用本仓库
`eventmesh-sdks/eventmesh-sdk-java` 模块）。**客户端不依赖任何 MQ jar**（不引 RocketMQ / kafka-clients）——它只发 HTTP。

### 发布 + 订阅（长轮询，自动 ACK）
```java
CloudEventsClient client = CloudEventsClient.builder()
    .runtimeUrl("http://localhost:8080")
    .clientId("order-svc")
    .pollIntervalMs(500L)
    .build();

// 订阅（后台长轮询，每条事件 handler 返回后自动 ACK）
client.subscribe("orders", "BROADCAST", event -> {
    System.out.println("收到: " + event.getId());
});

// 发布
CloudEvent e = CloudEventsClient.event("evt-1", "order-svc", "order.created",
    "{\"amt\":99}".getBytes(StandardCharsets.UTF_8));
boolean ok = client.publish("orders", e);
```

### 手动 ACK（at-least-once，业务幂等窗口由客户端控制）
```java
client.subscribeWithAck("orders", "LOAD_BALANCE", event -> {
    try {
        process(event);          // 业务处理
        return true;             // true → ACK，offset 推进
    } catch (Exception ex) {
        return false;            // false → 不 ACK，ACK 超时后重投
    }
});
```

### 请求-应答（阻塞）
```java
// 请求方：发一个请求，阻塞等回复（带 emcorrelationid）
CloudEvent req = CloudEventsClient.event("req-1", "caller", "query.price", payload);
CloudEvent reply = client.request("price-req", req, 10_000L);   // 最多等 10s
if (reply != null) { /* 用 reply */ }

// 应答方：订阅请求 topic，看到带 correlation 的请求就 reply
responder.subscribe("price-req", "LOAD_BALANCE", event -> {
    Object corr = event.getExtension("emcorrelationid");
    if (corr != null) {
        CloudEvent r = CloudEventsClient.event("reply-1", "price-svc", "query.price.reply",
            priceJson(event).getBytes(StandardCharsets.UTF_8));
        responder.reply(corr.toString(), r);
    }
});
```

> 注意：correlation 用 CloudEvents 扩展名 **`emcorrelationid`**（全小写无连字符，CloudEvents 规范不允许扩展名含连字符）。

### Lite Topic（仅 5.x 后端）

RocketMQ 5.5 Lite Topic（RIP-83）：topic 内的二级消息容器。客户端三步走（仅对 5.x 后端有效；4.x 后端返回 `false`、服务端 501）：

```java
// 1. 建/声明 lite topic（确保 parent 为 LITE 类型）—— 幂等，首次调用一次即可
client.createLiteTopic("orders", "user-42");

// 2. 订阅 lite topic（后台循环拉 LMQ + handler 回调，push 风格，和 subscribe 一致）
client.subscribeLite("orders", "user-42", event -> { /* 处理 lite 事件 */ });

// 3. 发布到 lite topic（带 __LITE_TOPIC，broker 路由进 LMQ）
client.publishLite("orders", "user-42",
    CloudEventsClient.event("lt-1", "order-svc", "order.lite", payload));
```

> Lite topic 语义与普通 topic 不同：`(parent, lite)` 唯一标识一个 LMQ 容器；`subscribeLite` 走**后台拉取**（不走
> EventMesh 的 ACK/重投/DLQ 可靠层，offset 在存储插件内自管）。适合海量轻量会话/子分类场景。停止用 `unsubscribe()` / `shutdown()`。

### SSE / WebSocket 推送
```java
// SSE：复用 HTTP 端口的 /events/stream（text/event-stream）
client.subscribeSse("orders", "BROADCAST", event -> { /* 服务端长连接推送 */ });

// WebSocket：runtime 的 WS 服务跑在独立端口（启动参数 -Deventmesh.ws.port=<port>，0=自动），
// 客户端需显式指定 wsUrl（否则会连到 HTTP 端口的 SSE 端点，握手失败）
CloudEventsClient wsClient = CloudEventsClient.builder()
    .runtimeUrl("http://localhost:8080")   // HTTP 端口（publish / subscribe / SSE）
    .wsUrl("http://localhost:8082")         // WS 推送端口
    .clientId("ws-sub").build();
wsClient.subscribeWs("orders", "BROADCAST", event -> { /* WS 推送 */ });
```
> SSE 与 WS 都是服务端推送（客户端不用轮询）；WS 需独立端口 + `wsUrl`，SSE 走 HTTP 端口。两者都自动 ACK。

### 关闭
```java
client.unsubscribe();
client.shutdown();
```

---

## 4. 后端选择：RocketMQ 4.x / RocketMQ 5.x / Kafka

**结论：客户端代码完全一致。** 三种后端的差异 100% 在 Runtime（服务端）侧——你只是把 Runtime 指向不同的
MQ 集群，客户端那份 `CloudEventsClient` 代码一行都不用改。

### 4.1 唯一要改的：Runtime（服务端）配置

| | RocketMQ 4.x | RocketMQ 5.x | Kafka |
|---|---|---|---|
| 选插件 | `-Deventmesh.storage.type=rocketmq` | `-Deventmesh.storage.type=rocketmq5` | `-Deventmesh.storage.type=kafka` |
| 接入地址 | `eventMesh.server.rocketmq.namesrvAddr=<4.x:9876>` | `eventMesh.server.rocketmq5.namesrvAddr=<5.x:9876>` | `eventMesh.server.kafka.namesrvAddr=<host:9092,...>` |
| 鉴权 | 无 / ACL | 无 / ACL | **SASL**：`security.protocol` / `sasl.mechanism` / `sasl.jaas.config`（透传给 kafka-clients） |
| 插件实现 | `RocketMQRemotingStoragePlugin`（4.9 remoting） | `RocketMQ5RemotingStoragePlugin`（5.5 remoting） | `KafkaMeshStoragePlugin`（kafka-clients） |
| 连接方式 | `NettyRemotingClient` 直连（remoting，**不引 rocketmq-client**） | 同左（**不引 rocketmq-client/gRPC**） | `KafkaProducer`/`Consumer`/`AdminClient`（assign+seek+poll，**无 consumer group**，EventMesh 自管 offset） |

部署时在 `eventmesh.properties`（或 `-D` 参数）里设这些值，Runtime 启动时按 `eventmesh.storage.type`
SPI 加载对应插件。客户端只要把 `runtimeUrl` 指向该 Runtime 的 HTTP 端口即可。

**Kafka + SASL（如 wemq-kafka）配置示例**（`eventmesh.properties`）：
```properties
eventMesh.server.kafka.namesrvAddr=127.0.0.1:9094
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="<UM用户名>" password="<UM密码>";
```
> Kafka 的 `security.*` / `sasl.*` / `ssl.*` 配置由 `KafkaMeshStoragePlugin` 透传给 producer / consumer / AdminClient（`kafka.` 前缀也接受）。非 SASL 的明文 Kafka 不设这些即可。

### 4.2 对客户端透明的行为差异（了解即可，不影响 API）

| 维度 | RocketMQ 4.x | RocketMQ 5.x | Kafka |
|---|---|---|---|
| 消费模型 | 经典 PULL（EventMesh 自管 offset + 分区所有权） | **POP**（broker 端分配队列，poll-all） | **assign+seek+poll**（无 consumer group，EventMesh 自管 offset） |
| 多实例去重 | EventMesh `PartitionOwnership` + 分区分配 | broker POP + lease gate | EventMesh `PartitionOwnership`（Kafka assign 分区） |
| ACK 语义 | offset 仅 ACK 后推进 | 相同 | 相同（Kafka offset 不 commit，EventMesh 自管） |
| 普通 publish/subscribe/request-reply | 一致 | 一致 | 一致 |

客户端调用的 `publish` / `subscribe` / `subscribeWithAck` / `request` / `reply` / `ack`，行为契约在三种后端上
完全一致——这就是抽象的价值。

### 4.3 Lite Topic（仅 5.x）

RocketMQ 5.5 的 **Lite Topic（RIP-83）** 是 5.x 独有能力（topic 内的二级消息容器，面向海量会话/子分类）。
**已端到端接入 HTTP 客户端**（仅对 5.x 后端可用；4.x / kafka / standalone 后端调 lite 方法返回 `false`，服务端 501）：

- 客户端：`CloudEventsClient.createLiteTopic(parent, lite)` / `publishLite(parent, lite, event)` / `subscribeLite(parent, lite, handler)`。
- Runtime 端点：`POST /events/lite/create`、`POST /events/lite/publish`、`GET /events/lite/poll`（→ `UniIngressService` → `LiteTopicCapable` 存储插件；`subscribeLite` 在客户端后台循环调 `/events/lite/poll`）。
- 插件：`RocketMQ5RemotingStoragePlugin` 实现 `LiteTopicCapable`（`sendLite` 带 `__LITE_TOPIC` 消息属性路由进 LMQ；`pullLite` 经典 PULL + liteTopic 从 LMQ 拉，offset 自管）。

Lite 用法见 §3.5。**注意**：4.x 后端没有 lite 能力，调 `publishLite` / `subscribeLite` 会得到 `false`/无推送（服务端返回 501）。

---

## 5. 一份端到端示例（4.x / 5.x 通用客户端）

```java
// === 这份代码同时适用于 4.x 后端和 5.x 后端，无需改动 ===
public class Demo {
    public static void main(String[] args) {
        CloudEventsClient client = CloudEventsClient.builder()
            .runtimeUrl(System.getProperty("eventmesh.runtime.url", "http://localhost:8080"))
            .clientId("demo-" + System.currentTimeMillis())
            .pollIntervalMs(500L)
            .build();

        client.subscribeWithAck("demo-topic", "LOAD_BALANCE", event -> {
            System.out.println("处理: " + event.getId() + " type=" + event.getType());
            return true; // ACK
        });

        for (int i = 0; i < 10; i++) {
            CloudEvent e = CloudEventsClient.event("e" + i, "demo", "demo.tick",
                ("tick-" + i).getBytes(StandardCharsets.UTF_8));
            client.publish("demo-topic", e);
        }

        // Runtime.sleep / 处理 …
        client.shutdown();
    }
}
```

切后端时**只改服务端**：
```bash
# 4.x 后端
gradle :eventmesh-runtime:dist
EVENTMESH_STORAGE_TYPE=rocketmq EVENTMESH_ROCKETMQ_NAMESRV=127.0.0.1:9876 bin/start.sh

# 5.x 后端（同一个 dist 镜像，三个 storage 插件都在）
EVENTMESH_STORAGE_TYPE=rocketmq5 EVENTMESH_ROCKETMQ5_NAMESRV=127.0.0.1:9876 bin/start.sh

# Kafka 后端（bootstrap + SASL 在 eventmesh.properties 里配，见 §4.1）
EVENTMESH_STORAGE_TYPE=kafka bin/start.sh
```
（具体环境变量名以 `bin/start.sh` 的 `-D` 映射为准；`eventmesh.storage.type` / `eventMesh.server.<rocketmq|rocketmq5|kafka>.namesrvAddr` 是关键属性。）

---

## 6. 注意事项 / 限制

- **ACK 语义**：`subscribe`（自动 ACK）在 handler 抛异常时**不会**重投（已 ACK）；要 at-least-once，用
  `subscribeWithAck` 并在失败时返回 `false`。
- **CloudEvents 扩展名**不能含连字符：`emcorrelationid`、`emsignature`、`emdlqreason`、`emtenantid` 等（全小写）。
- **请求-应答超时**：`request(topic, event, timeoutMs)` 是阻塞调用，超时返回 `null`；迟到回复会被丢弃。
- **Lite Topic**：仅 5.x 后端（已端到端接入 HTTP 客户端，见 §4.3）；4.x / Kafka 后端调 lite 方法返回 `false`（服务端 501）。
- **后端切换**：RocketMQ 4.x / 5.x / Kafka 之间切换是服务端配置切换，客户端代码无需改动、无需重新打包。
- **Kafka SASL**：对带鉴权的 Kafka（如 wemq-kafka），在 `eventmesh.properties` 配 `security.protocol` / `sasl.mechanism` / `sasl.jaas.config`，插件透传给 kafka-clients（见 §4.1）。

---

## 附：相关代码位置
- 客户端：`eventmesh-sdks/eventmesh-sdk-java/.../client/cloudevents/CloudEventsClient.java`（+ `CloudEventsClientBuilder`）
- Runtime HTTP 端点：`eventmesh-runtime/.../http/UniHttpServer.java`（`/events/*`）
- 4.x 存储插件：`eventmesh-storage-plugin/eventmesh-storage-rocketmq`（SPI key `rocketmq`）
- 5.x 存储插件：`eventmesh-storage-plugin/eventmesh-storage-rocketmq5`（SPI key `rocketmq5`，含 `LiteTopicCapable`）
- Kafka 存储插件：`eventmesh-storage-plugin/eventmesh-storage-kafka`（SPI key `kafka`，assign+seek+poll + SASL 透传）
- 设计文档：`docs/eventmesh-uni-architecture-redesign.md`
