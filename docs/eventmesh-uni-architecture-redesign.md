# EventMesh 架构简化重构方案

> 分支：`refactor/unified-runtime-pipeline` → 基于 本次讨论的全新方向
>
> 核心变化：**丢弃 MQ 的 Producer Group / Consumer Group 语义，MQ 仅作存储层，EventMesh 自主维护订阅分发语义。内部全程 `EventMeshFrame`（CloudEvent 退化为对外入口格式之一）；对外多协议（CloudEvents / MeshMessage / A2A）经 `FrameAdaptor` SPI 直接转 Frame，互不转换。SDK 简化为 HTTP-only。**
>
> **v2.0 架构深化（2026-08-13 整合 `eventmesh-architecture-refinement.md`）**：
> - 内部全程 `EventMeshFrame`（storage SPI + dispatch 管线 + 载体全翻 Frame）。
> - 对外协议转换收进 `FrameAdaptor` SPI（CloudEvents/MeshMessage/A2A 各独立插件，零 CE 互转）。
> - Offset 路②（纯本地、不上报 meta、不给 group.id、接管靠 MQ 重放）。
> - 全面粘性负载均衡（session 分配层 recommend、实例自采负载、不转发）。

---

## 结论先行

当前 EventMesh 架构继承了 Kafka/RocketMQ 的 Producer Group / Consumer Group 概念，导致：

- **SDK 复杂**：必须理解 Group、Topic、Tag 等 MQ 语义，学习成本高
- **语义混乱**：EventMesh 自己的订阅模型（负载均衡 / 广播 / 多播）与 MQ Group 概念纠缠不清
- **多协议负担**：TCP + HTTP + gRPC 三个 SDK 并行维护，协议适配代码膨胀

**重构方向：EventMesh 变成一个纯粹的"CloudEvents over MQ"的消息总线。MQ 是 EventMesh 的存储后端，EventMesh 管理自己的订阅分发逻辑，客户端只需要知道 HTTP + CloudEvents。**

---

## 一、核心设计原则

### 1.1 五条铁律

| 铁律 | 说明 |
|------|------|
| **MQ 无语义** | Kafka/RocketMQ 只当"持久化的 FIFO 队列"用，不暴露任何 Producer Group / Consumer Group / Tag 概念给客户端 |
| **EventMesh 自主订阅** | 订阅分发逻辑（谁收哪条消息、按什么策略分发）全部由 EventMesh 自己维护，不委托给 MQ |
| **EventMesh 自主 Offset** | EventMesh 自主管理每个订阅关系的消费位点（topic#clientId#partition → offset），参考 RocketMQ Client OffsetStore 实现，使用 RocksDB 持久化，不依赖 MQ Consumer Group offset |
| **SDK 极简** | 只有一个 SDK（HTTP），只暴露两个对象（CloudEvent + Subscription），只有三个 API（publish / subscribe / unsubscribe） |
| **内部 EventMeshFrame** | 内部全程 `EventMeshFrame`（定长头+KV属性+raw data），CloudEvent 退化为对外入口格式之一；对外多协议（CloudEvents/MeshMessage/A2A）经 `FrameAdaptor` SPI 直接转 Frame，互不转换 |
| **Connector 独立部署** | Connector Runtime 是独立进程，与 EventMesh Runtime 通过 HTTP + CloudEvents 接口通信，不共享内部组件，各自有独立的生命周期和 OffsetStore |

> **🔎 实现状态速览（v2.0 / 2026-08-13 盘点）**：五条铁律中，"内部 EventMeshFrame / Connector 独立部署 / SDK 极简（HTTP-only）"已落实；"EventMesh 自主订阅 / 自主 Offset"单实例成立（offset 路②纯本地 + MQ 重放接管，meta 上报默认关）。多实例协调改为**全面粘性**（session 分配层 recommend，停用转发层）。对外协议经 `FrameAdaptor` SPI（CloudEvents 独立插件 + MeshMessage + A2A 各自直接转 Frame，零 CE 互转）。详见 §19 架构深化。

### 1.2 与当前设计的本质区别

```
                    当前设计                              重构后设计
                    ─────────                              ──────────

  EventMesh SDK:
  ├─ TCP SDK       (Package 协议)            →   全部删除
  ├─ HTTP SDK      (EventMeshMessage)        →   简化为 CloudEvents-only
  └─ gRPC SDK      (proto CloudEvent)        →   全部删除

  MQ 角色:
  ├─ 暴露 Group 语义给客户端  ──────────────  →   纯存储，不暴露任何语义
  ├─ 多个 Consumer Group 竞争消费              ├─ EventMesh 1 个 Consumer 拉取
  └─ Producer Group (RocketMQ)                └─ EventMesh 1 个 Producer 写入

  订阅分发:
  ├─ Consumer Group 竞争/广播继承 MQ 语义 ──  →   EventMesh 自主实现
  │                                             ├─ 负载均衡: RoundRobin / 粘性会话
  └─ 分发策略散落在各 Processor 中             └─ 广播: 所有订阅者全量收到
                                                       多播: 按 tag 匹配订阅者

  协议适配:
  ├─ 24 HTTP Processor                         →   1 个统一的 IngressHandler
  ├─ 11 gRPC Processor                         →   删除（无 gRPC SDK）
  ├─ TCP Dispatcher + Session                   →   删除（无 TCP SDK）
  └─ 102 个 Processor 类                       →   ~10 个以内
```

---

## 二、目标架构总览

### 2.1 宏观架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        EventMesh Client                          │
│                     (HTTP SDK, CloudEvents-only)                  │
│                                                                   │
│   cloudEventsClient.publish(CloudEvent)                           │
│   cloudEventsClient.subscribe(topic, handler)                     │
│   cloudEventsClient.unsubscribe(topic)                            │
└────────────────────────────┬────────────────────────────────────┘
                             │  HTTP POST / GET
                             │  (application/cloudevents+json)
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                     EventMesh Runtime                             │
│                      (单进程, 统一 Runtime)                       │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │              HTTP Endpoint (唯一传输层)                     │  │
│  │  POST /events/publish   → Ingress Pipeline                 │  │
│  │  POST /events/subscribe → SubscriptionManager             │  │
│  │  POST /events/unsubscribe                                 │  │
│  │  GET  /events/poll      → PushService 下发                │  │
│  └──────────────────────────────┬───────────────────────────┘  │
│                                 │                                 │
│  ┌──────────────────────────────▼───────────────────────────┐  │
│  │              Ingress Pipeline                             │  │
│  │  AuthFilter → RateLimitFilter → AclFilter               │  │
│  │  → ProtocolFilter → TransformerEngine                   │  │
│  │  → RouterEngine →                                        │  │
│  └──────────────────────────────┬───────────────────────────┘  │
│                                 │ CloudEvent                      │
│                                 ▼                                 │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              Storage Plugin (MQ 存储层)                     │ │
│  │  ┌──────────────────────────────────────────────────────┐ │ │
│  │  │  单 Producer (EventMesh 自主管理)                      │ │ │
│  │  │  · 按 topic 分区写入                                   │ │ │
│  │  │  · 不暴露 Producer Group 概念                          │ │ │
│  │  │  · 不需要 clientId / producerGroup 配置                │ │ │
│  │  │  · 由 EventMesh SubscriptionManager 统一路由          │ │ │
│  │  └──────────────────────────────────────────────────────┘ │ │
│  └──────────────────────────────┬────────────────────────────┘ │
│                                 │ Consumer.poll()               │
│  ┌──────────────────────────────▼────────────────────────────┐ │
│  │  ┌──────────────────────────────────────────────────────┐ │ │
│  │  │  单 Consumer (EventMesh 自主管理)                     │ │ │
│  │  │  · 全量拉取所有 topic 的消息                          │ │ │
│  │  │  · 按 EventMesh 订阅规则分发，不走 Consumer Group     │ │ │
│  │  │  · 不暴露 Consumer Group / 负载均衡算法到客户端       │ │ │
│  │  └──────────────────────────────────────────────────────┘ │ │
│  └──────────────────────────────┬────────────────────────────┘ │
│                                 │ CloudEvent (按订阅过滤后)      │
│  ┌──────────────────────────────▼───────────────────────────┐  │
│  │              Egress Pipeline                              │  │
│  │  EnrichmentTransformer → FilterEngine (消费者过滤)        │  │
│  └──────────────────────────────┬───────────────────────────┘  │
│                                 │                                 │
│  ┌──────────────────────────────▼───────────────────────────┐  │
│  │           SubscriptionManager (核心)                       │  │
│  │  管理所有客户端订阅关系，按分发策略下发消息                 │  │
│  │  · 负载均衡分发 (RoundRobin / 最小连接数)                  │  │
│  │  · 广播分发 (所有订阅者都收到)                            │  │
│  │  · 多播分发 (按 subject/type/header 匹配)                 │  │
│  │  · 自主 OffsetStore (RocksDB, 参考 RocketMQ Client 实现)   │  │
│  └──────────────────────────────┬───────────────────────────┘  │
│                                 │ HTTP Long-Polling               │
│  ┌──────────────────────────────▼───────────────────────────┐  │
│  │              PushService (HTTP Long-Polling)              │  │
│  │  客户端 GET /events/poll?clientId=xxx&topics=topic1,topic2 │  │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  HTTP Server: GET /connector/sink/{id}/poll   (供 Connector Sink 拉取) │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ Kafka / RocketMQ
                             │ (纯存储，不理解业务语义)
                             ▼
                    ┌──────────────────────┐
                    │     Kafka / RocketMQ  │
                    │   (视为分布式 WAL)     │
                    │                      │
                    │  · 无 Producer Group   │
                    │  · 无 Consumer Group  │
                    │  · 无 Tag 过滤         │
                    │  · EventMesh 是唯一    │
                    │    Producer + Consumer│
                    └──────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  Connector Runtime (独立进程)                     │
│                   (Source/Sink Connector 管理器)                   │
│                                                                   │
│  SourceConnector.poll() → CloudEvent                             │
│      → HTTP POST /events/publish → EventMesh Runtime            │
│      → Storage (MQ)                                              │
│                                                                   │
│  SinkConnector: HTTP Long-Polling GET /connector/sink/{id}/poll │
│      ← EventMesh 下发 CloudEvent                                  │
│      ← Storage.poll() → EgressPipeline                          │
│      → 写入外部系统 (MySQL/Redis/HTTP API/...)                   │
│                                                                   │
│  各自维护独立的 OffsetStore (RocksDB)                              │
└─────────────────────────────────────────────────────────────────┘

```

### 2.2 协议栈简化对比

```
当前架构（5 层 + 遗留层）                    重构后架构（3 层）

Layer 5 · 编程模型
├─ OpenMessaging API                         Layer 3 · 编程模型
│  ├─ Producer / Consumer                   CloudEvents HTTP SDK
├─ EventMeshMessage (SDK POJO)                ├─ cloudEventsClient.publish()
├─ A2A Protocol                               ├─ cloudEventsClient.subscribe()
│                                            ├─ cloudEventsClient.unsubscribe()
Layer 4 · 数据格式                           Layer 2 · 数据格式
├─ CloudEvents (统一内部)                     CloudEvents 1.0 (唯一格式)
├─ EventMeshMessage (TCP SDK)                  (从客户端到 MQ 全链路)
├─ Package (TCP 帧)
├─ HttpEventWrapper (HTTP 旧)                Layer 1 · 传输
├─ proto CloudEvent (gRPC)                   HTTP Server (唯一传输层)
│                                            (REST + Long-Polling)
Layer 3 · 传输协议
├─ TCP Server                                 ─── MQ 存储 ───
├─ HTTP Server
├─ gRPC Server
                                           Kafka / RocketMQ
Layer 2 · 处理引擎                           (纯 WAL，EventMesh 单 Producer
├─ Ingress/Egress Pipeline                    单 Consumer 模式)
Layer 1 · 存储
├─ Kafka / RocketMQ / Pulsar

遗留问题:
├─ TCP/gRPC SDK 必须维护
├─ EventMeshMessage/Package 协议适配代码
└─ OpenMessaging API 与 MQ Group 绑定
```

---

## 三、Storage Plugin 重构：MQ 无状态存储化

### 3.1 当前问题

当前 Storage Plugin 的设计暴露了 MQ 的 Producer Group / Consumer Group 语义：

```java
// eventmesh-storage-rocketmq
MeshMQProducer.java:
  producerGroup: String        // RocketMQ ProducerGroup，客户端需要理解
  createTransactionProducer()  // 事务消息，EventMesh 需要继承这个复杂度

MeshMQConsumer.java:
  consumerGroup: String         // RocketMQ ConsumerGroup，核心问题
  subscribe(topic, subExpression)  // subExpression = RocketMQ Tag 过滤
  push() / pull()              // 两种消费模式混用
```

**问题：客户端通过 EventMesh SDK 配置 `consumerGroup`，本质上还是在用 RocketMQ 的 Consumer Group 语义。EventMesh 并没有真正提供自己的订阅分发模型。**

### 3.2 目标接口

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：✅ 接口 `MeshStoragePlugin` 已按此定义落地（send/poll-by-offset/assignPartitions/commitOffset/start/shutdown），无 producerGroup/consumerGroup/tag 参数。**但实现侧矛盾**：Kafka 实现内部仍设 `group.id`（G7），assignPartitions/commitOffset 在 Kafka 已实装、RocketMQ 仍部分为 stub。

重构后的 Storage Plugin 只提供两个核心方法：

```java
public interface MeshStoragePlugin {

    /**
     * 写入消息（EventMesh 管理 producer，不暴露 producerGroup）
     * @param topic   EventMesh topic（映射到 MQ topic）
     * @param event   CloudEvent
     */
    void send(String topic, CloudEvent event, SendCallback callback);

    /**
     * 订阅消息（全量拉取，不走 MQ 消费组）
     * @param topic   EventMesh topic
     * @param offset  消费位点（earliest / latest / 指定 offset）
     * @param handler 消息处理器
     */
    void subscribe(String topic, String offset, ConsumerHandler handler);

    /**
     * 批量拉取（供 SubscriptionManager 消费）
     */
    List<CloudEvent> poll(String topic, int maxEvents, long timeoutMs);

    /**
     * 提交消费位点（EventMesh 自己维护 offset）
     */
    void commitOffset(String topic, String offset);

    void start();
    void shutdown();
}
```

**关键变化：**

1. **删除 `producerGroup` / `consumerGroup` 全部配置项**
2. **Storage Plugin 内部只有一个 Producer 实例**（由 EventMesh Runtime 持有）
3. **Storage Plugin 内部只有一个 Consumer 实例**（由 EventMesh Runtime 持有，按需分区拉取）
4. **`subscribe()` 方法是内部订阅（EventMesh 用它拉取消息），不是客户端订阅**

### 3.3 Kafka 实现重构

```java
public class KafkaStoragePlugin implements MeshStoragePlugin {

    // EventMesh 管理一个 Producer，不暴露 producerGroup
    private org.apache.kafka.clients.producer.KafkaProducer<String, byte[]> producer;

    // EventMesh 管理一个 Consumer，不暴露 consumerGroup
    private org.apache.kafka.clients.consumer.KafkaConsumer<String, byte[]> consumer;

    // 分区分配由 EventMesh SubscriptionManager 控制
    private Map<String, List<TopicPartition>> topicPartitions = new ConcurrentHashMap<>();

    @Override
    public void send(String topic, CloudEvent event, SendCallback callback) {
        // CloudEvent → 二进制
        byte[] bytes = CloudEventMapper.toBytes(event);
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, bytes);

        // 不设置 producerGroup / transactionalId
        // Kafka producer 由 EventMesh Runtime 全局单例
        producer.send(record, (metadata, exception) -> {
            if (exception != null) callback.onError(exception);
            else callback.onSuccess(metadata.offset());
        });
    }

    @Override
    public List<CloudEvent> poll(String topic, int maxEvents, long timeoutMs) {
        // 手动指定消费分区（由 SubscriptionManager 路由）
        List<TopicPartition> partitions = topicPartitions.get(topic);
        if (partitions == null || partitions.isEmpty()) return Collections.emptyList();

        consumer.assign(partitions);
        ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(timeoutMs));

        List<CloudEvent> events = new ArrayList<>();
        for (ConsumerRecord<String, byte[]> record : records) {
            events.add(CloudEventMapper.fromBytes(record.value()));
        }
        return events;
    }
}
```

### 3.4 RocketMQ 实现重构

```java
public class RocketMQStoragePlugin implements MeshStoragePlugin {

    // EventMesh 管理一个 DefaultMQProducer，不暴露 producerGroup
    private DefaultMQProducer producer;

    // EventMesh 管理一个 DefaultMQPushConsumer，不暴露 consumerGroup
    private DefaultMQPushConsumer consumer;

    @Override
    public void send(String topic, CloudEvent event, SendCallback callback) {
        Message msg = new Message();
        msg.setTopic(topic);
        msg.setBody(CloudEventMapper.toBytes(event));
        // 不设置 tags / keys（这些是 MQ 语义，EventMesh 不暴露）

        producer.send(msg, (sendResult, exception) -> {
            if (exception != null) callback.onError(exception);
            else callback.onSuccess(sendResult.getOffsetMsgId());
        });
    }

    @Override
    public List<CloudEvent> poll(String topic, int maxEvents, long timeoutMs) {
        // RocketMQ PushConsumer 模式：
        // EventMesh 消费全部消息，然后按自己的订阅规则分发
        // 不使用 RocketMQ 的 tag 过滤（那是 MQ 语义）
        List<Message> msgs = pullFromRocketMQ(topic, maxEvents, timeoutMs);
        return msgs.stream()
                   .map(m -> CloudEventMapper.fromBytes(m.getBody()))
                   .collect(Collectors.toList());
    }
}
```

### 3.5 配置简化

```properties
# 旧配置（暴露 MQ 语义）
eventmesh.storage.rocketmq.producer.group=EventMeshProducer
eventmesh.storage.rocketmq.consumer.group=EventMeshConsumer
eventmesh.storage.rocketmq.consumer.tag=*

# 新配置（EventMesh 管理）
eventmesh.storage.type=kafka          # kafka / rocketmq / pulsar / s3stream（§15.8）
eventmesh.storage.bootstrap.servers=localhost:9092
eventmesh.storage.consumer.auto.offset.reset=earliest

# S3Stream 存储后端（§15.8，多后端并列）
# eventmesh.storage.type=s3stream
# eventmesh.storage.s3stream.endpoint=https://s3.xxx.com
# eventmesh.storage.s3stream.bucket=eventmesh-wal
# eventmesh.storage.s3stream.region=us-east-1
```

---

### 3.6 S3Stream 存储后端与跨后端语义对齐（§15.8）

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：❌ **S3Stream 后端未实现**（仅本文档规划，无 `S3StreamStoragePlugin` 代码）。当前存储后端仅 Kafka + RocketMQ。跨后端语义对齐表（§3.6.4）是目标设计，待 S3Stream 落地时验证。

§15.8 决策新增 S3Stream 作为 StoragePlugin 实现，与 Kafka/RocketMQ 并列。本节定义其适配方式与三种后端的语义对齐。

#### 3.6.1 S3Stream 是什么、为何契合"完全自主协调"

S3Stream 是 AutoMQ 的流式存储：**无状态 compute broker + 对象存储(S3)分级**。数据/offset 在 S3，broker 只算不存。这与 §15.1 "EventMesh 完全自主协调"高度契合：

```
契合点：
  · S3Stream broker 无状态 → EventMesh 实例可随意扩缩（compute 弹性）
  · 分区是逻辑概念 → EventMesh 的分区分配协议（§13.2.8）可平滑套用
  · offset 真相在 S3 → 但 EventMesh 分发 offset 仍自主管（§13.2.4，不冲突，见下）

注意（姿态 A 的边界，§15.8）：
  · S3Stream 本身有 compute 调度能力，但本方案不复用它
  · EventMesh 在 S3Stream 之上叠自己的分区分配（Meta 主导，§13.2.3）
  · S3Stream 退化为"数据 WAL + 物理分区"——其弹性优势仍在（存储无状态），调度优势不用
  → 这是 §15.8 选"姿态 A 最小集成"的代价：换取 §15.1 铁律不破
```

#### 3.6.2 两种集成深度

```
v1（推荐起步）：S3StreamStoragePlugin 薄包装 Kafka 线协议
  · S3Stream 兼容 Kafka wire protocol → 复用 KafkaStoragePlugin 的 Producer/Consumer
  · S3StreamStoragePlugin 仅换 bootstrap 指向 S3Stream endpoint + 配置 S3 参数
  · 零新协议代码，最快落地
  · 代价：受 Kafka client 行为约束（如 offset 提交语义），但 §13.2.4 已绕过（EventMesh 自管 offset）

v2（深度）：原生 S3Stream SDK
  · 用 S3Stream 原生 SDK（S3Stream 的 Stream 语义，非 Kafka 兼容层）
  · 直接操作 Stream/Offset，绕过 Kafka 兼容层开销
  · 可利用 S3Stream 的分级存储特性（热数据 SSD / 冷数据 S3）
  · 代价：新协议适配代码，与 Kafka/RocketMQ 实现分叉
```

#### 3.6.3 S3StreamStoragePlugin 实现（v1 薄包装）

```java
public class S3StreamStoragePlugin implements MeshStoragePlugin {

    // v1：复用 Kafka client（S3Stream 兼容 Kafka 线协议）
    private KafkaProducer<String, byte[]> producer;
    private KafkaConsumer<String, byte[]> consumer;

    public S3StreamStoragePlugin(S3StreamConfig config) {
        // bootstrap 指向 S3Stream endpoint，其余同 Kafka
        Properties p = new Properties();
        p.put("bootstrap.servers", config.getEndpoint());      // s3stream endpoint
        p.put("s3.endpoint", config.getS3Endpoint());          // S3 后端
        p.put("s3.bucket", config.getBucket());
        p.put("s3.region", config.getRegion());
        // 不设 group.id（§3.2 MQ 无语义）；enable.auto.commit=false（EventMesh 自管 offset）
        p.put("enable.auto.commit", "false");
        this.producer = new KafkaProducer<>(p);
        this.consumer = new KafkaConsumer<>(p);
    }

    @Override
    public void send(String topic, CloudEvent event, SendCallback callback) {
        // 同 KafkaStoragePlugin：CloudEvent → bytes → ProducerRecord
        byte[] bytes = CloudEventMapper.toBytes(event);
        producer.send(new ProducerRecord<>(topic, bytes), (m, e) -> {
            if (e != null) callback.onError(e);
            else callback.onSuccess(m.offset());
        });
    }

    @Override
    public List<CloudEvent> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
        // 按 EventMesh 分配的分区 + 自管 offset 拉取（§13.2.3 assignPartitions + §13.2.4 offset）
        consumer.assign(Collections.singletonList(new TopicPartition(topic, partition)));
        consumer.seek(new TopicPartition(topic, partition), startOffset);
        ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(timeoutMs));
        // 不 commit（EventMesh offset 独立于 MQ offset，§12.6.6）
        return records.stream().map(r -> CloudEventMapper.fromBytes(r.value())).collect(Collectors.toList());
    }

    @Override
    public void assignPartitions(String topic, List<Integer> partitions) {
        // §13.2.3：EventMesh 分区分配结果 → consumer.assign
        consumer.assign(partitions.stream()
            .map(p -> new TopicPartition(topic, p)).collect(Collectors.toList()));
    }
    // ... start/shutdown/commitOffset（commitOffset 为 no-op，EventMesh 自管）
}
```

#### 3.6.4 跨后端语义对齐表

三种后端通过 `MeshStoragePlugin` 抽象对齐，但底层语义有差异，须显式标注：

| 语义维度 | Kafka | RocketMQ | S3Stream(v1) | EventMesh 如何抹平 |
|---------|-------|----------|--------------|-------------------|
| 分区概念 | partition | queue（=partition 等价） | partition | 统一抽象为 partition，`assignPartitions(topic, List<Integer>)` |
| offset 类型 | long（分区单调） | long（queue 内单调） | long | 统一 long，§13.2.4 自管 |
| offset 提交 | `__consumer_offsets` | broker 端 | S3 | **EventMesh 不提交 MQ offset**（§12.6.6），自管 RocksDB+Meta |
| Tag/过滤 | 无（partition 内全量） | Tag 过滤 | 无 | EventMesh MULTICAST 自实现（§4.2.3），不用 MQ Tag |
| 广播 | consumer group=每消费者独立 | MessageModel.BROADCAST | 无原生 | EventMesh BROADCAST 自实现（§4.2.2），MQ 只存 1 份 |
| Producer Group | 无（Kafka 无） | ProducerGroup | 无 | 全删（§3.2 MQ 无语义） |
| 顺序 | partition 内 FIFO | queue 内 FIFO | partition 内 FIFO | EventMesh STICKY 保 partitionKey→同分区（§13.3.3） |
| 事务消息 | 有（txn API） | 有（半消息） | — | 明确不支持（§13.3.6） |

> **关键对齐**：三种后端 EventMesh 都**不提交 MQ offset、不用 MQ Tag、不用 MQ 广播/Group**——MQ 退化为"按 partition + offset 的 FIFO WAL"。这是"MQ 无语义"铁律在三种后端上的统一落实。S3Stream 的存算分离优势（存储弹性）保留，但 compute 调度不用（姿态 A）。

#### 3.6.5 多后端选型建议

```
S3Stream：新部署、追求存储成本（S3 比 Kafka broker 便宜）+ 弹性扩缩存算分离
Kafka   ：已有 Kafka 集群、低延迟（本地 broker，无 S3 RTT）、强生态
RocketMQ：已有 RocketMQ、需国产化/特定特性（但 EventMesh 已抹平其 Group/Tag 语义）
→ 三者通过 MeshStoragePlugin 并列，eventmesh.storage.type 切换（§3.5）
→ 混合部署：不同 topic 可绑不同后端（StoragePlugin 按 topic 路由，v2 增强）
```

#### 3.6.6 v2 原生 S3Stream SDK 细节（深度集成，非 v1 必须）

§3.6.2 的 v2 路线用 S3Stream 原生 SDK（非 Kafka 兼容层）。本节给出 Stream/Offset API 与差异。

**原生 API 模型（vs Kafka 兼容层）：**

```
S3Stream 原生概念：
  · Stream：逻辑流（≈ topic+partition），有独立的 offset 空间
  · Offset：Stream 内单调递增，由 S3 持久化（真相源）
  · 分级存储：热数据 SSD 缓存 / 冷数据 S3（自动分级）

原生 SDK 伪 API（示意）：
  S3StreamClient client = S3StreamClient.builder().endpoint(...).build();
  Stream stream = client.openStream("tenantA.orders", ReadWrite);
  long nextOffset = stream.append(cloudEventBytes);        // 写入，返回 offset
  List<Record> records = stream.fetch(startOffset, maxCount);  // 按 offset 范围读
  // 无 consumer group / 无 rebalance / 无自动 commit——纯流式 WAL

与 Kafka 兼容层（v1）的差异：
  ┌────────────────┬──────────────────────┬──────────────────────┐
  │ 维度           │ v1 Kafka 兼容层       │ v2 原生 SDK           │
  ├────────────────┼──────────────────────┼──────────────────────┤
  │ 协议           │ Kafka wire protocol   │ S3Stream 原生协议     │
  │ 抽象           │ TopicPartition        │ Stream                │
  │ offset 提交    │ Kafka client 行为     │ 不提交（EventMesh 自管）│
  │ 分级存储利用   │ 不暴露                │ 可配热/冷策略         │
  │ 性能开销       │ Kafka 兼容层额外开销   │ 直达，更低延迟        │
  │ 实现成本       │ 零（复用 KafkaStorage）│ 新适配代码            │
  └────────────────┴──────────────────────┴──────────────────────┘
```

**v2 S3StreamStoragePlugin 实现（原生）：**

```java
public class S3StreamStoragePlugin implements MeshStoragePlugin {
    private S3StreamClient client;
    private Map<String, Stream> streams = new ConcurrentHashMap<>();  // topic → Stream

    @Override
    public void send(String topic, CloudEvent event, SendCallback cb) {
        Stream stream = streams.computeIfAbsent(topic,
            t -> client.openStream(t, ReadWrite));
        long offset = stream.append(CloudEventMapper.toBytes(event));
        cb.onSuccess(offset);  // 同步返回 offset（S3Stream append 通常同步）
    }

    @Override
    public List<CloudEvent> poll(String topic, int partition, long startOffset,
                                 int maxEvents, long timeoutMs) {
        Stream stream = streams.get(topic);
        // 原生 fetch 按 offset 范围，无 assign/rebalance（§13.2.3 由 EventMesh 决定读哪些）
        List<Record> records = stream.fetch(startOffset, maxEvents);
        return records.stream().map(r -> CloudEventMapper.fromBytes(r.value())).collect(toList());
    }

    @Override
    public void assignPartitions(String topic, List<Integer> partitions) {
        // S3Stream 原生无 partition 概念（Stream 即逻辑分区）
        // 映射：EventMesh partition → S3Stream Stream（如 topic#partition → 独立 Stream）
        // 或单 Stream + EventMesh 在上层做分区路由
        // 设计选择见下"语义映射"
    }
}
```

**语义映射（EventMesh partition ↔ S3Stream Stream）：**

```
两种映射策略：
  A. 一 Stream 一 partition：tenantA.orders#0 → Stream "tenantA.orders.0"
     · 优势：partition 独立 offset 空间，与 §13.2.8 分区分配天然对齐
     · assignPartitions(topic, [0,1]) → openStream(topic.0), openStream(topic.1)
  B. 一 Stream 多 partition：tenantA.orders → 单 Stream，partition 编码进 record key
     · 优势：Stream 数少，管理简单
     · 劣势：partition 分配需 EventMesh 上层过滤，违背"分区独立拉取"
  → 推荐 A，与 Kafka/RocketMQ 的 partition 模型一致，§13.2.8 协议无需改

分级存储利用（v2 独有）：
  · 配置 Stream 的 tiering 策略：热数据 N 小时留 SSD，之后转 S3
  · EventMesh 读取历史消息（replay）时，S3Stream 自动从 S3 拉取
  · 降低长保留期成本（合规审计场景受益）
```

**何时上 v2：**

```
v1（Kafka 兼容层）：快速落地、验证 S3Stream 存储可行性
v2（原生 SDK）：需要 ①更低延迟（去兼容层）②分级存储降成本 ③绕过 Kafka client 限制
→ 建议先 v1 上线，压测确认瓶颈在 Kafka 兼容层后再迁 v2
```

---

### 4.1 核心职责

这是本次重构最核心的新组件：**EventMesh 自主维护订阅关系和分发策略，不再委托给 MQ 的 Consumer Group。**

```java
public class SubscriptionManager {

    // 订阅关系：topic → 订阅者列表
    private final Map<String, Set<Subscription>> subscriptions = new ConcurrentHashMap<>();

    // 订阅者：一个 HTTP 客户端的订阅上下文
    static class Subscription {
        String subscriptionId;       // 订阅唯一ID
        String clientId;               // 客户端标识
        DistributionMode mode;        // 分发模式
        CloudEventFilter filter;      // 过滤条件（CloudEvents header / type 匹配）
        HttpResponseEmitter emitter;  // HTTP 长轮询响应通道
        long lastHeartbeat;           // 心跳时间
    }

    enum DistributionMode {
        LOAD_BALANCE,  // 负载均衡：每条消息只发给一个订阅者（RoundRobin）
        BROADCAST,     // 广播：每条消息发给所有订阅者
        MULTICAST      // 多播：按 CloudEvents type/source/header 匹配发给对应订阅者
    }
}
```

### 4.2 三种分发模式详解

#### 4.2.1 负载均衡（LOAD_BALANCE）

```
场景：订单处理，同一订单的消息只需要一个消费者处理

EventMesh Topic: "orders" (MQ 存储层)
订阅者 A: mode=LOAD_BALANCE, clientId=worker-1
订阅者 B: mode=LOAD_BALANCE, clientId=worker-2
订阅者 C: mode=LOAD_BALANCE, clientId=worker-3

消息分发:
  order-001 → worker-1 (RoundRobin 第1轮)
  order-002 → worker-2 (RoundRobin 第2轮)
  order-003 → worker-3 (RoundRobin 第3轮)
  order-004 → worker-1 (RoundRobin 回到第1个)

关键: 分发逻辑由 EventMesh SubscriptionManager 决定，不走 MQ 的分区机制
     （MQ 只负责持久化，EventMesh 决定谁能收到哪条消息）
```

#### 4.2.2 广播（BROADCAST）

```
场景：配置更新，所有服务节点都需要收到

EventMesh Topic: "config-updates" (MQ 存储层)
订阅者: service-A, service-B, service-C, service-D (全部 4 个)

消息分发:
  config-change-v1 → [service-A, service-B, service-C, service-D] (全部 4 份)
  config-change-v2 → [service-A, service-B, service-C, service-D] (全部 4 份)

实现:
  · 存储层只需写 1 条消息到 MQ
  · EventMesh SubscriptionManager 读取后，按订阅列表复制 N 份下发
  · 每个订阅者通过 HTTP Long-Polling 收到自己的消息流
```

#### 4.2.3 多播（MULTICAST）

```
场景：按 CloudEvents type 路由到不同消费者

EventMesh Topic: "events" (MQ 存储层，混合了所有业务事件)

订阅关系:
  event.type="order.created"    → order-service
  event.type="payment.completed" → payment-service
  event.type="inventory.changed" → inventory-service
  event.type="user.registered"   → user-service + marketing-service

CloudEvent 过滤匹配（基于 CloudEvents extension）:
  "subject" extension: 匹配资源标识
  "x-em-destinations" extension: 显式指定目标服务列表

消息分发:
  CloudEvent(type=order.created, subject=order-123)
    → order-service

  CloudEvent(type=payment.completed, subject=pay-456)
    → payment-service

  CloudEvent(type=user.registered, subject=user-789)
    → [user-service, marketing-service]
```

### 4.3 SubscriptionManager 完整实现

```java
public class SubscriptionManager {

    private final Map<String, Set<Subscription>> topicSubscriptions = new ConcurrentHashMap<>();
    private final DistributionStrategyRegistry strategyRegistry;
    private final AtomicInteger roundRobinCounter = new ConcurrentHashMap<>();

    // 注册订阅
    public String subscribe(SubscribeRequest request, HttpResponseEmitter emitter) {
        String subId = UUID.randomUUID().toString();
        Subscription sub = new Subscription(subId, request.getClientId(),
                request.getMode(), request.getFilter(), emitter);

        topicSubscriptions
            .computeIfAbsent(request.getTopic(), k -> ConcurrentHashMap.newKeySet())
            .add(sub);

        return subId;
    }

    // 注销订阅
    public boolean unsubscribe(String topic, String subId) {
        Set<Subscription> subs = topicSubscriptions.get(topic);
        if (subs == null) return false;
        return subs.removeIf(s -> s.getSubscriptionId().equals(subId));
    }

    // 拉取并分发消息（由定时任务调用）
    public void pollAndDispatch(String topic, MeshStoragePlugin storage, long timeoutMs) {
        List<CloudEvent> events = storage.poll(topic, 100, timeoutMs);
        if (events.isEmpty()) return;

        Set<Subscription> subs = topicSubscriptions.get(topic);
        if (subs == null || subs.isEmpty()) return;

        for (CloudEvent event : events) {
            List<Subscription> targets = selectTargets(event, subs);
            for (Subscription target : targets) {
                dispatchToSubscriber(event, target);
            }
        }
    }

    // 按分发模式选择目标订阅者
    private List<Subscription> selectTargets(CloudEvent event, Set<Subscription> allSubs) {
        // 移除心跳过期的订阅者
        allSubs.removeIf(s -> s.isExpired(maxIdleMs));

        List<Subscription> activeSubs = new ArrayList<>(allSubs);
        if (activeSubs.isEmpty()) return Collections.emptyList();

        // 按分发模式路由
        DistributionMode mode = inferMode(activeSubs);
        switch (mode) {
            case BROADCAST:
                return activeSubs;  // 全部下发

            case LOAD_BALANCE: {
                // RoundRobin
                int idx = Math.abs(roundRobinCounter.incrementAndGet()) % activeSubs.size();
                return Collections.singletonList(activeSubs.get(idx));
            }

            case MULTICAST: {
                // 按 CloudEvents 属性过滤
                return activeSubs.stream()
                    .filter(s -> s.getFilter().match(event))
                    .collect(Collectors.toList());
            }

            default:
                return Collections.emptyList();
        }
    }

    // 通过 HTTP Long-Polling 下发
    private void dispatchToSubscriber(CloudEvent event, Subscription sub) {
        try {
            sub.getEmitter().send(event);  // HTTP 响应
        } catch (Exception e) {
            // 客户端断开：移除订阅
            removeSubscription(sub.getTopic(), sub.getSubscriptionId());
        }
    }
}
```

---

## 五、SDK 极简化：HTTP 家族 CloudEvents SDK

### 5.1 目标

当前 EventMesh SDK 有三个版本（TCP / HTTP / gRPC），各自有不同的对象模型和 API 风格：

```
当前 SDK:
├─ eventmesh-sdk-java (TCP)
│   ├─ Proxy tcpClient = Proxy.builder().build()
│   ├─ tcpClient.createClient(group, topics)
│   ├─ tcpClient.subscribe(topic, group)
│   └─ 发送 Package 二进制帧
│
├─ eventmesh-sdk-java (HTTP)
│   ├─ EventMeshHttpClient client = EventMeshHttpClient.builder().build()
│   ├─ eventMeshHttpClient.publish(message)
│   ├─ eventMeshHttpClient.subscribe(handler)
│   └─ 发送 EventMeshMessage JSON
│
└─ eventmesh-sdk-java (gRPC)
    ├─ EventMeshGrpcClient client = EventMeshGrpcClient.builder().build()
    ├─ client.publish(event)
    └─ 发送 proto CloudEvent
```

**重构后：只保留一个 `eventmesh-sdk-java`（HTTP 家族），API 设计遵循 CloudEvents 语义。** 共 4 个核心方法：

```java
// 4 个核心 API
public class CloudEventsClient {

    // 1. 异步发布事件
    CompletableFuture<Void> publish(CloudEvent event);

    // 2. 同步请求-应答（对齐 TCP 同步调用语义，详见 §17）
    CompletableFuture<CloudEvent> request(CloudEvent event, Duration timeout);

    // 3. 订阅事件（推送模式由传输层决定，见 §5.3）
    void subscribe(String topic, Consumer<CloudEvent> handler);

    // 4. 取消订阅
    void unsubscribe(String topic);

    // Builder（传输层可插拔）
    static CloudEventsClientBuilder builder();
}
```

### 5.1.1 传输层可插拔（用户按场景选）

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：❌ **三传输仅到位 2 种。** SSE✅（`SseConnection` + `/events/stream`）、Long-Polling✅（`LongPollingChannel` + `/events/poll`）；**WebSocket（本节默认主传输）完全未实现（G1）**。SDK 侧 `CloudEventsClient` 也仅 Long-Polling，无 WS/SSE 客户端。

SDK 仍是"HTTP SDK"，但传输层抽象为三种**用户可选**的 HTTP 家族协议（均不违背 §15.3 "无 gRPC / 无自定义 TCP"铁律）。不同场景选不同协议：

```java
// 传输层选择（builder 配置）
CloudEventsClient.builder()
    .runtimeUrl("http://eventmesh-server:8080")
    .clientId("order-service-1")
    .transport(Transport.LONG_POLLING)   // 或 WEBSOCKET / SSE，默认 WEBSOCKET
    .encoding(Encoding.BINARY)            // 或 STRUCTURED(JSON)，默认 BINARY
    .build();
```

| 传输 | 适用场景 | 特点 |
|------|---------|------|
| **WebSocket**（默认推送主传输） | 持久订阅事件流、双向控制（unsubscribe/ACK）、高吞吐 | 双向长连接，持续推多消息，可同连接发控制命令 |
| **SSE** | 单向流式输出（如代理大模型 token 流、A2A agent 流式回传） | 单向服务端→客户端，穿墙极佳，浏览器/LLM 生态成熟，自动重连 |
| **Long-Polling** | 防火墙严苛、WS 被禁的环境降级 | 兼容性最好，1 RTT 间隙延迟 |
| HTTP 请求-响应 | publish、request-reply（控制面 + 同步调用） | 非推送，请求即响应 |

> **选型逻辑**：推送主传输默认 WebSocket（双向持久订阅流）；若场景是"一次请求触发的单向流式回传"（典型如 LLM token 流）用 SSE；WS 被网络环境拦截时降级 Long-Polling。控制面（publish/subscribe 控制请求）与 request-reply 始终走 HTTP 请求-响应。详见 §13.7.1。

### 5.2 使用示例

```java
// 初始化客户端（默认 WebSocket 推送 + 二进制编码）
CloudEventsClient client = CloudEventsClient.builder()
    .runtimeUrl("http://eventmesh-server:8080")
    .clientId("order-service-1")
    .build();

// 1. 发布事件（CloudEvents 格式）
CloudEvent event = CloudEventBuilder.v1()
    .withId(UUID.randomUUID().toString())
    .withSource(URI.create("order-service"))
    .withType("order.created")
    .withDataContentType("application/json")
    .withData(GsonJsonFormat.toJson(data))
    .build();

client.publish(event);

// 2. 同步请求-应答（对齐 TCP 同步调用）
CloudEvent request = CloudEventBuilder.v1()
    .withType("order.query")
    .withExtension("x-em-reply-to", "reply." + reqId)
    .withExtension("x-em-correlation-id", reqId)
    .withData(queryJson)
    .build();
CloudEvent reply = client.request(request, Duration.ofSeconds(5)).join();  // 阻塞等应答，超时失败

// 3. 订阅事件
client.subscribe("order-events", receivedEvent -> {
    String type = receivedEvent.getType();
    if ("order.created".equals(type)) {
        processOrder(receivedEvent);
    }
});
```

### 5.3 内部实现

```
CloudEventsClient 内部实现:
  publish():
    HTTP POST /events/publish
    body: application/cloudevents-batch+json 或 binary 编码（Encoding.BINARY）
    返回: 202 Accepted

  request():                                  ← 新增：同步请求-应答（详见 §17）
    HTTP POST /events/request  (请求挂起，阻塞等应答)
    header: x-em-correlation-id, x-em-reply-to
    返回: 应答 CloudEvent；超时 → 请求失败

  subscribe():  推送传输三选一（Transport 配置）:
    WEBSOCKET:
      升级 GET /events/stream → ws 长连接
      服务端持续推 CloudEvent 帧；客户端同连接发 unsubscribe/ACK 控制帧
      适用：持久订阅、高吞吐、双向控制
    SSE:
      GET /events/stream  Accept: text/event-stream
      服务端单向推 data: <CloudEvent>\n\n，断线自动重连
      适用：单向流式输出（LLM token 流、A2A 流式回传）
    LONG_POLLING:
      循环 GET /events/poll?clientId=xxx&topics=xxx&timeout=30s
      收到消息 → 回调 handler；循环拉取
      适用：WS 被禁的降级场景

  unsubscribe():
    HTTP POST /events/unsubscribe   body: { topic, clientId }
    （WS 模式下也可走控制帧）
```

### 5.4 删除清单与不可逆风险注记

| 删除项 | 理由 |
|--------|------|
| TCP SDK (`tcp/` 子包) | 协议复杂，维护成本高；HTTP 家族（WS/SSE/Long-Polling）可覆盖全部场景 |
| gRPC SDK (`grpc/` 子包) | HTTP 请求-响应 + WebSocket 推送可替代；减少依赖 |
| `EventMeshMessage` 类 | 替换为标准 CloudEvents |
| `Package` 类 | TCP 协议帧，SDK 简化后不需要 |
| `HttpCommand` 类 | 旧 HTTP 协议，已被 CloudEvents 替代 |
| OpenMessaging API (`io.openmessaging.api.*`) | 与 MQ Group 语义绑定，违反"MQ 无语义"原则 |
| `MeshMessageProtocolAdaptor` | TCP SDK 的 Adaptor |
| `OpenMessageProtocolAdaptor` | OpenMessaging 的 Adaptor |

> **⚠️ 不可逆风险注记（承接 §15.3）**：全删 TCP+gRPC SDK 是不可逆动作。 当前**主要使用 TCP 协议**，存量 TCP 客户端须迁移到 HTTP 家族 SDK（WebSocket/SSE/Long-Polling + request-reply）。落地前必须：
> 1. 排查存量 TCP 客户端的**实时性/吞吐/同步调用**需求，确认三种 HTTP 传输可覆盖（毫秒级延迟用 WebSocket、同步调用用 request-reply）；
> 2. 对高 TPS 链路做 **WebSocket+二进制 vs TCP** 的压测对比，确认吞吐可接受；
> 3. 排查 TCP 特有运维操作（redirect/reject）在新 Admin 面（§13.5.4）的覆盖情况。
> 以上依赖落地基线（§15.4）确定后启动。

---

## 六、Runtime 入方向重构：统一 IngressHandler

### 6.1 当前 IngressProcessor 的问题

当前 `IngressProcessor` 是 102 个 Processor 类的统一入口，设计良好，但仍有遗留负担：

1. **兼容旧协议**：仍处理 `EventMeshMessage` / `Package` / `HttpCommand` 等旧格式
2. **多协议路由**：TCP/HTTP/gRPC 复用，但 gRPC SDK 被删除后，gRPC Adaptor 仅剩服务端接收能力
3. **ProtocolAdaptor 种类过多**：5 个 Adaptor 中 2 个（TCP 的 MeshMessageAdaptor、OpenMessagingAdaptor）在新架构下不再需要

### 6.2 简化后的 IngressHandler

```java
@HttpHandler("/events")
public class UnifiedIngressHandler {

    private final IngressPipeline pipeline;
    private final SubscriptionManager subscriptionManager;
    private final MeshStoragePlugin storagePlugin;

    /**
     * POST /events/publish
     * 客户端发送 CloudEvent 到 EventMesh
     */
    public void publish(HttpRequest request, HttpResponse response) {
        // 1. 解析 CloudEvents
        CloudEvent event = parseCloudEvent(request);

        // 2. 经过 Ingress Pipeline
        PipelineResult result = pipeline.process(event);

        // 3. 写入 MQ
        if (result.getAction() == PipelineResult.Action.CONTINUE) {
            String topic = determineTopic(event);
            storagePlugin.send(topic, result.getEvent(), new SendCallback() {
                @Override
                public void onSuccess(Object metadata) {
                    response.writeSuccess(202, "Accepted");
                }
                @Override
                public void onError(Throwable t) {
                    response.writeError(500, t.getMessage());
                }
            });
        } else {
            response.writeError(result.getAction().toHttpStatus(), "Rejected");
        }
    }

    /**
     * POST /events/subscribe
     * 客户端注册订阅关系
     */
    public void subscribe(HttpRequest request, HttpResponse response) {
        SubscribeRequest req = parse(request, SubscribeRequest.class);
        String subId = subscriptionManager.subscribe(req, createEmitter(response));
        response.writeJson(200, new SubscribeResponse(subId));
    }

    /**
     * POST /events/unsubscribe
     * 客户端取消订阅
     */
    public void unsubscribe(HttpRequest request, HttpResponse response) {
        UnsubscribeRequest req = parse(request, UnsubscribeRequest.class);
        subscriptionManager.unsubscribe(req.getTopic(), req.getSubscriptionId());
        response.writeSuccess(200, "OK");
    }

    /**
     * GET /events/poll
     * HTTP Long-Polling：客户端拉取已下发的消息
     * (由 SubscriptionManager 主动填充 emitter，poll handler 读取队列)
     */
    public void poll(HttpRequest request, HttpResponse response) {
        String clientId = request.getParam("clientId");
        String topics = request.getParam("topics");
        long timeout = Long.parseLong(request.getParam("timeout", "30000"));

        // 创建挂起的 HTTP 响应（不立即返回）
        AsyncContext ctx = AsyncContext.startAsync(request, response);
        subscriptionManager.registerPollChannel(clientId, topics, ctx, timeout);
    }

    // ── CloudEvents 解析 ──
    private CloudEvent parseCloudEvent(HttpRequest request) {
        String contentType = request.getHeader("Content-Type");
        if (contentType.contains("cloudevents")) {
            // 标准 CloudEvents 1.0
            return new CloudEventDecoder().decode(request.getBody());
        } else if (contentType.contains("json")) {
            // 兼容：JSON body → CloudEvent（通过 extension 字段扩展）
            return jsonToCloudEvent(request.getBody());
        }
        throw new ProtocolHandleException("Unsupported Content-Type: " + contentType);
    }

    private String determineTopic(CloudEvent event) {
        // Topic 来源优先级：
        // 1. CloudEvents "subject" extension（业务 topic）
        // 2. 配置文件默认 topic
        String topic = event.getExtension("subject");
        return topic != null ? topic : DEFAULT_TOPIC;
    }
}
```

### 6.3 简化的 ProtocolAdaptor 体系

```
当前（5 个 Adaptor）:
├─ HttpProtocolAdaptor              ← 保留（HTTP SDK 入口）
├─ CloudEventsProtocolAdaptor       ← 保留（CloudEvents 规范入口）
├─ MeshMessageProtocolAdaptor        ← 删除（TCP SDK）
├─ OpenMessageProtocolAdaptor        ← 删除（OpenMessaging）
└─ EnhancedA2AProtocolAdaptor        ← 保留（A2A Agent 通信）

重构后（3 个 Adaptor）:
├─ HttpCloudEventsAdaptor            ← HTTP → CloudEvent（主流）
├─ A2AAdaptor                        ← A2A JSON → CloudEvent（复用 HTTP Adaptor）
└─ (CloudEvents 直接流通)             ← CloudEvents SDK 直接发送，不需要 Adaptor
```

**ProtocolAdaptor 的职责也简化了：** 只负责"把非 CloudEvents 格式转成 CloudEvents"。HTTP SDK 发送的本身就是 CloudEvents JSON，Adaptor 只做解析，不做格式转换。

---

## 七、Runtime 出方向重构：PushService 替代旧 Consumer 体系

### 7.1 当前 Consumer 体系的问题

当前 EventMesh 的消费者模型混乱：

```
当前 Consumer 体系:
├─ TCP Consumer:
│   ├─ Session 管理（ClientSessionMap）
│   ├─ 下发模式：Session.send() 直接写 TCP Socket
│   └─ 订阅管理: ClientGroupPackManagement
│
├─ HTTP PushConsumer:
│   ├─ EventMeshHttpServer.abstractHTTPServer
│   ├─ LRUCache<consumerGroup, AsyncContext>
│   └─ 订阅管理: EventMeshHTTPServer.localSubscriptionInfoMap
│
└─ gRPC PushConsumer:
    └─ EventMeshGrpcServer.pushToClient()
```

**三套 Consumer 管理体系，各自独立维护 Session/Subscription 状态。**

### 7.2 统一 PushService

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：⚠️ `PushService`（per-client 有界缓冲 + ACK callback 跟踪）+ `TransportChannel` 抽象 + `LongPollingChannel`/`SseConnection` 实现✅；**`WebSocketChannel` 未实现（G1）**；`Connection`/`ConnectionPushPump`（drain 缓冲到连接）有。慢消费者状态机见 §13.6.2（G11）。

重构后只有 HTTP 家族 Consumer，推送通道抽象为 `TransportChannel`，支持三种传输（WebSocket / SSE / Long-Polling），用户按场景选（§5.1.1 / §13.7.1）：

```java
public class PushService {

    // 订阅者推送通道（clientId → TransportChannel），统一三种传输
    private final Map<String, TransportChannel> channels = new ConcurrentHashMap<>();

    // 消息缓冲区（通道未连接 / 慢消费者时暂存，防丢；背压上限见 §13.6.2）
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<CloudEvent>> pendingEvents =
            new ConcurrentHashMap<>();

    /**
     * 由 SubscriptionManager 调用：有一条消息需要下发给订阅者
     * channel.send() 内部按传输类型走不同下发路径
     */
    public void push(String clientId, CloudEvent event) {
        TransportChannel channel = channels.get(clientId);
        if (channel != null && channel.isActive()) {
            // 立即推送：WS 帧 / SSE data: 行 / Long-Polling 响应
            channel.send(event);
        } else {
            // 通道未连接，存入缓冲区（下次连接时 flush）
            pendingEvents.computeIfAbsent(clientId, k -> new ConcurrentLinkedQueue<>())
                         .add(event);
        }
    }

    /**
     * 客户端建立推送通道（三种传输统一注册）
     */
    public void registerChannel(String clientId, TransportType type, ChannelContext ctx, long timeoutMs) {
        // 先 flush 缓冲区历史消息
        ConcurrentLinkedQueue<CloudEvent> buffer = pendingEvents.remove(clientId);

        TransportChannel channel = TransportChannelFactory.create(type, clientId, ctx, buffer, timeoutMs);
        channels.put(clientId, channel);

        channel.startTimeoutCheck(() -> {
            channels.remove(clientId);
            ctx.complete();  // 超时/断开清理
        });
    }
}

// 传输通道抽象（三种传输统一接口）
interface TransportChannel {
    boolean isActive();
    void send(CloudEvent event);     // WS 帧 / SSE data: / LP 响应
    void startTimeoutCheck(Runnable onTimeout);
}
// WSChannel / SSEChannel / LongPollingChannel 三种实现
```

> **与 request-reply 的关系**：request-reply 的应答推送（§17）也复用 `TransportChannel`——若请求方持 WS 通道，应答可走 WS 推回；否则走原挂起的 HTTP 请求响应返回。push() 与 request-reply 应答共享通道，但语义面不同（§17）。

### 7.3 与旧 Consumer 体系的关系

```
旧 Consumer 体系                              新 PushService
────────────────                              ──────────────
ClientSessionMap (TCP)              →          删除（无 TCP SDK）
LRUCache<consumerGroup, AsyncContext> →         channels (clientId → TransportChannel)
localSubscriptionInfoMap (HTTP)     →          SubscriptionManager.topicSubscriptions
ClientGroupPackManagement (TCP sub)  →          SubscriptionManager
EventMeshGrpcServer.pushToClient()  →          删除（无 gRPC SDK）
Session.send() (TCP 直写)            →          TransportChannel.send()（WS/SSE/LP 统一）
```

---

## 八、Connector Runtime：与 EventMesh Runtime 完全独立

### 8.1 设计原则

**Connector Runtime 与 EventMesh Runtime 是两个完全独立的进程**，通过标准 HTTP + CloudEvents 接口通信，不共享内部组件。

```
┌─────────────────────────────────────┐    HTTP / CloudEvents    ┌──────────────────────────────────────┐
│         Connector Runtime              │ ←──────────────────────→  │         EventMesh Runtime            │
│                                        │                           │                                       │
│  ┌──────────────────────────────────┐ │   POST /events/publish    │  ┌──────────────────────────────────┐ │
│  │ Source/Sink Connector 管理器      │ │ ────────────────────────→ │  │ UnifiedIngressHandler            │ │
│  │ · Connector 生命周期管理           │ │                           │  │  → IngressPipeline              │ │
│  │ · 外部系统数据拉取/写入             │ │                           │  │  → Storage (MQ)                 │ │
│  │ · Source offset 管理（独立）         │ │                           │  └──────────────────────────────────┘ │
│  │ · Sink offset 管理（独立）           │ │   SubscriptionManager     │                                       │
│  └──────────────────────────────────┘ │ ←─────────────────────── │  ┌──────────────────────────────────┐ │
│                                        │   GET /events/poll       │  │ SubscriptionManager              │ │
│  Source/Sink Connector 代码结构:        │                           │  │  · 自主 offset 管理 (RocksDB)    │ │
│  · SourceConnector.poll() → CloudEvent                           │  │  · 订阅关系 (topic→clientIds)   │ │
│  · SinkConnector.put(CloudEvent)    │                           │  │  · 分发策略 (LB/Bcast/Mcast)    │ │
│  · 各自维护自己的 offset（独立 RocksDB）                           │  └──────────────────────────────────┘ │
└─────────────────────────────────────┘                           └──────────────────────────────────────┘
```

### 8.2 两者的边界

| 职责 | Connector Runtime | EventMesh Runtime |
|------|-----------------|-------------------|
| **数据拉取** | Source Connector 从外部系统拉取 | 从 MQ 拉取（供下游客户端订阅） |
| **数据写入** | Sink Connector 写入外部系统 | 写入 MQ（来自客户端发布） |
| **Pipeline** | Source/Sink 各自可选的轻量 Transform | 完整的 Ingress/Egress Pipeline（ACL/RateLimit/Filter） |
| **订阅模型** | 无（只拉外部数据） | 有（LOAD_BALANCE / BROADCAST / MULTICAST） |
| **Offset 管理** | Source 拉取 offset + Sink 写入确认 offset（各自独立） | Consumer 消费 offset（EventMesh 自主管理，详见 §4.4） |
| **部署模型** | 独立进程，独立部署 | 独立进程，独立部署 |

### 8.3 Connector → EventMesh 数据流（Source Connector）

```
外部系统 (MySQL/Redis/MQ/...) 
    ↓
SourceConnector.poll()   ← Connector Runtime 管理
    ↓ CloudEvent
HTTP POST /events/publish   ← 标准 CloudEvents HTTP
    ↓
EventMesh UnifiedIngressHandler
    ↓
IngressPipeline (ACL → RateLimit → Filter)
    ↓
Storage.send() → Kafka/RocketMQ   ← EventMesh Runtime 管理
```

### 8.4 EventMesh → Connector 数据流（Sink Connector）

```
Kafka/RocketMQ  ← EventMesh Runtime 管理（Storage.poll()）
    ↓
EgressPipeline (Enrichment → Filter)   ← EventMesh Runtime 管理
    ↓
HTTP POST /connector/sink/{connectorId}   ← 标准 CloudEvents HTTP
    ↓
SinkConnector.put(CloudEvent)   ← Connector Runtime 管理
    ↓
外部系统 (MySQL/Redis/HTTP API/...)
```

### 8.5 Connector Runtime 内部结构

```java
public class ConnectorRuntime {

    private final Map<String, SourceConnector> sources = new ConcurrentHashMap<>();
    private final Map<String, SinkConnector> sinks = new ConcurrentHashMap<>();
    private final OffsetStore offsetStore;       // Connector 自有的 RocksDB offset store
    private final HttpClient httpClient;         // 向 EventMesh 发送 HTTP 请求

    public void start() {
        // 1. 加载 Connector 配置（conf/connectors.yaml）
        List<ConnectorDef> defs = loadConnectorDefs();

        // 2. 启动 Source Connector
        for (ConnectorDef def : defs.getSources()) {
            SourceConnector connector = createSourceConnector(def);
            sources.put(def.getId(), connector);
            executor.submit(() -> runSource(connector, def));
        }

        // 3. 启动 Sink Connector（监听 EventMesh 下发）
        for (ConnectorDef def : defs.getSinks()) {
            SinkConnector connector = createSinkConnector(def);
            sinks.put(def.getId(), connector);
            startSinkListener(connector, def);
        }
    }

    // Source: 拉取外部数据 → 发布到 EventMesh
    private void runSource(SourceConnector connector, ConnectorDef def) {
        String eventMeshTopic = def.getTargetTopic();
        while (running) {
            List<CloudEvent> events = connector.poll();
            for (CloudEvent event : events) {
                try {
                    // HTTP POST 到 EventMesh
                    httpClient.post("/events/publish", event);
                    connector.commit(event);   // 提交 Source offset
                } catch (Exception e) {
                    connector.retry(event);     // 重试策略
                }
            }
        }
    }

    // Sink: 接收 EventMesh 下发 → 写入外部系统
    private void startSinkListener(SinkConnector connector, ConnectorDef def) {
        String sourceTopic = def.getSourceTopic();
        // HTTP Long-Polling 订阅 EventMesh
        String pollUrl = String.format("/connector/sink/%s/poll", connector.getId());

        // EventMesh SubscriptionManager 按 BROADCAST 模式将 MQ 数据下发
        // Sink 通过 HTTP Long-Polling 接收
        while (running) {
            List<CloudEvent> events = httpClient.poll(pollUrl, sourceTopic);
            if (!events.isEmpty()) {
                connector.put(events);
                connector.flush();
                // 确认 offset（写入 Connector 自有的 RocksDB）
                connector.commitOffset(events.get(events.size() - 1));
            }
        }
    }
}
```

### 8.6 Connector Runtime 与 EventMesh Runtime 的部署关系

```
独立部署（推荐）:
  ConnectorRuntime 进程    EventMeshRuntime 进程
  ┌─────────────────┐      ┌─────────────────────┐
  │ Source/Sink     │      │ IngressPipeline     │
  │ OffsetStore     │      │ SubscriptionManager  │
  │ HTTP Client     │ ←──→ │ OffsetStore         │
  └─────────────────┘      │ PushService         │
                            │ AdminClient         │
                            └─────────────────────┘
                               ↓                 ↑
                          Kafka/RocketMQ      Kafka/RocketMQ
                          (独立部署)           (独立部署)

共用同一 MQ 集群（但各自使用独立 topic，互不干扰）
```

### 8.7 删除清单（Connector 相关）

| 删除项 | 理由 |
|--------|------|
| `ConnectorRuntime` 类 | 独立为 `ConnectorRuntime` 进程，不再是 EventMesh Runtime 内部类 |
| `ConnectorRuntimeService` | EventMesh Runtime 不再持有 Connector |
| `SourceRunner` / `SinkRunner` 内部线程 | Connector Runtime 自己管理线程 |
| `BlockingQueue<ConnectRecord>` 旁路 | Connector → EventMesh 走 HTTP，不再走内存队列 |
| Connector 的 `System.exit(-1)` Bug（Source/Sink finally 块） | Connector Runtime 重写后不会出现此问题 |

### 8.8 保留清单（Connector Runtime 自身）

| 保留项 | 说明 |
|--------|------|
| `SourceConnector` 抽象类 | Connector Runtime 的 Source 基类 |
| `SinkConnector` 抽象类 | Connector Runtime 的 Sink 基类 |
| 各 Connector 实现（Kafka/RocketMQ/MySQL/Redis/...） | 独立维护 |
| Connector 配置管理（config yaml） | Connector Runtime 自己管理 |
| Connector offset 管理 | Connector Runtime 的 `OffsetStore` 自己管理 |

### 8.9 Connector Runtime 与 Meta / S3Stream 集成（§15.8 补充）

Connector Runtime 是独立进程，但其与 Meta / 存储后端的关系须明确，避免与 EventMesh Runtime 的控制面混淆。

**① 与 Meta 的关系：只读 + 自有 offset，不参与分区协调**

```
Connector Runtime ≠ EventMesh Runtime，不持有 MQ 分区租约：
  · 不参与 §13.2.8 分区分配协议（那是 EventMesh Runtime 拉取 MQ 的事）
  · Connector 的"offset"是外部系统的进度（如 MySQL binlog position、Kafka 源 offset），
    与 EventMesh 分发 offset（§13.2.4）完全正交

Connector 与 Meta 的交互（只读 + 上报）：
  · 读：从 Meta 发现 EventMesh Runtime 地址（POST /events/publish 的目标）
  · 读：从 Meta 读 ACL/限流规则（若 Connector 也过 Pipeline，见 ②）
  · 写：Connector offset 远程副本上报（独立 key 空间，见 ③）
  · 不写：分区分配表、订阅视图（那些是 EventMesh Runtime 的）
```

**② Connector 与存储后端（含 S3Stream）的关系**

```
Source Connector：
  外部系统 → SourceConnector.poll() → CloudEvent
    → HTTP POST /events/publish → EventMesh Runtime → Storage.send(MQ/S3Stream)
  · Connector 不直接写 MQ/S3Stream，而是经 EventMesh Runtime（走 IngressPipeline，过 ACL/限流）
  · 这样 Connector 复用 EventMesh 的安全/限流/trace，不自建

Sink Connector：
  EventMesh Runtime → Storage.poll() → SubscriptionManager（BROADCAST 模式）
    → HTTP /connector/sink/{id}/poll 下发 → SinkConnector.put() → 外部系统
  · Sink 通过 HTTP Long-Polling 从 EventMesh 拉（§8.5），不直接读 MQ/S3Stream
  · S3Stream 作为 EventMesh 存储后端时，Sink 无感知（对它就是 HTTP CloudEvents）
```

**③ Connector offset 的 Exactly-Once 落地（§16 层面 A）**

```
Connector 自有 offset 两级存储（与 EventMesh 分发 offset 独立）：
  ├─ 本地 RocksDB（Connector 进程内）
  └─ 远程 Admin Server（注意：是 Admin Server，不是 Meta）

  对比 EventMesh 分发 offset（§13.2.4）：
    EventMesh：本地 RocksDB + Meta（控制面）
    Connector：本地 RocksDB + Admin Server（管理面）
  → 两套 offset 互不干扰，各自独立实现 EO

Source EO 流程：
  1. poll 外部数据 → 本地 RocksDB 记 source offset
  2. HTTP POST /events/publish（等 EventMesh 202 Accepted）
  3. publish 成功 → commit source offset 到 Admin Server
  4. publish 失败 → 不 commit，重试（本地 offset 未推进，重拉同批）
  → 至少一次 publish + offset 不超前 = 源端不丢不重

Sink EO 流程：
  1. HTTP poll 从 EventMesh 拉一批 CloudEvent
  2. SinkConnector.put() 写外部系统（需外部系统幂等或事务）
  3. 写成功 → commit sink offset（已处理到哪）到 Admin Server
  4. 写失败 → 不 commit，重试（重拉同批，靠外部系统幂等去重）
  → Sink 的 EO 依赖外部系统幂等（与 §15.2 客户端幂等同理）
```

**④ Connector 配置（含 S3Stream 间接使用）**

```yaml
# conf/connectors.yaml（Connector Runtime 自管）
sources:
  - id: mysql-cdc-1
    type: jdbc
    config: { ... }
    targetTopic: tenantA.orders        # 发布到 EventMesh 的 topic
    eventmeshRuntime: meta-discovery   # 从 Meta 发现 Runtime 地址

sinks:
  - id: redis-sink-1
    type: redis
    config: { ... }
    sourceTopic: tenantA.orders
    eventmeshRuntime: meta-discovery

# 注：Connector 不配 S3Stream——它经 EventMesh Runtime 间接使用存储
# eventmesh.storage.type=s3stream 是 EventMesh Runtime 的配置（§3.5）
```

> **小结**：Connector Runtime 与 EventMesh Runtime 通过 HTTP+CloudEvents 解耦；Connector 不碰 MQ/S3Stream、不参与分区协调；其 offset 独立于 EventMesh 分发 offset（本地 RocksDB + Admin Server），实现源端 EO。S3Stream 对 Connector 透明。

---

## 九、协议启动入口统一化

### 9.1 当前状态

```
旧入口:
├─ EventMeshStartup.main()           → EventMeshBootstrap (init/start/shutdown)
│   ├─ EventMeshHttpBootstrap
│   ├─ EventMeshTcpBootstrap
│   ├─ EventMeshGrpcBootstrap
│   └─ EventMeshAdminBootstrap
│
└─ RuntimeInstanceStarter (无 main)   → Runtime (init/start/stop)
    ├─ ConnectorRuntime
    ├─ FunctionRuntime
    └─ MeshRuntime
```

### 9.2 统一启动入口

```java
// 单一入口
public class EventMeshApplication {

    public static void main(String[] args) {
        // 1. 加载配置
        EventMeshConfig config = loadConfig(args);

        // 2. 初始化组件（可插拔，按需启动）
        MeshStoragePlugin storage = StoragePluginLoader.load(config.getStorageType());

        // 3. 构造 Runtime 上下文
        RuntimeContext ctx = RuntimeContext.builder()
            .config(config)
            .storage(storage)
            .ingressPipeline(new IngressPipeline(config))
            .egressPipeline(new EgressPipeline(config))
            .subscriptionManager(new SubscriptionManager())
            .pushService(new PushService())
            .connectorRuntimeService(new ConnectorRuntimeService(storage))
            .adminClient(new AdminClient(config))
            .build();

        // 4. 启动
        ctx.start();

        // 5. 注册优雅停机
        Runtime.getRuntime().addShutdownHook(new Thread(ctx::shutdown, "shutdown-hook"));

        System.out.println("EventMesh Runtime started: " + config.getNodeId());
    }
}
```

**删除的旧入口：**

| 删除项 | 理由 |
|--------|------|
| `EventMeshStartup` | 替换为 `EventMeshApplication` |
| `EventMeshBootstrap` 接口 | 替换为 `RuntimeContext` |
| `EventMeshHttpBootstrap` | 替换为 `UnifiedIngressHandler` |
| `EventMeshTcpBootstrap` | 无 TCP SDK，删除 |
| `EventMeshGrpcBootstrap` | 无 gRPC SDK，删除 |
| `EventMeshAdminBootstrap` | 管理接口整合到 `AdminClient` |
| `Runtime` / `RuntimeInstanceStarter` | v2 完全废弃，功能整合到统一 Runtime |

---

## 十、文件删除清单

### 10.1 按模块分类

#### eventmesh-sdk-java（大幅精简）

| 删除 | 说明 |
|------|------|
| `tcp/` 全部子包 | TCP SDK，无 gRPC SDK |
| `grpc/` 全部子包 | HTTP SDK 可覆盖 |
| `producer/impl/EventMeshTCPProducer.java` | TCP 协议 |
| `producer/impl/EventMeshGrpcProducer.java` | gRPC 协议 |
| `consumer/EventMeshTCPClient.java` | TCP 客户端 |
| `consumer/EventMeshHttpClient.java` | 替换为 `CloudEventsClient` |
| `protocol/` 全部子包 | 自定义协议，非 CloudEvents |
| `common/` 中的 `EventMeshMessage.java` | 替换为 CloudEvents |
| `common/` 中的 `Package.java` | TCP 协议帧 |
| `common/` 中的 `Command.java` | TCP 命令码 |

#### eventmesh-protocol-plugin（精简）

| 删除 | 说明 |
|------|------|
| `eventmesh-protocol-meshmessage/` | TCP SDK 的 ProtocolAdaptor |
| `eventmesh-protocol-openmessage/` | OpenMessaging，与 MQ Group 绑定 |
| `eventmesh-protocol-grpc/` + `eventmesh-protocol-grpcmessage/` | gRPC SDK 协议插件 |

#### eventmesh-storage-plugin（精简）

| 删除 | 说明 |
|------|------|
| `consumerGroup` / `producerGroup` 配置项 | MQ 语义，不暴露 |
| `MeshMQProducer.createTransactionProducer()` | 事务消息过于复杂 |
| `subscribe(topic, subExpression)` 的 `subExpression` | MQ Tag 过滤语义 |
| RocketMQ: `DefaultMQPushConsumer` 的 `consumerGroup` | EventMesh 自己管理订阅 |

#### eventmesh-runtime（精简）

| 删除 | 说明 |
|------|------|
| `protocol/tcp/` 全部子包 | 无 TCP SDK |
| `protocol/grpc/` 全部子包 | 无 gRPC SDK |
| `processor/tcp/` 全部 Processor | TCP 协议处理器 |
| `processor/grpc/` 全部 Processor | gRPC 协议处理器 |
| `processor/http/` 大部分 Processor | 简化为 `UnifiedIngressHandler` |
| `Session` / `ClientSession` 体系 | TCP Session |
| `ClientGroupPack` / `ClientGroupPackManagement` | Consumer Group 管理 |
| `EventMeshTcpServer` | TCP Server |
| `EventMeshGrpcServer` | gRPC Server |
| `HelloProcessor` / `GoodbyeProcessor` | TCP 会话管理 |
| `SubscribeProcessor` / `UnSubscribeProcessor` | TCP 订阅 |

#### eventmesh-common（精简）

| 删除 | 说明 |
|------|------|
| `protocol/tcp/Package.java` | TCP 帧格式 |
| `protocol/http/HttpCommand.java` | 旧 HTTP 协议 |
| `protocol/http/HttpRequestProtocolRequest.java` | 旧 HTTP 协议 |
| `common/Message.java` | 旧消息格式 |
| `protocol/asm/` 全部 | TCP ASM 字节码（会话加密） |

### 10.2 预期代码量变化

| 模块 | 当前行数（估算） | 重构后行数（估算） | 变化 |
|------|----------------|------------------|------|
| eventmesh-sdk-java | ~15,000 | ~3,000 | -80% |
| eventmesh-protocol-plugin | ~6,885 | ~1,500 | -78% |
| eventmesh-storage-plugin | ~5,000 | ~3,500 | -30% |
| eventmesh-runtime | ~32,331 | ~15,000 | -54% |
| eventmesh-common | ~12,000 | ~6,000 | -50% |
| **合计** | **~71,000** | **~29,000** | **-59%** |

---

## 十一、重构分阶段实施计划

> **v1.1 更新**：原 Phase 1–8 仅覆盖"减法"与单实例核心数据通路。对照 §13 能力缺口，新增 7 个补充阶段（Phase 2.5 / 4.5 / 5.5 / 5.6 / 6.6 / 7.5 / 8.5），分别补齐多实例协调、安全、下发可靠性、可观测性、运维、Admin 重做、接入扩展。**Phase 1–8 完成 ≠ 可上生产**；🔴 阻断/高优先级的补充阶段为生产前硬性前置。

### 阶段总览与依赖

```
Phase 1  Storage Plugin 重构（MQ 无语义化 + S3Stream 多后端，§15.8）
   │
Phase 2  SubscriptionManager 新增（单实例分发逻辑）
   │
Phase 2.5  🔴 多实例消费协调（控制面=Meta，§15.5）  ← 阻断，生产 HA 前必做
   │   (Meta 分区分配+租约 / offset 两级存储 / 订阅关系同步 / 实例间转发)
   │
Phase 3  SDK 简化（HTTP 家族，4 API 含 request-reply，三传输默认 WS，§15.6/§15.7）
   │
Phase 3.5  🔴 request-reply 同步调用  ← 对齐 TCP 同步调用，超时丢弃
   │
Phase 4  Runtime 入方向简化（UnifiedIngressHandler）
   │
Phase 4.5  🔴 安全  ← TLS/mTLS + 认证鉴权 + 租户隔离 + 签名
   │
Phase 5  Runtime 出方向简化（PushService，TransportChannel 三传输 + 虚拟线程，§15.8）
   │
Phase 5.5  🔴 下发可靠性  ← ACK + 重试 + DLQ + STICKY 顺序 + 去重
   │
Phase 5.6  🟠 可观测性  ← metrics + trace 传播 + 消息轨迹
   │
Phase 6  Connector Runtime 独立化
   │
Phase 6.6  🟠 运维  ← 限流 + 背压 + 动态配置 + 优雅停机 + 连接管理
   │
Phase 7  启动入口统一（Java 21）
   │
Phase 7.5  🔴 Admin 管理面重做  ← 替换依赖 TCP/Group 的旧 handler
   │
Phase 8  清理收尾
   │
Phase 8.5  🟡 接入扩展  ← WebHook 推送 + MQTT 声明（WS/SSE/批量已在 Phase 3/5 核心）

生产就绪门槛 = Phase 1–8 + 2.5 + 3.5 + 4.5 + 5.5 + 7.5 全部完成
```

> **🔎 实现状态 vs 门槛（v1.11 / 2026-07-06 盘点）**：**当前代码未达此门槛，不可上生产 HA。** 门槛所列 Phase 中，**Phase 2.5（多实例协调）实质未完成（❌，G2–G6）**、Phase 5 的 WebSocket 主传输未实现（G1）；Phase 3.5/4.5/5.5/7.5 为 ⚠️ 部分完成。即"单实例快乐路径"可用，"多实例 HA + 默认 WS 推送"待补。逐项状态见各 Phase 标题处标注，证据见附录 F。

| 阶段 | 优先级 | 类型 | 详细设计 |
|------|--------|------|---------|
| Phase 1 | P0 | 减法 | §3 / §15.8 |
| Phase 2 | P0 | 核心 | §4 |
| **Phase 2.5** | 🔴 P0 | 补充 | §13.2 / §15.5 |
| Phase 3 | P0 | 核心 | §5 / §15.6 |
| **Phase 3.5** | 🔴 P0 | 补充 | §17 / §15.7 |
| Phase 4 | P1 | 核心 | §6 |
| **Phase 4.5** | 🔴 P1 | 补充 | §13.4 |
| Phase 5 | P0 | 核心 | §7 / §15.6 / §15.8 |
| **Phase 5.5** | 🔴 P1 | 补充 | §13.3 |
| **Phase 5.6** | 🟠 P2 | 补充 | §13.5.1–13.5.3 |
| Phase 6 | P1 | 独立 | §8 |
| **Phase 6.6** | 🟠 P2 | 补充 | §13.6 |
| Phase 7 | P2 | 收口 | §9 / §15.8 |
| **Phase 7.5** | 🔴 P1 | 补充 | §13.5.4 |
| Phase 8 | P3 | 收尾 | — |
| **Phase 8.5** | 🟡 P3 | 补充 | §13.7.2 |

---

### Phase 1：Storage Plugin 重构（最底层，先行）

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：⚠️ `MeshStoragePlugin` 接口（send / poll-by-offset / assignPartitions / commitOffset）+ Kafka/RocketMQ 原生 pull 模式已实现并去 Group 配置；**但 Kafka 仍设 `group.id="eventmesh-storage-internal"` 违反"MQ 无语义"铁律（附录 F G7）**，S3Stream 后端未实现（附录 F.5），`sendBatch` 批量未实现。

**目标**：MQ 无语义化改造，Storage Plugin 只暴露 send/poll，不暴露任何 Group 概念

```
阶段产出:
├─ MeshStoragePlugin 接口重定义（send/poll/start/shutdown）
├─ KafkaStoragePlugin 重实现（单 Producer + 单 Consumer）
├─ RocketMQStoragePlugin 重实现（单 Producer + 单 PushConsumer）
└─ 删除 producerGroup / consumerGroup 全部配置项
```

> **接口预留（为 Phase 2.5 多实例协调）**：本阶段在 `MeshStoragePlugin` 预留 `assignPartitions(topic, partitions)` 与 `poll(topic, partition, startOffset, maxEvents, timeoutMs)` 方法签名（详见 §13.2.3），先返回全量分区，Phase 2.5 再接 Admin Server 分配。

**影响范围**：Storage Plugin 模块内部，不影响 Runtime 或 SDK
**测试**：单元测试 Storage Plugin 的 send/poll
**DoD（验收标准）**：
- [ ] `MeshStoragePlugin` 接口仅含 send/poll/commitOffset/assignPartitions/start/shutdown，无任何 Group/Tag 配置
- [ ] Kafka/RocketMQ/S3Stream 三实现均通过 send→poll 往返测试
- [ ] S3Stream v1 薄包装能连真实 S3Stream endpoint 读写（§3.6.3）
- [ ] 删除全部 producerGroup/consumerGroup/tag 配置项，启动不报缺失
- [ ] 接口预留 `assignPartitions` / 按 offset 范围 `poll`（供 Phase 2.5）

### Phase 2：SubscriptionManager 新增（核心逻辑）

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：✅ 三分发模式（LOAD_BALANCE / BROADCAST / MULTICAST）+ LOAD_BALANCE_STICKY（单实例按 `partitionkey` hash 粘性）+ 心跳过期清理已实现并单元测试（5 测试全绿）。

**目标**：实现 EventMesh 自主订阅分发逻辑

```
阶段产出:
├─ SubscriptionManager 完整实现（LOAD_BALANCE/BROADCAST/MULTICAST）
├─ PushService 实现（HTTP Long-Polling）
├─ SubscriptionManager 接入 Storage Plugin（MQ poll → 分发）
└─ SubscriptionManager 接入 PushService（分发 → HTTP 下发）
```

**影响范围**：新增类，不影响现有代码
**测试**：SubscriptionManager 单元测试（模拟 Storage poll + 验证分发逻辑）
**DoD**：
- [ ] 三种分发模式（LOAD_BALANCE/BROADCAST/MULTICAST）分发结果正确（单元测试覆盖）
- [ ] selectTargets 过滤心跳过期订阅者
- [ ] Storage.poll → selectTargets → PushService.push 链路打通（单实例）
- [ ] 订阅/退订线程安全（并发 subscribe/unsubscribe 无竞态）

### Phase 2.5：多实例消费协调（🔴 阻断，生产 HA 前必做）

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：❌ **阻断，实质未完成。** 分区不重叠拉取（G2）、gen fencing 防脑裂（G3）、实例间转发（G4，`EventMeshApplication` 直接 `return false`）、offset 两级存储（G5，仅本地 RocksDB）**均未接**；Meta 仅 Nacos 后端且有 prefix-scan/CAS 缺陷（G6）。`ClusterCoordinator`/`ClusterMembership`/`PartitionAssigner`/`ClusterSubscriptionStore` 有骨架但 boot 未 wire assigner、未调 `assignPartitions`。**当前多实例部署 = 每实例全量拉取 + 重复消费。** DoD 全部 ❌。

**目标**：解决单 Consumer 全量拉取在多实例下的重复消费/竞争问题，让方案可横向扩展。详细设计见 §13.2。

```
阶段产出:
├─ 分区分配 + 租约机制
│   ├─ Admin Server 主导：topic#partition → ownerInstanceId + 租约续约
│   └─ 降级模式：实例从 MetaService 拿全量列表，按 partition%N 自洽分配
├─ MeshStoragePlugin 接入分配结果
│   ├─ assignPartitions(topic, partitions) 实装（替换全量 assign）
│   └─ poll(topic, partition, startOffset, ...) 按 offset 范围拉取
├─ Offset 集中存储（两级）
│   ├─ 本地 RocksDB（缓存 + 降级可用，§12.6）
│   └─ 远程（Admin Server / MetaService KV）异步同步 + 启动拉取
│   └─ readOffset = max(local, remote)，clientId 迁移不丢进度
├─ 订阅关系集群同步
│   ├─ 集群级订阅视图（MetaService KV / Admin Server）：topic → Set<Subscription>
│   ├─ 本地缓存全量视图（MetaService watch 推送变更）
│   └─ subscribe()/unsubscribe() 写集群级 + 广播失效
├─ 实例间消息转发
│   ├─ clientId → 所在实例的路由表（MetaService 维护）
│   ├─ 分区拥有者拉到消息 → selectTargets → 查目标 clientId 所在实例
│   └─ 跨实例 HTTP POST /internal/forward 转发到目标实例投递
└─ 负载均衡全局性
    ├─ LOAD_BALANCE_STICKY 按 partitionkey 粘性（全局一致，§13.3.3）
    └─ 纯 RoundRobin 跨实例退化为实例内 + 分区天然分流（可接受）
```

**影响范围**：SubscriptionManager、MeshStoragePlugin、MetaService 接入；新增分区分配器、实例间转发模块
**依赖**：Phase 2（SubscriptionManager 已存在）、Meta 注册中心可用（§15.5）
**测试**：
- 单实例：分区分配不退化原有功能
- 多实例：2+ 实例下分区不重叠、无重复消费
- 故障注入：杀一个实例 → 分区租约到期 → 其他实例接管 → clientId offset 从远程恢复不丢
- 降级：Meta 不可用 → 实例自洽分配仍能运行
**DoD（🔴 阻断阶段，生产 HA 前必做）**：
- [ ] 2+ 实例下分区无重叠拉取（验证不重复消费）
- [ ] gen fencing 生效：模拟网络分区，旧 owner 自停 poll（§13.2.8 ④）
- [ ] offset 两级存储：clientId 迁移实例后从 Meta 恢复进度，零重放
- [ ] 集群级订阅视图：subscribe 打到实例 A，拉消息在实例 B，订阅者仍收到
- [ ] 实例间转发：跨实例 clientId 能收到下发
- [ ] 降级：Meta 挂后实例自洽分配，publish/已有下发不中断（§13.2.9）
- [ ] Meta 恢复后渐进对齐（offset/订阅/分配），无数据丢失

### Phase 3：SDK 简化（客户端影响最大，Phase 2 完成后做）

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：⚠️ `CloudEventsClient` 4 API（publish/request/reply/subscribe/unsubscribe）齐全；**但仅 Long-Polling 一种传输**（无 WebSocket/SSE 客户端，G16）、无批量 `publish(List)`、poll 异常只 log 不自动重连、ACK 在 handler 返回后自动触发（客户端无法控制 ACK 时机做幂等窗口）。TCP/gRPC SDK 已物理删除 ✅。

**目标**：HTTP-only CloudEvents SDK

```
阶段产出:
├─ CloudEventsClient（新类）
├─ CloudEventsClientBuilder
├─ subscribe/unsubscribe 实现（HTTP Long-Polling 轮询）
├─ publish 实现（HTTP POST CloudEvents JSON）
└─ 集成测试（publish → subscribe → 验证收到）
```

**废弃（不影响 Runtime 运行，仅废弃 SDK API）**：
- TCP SDK 代码冻结，文档标记 `@Deprecated`
- gRPC SDK 代码冻结，文档标记 `@Deprecated`
- OpenMessaging API 标记 `@Deprecated`

**DoD**：
- [ ] `CloudEventsClient` 4 API（publish/request/subscribe/unsubscribe）可用
- [ ] 三传输（WebSocket/SSE/Long-Polling）可切换且各自推送正常（§5.1.1）
- [ ] 二进制 CloudEvents 编码默认（§15.8）
- [ ] 端到端：publish → subscribe → 收到（三传输各跑通）
- [ ] SDK 自动重连（连接断开后恢复订阅）
- [ ] TCP/gRPC/OpenMessaging 标记 `@Deprecated`，编译警告可见

### Phase 3.5：request-reply 同步调用（🔴 高优先级，对齐 TCP 同步调用）

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：⚠️ 同实例 request→reply + 超时丢弃迟到应答 + correlationId 匹配表已实现并测试；**但跨实例自寻址（`x-em-reply-instance` + `/internal/reply-forward`，§17.6）未实装（G10）**——请求方与响应方在不同实例时应答丢失。

**目标**：对齐  TCP 同步调用语义，新增 RPC-over-bus 能力。详细设计见 §17。

```
阶段产出:
├─ request() API（§5.1 第 4 个 API）
├─ HTTP 映射
│   ├─ POST /events/request（请求挂起，阻塞等应答）
│   ├─ POST /events/reply（带 correlationId）
│   └─ 路由：复用 §13.2.5 clientId→instance 路由表（存 Meta）做跨实例应答
├─ correlationId 匹配表 + 超时清理
├─ 超时处理：超时即失败，迟到应答默认丢弃（§15.7）
└─ 语义边界：与 at-least-once pub/sub 独立，不重投/不进 DLQ
```

**影响范围**：UnifiedIngressHandler 加 request/reply 端点、SDK 加 request() API
**依赖**：Phase 3（SDK）、Phase 4（IngressHandler）、Phase 2.5（Meta 路由表，跨实例应答）
**测试**：request→reply 正常返回；超时失败、迟到应答丢弃；跨实例应答路由正确；request 不触发 DLQ
**DoD（🔴 高优先级）**：
- [ ] `request(event, timeout)` 正常收到应答
- [ ] 超时返回失败，迟到应答被丢弃（§15.7）
- [ ] 跨实例应答：请求方与响应方在不同实例，应答自寻址路由正确（§17.6，不查 Meta 全局表）
- [ ] request-reply 不重投、不进 DLQ（与 at-least-once 语义隔离）
- [ ] correlationId 匹配表超时清理无泄漏

### Phase 4：Runtime 入方向简化

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：✅ `UniHttpServer` 端点齐全（publish/subscribe/unsubscribe/poll/ack/request/reply/stream），基于 JDK `HttpServer` + 虚拟线程（注释提 production 可换 netty `AbstractHTTPServer`）。旧 24 个 HTTP Processor 已替换。

**目标**：UnifiedIngressHandler 替代 24 个 HTTP Processor

```
阶段产出:
├─ UnifiedIngressHandler（统一 HTTP 入口）
│   ├─ publish() → IngressPipeline → Storage.send()
│   ├─ subscribe() → SubscriptionManager.subscribe()
│   ├─ unsubscribe() → SubscriptionManager.unsubscribe()
│   └─ poll() → PushService.registerPollChannel()
├─ 删除 20+ 个旧的 HTTP Processor
├─ 删除 MeshMessageProtocolAdaptor / OpenMessageProtocolAdaptor
└─ IngressPipeline 精简（去掉处理 Package / HttpCommand 的分支）
```

**测试**：集成测试（CloudEvents SDK → UnifiedIngressHandler → Storage → 验证）
**DoD**：
- [ ] UnifiedIngressHandler 统一入口（publish/subscribe/unsubscribe/poll/request/reply）
- [ ] 删除 20+ 旧 HTTP Processor，无残留路由
- [ ] IngressPipeline 去掉 Package/HttpCommand 分支，仅处理 CloudEvents
- [ ] CloudEvents binary + structured 两种编码均能解析（§13.8.1）

### Phase 4.5：安全能力（🔴 高优先级，生产前必做）

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：⚠️ `FilterChain` + `IngressFilter` 体系 + `TokenAuthFilter` / `SignatureVerifierFilter`（HMAC-SHA256）已实现并测试；**但 `AclFilter` 是 `Map<String,Set<String>>` 静态骨架，非文档 §13.4.2 规则模型（无 priority/DENY/action/Meta-watch，G9）**；TLS 有 `TlsContextFactory`（真生成 SSLContext + mTLS truststore）但 `EventMeshApplication.main` **默认不接线** TLS/FilterChain/Legacy（G14），且硬编码 TLSv1.2、truststore 密码不独立。

**目标**：补齐 TLS/mTLS、认证鉴权、租户隔离、CloudEvents 签名。HTTP-only 方案若明文传输则不可上生产。详细设计见 §13.4。

```
阶段产出:
├─ TLS / mTLS（复用 develop 的 SslContextFactory + EventMeshTlsConfig）
│   ├─ HTTPS Long-Polling
│   ├─ TlsMode：DISABLED / PERMISSIVE（平滑迁移）/ ENFORCING
│   └─ 双向认证：tls.server.client.auth = NONE/OPTIONAL/REQUIRE + truststore
├─ AuthFilter（认证：你是谁）—— 内置 IngressFilter（不装 SPI 插件）
│   ├─ 内置 TokenAuthFilter；扩展认证 = 新增 IngressFilter
│   ├─ SDK builder 加 .credential(token / username+password)
│   └─ 每请求 Authorization 头校验，失败 → 401
├─ AclFilter（鉴权：你能做什么）—— 内置 IngressFilter
│   ├─ topic 粒度权限：publish / subscribe
│   ├─ 权限上下文：CloudEvents extension emuserid / emtenantid
│   └─ 规则经 MetaService 动态下发，失败 → 403
├─ 租户隔离
│   ├─ topic 命名空间：<tenantId>.<topic>
│   ├─ AclFilter 按 tenant 隔离订阅关系与消息
│   └─ SubscriptionManager 按 tenant 过滤订阅视图
└─ CloudEvents 签名（借鉴 A2A AgentCardSignature）
    ├─ extension x-em-signature = HMAC-SHA256(secret, canonical(event))
    └─ 接收方验签，防篡改 + 来源可信
```

**影响范围**：IngressPipeline 的 AuthFilter / AclFilter 实装、SDK 加 credential、配置项扩展
**依赖**：Phase 4（UnifiedIngressHandler + Pipeline 已存在）
**测试**：无凭证/错误凭证 → 401；越权 topic → 403；mTLS 握手失败 → 拒连；签名错误 → 拒收；租户 A 看不到租户 B
**DoD（🔴 高优先级）**：
- [ ] HTTPS Long-Polling/WS/SSE 全传输启用 TLS，ENFORCING 模式明文被拒
- [ ] mTLS 双向认证：无客户端证书 → 拒连（client.auth=REQUIRE）
- [ ] AuthFilter：无凭证→401，token/basic 校验通过
- [ ] AclFilter：越权 topic→403，规则 priority 匹配 + DENY 优先生效（§13.4.2）
- [ ] ACL 规则经 Meta watch 下发，热路径零 RTT（本地缓存匹配）
- [ ] 租户隔离：tenantA 看不到 tenantB 的订阅/消息（resource 带 tenant 前缀）
- [ ] CloudEvents 签名验签：篡改→拒收（§13.4.4）

### Phase 5：Runtime 出方向简化（三传输 + 虚拟线程）

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：❌ `PushService` + `LongPollingChannel` + `SseConnection`（`/events/stream`）+ Java 21 虚拟线程（`newVirtualThreadPerTaskExecutor`）已实现；**但 WebSocket（文档 §15.6 默认主传输）完全未实现（G1，全仓零 WS 代码）**；`sendBatch` 批量未实现。三传输实际只到位 2 种（SSE + Long-Polling）。

**目标**：PushService + SubscriptionManager 替代旧的 Consumer 体系，推送通道支持三传输，并发用 Java 21 虚拟线程

```
阶段产出:
├─ PushService 实现（替代 LRUCache<consumerGroup, AsyncContext>）
├─ TransportChannel 三传输（§7.2 / §15.6）
│   ├─ WebSocketChannel（默认推送主传输，双向持久流）
│   ├─ SSEChannel（单向流式，LLM token 流）
│   └─ LongPollingChannel（防火墙降级）
├─ 批量发送 + 二进制 CloudEvents 编码（从 Phase 8.5 提前到核心，§15.8）
│   ├─ SDK: publish(List<CloudEvent>) 批量
│   ├─ Storage: sendBatch(topic, events, callback)
│   └─ Encoding.BINARY 默认（高 TPS 降开销）
├─ Java 21 虚拟线程处理挂起连接（§15.8）
│   └─ 每个待推通道占虚拟线程，挂起成本接近 0
├─ SubscriptionManager.pollAndDispatch() 定时任务
│   → Storage.poll() → selectTargets() → PushService.push()
├─ 删除 ClientSession / SessionManager（TCP）
├─ 删除 ClientGroupPack / ClientGroupPackManagement（Consumer Group）
└─ 删除 EventMeshTcpServer / EventMeshGrpcServer
```

**测试**：端到端测试（SDK subscribe → Runtime 下发 → 验证收到）；三传输各自推送；批量 publish TPS 提升；虚拟线程下万级挂起连接稳定
**DoD**：
- [ ] TransportChannel 三实现（WS/SSE/LP）统一接口，各自推送正常（§7.2）
- [ ] WebSocket 为默认推送主传输，毫秒级延迟
- [ ] 批量 publish（`sendBatch`）TPS 较单条提升（压测对比）
- [ ] 二进制 CloudEvents 编码默认，JSON 可选
- [ ] Java 21 虚拟线程承载挂起连接，万级连接下内存/CPU 稳定
- [ ] 删除 ClientSession/ClientGroupPack/EventMeshTcpServer/EventMeshGrpcServer，编译通过

### Phase 5.5：下发可靠性（🔴 高优先级，生产前必做）

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：⚠️ `ReliableDispatcher` 完整：ACK 后才推进 offset（at-least-once）+ 指数退避（1/2/4/8/16s）+ 超 maxAttempts 转 `<topic>.DLQ` + STICKY 顺序 + trace 埋点；**但多实例下 STICKY 退化为 RoundRobin 破坏顺序（G8）、退避无 jitter（G13）**。DLQ 携带 reason/retry-count ✅。

**目标**：废弃 §12.6.4 的"不做 ACK、发送成功即推进 offset"简化版，补齐 ACK + 重试 + DLQ + 顺序 + 去重，建立消息总线基本契约（至少一次）。详细设计见 §13.3。

```
阶段产出:
├─ ACK 机制（至少一次）
│   ├─ POST /events/ack { subId, clientId, topic, partition, offset }
│   ├─ offset 仅在 ACK 后推进；ACK 超时（ackTimeout，如 30s）→ 视为失败重投
│   └─ SDK 处理完一批 → 显式 ACK，再 poll 下一批
├─ 重试与死信队列（内置 ReliableDispatcher，不接 eventmesh-retry SPI 插件）
│   ├─ 下发失败/ACK 超时 → ReliableDispatcher 重投，指数退避（1s/2s/4s/8s/16s）
│   ├─ 超 maxAttempts（默认 6 = 初次 + 5）→ 转 DLQ topic: <原topic>.DLQ
│   └─ 死信携带 emdlqreason / emdlqretrycount + 原始 CloudEvent
├─ 顺序消息（STICKY 粘性会话）
│   ├─ 新增 DistributionMode: LOAD_BALANCE_STICKY
│   ├─ partitionKey = CloudEvents partitionkey extension
│   └─ target = subscribers[hash(partitionKey) % size]，同 key 永远同 worker 保序
├─ 延迟/定时消息（决策）
│   ├─ 保留 TTL（CloudEvents time + x-em-ttl，过期丢弃）
│   └─ 定时投递 v1 不支持（声明 + 列入 roadmap）
└─ 去重 / 幂等
    ├─ CloudEvents id 作为 dedupId，重投不变
    ├─ EventMesh 不做全局去重，靠 at-least-once + 客户端幂等
    └─ 明确交付语义 = 至少一次（事务消息明确不支持）
```

**影响范围**：PushService 加 ACK 跟踪、新增重试器与 DLQ 路由、SubscriptionManager 加 STICKY 模式、SDK 加 ACK 调用
**依赖**：Phase 5（PushService 已存在）
**测试**：下发后客户端不 ACK → 超时重投；重投超阈值 → 进 DLQ；同 partitionkey 消息 → 同 worker 顺序到达；客户端按 id 幂等不重复处理
**DoD（🔴 高优先级）**：
- [ ] ACK 机制：offset 仅在 ACK 后推进（§13.3.1），ACK 超时→重投
- [ ] Retryer：指数退避（1s/2s/4s/8s/16s + jitter），超 maxRetries 转 DLQ（§13.3.2）
- [ ] DLQ topic `<topic>.DLQ` 可独立订阅，死信带 reason/retry-count
- [ ] LOAD_BALANCE_STICKY：同 partitionkey → 同 worker，保序（§13.3.3）
- [ ] 文档/SDK 明确声明交付语义=至少一次，不承诺恰好一次
- [ ] crash 恢复：offset 未推进的消息重启后重放（靠幂等收敛）

### Phase 5.6：可观测性（🟠 中优先级）

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：⚠️ `UniMetrics`（OTel 仪表，8 项核心：publish/publish_failed/rate_limited/dispatched/dispatch_latency/ack/redeliveries/dlq）+ `UniTrace`（OTel Span：publish/dispatch/ack/retry/dlq，以 CloudEvent id 关联）已埋；**余 8 项 metrics（offset_lag / active_subscribers / pending_queue / slow_consumer / partition_owner 等，附录 F.5）未实现**；traceparent/tracestate/baggage 全链路透传未显式实装。

> **决策（v1.9）：可观测只用 OpenTelemetry，不支持其他扩展。** metrics 与 trace 全部走 OTel（Meter / Tracer API），由部署侧配置的 OTel exporter（OTLP、Prometheus-via-OTel 等）导出。**不再**复用/支持 `eventmesh-metrics-prometheus`、`eventmesh-trace-plugin`(zipkin/jaeger/pinpoint) 这些独立插件——它们是 legacy，uni runtime 不接线（见附录 D.8）。实现：`org.apache.eventmesh.runtime.uni.metrics.UniMetrics`（OTel LongCounter/LongHistogram 仪表）。

**目标**：补齐 metrics、分布式 trace 传播、消息轨迹。详细设计见 §13.5.1–13.5.3。

```
阶段产出:
├─ Metrics（OpenTelemetry Meter 仪表，唯一路径）
│   ├─ eventmesh_publish_count / eventmesh_publish_failed_count / eventmesh_rate_limited_count
│   ├─ eventmesh_dispatched_count / eventmesh_dispatch_latency_nanos
│   └─ eventmesh_ack_count / eventmesh_redeliveries_count / eventmesh_dlq_count
├─ 分布式 Trace 传播（OpenTelemetry Tracer，唯一路径）
│   ├─ W3C traceparent（CloudEvents Distributed Tracing extension）
│   └─ 链路：SDK publish → IngressPipeline → Storage.send →
│            pollAndDispatch → 跨实例 forward → push → 客户端 ACK
│            OTel Span，经 OTLP / OTel-Prometheus exporter 导出
└─ 消息轨迹（OTel Span，按 CloudEvents id 关联）
    └─ 关键节点埋点：publish / 入 MQ / dispatch / push / ack / retry / dlq
       支持按 CloudEvents id 查询全链路轨迹
```

**影响范围**：各 Pipeline / PushService / 重试器埋点；OTel 仪表注册新指标；trace context 透传
**依赖**：Phase 5（出方向通路存在）、Phase 5.5（重试/DLQ 节点需埋点）
**测试**：OTel exporter（如 OTLP）抓取到各指标与 span；按 id 查到全轨迹
**DoD**：
- [ ] 8+ 项 OTel metrics 仪表暴露（经 OTel exporter，含 topic/tenant/mode 标签，§13.5.1）
- [ ] traceparent/tracestate/baggage 全链路透传（§13.5.2）
- [ ] 关键节点 Span（publish/ingress/storage/dispatch/push/ack + retry/dlq）可见
- [ ] 按 CloudEvents id 查询全链路轨迹
- [ ] uni runtime 不依赖任何 `eventmesh-trace-plugin` / `eventmesh-metrics-prometheus` 类

### Phase 6：Connector Runtime 独立化

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：✅ 独立模块 `eventmesh-connector-runtime` + `ConnectorApplication`（独立 main 进程）+ `RemoteOffsetStore` / `RocksDBConnectorOffsetStore`（本地+远程 EO 双写，§8.9）+ at-least-once commit-on-success 框架。**完成度最高的 Phase。**

**目标**：Connector Runtime 与 EventMesh Runtime 完全分离，各自独立部署

```
阶段产出:
├─ ConnectorRuntime 独立为独立 Java 进程（独立 main 入口）
├─ SourceConnector 拉取外部数据 → HTTP POST /events/publish 到 EventMesh
├─ SinkConnector 订阅 EventMesh → HTTP Long-Polling 接收 → 写入外部系统
├─ EventMesh Runtime 删除 ConnectorRuntimeService（不再持有 Connector）
├─ Connector 自有 OffsetStore（RocksDB，与 EventMesh 的 OffsetStore 完全独立）
└─ 删除 Connector 的 BlockingQueue 旁路 + System.exit(-1) Bug
```

**测试**：Connector Runtime 独立启动 → Source 写入 → EventMesh 收到 → 下发 → Sink 收到 → 外部系统确认
**DoD**：
- [ ] Connector Runtime 独立 Java 进程（独立 main），不经 EventMesh Runtime 内嵌
- [ ] Source → HTTP POST /events/publish → EventMesh 收到
- [ ] Sink → HTTP Long-Polling 接收 → 写外部系统
- [ ] Connector 自有 OffsetStore（本地 RocksDB + Admin Server 远程，§8.9），与 EventMesh 分发 offset 独立
- [ ] Source/Sink 的 EO 流程成立（publish 成功才 commit source offset，§16 层面 A）
- [ ] 删除 BlockingQueue 旁路 + System.exit(-1) Bug

### Phase 6.6：运维与稳定性（🟠 中优先级）

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：⚠️ `TokenBucketRateLimiter`（per-topic）+ 慢消费者四态状态机（HEALTHY/SLOW/STALLED/EVICTED）+ 优雅停机（drain+等在飞 ACK+flush offset）已实现；**但慢消费者状态机有缺陷（无周期采样/溢出仅 1 种策略/STALLED 仍入队，G11）**、停机缺"释放分区租约通知 Meta 重分配"（G12）、动态配置热更新（`EventMeshDynamicConfigManager`）未接线、僵尸 poll 清理未实现。

**目标**：补齐限流、背压、动态配置、优雅停机、连接生命周期管理。详细设计见 §13.6。

```
阶段产出:
├─ 限流（复用 Guava RateLimiter + RateLimiterRulerListener）
│   ├─ RateLimitFilter（Ingress）：per topic / per clientId，超限 → 429
│   ├─ 规则经 MetaService 动态下发
│   └─ 下发侧限流（新增，develop 仅 TCP 侧有）：per clientId 配额，慢消费者不丢
├─ 背压与慢消费者隔离
│   ├─ 每 clientId pendingEvents 队列 maxPending 上限（如 10000）
│   ├─ 超限策略：丢弃最旧 + 记 metric（或转 DLQ）
│   ├─ 慢消费者检测：poll 间隔超 slowThreshold → 减配额/暂停/告警
│   └─ 连续 N 周期 slow → 自动 unsubscribe（防泄漏）
├─ 动态配置与热更新（复用 EventMeshDynamicConfigManager + MetaService）
│   └─ 订阅/限流/过滤/ACL 规则热更新，不重启
├─ 优雅停机
│   ├─ 停新请求(503) → drain pending → 等在飞 ACK(graceful 10s)
│   ├─ flush offset（本地+远程）→ 释放分区租约（通知 Admin 重分配）
│   └─ 关闭 Storage / HTTP Server
│   > **v1.11 实现**: `UniRuntime.shutdown(gracefulMs)` 已实现完整 drain 流程:
│   > 停 pull-loop → final dispatcher tick(drain pending) → 循环等在飞 ACK(gracefulMs, 默认10s)
│   > → flush+close offsetStore → close storage. `EventMeshApplication` shutdown hook 调用.
└─ 连接生命周期管理
    ├─ poll 超时清理、僵尸 poll 检测（lastHeartbeat 清理）
    ├─ 客户端断开 → 移除订阅 + 释放资源
    └─ poll channel 总数上限，超限拒绝
```

**影响范围**：Pipeline 加 RateLimitFilter、PushService 加背压/慢消费者逻辑、shutdown 流程细化、连接清理调度
**依赖**：Phase 6、Phase 5.5（DLQ 用于背压溢出）
**测试**：超限 → 429；慢消费者积压触发背压不拖垮其他订阅者；停机后 offset 不丢、分区被接管；僵尸 poll 被清理
**DoD**：
- [ ] RateLimitFilter：per topic/clientId 限流，超限→429，规则经 Meta 动态下发
- [ ] 背压：每 clientId 有界队列 + 溢出策略，慢消费者状态机（HEALTHY/SLOW/STALLED/EVICTED）生效（§13.6.2）
- [ ] 广播场景：单慢消费者不阻塞其他订阅者（线程池隔离）
- [ ] 优雅停机：drain pending → 等在飞 ACK → flush offset → 释放分区租约，offset 不丢
- [ ] 僵尸 poll / 断连客户端被清理，无连接泄漏
- [ ] 动态配置热更新（订阅/限流/ACL 规则不重启生效）

### Phase 7：启动入口统一

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：✅ `EventMeshApplication` 单一 main 入口，统一管理 runtime + 流量 HTTP + admin HTTP 三组件 init/start/shutdown 生命周期 + 虚拟线程 + shutdown hook。旧 Tcp/Grpc/Admin Bootstrap + RuntimeInstanceStarter 已删。

**目标**：EventMeshApplication 单一入口

```
阶段产出:
├─ EventMeshApplication.main()（替换 EventMeshStartup）
├─ RuntimeContext（统一组件生命周期管理）
├─ 删除 EventMeshTcpBootstrap / EventMeshGrpcBootstrap
├─ 删除 EventMeshAdminBootstrap（管理接口整合）
└─ 删除 RuntimeInstanceStarter（旧 v2 入口）
```

**DoD**：
- [ ] `EventMeshApplication.main()` 单一入口，按需加载组件（Storage/Pipeline/SubscriptionManager/PushService/Meta）
- [ ] RuntimeContext 统一 init/start/shutdown 生命周期
- [ ] Java 21 构建（虚拟线程启用，§15.8）
- [ ] 删除 Tcp/Grpc/Admin Bootstrap + RuntimeInstanceStarter，无残留入口
- [ ] 优雅停机 hook 注册（§13.6.4）

### Phase 7.5：Admin 管理面重做（🔴 高优先级，生产前必做）

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：⚠️ `UniAdminServer`/`UniAdminService` 实现 ~5/8 接口（subscriptions / offsets / rejectClient / dlqReplay / metrics + pendingDeliveries）；**缺 `/admin/clients`、`/admin/dlq/{topic}` browse、`/admin/ratelimit/rules` 下发、`/admin/health`（分区视图）**；数据为**进程内本地视图**，非文档要求的集群级（Meta 聚合）（G15）。

**目标**：纠正原文档"Admin 不变"的错误——旧 admin v1 的 19 个 handler 大量依赖已删的 TCP session / Consumer Group，必须重做。详细设计见 §13.5.4。

```
阶段产出:
├─ 新 Admin 面（基于集群级 SubscriptionManager + OffsetStore 视图，经 Admin Server）
│   ├─ GET  /admin/subscriptions        集群订阅关系（按 topic/tenant）
│   ├─ GET  /admin/offsets              offset lag（按 topic/clientId）
│   ├─ GET  /admin/clients              在线客户端 + 所属实例
│   ├─ POST /admin/clients/{id}/reject  踢客户端（清订阅）
│   ├─ GET  /admin/dlq/{topic}          浏览死信
│   ├─ POST /admin/dlq/{topic}/replay   死信重投
│   ├─ PUT  /admin/ratelimit/rules      下发限流规则
│   └─ GET  /admin/health               实例健康/分区分配视图
└─ 删除依赖 TCP/Group 的旧 handler
    （ShowListenClientByTopicHandler / RedirectGroupBatchHandler /
     RejectClientByIpPortHandler 等已无对应底层）
```

**影响范围**：admin handler 模块重写，Admin Server 数据模型适配集群级订阅/offset 视图
**依赖**：Phase 7、Phase 2.5（集群级视图）、Phase 5.5（DLQ 浏览/重投）
**测试**：订阅关系查询准确；踢客户端生效；死信重投成功；限流规则下发到所有实例
**DoD（🔴 高优先级）**：
- [ ] 新 Admin 8 个接口（subscriptions/offsets/clients/reject/dlq browse/dlq replay/ratelimit/health）可用（§13.5.4）
- [ ] 数据来源是集群级视图（Meta + OffsetStore），非进程内
- [ ] 踢客户端（reject）后其订阅清除、连接断开
- [ ] 死信 replay 重新触发下发/WebHook
- [ ] 限流规则经 Meta 下发到所有实例（最终一致）
- [ ] 删除依赖 TCP/Group 的旧 admin v1 handler（19 个），无残留引用

### Phase 8：清理收尾

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：✅（v1.10 决策已落实）TCP 兼容桥（`transport.tcp`：`UniTcpServer` + `MeshMessagePackageRouter` 翻译层 + `TcpPushChannel` 出向）+ HTTP 兼容桥（`transport.http`：`LegacyHttpBridge`）到位，老 TCP/HTTP 客户端零改动；gRPC/OpenMessaging 代码已物理删除。**未做**：性能基线压测报告、全量 §18 E2E 套件（多实例类用例因 Phase 2.5 未完成而不成立）。

> **v1.10 决策修正：TCP 不直接删，保留为边缘协议适配器以兼容老客户端。** 原文"物理删除 TCP SDK"会让存量  TCP 客户端无法接入。修正方案：TCP 退化为新架构的 ingress/egress 传输适配器（与 HTTP/WebHook/长轮询并列），**保留**线协议（`Package`/`Command`/`Codec`）+ 翻译层（`TcpMessageProtocolResolver`/`MeshMessageProtocolAdaptor`）+ netty TCP server 骨架；**替换/删除** TCP 自有的核心逻辑（`ClientSession`/`ClientGroupPack`/`ClientGroupPackManagement`/rebalance、Consumer Group 语义）—— 这些由新 `SubscriptionManager`+`ReliableDispatcher` 接管。实现：`org.apache.eventmesh.runtime.uni.transport.tcp`（`TcpPushChannel` 出向 PushChannel、`TcpAckRegistry` 关联客户端 ACK、`TcpIngressBridge` 入向帧→CloudEvent→`UniIngressService`、`TcpFrameCodec`/`TcpFrameDecoder` 抽象）。老 TCP 客户端零改动。gRPC 同理可作边缘适配器（如无存量 gRPC 客户端则可删）。

```
阶段产出:
├─ TCP 兼容桥：保留线协议+翻译层，TCP 核心(session/group/rebalance)删，接 UniIngressService
├─ 删除 gRPC SDK 相关代码（如无存量 gRPC 客户端）
├─ 删除 OpenMessaging API 相关代码
├─ 更新文档（README / Quick Start / SDK Guide）
├─ 性能基线测试（TPS 对比）
└─ 端到端集成测试套件
```

**DoD**：
- [ ] TCP 兼容桥：老 TCP 客户端能 publish/subscribe/收推送/ACK（端到端，§18 E2E-43 TCP 兼容）
- [ ] TCP 核心(session/group/rebalance)代码物理删除，编译通过；TCP 线协议 + 翻译层保留
- [ ] gRPC/OpenMessaging 代码物理删除（非仅 @Deprecated），编译通过
- [ ] 全量集成测试套件通过（§18）
- [ ] 性能基线：WebSocket+二进制+批量 vs TCP 适配器 vs 旧 TCP 的 TPS/延迟对比报告
- [ ] 文档（README/Quick Start/SDK Guide）更新为新架构
- [ ] 无对已删类的残留引用（grep 干净）

### Phase 8.5：接入能力扩展（🟡 中低优先级）

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：✅ `WebHookChannel`（HMAC-SHA256 签名头 `X-Em-Signature`/`X-Em-Timestamp`/`X-Em-Delivery-Id` + 指数退避重试 + 转 DLQ + 2xx=ACK）已实现并测试；WS/SSE/批量已提前到 Phase 5 核心（其中 WS 见 G1 未完成）。MQTT 不支持已声明。

**目标**：补齐 WebHook 主动推送、MQTT 声明。（注：WS/SSE/批量/二进制已在 Phase 5 提前到核心，详见 §15.6/§15.8。）详细设计见 §13.7。

```
阶段产出:
├─ WebHook 主动推送（补纯 pull/push 模式下无法推第三方的能力）
│   ├─ subscribe 时配 delivery: { type:webhook, url, secret, retry }
│   ├─ EventMesh 主动 HTTP POST 推送到 deliveryUrl + x-em-signature 签名
│   └─ 失败重试（指数退避）+ 转 DLQ，与 WebSocket/SSE/Long-Polling 并存
└─ MQTT 声明不支持（如需由独立网关转 CloudEvents 接入，不在 Runtime 范围）
```

**影响范围**：WebHook 投递模块
**依赖**：Phase 8（核心通路已收尾）、Phase 5.5（WebHook 重试/DLQ 复用）
**测试**：WebHook 推送失败重试到 DLQ；与三传输并存
**DoD**：
- [ ] WebHook delivery：subscribe 配 deliveryUrl，EventMesh 主动 POST 推送
- [ ] 签名：X-Em-Signature(HMAC-SHA256) + X-Em-Timestamp(防重放) + X-Em-Delivery-Id(去重)（§13.7.2）
- [ ] WebHook 失败重试（指数退避）+ 转 DLQ
- [ ] WebHook 与 WS/SSE/LP 并存（订阅者按需选 delivery）
- [ ] 文档声明不支持 MQTT（需由独立网关转 CloudEvents）

---

## 十二、关键设计决策讨论

### 12.1 MQ 无语义 vs 保留 MQ 某些能力

**用户明确要求**：MQ 只做存储，不暴露 Producer Group / Consumer Group / Tag

这意味着：
- **丢弃**：MQ 的分区负载均衡语义（MQ 按分区 hash 决定哪个 Consumer 消费），由 EventMesh SubscriptionManager 替代
- **丢弃**：MQ 的 Tag 过滤（`subExpression = "TAG1 || TAG2"`），由 EventMesh MULTICAST 模式的 CloudEvents 过滤替代
- **保留**：MQ 的持久化（WAL）能力——这是 MQ 作为"分布式日志"的核心价值
- **保留**：MQ 的 offset 管理——EventMesh SubscriptionManager 依赖 MQ 的 offset 来实现 replay

### 12.2 HTTP Long-Polling vs WebSocket / SSE

> **⚠️ 本节选型已被 §15.6 取代**：初版选 Long-Polling 默认。结合  负载（毫秒级延迟 + 高吞吐 + TCP 同步调用），§15.6 决策改为 **WebSocket 默认 + SSE 单向流 + Long-Polling 降级，三种用户可选**。详见 §5.1.1 / §7.2 / §13.7.1。本表保留以记录决策演进。

**选择 HTTP Long-Polling**（而非 WebSocket 或 SSE）：

| 方案 | 优点 | 缺点 |
|------|------|------|
| **HTTP Long-Polling（初版选中，现已降级为备选）** | 简单，兼容性好，穿防火墙/F5/Nginx 无压力，不需要特殊协议升级 | 稍高延迟（max 1 RTT），每次建立连接 |
| WebSocket（现默认推送主传输） | 实时，低延迟，双向，高吞吐 | 需要代理层特殊配置（部分企业网络禁止）→ 此时降级 Long-Polling |
| SSE（现单向流式选项） | 服务器推送，简单，穿墙极佳，LLM 生态成熟 | 单向（不能同时 publish）→ 控制命令走 HTTP 请求响应 |

**设计**：§15.6 后为 WebSocket 默认推送，SSE 用于单向流式输出，Long-Polling 作防火墙降级，SDK 内部自动重连。

### 12.3 负载均衡：RoundRobin vs 最小连接数

当前选型 RoundRobin，理由：

1. **实现简单**：RoundRobin 计数器是原子自增，不需要维护连接状态
2. **足够公平**：在大多数场景下（消费者性能相近）效果等同于最小连接数
3. **可扩展**：未来可以通过配置切换为"最小连接数"策略

### 12.4 广播模式的消息放大问题

**场景**：1000 个订阅者，广播模式需要向 MQ 写 1 条，但下发时 EventMesh 需要建立 1000 个 HTTP Long-Polling 连接。

**解决**：
- MQ 只写 1 条（广播效率高）
- EventMesh 内部通过线程池并发下发（不阻塞 MQ poll）
- 订阅者端 HTTP SDK 维护重连机制（当下发失败时不阻塞其他订阅者）

### 12.5 多播模式的 CloudEvents 过滤标准

**多播匹配规则**（按优先级）：

```
1. "x-em-subscriptions" extension（显式订阅列表）
   CloudEvent: { "x-em-subscriptions": ["service-A", "service-B"] }
   → 只发给 service-A 和 service-B

2. "type" 属性匹配（CloudEvents 标准字段）
   CloudEvent.type = "order.created"
   → 订阅了 type=order.created 的订阅者都收到

3. "source" 属性匹配（CloudEvents 标准字段）
   CloudEvent.source = "/orderservice"
   → 订阅了 source=/orderservice 的订阅者都收到

4. "subject" extension（业务标识）
   CloudEvent.subject = "topic-orders"
   → MQTT Topic 映射，供 SubscriptionManager 路由

优先级：1 > 2 > 3 > 4
```

### 12.6 Offset 管理：EventMesh 自主 offset 管理（参考 RocketMQ Client 实现）

**核心原则**：EventMesh **完全自主管理 offset**，不依赖 MQ 的 Consumer Group offset 机制。

这意味着 EventMesh 自己维护一张 "topic → MQ offset → 分发状态" 的映射表，每个订阅关系独立追踪。

> **RocksDB 定位（§15.8 澄清）**：本节 RocksDB 为**本地完整 offset 副本**。多实例下 offset 真相源在 **Meta**（§13.2.4 两级存储），RocksDB 的职责是：①高频写本地（每批 ACK）+ 低频刷 Meta（写卸载，防 Meta 被 offset 写压垮）；②crash 恢复读本地完整 offset，零重放（即使 Meta flush 滞后）；③Meta 不可用时本地兜底。RocksDB 与 Meta 非冗余——是"本地完整副本 + Meta 写缓冲 + 降级兜底"，非主存储。

#### 12.6.1 为什么不用 MQ 的 Consumer Group offset

```
MQ Consumer Group offset（当前 EventMesh 依赖的机制）:
  Kafka:  Consumer Group → __consumer_offsets topic（broker 端管理）
  RocketMQ:  Consumer Group → consumeOffset.json（broker 端管理）

问题:
  · MQ offset 是按 Consumer Group 粒度管理的
  · EventMesh 的 LOAD_BALANCE 模式中，同一 topic 可能有多个订阅者（不同 clientId）
  · MQ 只能追踪 "某 Consumer Group 消费到哪"，无法追踪 "某 clientId 消费到哪"
  · EventMesh 在 BROADCAST 模式下，一条消息对多个 clientId 只消费一次（MQ offset 推进一次）
    但每个 clientId 的实际分发进度可能不同（有些客户端重连后需要 replay）
  · MULTICAST 模式下，MQ offset 无法区分"哪个 clientId 消费了哪条消息"
```

#### 12.6.2 RocketMQ Client 的 Offset 管理模式（参考实现）

RocketMQ 的 `DefaultMQPushConsumerImpl` 使用 `OffsetStore` 接口管理 offset，有两种实现：

```java
// RocketMQ: OffsetStore 接口
public interface OffsetStore {
    long readOffset(final MessageQueue mq, final ReadOffsetType type);  // 读取 offset
    void writeOffset(final MessageQueue mq, long offset);                // 持久化 offset
    void flush();                                                         // 刷盘
    long load();                                                          // 从磁盘加载
}

// 两种实现：
// 1. LocalFileOffsetStore：offset 存本地文件（/root/.rocketmq_offset/）
// 2. RemoteBrokerOffsetStore：offset 由 broker 管理（通过 RPC 上报）

// LocalFileOffsetStore 关键实现（RocketMQ 4.x）：
public class LocalFileOffsetStore implements OffsetStore {

    private final String storePath;    // /root/.rocketmq_offset/{consumerGroup}/{topic}/partitionN
    private final ConcurrentHashMap<MessageQueue, AtomicLong> offsetTable =
        new ConcurrentHashMap<>();     // 内存中的 offset 快照

    @Override
    public long readOffset(MessageQueue mq, ReadOffsetType type) {
        // 1. 尝试从内存取
        AtomicLong offset = offsetTable.get(mq);
        if (offset != null) return offset.get();

        // 2. 内存没有，从本地文件加载
        String filePath = buildOffsetFilePath(mq);
        if (Files.exists(Paths.get(filePath))) {
            String content = Files.readString(Paths.get(filePath));
            long savedOffset = Long.parseLong(content.trim());
            offsetTable.put(mq, new AtomicLong(savedOffset));
            return savedOffset;
        }

        // 3. 文件也不存在，按 type 决定
        return type == ReadOffsetType.READ_FROM_STORE ? 0 : -1;
    }

    @Override
    public void writeOffset(MessageQueue mq, long offset) {
        // 先写内存
        offsetTable.computeIfAbsent(mq, k -> new AtomicLong())
                   .set(offset);

        // 异步刷盘（不是每条消息都刷盘，而是批量异步刷）
        // 参考 RocketMQ: flushOffsetInterval = 1000ms（可配置）
        scheduleAsyncFlush(mq, offset);
    }

    @Override
    public void flush() {
        // 同步刷盘（进程退出时调用）
        for (Map.Entry<MessageQueue, AtomicLong> e : offsetTable.entrySet()) {
            String path = buildOffsetFilePath(e.getKey());
            Files.writeString(Paths.get(path), String.valueOf(e.getValue().get()));
        }
    }
}
```

#### 12.6.3 EventMesh OffsetStore 设计

参考 RocketMQ Client 的 `LocalFileOffsetStore`，EventMesh 实现自己的 `EventMeshOffsetStore`：

```java
public class EventMeshOffsetStore {

    // 两级结构：topic → clientId → partition → offset
    // · topic: EventMesh 的逻辑 topic（对应 MQ topic）
    // · clientId: 订阅者的客户端标识
    // · partition: MQ 的物理分区（Kafka 有，RocketMQ 的 queueId）
    // · offset: MQ 的逻辑 offset（EventMesh 自己追踪）
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, PartitionOffsetTable>> tables =
        new ConcurrentHashMap<>();

    static class PartitionOffsetTable {
        AtomicLong currentOffset = new AtomicLong(0);
        String storePath;   // 本地文件路径
    }

    // 存储介质：RocksDB（参考 RocketMQ 5.0 RemoteBrokerOffsetStore 的 RocksDB 实现）
    // 选择 RocksDB 而非普通文件的理由：
    // · 高并发写入（多个 SubscriptionManager 线程并发更新 offset）
    // · 支持批量写入（WAL + MemTable + SSTable）
    // · 进程崩溃后自动恢复
    // · 支持范围查询（按 topic 批量读取所有 clientId 的 offset）
    private final RocksDB rocksDB;

    // 构造函数：打开 RocksDB 实例
    public EventMeshOffsetStore(String dataPath) {
        Options options = new Options()
            .setCreateIfMissing(true)
            .setWriteBufferSize(64 * 1024 * 1024)   // 64MB MemTable
            .setMaxWriteBufferNumber(3);              // 3 份 MemTable

        this.rocksDB = RocksDB.open(options, dataPath + "/offset_store");
    }

    // Key 设计：{topic}#{clientId}#{partition}
    //           → 唯一确定一个订阅关系的消费位点
    // 示例: "orders#worker-1#0" → "1042" (第1042条消息已分发)
    private byte[] buildKey(String topic, String clientId, int partition) {
        return (topic + "#" + clientId + "#" + partition).getBytes(StandardCharsets.UTF_8);
    }

    // 读取 offset（启动时调用，恢复分发进度）
    public long readOffset(String topic, String clientId, int partition) {
        byte[] key = buildKey(topic, clientId, partition);
        byte[] value = rocksDB.get(key);
        if (value == null) return -1;   // -1 表示从未消费过
        return Long.parseLong(new String(value));
    }

    // 写入 offset（每批消息处理完成后调用）
    public void writeOffset(String topic, String clientId, int partition, long offset) {
        byte[] key = buildKey(topic, clientId, partition);
        byte[] value = String.valueOf(offset).getBytes(StandardCharsets.UTF_8);
        rocksDB.put(key, value);
    }

    // 批量读取某 topic 的所有 clientId offset（用于 Admin Server 查询）
    public Map<String, Long> readAllOffsets(String topic) {
        byte[] prefix = (topic + "#").getBytes(StandardCharsets.UTF_8);
        RocksIterator it = rocksDB.newIterator();
        Map<String, Long> result = new HashMap<>();

        for (it.seek(prefix); it.isValid(); it.next()) {
            String key = new String(it.key());
            if (!key.startsWith(topic + "#")) break;
            String rest = key.substring(topic.length() + 1);
            long offset = Long.parseLong(new String(it.value()));
            result.put(rest, offset);
        }
        return result;
    }

    // 优雅关闭时刷盘
    public void flush() {
        rocksDB.flush(new FlushOptions().setWaitForFlush(true));
    }
}
```

#### 12.6.4 SubscriptionManager 与 OffsetStore 集成

```java
public class SubscriptionManager {

    private final EventMeshOffsetStore offsetStore;
    private final MeshStoragePlugin storage;

    // 分发消息（按 LOAD_BALANCE 模式）
    public void pollAndDispatch(String topic, String clientId, int partition, long timeoutMs) {
        // 1. 读取 EventMesh 自主管理的 offset
        long lastOffset = offsetStore.readOffset(topic, clientId, partition);
        long startOffset = lastOffset < 0 ? OffsetResetType.EARLIEST.apply() : lastOffset + 1;

        // 2. 从 MQ 按 offset 范围拉取
        List<CloudEvent> events = storage.poll(topic, partition, startOffset, 100, timeoutMs);
        if (events.isEmpty()) return;

        // 3. 找下一个有效的 offset（MQ 返回的最后一条消息的 offset + 1）
        long nextOffset = events.get(events.size() - 1).getExtension("x-mq-offset", Long.class) + 1;

        // 4. 发送给订阅者
        for (CloudEvent event : events) {
            dispatchToSubscriber(event, clientId);
        }

        // 5. 收到 ACK 后才更新 offset（可靠下发）
        //    （简化版：发送成功即更新 offset，不做重试和 ACK 确认）
        offsetStore.writeOffset(topic, clientId, partition, nextOffset);
    }

    // 消费者重连时，从 offset 恢复
    public void onClientReconnect(String clientId, String topic) {
        for (int partition = 0; partition < getPartitionCount(topic); partition++) {
            long offset = offsetStore.readOffset(topic, clientId, partition);
            if (offset >= 0) {
                // 从上次位置继续下发
                resendFromOffset(topic, clientId, partition, offset + 1);
            }
        }
    }
}
```

#### 12.6.5 Offset 管理的三种场景

```
场景 1：正常消费（LOAD_BALANCE）
  EventMesh offset 记录: "orders#worker-1#0" = 1042
  → worker-1 收到 orders topic 第 1043 条消息

场景 2：消费者重启（BROADCAST 恢复）
  worker-2 重启后，从 offsetStore 读取 "orders#worker-2#0" = 300
  → 从 MQ offset 300 重新拉取（replay）
  → 不会漏消息

场景 3：MULTICAST（每个 clientId 独立 offset）
  topic="events", clientId="order-service", partition=0 → offset=5000
  topic="events", clientId="payment-service", partition=0 → offset=3200
  topic="events", clientId="inventory-service", partition=0 → offset=8100
  → 三个 service 的消费进度完全独立，互不干扰
```

#### 12.6.6 与 MQ offset 的关系

```
EventMesh 管理的是 "订阅关系 offset"（谁收到了哪条）：
  topic#clientId#partition → eventMesh_offset
  · 一个 topic 可能有多个 clientId，每个 clientId 有自己的 offset
  · EventMesh offset 是"已下发确认"的位置，不等于 MQ 的消费位点

MQ 管理的是 "原始数据位点"：
  topic#partition → mq_offset
  · Storage.poll() 从指定 MQ offset 拉取
  · EventMesh 每次 poll(startOffset, maxEvents) 使用的是 MQ offset

EventMesh offset 和 MQ offset 的对应关系：
  EventMesh offset_store:
    "orders#worker-1#0" → 1042  (worker-1 已收到第1042条)
      ↓ 对应
  Storage.poll(topic, partition=0, startOffset=1043, maxEvents=100)
      ↓ 拉取
  MQ 返回 100 条消息（offset 1043~1142）
      ↓
  发送成功 → offset_store.update("orders#worker-1#0" = 1143)
```

#### 12.6.7 Offset 持久化策略

| 策略 | 说明 | 适用场景 |
|------|------|---------|
| **每条消息后写 RocksDB** | 最可靠，但性能差（~1ms/次） | 不推荐 |
| **批量异步写（每 100 条或每 1s）** | 平衡可靠性和性能 | **推荐（默认）** |
| **进程退出时 flush** | RocketMQ `LocalFileOffsetStore` 策略 | 不够可靠 |
| **幂等写（LSM 树批量合并）** | RocksDB WAL + MemTable flush 时合并写入 | **推荐（RocksDB 天然支持）** |

**最终选择**：RocksDB + 批量异步写 + 进程退出时强制 flush。MQ offset 用作"数据可用性"探测，EventMesh offset 用作"分发进度"追踪。

> **多实例补充（§13.2.4 / §15.8）**：RocksDB 是本地完整副本；集群真相源在 Meta。高频写 RocksDB，低频批量刷 Meta；启动/接管时 `readOffset = max(local, remote)`，以 Meta 远程为准（最新集群进度）。Meta 不可用时退化为纯本地 RocksDB。

---

## 十三、能力缺口与设计补充（v1.1）

> 前述章节（§1–§12）完成了"减法"设计：删 TCP/gRPC/OpenMessaging SDK、MQ 无语义化、单 Producer/单 Consumer、统一 HTTP+CloudEvents。但整体只覆盖了**快乐路径**（publish → 存储 → poll → 分发）。经与 develop 分支现有能力逐一对照，以下能力在生产落地前必须补齐设计，否则方案存在架构性 blocker。本节按严重程度排序给出缺口与具体设计补充。

### 13.1 缺口总览

| # | 能力 | develop 分支现状 | 本文档现状 | 严重程度 | 补充章节 |
|---|------|-----------------|-----------|---------|---------|
| 1 | 多实例消费协调 | 多 Consumer Group rebalance | **缺失（架构 blocker）** | 🔴 阻断 | §13.2 |
| 2 | Offset 集中存储 | RocketMQ broker 端 offset | 仅本地 RocksDB | 🔴 阻断 | §13.2.4 |
| 3 | 订阅关系集群同步 | ClientGroupPack（进程内） | 进程内 Map | 🔴 阻断 | §13.2.5 |
| 4 | 下发 ACK / 至少一次 | TCP SessionSender 重试 | §12.6.4 自述"不做 ACK" | 🔴 高 | §13.3.1 |
| 5 | 重试 + 死信队列 | eventmesh-retry + DLQ topic | 仅 Connector 侧一行 retry | 🔴 高 | §13.3.2 |
| 6 | 顺序消息（粘性会话） | 较弱 | RoundRobin 主动破坏顺序 | 🟠 中高 | §13.3.3 |
| 7 | 去重 / 幂等 | 无 | 无 | 🟠 中高 | §13.3.5 |
| 8 | TLS / mTLS | SslContextFactory + TlsConfig | HTTP 明文未提 HTTPS | 🔴 高 | §13.4.1 |
| 9 | 认证鉴权 | security-plugin(acl/token/basic) | Pipeline 列名零设计 | 🔴 高 | §13.4.2 |
| 10 | Metrics | metrics-prometheus(OTel) | 完全未提 | 🟠 中 | §13.5.1 |
| 11 | 分布式 Trace 传播 | trace-plugin(zipkin/jaeger/pinpoint) | 完全未提 | 🟠 中 | §13.5.2 |
| 12 | Admin 管理面 | admin v1 19 handler + admin-server | 误标"不变"，底层已删 | 🔴 高 | §13.5.4 |
| 13 | 限流 | Guava RateLimiter + meta 动态规则 | RateLimitFilter 列名零设计 | 🟠 中 | §13.6.1 |
| 14 | 背压 / 慢消费者隔离 | 无显式 | 广播 1000 订阅者无背压 | 🟠 中 | §13.6.2 |
| 15 | 优雅停机语义 | shutdown hook | 仅一句 ctx::shutdown | 🟡 中低 | §13.6.4 |
| 16 | 实时推送传输 | gRPC 流式 | Long-Polling 1 RTT 延迟 | 🟡 中低 | §13.7.1 |
| 17 | WebHook 主动推送 | WebhookPushRequest + connector | 纯 pull 无法主动 push | 🟠 中 | §13.7.2 |
| 18 | 批量发送 | BatchSendAsyncEventProcessor | send() 单条 | 🟡 中低 | §13.7.3 |
| 19 | topic/subject 语义 | — | §6.2 与 §12.5 自相矛盾 | 🟠 中 | §13.8.3 |
| 20 | 过滤表达式语法 | — | CloudEventFilter.match() 未定义 | 🟠 中 | §13.8.4 |

### 13.2 多实例消费协调（架构 blocker，最高优先级）

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：✅ **架构 blocker 已解除（issue #5293 第三 PR #5308 + issue #5309）。** §13.2.3 分区不重叠（Meta CAS + fencing）、§13.2.4 offset 两级存储、§13.2.8 FencingToken 均已实装。§13.2.5 实例间转发与 §13.2.9 降级时序尚未实装。多实例默认 = `LOCAL_STICKY_PULL`（单实例 poll-all）；选择 `PARTITION_OWNED_PULL` 启动 CAS 分配（完整原型可选开）。**双拓扑矩阵见 §13.2.10；E2E 验证见 §18.5；默认选型 = LOCAL_STICKY_PULL（向后兼容）。**

#### 13.2.1 问题

§3.2 规定"Storage Plugin 内部只有**一个** Consumer 实例（由 EventMesh Runtime 持有），全量拉取所有 topic 的消息"。但生产部署是多 Runtime 实例。若每个实例都 `consumer.assign(全部分区)` 全量拉取：

```
实例 A ──assign(p0,p1,p2)──→ 拉到 msg-100
实例 B ──assign(p0,p1,p2)──→ 拉到 msg-100   ← 重复
实例 C ──assign(p0,p1,p2)──→ 拉到 msg-100   ← 重复
```

→ **每条消息被 N 个实例重复拉取、重复分发**。方案无法横向扩展，且与"LOAD_BALANCE 每条只发一个订阅者"的语义直接冲突。

#### 13.2.2 方案选型

| 方案 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| A. 复用 MQ Consumer Group rebalance | EventMesh 仍用 MQ 的 Group 做 rebalance | 成熟、零开发 | **违反"MQ 无语义"铁律**，把 Group 又暴露回来，本重构作废 |
| B. Meta 主导分区分配 + 租约 | Meta（注册中心）给每个实例分配分区，实例持租约拉取 | 符合"EventMesh 自主"，复用 develop `eventmesh-meta`，强一致 + watch | Meta 成为协调强依赖，需 HA + 降级 |
| C. 实例自洽分配（Meta 协调） | 实例从 Meta 拿全量实例列表，按 `partition % N == myIndex` 自洽分配 | 无中心分配器，降级友好 | 无负载感知，实例增删需重新分配 |
| D. 全量拉取 + 下发去重 | 每实例全拉，靠 dedupId 去重 | 无协调 | N 倍 MQ 流量、N 倍 CPU，不可扩展 |

**决策（§15.5）：B（在线）+ C（降级）组合，控制面 = Meta 注册中心。** Meta 在线时主导分配（强一致 KV + watch 推送，负载感知）；Meta 不可用时降级为 C（实例自洽），保证 Runtime 仍可独立运行（延续"可降级部署"原则）。**不**用 Admin Server 承担协调职责（见 §13.2.7 边界）。

#### 13.2.3 推荐：Meta 主导分区分配 + 实例自洽降级

```
┌──────────────────────────────────────────────────────────────┐
│              Meta 注册中心（全局控制面，复用 eventmesh-meta）   │
│  · 分区分配表： topic#partition → ownerInstanceId            │
│  · 实例租约：    instanceId → { partitions, leaseExpireAt }  │
│  · 负载感知：    按实例订阅数/TPS 做均衡（可选，v2 增强）       │
│  强一致 KV + watch 推送（nacos/etcd/consul/zk/raft 任选）      │
└──────────────┬──────────────────────────────┬────────────────┘
               │ watch 分配变更                 │ 心跳续约
               ▼                                ▼
┌──────────────────────┐              ┌──────────────────────┐
│  Runtime 实例 A       │              │  Runtime 实例 B       │
│  持有 p0,p1 租约      │              │  持有 p2 租约          │
│  consumer.assign(p0,p1)│             │  consumer.assign(p2)  │
│  仅拉自己负责的分区    │              │  仅拉自己负责的分区    │
└──────────────────────┘              └──────────────────────┘

降级模式（Meta 不可用）：
  实例从最后缓存的全量实例列表 [A,B,C] 自洽分配
  → A: p0%3==0 → p0；B → p1；C → p2
  → Meta 恢复后重新对齐到权威分配表
  注：降级期间本地 RocksDB offset 仍可用，不丢进度
```

**Storage Plugin 接口需扩展**（§3.2 的 `poll` 之外）：

```java
public interface MeshStoragePlugin {
    // ... 原 send/poll/commitOffset ...

    /** 由 SubscriptionManager 告知本实例负责的分区（替代 consumer.assign 全量） */
    void assignPartitions(String topic, List<Integer> partitions);

    /** 按 MQ offset 范围拉取（供按 offset 精确 replay） */
    List<CloudEvent> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs);
}
```

#### 13.2.4 Offset 集中存储（升级 §12.6 的本地 OffsetStore）

§12.6 的 `EventMeshOffsetStore` 只存**本地 RocksDB**。多实例下：clientId 原连实例 A，A crash 后 clientId 重连到实例 B，B 的本地 RocksDB 没有该 clientId 的 offset → 从 earliest 重放或丢进度。

**升级为两级存储（远程层 = Meta）**：

```
EventMeshOffsetStore（两级）
├─ 本地 RocksDB（缓存 + 降级可用）    ← 写穿透第一步
└─ 远程 Meta KV（控制面，强一致）       ← 异步同步，启动时拉取

writeOffset(topic, clientId, partition, offset):
  1. rocksDB.put(key, offset)        // 立即本地落盘
  2. async → meta.put(key, offset)   // 批量异步（每 1s 或 100 条）

readOffset(topic, clientId, partition):   // 启动 / 接管时
  1. local = rocksDB.get(key)
  2. remote = meta.get(key)              // 以远程为准（最新集群进度）
  3. return max(local, remote)
```

**clientId 迁移**：实例 B 接管 clientId 时，从 Meta 远程读 offset 继续，不丢进度。本地 RocksDB 仅作降级缓存（Meta 不可用时仍能从本地恢复）。

#### 13.2.5 订阅关系集群同步

`subscribe()` 请求可能打到任意实例（HTTP 无亲和），但拉取该 topic 分区的可能是另一个实例。§4.3 的 `topicSubscriptions` 是**进程内 Map**，多实例不互通。

**升级为集群级订阅视图（存 Meta，控制面）**：

```
订阅关系存储（Meta KV）
  key:   topic
  value: Set<Subscription>{ subId, clientId, mode, filter, ownerInstance }

每个实例：
  · 本地缓存全量订阅视图（Meta watch 推送变更）
  · subscribe()/unsubscribe() 写 Meta + 广播失效

分发决策点（关键）：
  分区拥有者实例拉到消息
    → 查全局订阅视图 selectTargets(event, allSubs)
    → 对每个目标 clientId：
        · 查 "clientId 当前通道在哪个实例"（连接路由表，存 Meta）
        · 若在本实例 → 直接 push 到其 TransportChannel
        · 若在别的实例 → instance-to-instance HTTP 转发到目标实例
    → 目标实例收到转发 → 投入该 clientId 的 pendingEvents / TransportChannel
```

```
拉取实例 A (拥有 p0)          目标实例 B (clientId-X 的通道在此)
  msg → selectTargets           │
   → clientId-X 在实例 B ───────┼──→ HTTP POST /internal/forward
                                │    { clientId, event }
                                ▼
                              push 到 clientId-X 的 TransportChannel
```

> 这是本方案最复杂的一环。若 v1 不做多实例，可先单实例部署 + 预留接口；但**生产 HA 必须实现**，否则单点故障。

#### 13.2.6 负载均衡的全局性

§4.3 的 `roundRobinCounter` 是**单实例原子计数器**。多实例下"3 个 worker 分散在 3 个 Runtime"无法做到全局 RoundRobin（每个实例独立计数 → 倾斜）。

**决策**：
- LOAD_BALANCE 默认按 **partitionKey 粘性**（§13.3.3），天然全局一致（hash 不依赖实例）。
- 纯 RoundRobin 仅在"所有订阅者连同一实例"时精确；跨实例时退化为"实例内 RoundRobin + 实例间按分区天然分流"，可接受。

#### 13.2.7 Meta vs Admin Server 职责边界（控制面 vs 管理面）

文档多处出现 Meta 与 Admin Server，须厘清两者职责，避免把强一致协调塞进管理面：

| | Meta（注册中心 / 全局控制面） | Admin Server（管理面） |
|---|---|---|
| 性质 | 强一致 KV + watch 推送 | 独立进程，业务管理 |
| 实现 | 复用 develop `eventmesh-meta`（nacos/etcd/consul/zk/raft） | develop `eventmesh-admin-server` |
| 存什么 | 实例注册心跳、分区分配表、clientId 路由表、集群订阅视图、offset 远程副本、动态规则（限流/ACL/过滤） | Job 管理、DLQ 浏览重投、人工运维指令、指标聚合展示 |
| 谁读写 | Runtime 自动（控制路径，强一致） | 运维/管理员（管理路径） |
| 可用性 | 强（挂则协调停摆）→ HA + 降级（§13.2.3 C） | 中（挂则运维受影响，Runtime 可降级运行） |
| 与数据面关系 | 数据面协调的权威源 | 只读 Meta 视图做展示，不参与强一致协调 |

**原则**：控制面的活归 Meta；Admin Server 只做管理面（展示 + 运维指令），**不**承担分区/订阅/offset 的强一致存储。这样控制面强一致靠 Meta，管理面可独立演进，职责不互相耦合。

> **develop 现状梳理（v1.9 决策：弃用 Registry，只用 Meta）**：仓库存在 `eventmesh-meta`（MetaService SPI，5 后端）与 `eventmesh-registry`（仅 nacos，偏实例发现）两套注册抽象。**决定：弃用 `eventmesh-registry`，只用 `eventmesh-meta`（MetaService）作为唯一控制面**，实例发现能力并入 MetaService。不再保留两套并存的注册抽象。

#### 13.2.8 分区分配协议细节（租约 / 重均衡 / 防脑裂）

§13.2.3 给出"Meta 主导分配 + 实例自洽降级"的骨架，本节细化协议机制。核心约束：**MQ 无语义下（§15.1），所有 fencing 必须由 EventMesh 自己实现**——不能依赖 MQ 的 epoch/leader-generation。

**① 确定性分配算法（无中心分配器时的 v1 基线）**

```
输入：全量实例列表 [I0,I1,...,In-1]（Meta 维护，按 instanceId 稳定排序）
     全量分区 [P0,P1,...,Pm-1]

v1 算法（环形取模，确定性）：
  实例 Ii 负责分区 Pj  iff  (j + rebalanceOffset) % n == i
  → rebalanceOffset=0 时：P0→I0, P1→I1, ..., Pn→I0（环形）

特性：
  · 纯函数：相同实例列表 → 相同分配（所有实例算出一致结果）
  · 增量友好：增删 1 个实例，约 1/n 分区迁移（接近最小迁移）
  · 无负载感知：v1 不看实例负载，v2 由 leader 增强（见下）

谁来算：v1 每个实例自己算（读 Meta 的实例列表）；v2 由 leader 算后写入 Meta，其余实例读
```

**② 租约 = 心跳（不引入第三套机制）**

```
租约机制：
  实例定期（leaseRenewInterval，如 5s）向 Meta 更新自己的心跳 key：
    key: /em/instances/<instanceId>
    value: { lastHeartbeat, ownedPartitions:[...], gen }
    TTL: leaseTtl（如 15s，3 倍续约间隔）

租约过期：
  心跳 TTL 到期未续约 → Meta 视实例下线 → 从实例列表移除 → 触发重均衡
  → 与"心跳上报"复用同一通道，不为租约另起机制
```

**③ 重新均衡触发时机**

```
触发重均衡的事件（实例 watch Meta 感知）：
  · 实例上线（新实例注册）→ 分区可能迁给它
  · 实例下线（租约过期 / 主动注销）→ 其分区需重分配
  · 实例列表稳定排序变化（instanceId 变更，罕见）
  · v2：leader 主动再平衡（负载倾斜超阈值）

重均衡流程：
  1. 实例列表变更 → 所有实例重新计算分配（确定性算法，结果一致）
  2. 失去分区的实例：停止该分区 poll，但**不立即 commit offset**（见 ④ 迁移）
  3. 获得分区的实例：从 Meta 读该分区最新 offset，assignPartitions() 后开始 poll
  4. generation +1（fencing，见 ④）
```

**④ Offset 迁移与 fencing（防脑裂核心）**

最危险场景：实例 A 被判定下线、分区 P0 重新分配给实例 B，但 A 其实没死（网络分区），A 和 B **同时 poll P0** → 重复消费/offset 互相覆盖。

```
fencing 机制（EventMesh 自实现，MQ 无语义下必需）：
  generation（代次）：每次重均衡 +1，写入 Meta 分配表
    /em/assignments/<topic#partition> = { owner: <instanceId>, gen: <递增> }

  实例 poll 前 fencing 检查：
    poll 伪代码：
      assignment = meta.get("/em/assignments/" + topic + "#" + partition)
      if (assignment.gen != myGen || assignment.owner != myInstanceId) {
          // 我已不是该分区 owner，停止 poll（被 fencing）
          consumer.pause(partition); return;
      }
      consumer.poll(...);  // 否则正常拉取

  效果：A 网络分区后无法续租约 → gen 推进 → A 恢复时发现自己 gen 过期 → 自停
        B 拿到新 gen → 唯一 poller
  → 不依赖 MQ 的 epoch，纯 EventMesh 侧实现
```

> **注意**：fencing 只能"软 fencing"（poll 前检查），无法阻止 A 的 MQ client 在底层继续拉。配合 §13.3.5 去重（CloudEvents id 幂等），即使短暂双 poll，下发侧靠客户端幂等兜底。这是"MQ 无语义"铁律的必然代价——换来不绑 MQ rebalance。

**⑤ Offset 迁移的安全交接**

```
失去分区的实例（旧 owner）：
  · 停 poll → flush 本地 offset 到 Meta（最后一次 commit）
  · gen 推进前完成的 poll，其 offset 已 commit；之后不再 commit

获得分区的实例（新 owner）：
  · readOffset = max(local, meta)  // §13.2.4 两级存储
  · 从该 offset 继续 poll
  · 不需要"交接握手"——确定性算法 + Meta offset 真相源，新 owner 自己读进度即可

窗口期：gen 推进到新 owner 开始 poll 之间，可能短暂无人 poll（消息延迟，不丢）
       或双 poll（靠 ④ fencing + ⑤ 幂等收敛）
```

**⑥ v2 增强（leader 主导 + 负载感知，非 v1 必须）**

```
v1（基线）：无 leader，每实例确定性自算分配。简单、无单点，但无负载感知。
v2（增强）：
  · Meta 选一个实例作 assignment-leader（基于租约的最小 instanceId，或 Raft）
  · leader 周期性按实例负载（订阅数/TPS/积压）重算分配，写入 Meta
  · 其余实例读 Meta 分配表（不再自算）
  · leader 切换：租约失效 → 重新选主（Raft 模式天然容错）

何时上 v2：v1 在实例性能均质时够用；出现明显负载倾斜（如某实例订阅热点 topic）时再上
```

**⑦ 降级模式与一致性**

```
Meta 不可用（§13.2.3 降级）：
  · 实例用最后缓存的全量实例列表 + 确定性算法继续分配
  · gen 不再推进（无法 fencing 新故障）→ 退化为"尽力而为"
  · offset 仍读写本地 RocksDB（不丢进度）
  · Meta 恢复后：重算分配 + 推进 gen + 对齐 offset 远程副本
  → 降级期间可能短暂双 poll，靠幂等收敛；可接受（降级本就是异常态）
```

**小结**：协议 = 确定性分配 + 租约心跳 + gen fencing + 两级 offset + 幂等兜底。无 MQ 依赖、无中心分配器（v1）、可降级。复杂度集中在 fencing 与重均衡窗口，是"完全自主协调"的必然成本（§15.1 决策已接受）。

#### 13.2.9 降级模式端到端时序（Meta 不可用 → 自洽 → 恢复对齐）

§13.2.3 / §13.2.8 提及降级，本节给出端到端时序，明确各路径在 Meta 挂/恢复期间的行为。

**① Meta 不可用检测与进入降级**

```
T0  Meta 心跳/watch 失败（连续 K 次，如 3 次/5s）
T1  所有 Runtime 实例标记 Meta=DEGRADED
    ├─ 分区分配：冻结在最后缓存的全量实例列表 + 确定性算法（§13.2.8 ①）
    │   → gen 不再推进（无法 fencing 新故障）
    ├─ offset 写：仅写本地 RocksDB（远程层不可用）
    ├─ offset 读：仅读本地（max(local) 退化为 local）
    ├─ 订阅视图：冻结在最后缓存的全量视图（新 subscribe 仅本实例可见）
    ├─ ACL/限流规则：冻结在最后缓存
    └─ 实例间转发：仍可用（实例列表缓存仍在，能找到目标实例地址）
```

**② 降级期间各路径行为**

```
路径 A：publish
  SDK → POST /events/publish → IngressPipeline（用缓存 ACL，最终一致）
    → Storage.send(MQ/S3Stream)  ← MQ 不依赖 Meta，正常写
  → publish 正常（存储后端独立于 Meta）

路径 B：subscribe / unsubscribe
  subscribe 写集群级视图失败 → 仅写本实例进程内 Map
  → 本实例订阅仍工作；跨实例订阅不互通（新订阅者若 poll 到别的实例，收不到）
  → 影响：新订阅者在降级期间可能丢消息，恢复后对齐
  → 缓解：客户端 SDK 重试 subscribe + 恢复后重连

路径 C：下发（pollAndDispatch）
  分区拥有者实例拉 MQ → selectTargets（用缓存订阅视图）
    → 本实例订阅者：正常 push
    → 跨实例订阅者：实例间转发（缓存路由表仍可用）→ 正常 push
  → 已有订阅的下发基本正常（依赖缓存视图）

路径 D：offset 推进
  ACK → 写本地 RocksDB（不刷 Meta）
  → crash 恢复：读本地，不丢进度
  → 但 clientId 迁移到别的实例：新实例本地无该 offset → 从 earliest 重放或丢进度
  → 影响：降级期间实例 crash 导致 clientId 迁移时，可能重放（靠幂等收敛，§13.3.5）

路径 E：重均衡
  实例增删：无法感知（Meta 挂，租约/列表不更新）
  → 死实例的分区无人接管（gen 不推进）→ 该分区消息积压
  → 影响：降级期间实例故障 = 分区暂停（不丢，恢复后继续）
```

**③ Meta 恢复 → 对齐流程**

```
T2  Meta 恢复，watch 重连
T3  各实例标记 Meta=RECOVERING，开始对齐：
    ├─ 实例列表对齐：重新注册心跳 → Meta 重建全量列表
    ├─ offset 对齐：本地 RocksDB offset 批量刷 Meta
    │   → readOffset 回归 max(local, remote)
    ├─ 订阅视图对齐：本实例进程内 Map 的新订阅 → 重新写 Meta
    │   → 恢复集群级视图一致性
    ├─ 分配重算：全量列表就绪 → 确定性算法重算 → gen 推进
    │   → 死实例的分区重新分配给存活实例 → 接管拉取（从 Meta offset 继续）
    └─ ACL/规则：watch 推送最新规则，覆盖缓存
T4  对齐完成，标记 Meta=HEALTHY，退出降级

对齐期间的保护：
  · gen 推进时，旧 owner 自停（§13.2.8 ④ fencing）
  · 重放窗口靠幂等收敛（§13.3.5）
  · 对齐是渐进的，不阻塞正常 publish/下发
```

**④ 降级保证矩阵**

| 能力 | 降级期间 | 恢复后 |
|------|---------|--------|
| publish | ✅ 正常（MQ 独立于 Meta） | ✅ |
| 已有订阅下发 | ✅ 正常（缓存视图） | ✅ |
| 新订阅 | ⚠️ 仅本实例可见 | ✅ 对齐到集群 |
| offset 不丢 | ✅ 本地 RocksDB | ✅ 刷回 Meta |
| clientId 迁移 | ⚠️ 可能重放（幂等收敛） | ✅ |
| 实例故障接管 | ❌ 分区暂停（不丢） | ✅ 重分配接管 |
| ACL/限流 | ⚠️ 冻结最后规则 | ✅ 刷新 |

> **降级哲学**：Meta 挂时"尽力而为 + 不丢数据"，牺牲部分一致性（新订阅、故障接管）换取可用性；恢复后渐进对齐，幂等兜底收敛。这是 §15 "可降级部署"原则在多实例协调上的落实。

#### 13.2.10 统一投递拓扑：sticky 模型 + Meta CAS fencing（#5293 实装）

> **🔎 实现状态（v1.12 / 2026-08-19）**：✅ 已实现。删除跨实例转发路径与 `LOAD_BALANCE_STICKY` 模式；分区所有权改为 Meta CAS + `FencingToken`（替代 gen 数字）；心跳调度补齐（#5288）。含故障注入测试 `ClusterDeliveryFaultTest`（in-process 3-4 实例：稳态分配 / 宕机接管 / 扩容防搁浅 / Meta 分区脑裂防护 / 分区愈合）。

**① 投递拓扑统一为 sticky（删除转发路径）**

此前架构同时存在两条下发路径：分区 owner 实例拉取后**本地下发**，或**跨实例转发**给订阅者所在实例（`HttpForwarder` + `/internal/forward` 端点）。双路径导致：订阅漂移时序复杂、转发故障域大、`LOAD_BALANCE_STICKY` 语义与转发耦合。

**决定**：只保留 sticky 单路径——

```
删除：
  · HttpForwarder（整个类）+ UniHttpServer 的 /internal/forward、/internal/reply-forward 端点
  · EventMeshApplication 中转发相关 wiring
  · DistributionMode.LOAD_BALANCE_STICKY 枚举值（破坏性变更，模式合并）

模型：
  · 每实例只拉取自己 OWN 的分区（PartitionOwnership），本地下发给本实例订阅者
  · 订阅者通过 /events/subscribe 返回的 instanceUrl 固定（pin）到一个实例
    → SDK 的 poll/ack 永远落在同一实例，无跨实例转发需求
  · LOAD_BALANCE 吸收原 LOAD_BALANCE_STICKY 行为：事件带 partitionkey 属性时
    hash(partitionkey) 稳定路由到一个订阅者（保序），否则 round-robin
```

**② Meta CAS fencing：`tryAcquire` + `FencingToken`（替代 gen）**

§13.2.8 ④ 原设计用自增 gen 数字做 fencing，但旧实现的读写是 read-then-write（非原子）：两实例同时读到 `null` 会双双 `put`，后写者静默获胜——fencing 失效。

**实装**：

```
MetaStore 新增原子 CAS 接口：
  boolean tryAcquire(String key, String expectedOldValue, String newValue)
  · expectedOldValue == null → 键必须不存在（首claim）
  · 实现：Nacos 2.x publishConfigCas(dataId, group, content, casMd5)
          casMd5 = MD5(expectedOldValue == null ? "" : expectedOldValue)
          InMemoryMetaStore → ConcurrentHashMap.replace(key, old, new) / putIfAbsent

FencingToken（每 JVM 一个，单调递增）：
  · 格式 "<bootEpoch>:<counter>"，bootEpoch = 启动毫秒时间戳，counter 原子自增
  · 排序：先比 bootEpoch（旧 JVM 永远输），同 epoch 比 counter
  · 存活于 Meta：/em/assignments/<topic#partition> = "<token>|<ownerInstanceId>"

acquireOrFence 协议（PartitionOwnership）：
  Case 1 键不存在（或为释放墓碑 ""）→ tryAcquire(currentValue → myToken|self)；CAS 失败 = 输了竞争，下轮再读
  Case 2 owner 是自己 → 同步本地 token，继续持有
  Case 3 owner 是别人 → 接管条件（满足其一即 tryAcquire(currentValue → myToken|self)）：
        · owner 已被 TTL 驱逐（不在 live set）→ 强制接管
          （仍轮询的僵尸实例必然已心跳失败、leaseValid=false 停止轮询，强制接管安全）
        · myToken > metaToken（CAS fencing）
        否则自己被 fence，停止 poll 该分区

释放路径 releaseStale（成员变更防搁浅）：
  · 分区离开本实例的 assigner 份额（扩缩容改变取模映射）而 Meta 记录仍指向自己
    → CAS 到释放墓碑 ""（仅当记录仍指向自己，不会破坏并发接管）
  · 新 rightful owner 下轮以 Case 1 认领；否则旧 owner 的较高 token 会把新 owner
    永久 fence（分区搁浅，无人拉取）
  · 墓碑 "" 与键不存在在 CAS 语义上等价（Nacos casMd5 = MD5("") 双向兼容）
```

**③ 心跳调度补齐（#5288 修复）**

`ClusterMembership.heartbeat()` 此前从未被调度执行（`EventMeshApplication` 没有任何调用点），导致 `/session/recommend` 永远看不到本实例、TTL 永远过期。实装：`enableCluster` 中以 5s 周期调度心跳，shutdown 时随分区租约一并释放（§13.6.4 step 5 / G12）。

**④ 与 §13.2.8 原设计的差异**

| 原设计 | 实装 | 原因 |
|--------|------|------|
| gen 数字（metaGen+1 覆盖） | FencingToken（bootEpoch:counter）+ CAS | gen 覆盖是 read-then-write 非原子；token 排序天然单调且跨重启有效 |
| 心跳 value 含 ownedPartitions+gen | 心跳 value = `<ts>\|<addr>\|<load>` | 分配表已在 /em/assignments/*，心跳只承担租约+负载上报 |
| 实例间转发保订阅可达 | sticky：instanceUrl 固定订阅者 | 转发路径故障域大、时序复杂，删除（见 ①） |
| LOAD_BALANCE_STICKY 独立模式 | 合并入 LOAD_BALANCE（partitionkey 路由） | sticky 成为唯一拓扑后无需独立模式 |

#### 13.2.10 双拓扑矩阵：LOCAL_STICKY_PULL vs PARTITION_OWNED_PULL（issue #5309, PR #5309-full）

§13.2.3 中的 Meta CAS 分配原型在默认场景不一定启动：单实例部署仅为一个 pull-loop，Meta 依赖是个负担。`DeliveryTopology` 枚举提供了两种合法选型，选择通过 `eventmesh.delivery.topology` 进行（如不设置默认为 `LOCAL_STICKY_PULL`）：

| 维度 | LOCAL_STICKY_PULL（默认，向后兼容） | PARTITION_OWNED_PULL（启动多实例 scale-out） |
|---|---|---|
| **适用场景** | 单实例 / 低吞吐量 / 本地开发 | 生产 HA / 多实例水平扩展 |
| **MQ 分区负载** | 每实例 poll 全分区（partition = -1） | 每实例仅 poll 自己 owner 的分区（CAS 分配） |
| **Meta 依赖** | 无（本地仅为单实例 poll-loop） | 必须（Nacos / etcd / ZK / raft），降级为本地仅自己看到 |
| **实例增减** | 重复消费（需上层 LB 调度） | 自动重平衡（FencingToken 领先者接管） |
| **启动开销** | 零（仅 `start()` 中 offset 对齐） | 启动 `ClusterMembership` + `PartitionOwnership` + heartbeat 调度（`startPartitionOwnership()`） |
| **故障调试难度** | 低（不涉及 Meta） | 高（CAS 并发 / fence / 资源标记轮转） |
| **代码进入点** | `UniRuntime` 6-arg 构造（不变） | `UniRuntime` 9-arg 构造 + `withClusterMeta(MetaStore)` 注入 Nacos/etcd 实例 |
| **E2E 验证** | §18.5 原有 in-process 场景（未启动 ownership） | `UniRuntimeTopologyWiringTest` 启动全生命周期 E2E + `ClusterDeliveryFaultTest` 5 个 CAS/fence 场景 |
| **适用 §§** | 为默认 —— 所有无 cluster 需求的环境都不受影响 | 生产上线前需 `withClusterMeta()` 注入指定 Meta 后端 |

**选型语义（DeliveryTopology.fromConfig）**

- `eventmesh.delivery.topology` 为 null / 空 → `LOCAL_STICKY_PULL`（向后兼容）
- 为 `LOCAL_STICKY_PULL` / `PARTITION_OWNED_PULL` → 解析为对应枚举
- 为任何其他值 → `IllegalArgumentException`（fail-fast，不允许错误字面重赋定义为单实例）

**`PARTITION_OWNED_PULL` 启动流程（UniRuntime.start 中）**

```
1. 原有：storage.init → storage.start → alignPullOffsetsToAck → 调度 pull/tick/cleanup
2. 新增：若 topology == PARTITION_OWNED_PULL，调用 startPartitionOwnership()：
     • clusterMeta == null → new InMemoryMetaStore()（本地默认，生产请调用者使用 withClusterMeta() 注入主云 Meta 实例）
     • new FencingToken() · new ClusterMembership(meta, instanceId, instanceAddress, 30s TTL, clock, token)
     • new PartitionOwnership(membership, meta, storage, instanceId, 5s, clock, token)
     • ownership.start(ingress::activeTopicsClustered)
     • ingress.withPartitionOwnership(ownership)
3. shutdown() 调用 stopPartitionOwnership() 释放 Meta 分配记录
```

**与 §13.2.8 原设计的差异（实装时的补充决定）**

| 原设计 | 本次实装（PR #5309-full） | 原因 |
|---|---|---|
| 所有实例默认启动 ownership | 两种拓扑并存，默认 LOCAL_STICKY_PULL（单实例完全不启动 Meta） | 单实例部署不应牵走 Meta 依赖 |
| `EventMeshApplication.enableCluster()` 启动 | `UniRuntime` 中 `topology == PARTITION_OWNED_PULL` 启动（PR #5308 中已重构） | 与 uni runtime lifecycle 合一，不再对外暴露 cluster enable 按钮 |
| Ownership 仅在 PARTITION_OWNED_PULL 启动 | 同 | 代码中通过 `if (topology == PARTITION_OWNED_PULL) startPartitionOwnership()` 限定 |
| 默认 instanceId = 主机名 | 默认 = `"standalone"`（LOCAL_STICKY_PULL）；PARTITION_OWNED_PULL 下调用者必须传入唯一 ID | 唯一性是 cluster 调度的前提，不能为空 |

**验证覆盖（PR #5309-full 包含）**

- Unit test: `DeliveryTopologyTest`（7 个 case）—— null / empty / 空格 / 精确名 / trim / 大小写 / 未知值的完整视图
- E2E wiring: `UniRuntimeTopologyWiringTest`（3 个 case）—— 两实例 PARTITION_OWNED_PULL 收敛于全覆盖不重复、LOCAL_STICKY_PULL 从不分配分区、null topology fail-fast
- Fault injection（现有 `ClusterDeliveryFaultTest` 担任）: 5 个场景 —— 稳态分割 / crash 接管 / membership churn / Meta 分区暂停调用 / 分区恢复后重夺
- Testcontainers（Kafka + Nacos）E2E 由 §13.2.3.1 follow-up 账号负责（本 PR 不含 Docker 依赖）

---

### 13.3 下发可靠性与消息语义

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：⚠️ §13.3.1 ACK（offset 仅 ACK 推进）、§13.3.2 重试+DLQ（指数退避+`<topic>.DLQ`）、§13.3.5 去重声明、§13.3.6 不支持事务——均已实现（`ReliableDispatcher`）。**缺口**：§13.3.2 退避无 jitter（G13）；§13.3.3 STICKY 单实例✅但多实例退化为 RoundRobin（G8）；§13.3.4 TTL 过期丢弃未实现（附录 F.5）。

§12.6.4 自述"简化版：发送成功即更新 offset，不做重试和 ACK 确认"——这意味着 **HTTP 下发失败即丢消息**。消息总线的基本契约必须补齐。

#### 13.3.1 ACK 机制（至少一次）

Long-Polling 下发是单向的（poll 响应里塞消息），客户端是否收到/处理完未知。需显式 ACK：

```
SDK:
  poll() 返回一批消息 + lastOffset
  处理完成后 → POST /events/ack { subId, clientId, topic, partition, offset }

Runtime:
  offset 仅在 ACK 后推进（at-least-once）
  ACK 超时（ackTimeout，如 30s）未收到 → 视为下发失败 → 重投

接口：
  POST /events/ack
  body: { "subId":"...", "clientId":"...", "topic":"orders",
          "partition":0, "offset":1143 }
  resp: 200 OK
```

```
下发 → 等 ACK
  ├─ ACK 到达 → offsetStore.writeOffset(offset)     ✅ 推进
  ├─ ACK 超时 → 重投（同消息同 dedupId）             🔁
  └─ 重投超 maxRetries → 转 DLQ                      ☠️
```

#### 13.3.2 重试与死信队列

> **v1.9 决策：retry 内置，不暴露 SPI 插件扩展。** uni runtime 的重试由内置 `ReliableDispatcher`（指数退避 + DLQ）承担，**不复用/不接线** `eventmesh-retry` 模块（及其 SPI 插件）。重试策略不可插拔（固定为指数退避），无需 SPI 扩展点。

内置 `ReliableDispatcher` + DLQ topic 机制：

```
下发失败/ACK 超时
  → ReliableDispatcher 调度重投（指数退避：1s/2s/4s/8s/16s）
  → 重投
  → 超过 maxAttempts（默认 6 = 初次 + 5 次重试）
  → 转 DLQ topic: "<原topic>.DLQ"
  → DLQ 可被独立订阅（人工/自动消费）+ 告警

DLQ 消息携带：
  · 原始 CloudEvent（含 id）
  · 失败原因、重试次数、首次失败时间
  · 死信 CloudEvent extension: emdlqreason, emdlqretrycount（无连字符，CloudEvents 命名规则）
```

**重试器实现（`ReliableDispatcher`，内置）：**

```
ReliableDispatcher（单实例，pending 表 + tick 调度）：
  · pending: ConcurrentHashMap<deliveryId, Delivery>，Delivery 记 attempt + nextAttemptAt
  · tick() 周期推进（由 UniRuntime 调度，或接 HashedWheelTimer）：扫过期 Delivery
  · 超时/nack → 重投；attempt >= maxAttempts → DLQ
  · offset 仅在 ack() 推进（§13.3.1）

退避（固定指数，不可插拔）：
  · 1s,2s,4s,8s,16s（2^n，封顶 16s）
  · （jitter 可后续加，防重试风暴同步）

重试流程时序：
  T0  下发 msg-1 到 clientId-X，记 pending {deliveryId, attempt=1, nextAttemptAt=T0+ackTimeout}
  T1  ACK 超时（ackTimeout 未收到）→ tick 命中 → 重投，attempt=2，nextAttemptAt=T+退避
  T2  重投成功（ACK 到达）→ ack() 推进 offset，pending 移除
  T3  重投又超时 → tick 再重投，attempt++ ... 直到 attempt=maxAttempts
  T4  attempt>=maxAttempts → tick 命中转 DLQ（写 <topic>.DLQ），pending 移除，记 metric

关键：offset 只在 ACK 成功后推进（§13.3.1）。
      重投不推进 offset，故 MQ 侧消息不移除——重投的是"同一消息再次下发"
```

**并发与持久化：**

```
并发：
  · Retryer 进程内单例，RetryTask 不可变（msgId+retryCount+nextRetryAt）
  · 重投通过 PushService.push（线程安全）
  · 同 msgId 的多次失败：仅一个 RetryTask 在轮（去重入队，按 msgId）

持久化（crash 恢复）：
  · Retryer 是内存时间轮，进程 crash 则重试任务丢失
  · 恢复策略：crash 后 offset 未推进的消息，由 SubscriptionManager 重新 poll + 下发
    （因为 offset 没推进，MQ 侧消息还在，重启后自然重拉重发）
  → 不持久化 Retryer，靠"offset 不超前"保证不丢（crash 后重放，幂等收敛）
  → 代价：crash 后已重试 N 次的计数丢失，重新从 0 计；可接受（重试计数非关键状态）

与 DLQ 的关系：
  · DLQ 是持久化 topic（写 MQ），非内存
  · 转 DLQ 后 offset 推进（视为"已处理"，不再重投）
  · DLQ 消息可被独立订阅消费/重投（Admin 面 §13.5.4 replay）
```

**配置项：**

```properties
eventmesh.retry.maxRetries=5                  # 最大重试次数
eventmesh.retry.backoff=EXPONENTIAL           # EXPONENTIAL/FIXED/LINEAR
eventmesh.retry.initialDelayMs=1000           # 初始退避
eventmesh.retry.maxDelayMs=60000              # 退避封顶
eventmesh.retry.jitterRatio=0.2               # ±20% jitter
eventmesh.retry.ackTimeoutMs=30000            # ACK 超时
eventmesh.dlq.topicSuffix=.DLQ                # 死信 topic 后缀
```

#### 13.3.3 顺序消息（粘性会话）

§4.2.1 的 RoundRobin 会**主动破坏分区顺序**（同 key 消息轮询到不同 worker）。补 STICKY 子模式：

```
DistributionMode:
  LOAD_BALANCE         // RoundRobin（默认，无序）
  LOAD_BALANCE_STICKY  // 粘性：按 partitionKey 绑定 clientId
  BROADCAST
  MULTICAST

STICKY 路由：
  partitionKey = event.getExtension("partitionkey")  // CloudEvents 规范字段
  target = subscribers[ hash(partitionKey) % subscribers.size() ]
  → 同 key 永远发同一 worker → 保序

适用：订单状态机（同一订单需顺序处理）、事件溯源
```

> CloudEvents 1.0 规范已定义 `partitionkey` extension，直接复用。

#### 13.3.4 延迟 / 定时消息

develop 仅 TTL（消息存活时间，过期丢弃），无定时投递。

**决策（v1）**：
- **保留 TTL**：CloudEvents `time` + `x-em-ttl`，过期消息不下发、直接丢弃。
- **不支持定时投递**（指定时刻才下发）：v1 不实现。如需，后续独立延迟队列 + 时间轮触发，列入 roadmap。
- 文档明确声明，避免误用。

#### 13.3.5 去重与幂等

单 Consumer 全量拉 + ACK 超时重投 = **必然重复下发**。

**决策**：
- CloudEvents `id` 作为 **dedupId**，重投时 `id` 不变。
- EventMesh 侧**不做全局去重**（成本高、需状态存储），靠 **at-least-once + 客户端幂等**。
- 客户端按 `id` 去重（业务侧幂等表/本地去重窗口）。
- 文档明确：**交付语义 = 至少一次**，不承诺恰好一次（Exactly-Once 由业务幂等保证）。

#### 13.3.6 事务消息（明确不支持）

develop 无事务消息。本重构删除 `createTransactionProducer`。

**决策**：明确声明**不支持事务消息/半消息/事务回查**。需要事务语义的业务用 saga、本地消息表、Outbox 模式实现。

### 13.4 安全能力（完全缺失，develop 全有）

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：⚠️ §13.4.1 TLS 有 `TlsContextFactory`（真 SSLContext + mTLS）但 boot 默认不接线 + 硬编码 TLSv1.2 + truststore 密码不独立（G14）；§13.4.2 `FilterChain`+`TokenAuthFilter`+`SignatureVerifierFilter` 实现，**但 `AclFilter` 是静态 map 骨架、非 §13.4.2 规则模型（G9）**；§13.4.3 租户隔离只剩 topic 字符串匹配（SubscriptionManager 无 tenant 过滤）；§13.4.4 签名验签✅。

#### 13.4.1 TLS / mTLS

文档 HTTP-only 却未提 HTTPS，Long-Polling 明文传输。复用 develop 的 `SslContextFactory` + `EventMeshTlsConfig`：

```properties
# 新增配置
eventmesh.server.tls.mode=ENFORCING        # DISABLED / PERMISSIVE / ENFORCING
eventmesh.server.tls.ssl.protocol=TLSv1.3
eventmesh.server.tls.keystore.path=...
eventmesh.server.tls.keystore.password=...
# mTLS 双向认证
eventmesh.server.tls.client.auth=REQUIRE   # NONE / OPTIONAL / REQUIRE
eventmesh.server.tls.truststore.path=...
```

- Long-Polling 走 HTTPS。
- PERMISSIVE 模式支持平滑迁移（明文+密文共存）。

#### 13.4.2 认证与鉴权

§2.1 Pipeline 列了 `AuthFilter` / `AclFilter` 名字却零设计。补：

> **v1.9 决策：security 不用 SPI 插件扩展，用 filter 链扩展。** uni runtime 内置 `FilterChain` + `IngressFilter` 接口（`org.apache.eventmesh.runtime.uni.security`）。认证/鉴权/签名各是一个 filter（`TokenAuthFilter` / `AclFilter` / `SignatureVerifierFilter`），**不复用/不接线** `eventmesh-security-plugin`（及其 SPI 插件 jar）。扩展安全能力 = 新增一个 `IngressFilter` 实现（代码扩展，配置进 FilterChain），而非打 SPI 插件包。

```
AuthFilter（认证：你是谁）—— 实现 IngressFilter
  · 内置 TokenAuthFilter（token）；扩展认证方式 = 新增 IngressFilter（不装 SPI 插件）
  · SDK builder 加 .credential(token/username+password)
  · 每请求 Authorization 头校验
  · 失败 → 401

AclFilter（鉴权：你能做什么）—— 实现 IngressFilter
  · topic 粒度权限：publish / subscribe
  · 权限上下文：CloudEvents extension emuserid / emtenantid（无连字符，CloudEvents 命名规则）
  · 规则可经 MetaService 动态下发
  · 失败 → 403
```

**ACL 规则模型细化：**

```
规则结构（每条 ACL 规则）：
  {
    principal:  "<tenantId>.<userId>" | "<tenantId>.*" | "*",   // 主体（支持通配）
    resource:   "<tenantId>.<topic>" | "<tenantId>.*" | "*",    // 客体 topic（含租户前缀，§13.4.3）
    action:     PUBLISH | SUBSCRIBE | REQUEST | "*",            // 操作
    effect:     ALLOW | DENY,                                   // 效果
    priority:   <int>                                           // 匹配优先级（高优先）
  }

匹配算法（按 priority 降序遍历，首条匹配生效）：
  1. 规则按 priority 降序排列（DENY 优先原则：同 priority 时 DENY 先匹配）
  2. 对请求 (principal, resource, action) 遍历：
     principal 匹配（精确 > tenantId.* > *）AND
     resource 匹配（精确 > tenantId.* > *）AND
     action 匹配（精确 > *）
     → 首条命中规则的 effect 生效
  3. 无任何规则命中 → 默认 DENY（白名单模型）

示例规则集：
  priority=100  DENY  principal=tenantB.*   resource=tenantA.*  action=*      // 跨租户禁止
  priority=50   ALLOW principal=tenantA.*   resource=tenantA.*  action=*      // 租内全允许
  priority=10   ALLOW principal=tenantA.svc1 resource=tenantA.orders action=PUBLISH
  → tenantB 用户访问 tenantA.orders：命中 priority=100 DENY → 403
  → tenantA.svc1 发布 tenantA.orders：命中 priority=50 ALLOW（10 也允许，但 50 先匹配）
```

**规则在 Meta 的存储与下发：**

```
Meta 存储：
  key:   /em/acl/rules
  value: List<Rule>（JSON 数组，按 priority 排序）
  watch: 所有 Runtime 实例 watch 此 key

下发与生效：
  · 规则变更（Admin/运维写 Meta）→ watch 推送 → 全实例更新本地规则缓存
  · AuthFilter 认证后，AclFilter 用本地缓存做匹配（无 Meta 查询，热路径零 RTT）
  · 规则缓存最终一致（watch 推送有 ms 级延迟，可接受）

租户隔离联动（§13.4.3）：
  · resource 带 tenantId 前缀，ACL 天然按租户隔离
  · 跨租户访问由 priority 最高的 DENY 规则拦截
  · SubscriptionManager 按 tenantId 过滤订阅视图（看不到别租户订阅）
```

#### 13.4.3 租户隔离

- topic 命名空间：`<tenantId>.<topic>`（如 `tenantA.orders`）。
- AclFilter 按 tenant 隔离：租户 A 看不到租户 B 的订阅关系与消息。
- 多租户共享集群，SubscriptionManager 按 tenant 过滤订阅视图。

#### 13.4.4 CloudEvents 签名

借鉴 A2A 的 `AgentCardSignature`。可信来源校验：

```
CloudEvents extension: x-em-signature = HMAC-SHA256(secret, canonical(event))
  · canonical = source + type + id + time + data 的规范化拼接
  · 接收方（Runtime / 订阅者）用预共享密钥验签
  · 防篡改 + 来源可信
```

### 13.5 可观测性（完全缺失，develop 全有）

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：⚠️ §13.5.1 metrics 8/16 项核心已实现（OTel 仪表）；§13.5.2 trace 关键节点 Span 已埋（`UniTrace`，publish/dispatch/ack/retry/dlq），**但 traceparent/tracestate/baggage 全链路显式透传未做**；§13.5.4 Admin 仅 ~5/8 接口且本地视图（G15）。legacy metrics/trace 插件确未接线（符合 v1.9 决策）。

#### 13.5.1 Metrics

**只用 OpenTelemetry**（Meter API，`org.apache.eventmesh.runtime.uni.metrics.UniMetrics`）。**不**用 `eventmesh-metrics-prometheus`（legacy，不接线）。指标定义（名称 / 类型 / 标签 / 含义）：

> 注：下表为目标全集；当前实现已落地核心 8 项（publish/publish_failed/rate_limited/dispatched/dispatch_latency/ack/redeliveries/dlq），其余（带 *）随相应 Phase 补齐。

| 指标 | 类型 | 标签 | 含义 |
|------|------|------|------|
| `eventmesh_publish_count` | Counter | topic, tenant | 入方向 publish 数 |
| `eventmesh_publish_failed_count` | Counter | topic, tenant | publish 失败数 |
| `eventmesh_rate_limited_count` | Counter | topic | 被限流拒绝的 publish 数 |
| `eventmesh_dispatched_count` | Counter | topic, mode(LB/Bcast/Mcast), tenant | 下发消息数 |
| `eventmesh_dispatch_latency_nanos` | Histogram | topic | selectTargets + push 延迟 |
| `eventmesh_ack_count` | Counter | topic, clientId | ACK 数 |
| `eventmesh_redeliveries_count` | Counter | topic, clientId | 重投数 |
| `eventmesh_dlq_count` | Counter | topic, reason | 死信数 |
| `eventmesh_poll_idle_ratio` * | Gauge | topic | poll 空闲比例（无消息） |
| `eventmesh_active_subscribers` * | Gauge | topic, tenant | 活跃订阅者数 |
| `eventmesh_offset_lag` * | Gauge | topic, partition | 分发 offset 落后 MQ offset |
| `eventmesh_pending_queue_size` * | Gauge | topic, clientId | 每 clientId 背压队列水位 |
| `eventmesh_slow_consumer_count` * | Gauge | topic, state(SLOW/STALLED) | 慢消费者数（§13.6.2） |
| `eventmesh_request_reply_count` * | Counter | topic, outcome(ok/timeout) | request-reply 调用数 |
| `eventmesh_partition_owner` * | Gauge | topic, partition, instance | 分区分配视图（§13.2.8） |

> 标签设计：topic / tenant / mode 为核心维度，便于按租户/topic 切片。所有指标走 OTel API，**默认经 OTel Prometheus exporter 暴露 `/metrics`**（部署侧可换 OTLP 等），不经 legacy `metrics-prometheus` 插件、无 SPI 扩展点。

#### 13.5.2 分布式 Trace 传播

> **v1.11 实现**: `UniTrace` (org.apache.eventmesh.runtime.metrics.UniTrace) 已在 publish/dispatch/ack/retry/dlq 关键节点埋 OTel Span。CloudEvent id/type 作为 span 属性, 链路: publish → dispatch → ack (+ retry/dlq 分支)。

全链路 CloudEvents，trace context 用 **W3C `traceparent` + `tracestate` + `baggage`**（CloudEvents Distributed Tracing extension）：

```
CloudEvent extensions（trace 透传字段）：
  traceparent: 00-{traceId}-{spanId}-{flags}     // W3C Trace Context，必传
  tracestate:  vendor1=value,vendor2=value       // 厂商扩展（可选）
  baggage:     k1=v1,k2=v2                       // 跨服务业务上下文（如 tenantId、userId）

传播规则：
  · 请求方生成 traceparent（或继承上游），写入 CloudEvent extension
  · 每跳（Runtime 内部节点）继承 traceId，生成新 spanId
  · 跨实例 forward、跨 Connector、SDK ↔ Runtime 均透传 traceparent + baggage
  · baggage 用于透传 tenantId/userId（与 ACL 的 x-em-tenantid 对齐）
```

**关键节点 Span 设计：**

```
Span 链路（一条 publish → 下发的完整 trace）：
  [span] sdk.publish          (SDK 侧, parent=traceparent)
    └─ [span] ingress.pipeline (Runtime, AuthFilter→AclFilter→Transformer→Router)
         └─ [span] storage.send (Runtime, 写 MQ)
              └─ [span] dispatch.pollAndDispatch (Runtime, 拉取+selectTargets)
                   ├─ [span] push.toClient (Runtime, TransportChannel.send)
                   │    └─ [span] client.ack (SDK 侧)
                   └─ [span] forward.crossInstance (若跨实例转发, §13.2.5)

异常分支 Span：
  · retry：dispatch.retry（记录重试次数、退避时长）
  · dlq：dispatch.dlq（记录死信原因）
  · request-reply：request.send → reply.route → reply.deliver（§17，独立 trace）

Span 属性（attributes）：
  · topic, tenantId, mode, clientId, partition, offset, cloudEventId
  · error（失败时）, retryCount
```

用 **OpenTelemetry Tracer** 创建 Span，经 OTel exporter（OTLP 等）导出。**不**用 `eventmesh-trace-plugin`(zipkin/jaeger/pinpoint)（legacy，不接线）。按 CloudEvents `id` 关联同一消息的所有 span，形成全链路轨迹。

#### 13.5.3 消息轨迹

用 **OpenTelemetry Span**（同 §13.5.2），在关键节点埋点：publish / 入 MQ / dispatch / push / ack / retry / dlq。支持按 CloudEvents `id` 查询某条消息的全链路轨迹。不再依赖 `eventmesh-trace-plugin`。

#### 13.5.4 Admin 管理面重做（文档误标"不变"）

§13（旧）称 Admin "不变"，但旧 admin v1 的 19 个 handler 大量依赖 **TCP session / Consumer Group**（如 `ShowListenClientByTopicHandler`、`RedirectGroupBatchHandler`、`RejectClientByIpPortHandler`），底层已删 → **Admin 必须重做**。

新 Admin 面（基于 SubscriptionManager + OffsetStore 集群级视图，经 Admin Server）：

| 接口 | 功能 |
|------|------|
| `GET /admin/subscriptions` | 查询集群订阅关系（按 topic/tenant） |
| `GET /admin/offsets` | 查询 offset lag（按 topic/clientId） |
| `GET /admin/clients` | 查询在线客户端 + 所属实例 |
| `POST /admin/clients/{id}/reject` | 踢客户端（清除其订阅） |
| `GET /admin/dlq/{topic}` | 浏览死信 |
| `POST /admin/dlq/{topic}/replay` | 死信重投 |
| `PUT /admin/ratelimit/rules` | 下发限流规则 |
| `GET /admin/health` | 实例健康/分区分配视图 |

### 13.6 运维与稳定性

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：⚠️ §13.6.1 限流（`TokenBucketRateLimiter` per-topic，但非 Meta 动态下发）、§13.6.2 背压慢消费者四态状态机（有缺陷 G11）、§13.6.4 优雅停机（drain+等ACK+flush，缺租约释放 G12）已实现；**§13.6.3 动态配置热更新、§13.6.5 僵尸 poll 清理/连接上限未实现**。

#### 13.6.1 限流

§2.1 的 `RateLimitFilter` 实化。复用 develop 的 Guava `RateLimiter` + `RateLimiterRulerListener`（meta 动态规则）：

```
RateLimitFilter（Ingress）：
  · per topic / per clientId 限流
  · 规则经 MetaService 动态下发（RateLimiterRulerListener）
  · 超限 → 429 Too Many Requests

下发侧限流（新增，develop 仅 TCP 侧有）：
  · per clientId 下发速率上限，防慢消费者拖垮
  · 超限 → 消息暂存 pendingEvents，不丢
```

#### 13.6.2 背压与慢消费者隔离

§12.4 广播 1000 订阅者场景，慢消费者会拖垮线程池。本节细化背压机制。

**① 每 clientId 有界队列 + 溢出策略矩阵**

```
每 clientId 一个独立有界队列（pendingEvents），与分发模式解耦：
  · 容量 maxPending（默认 10000，可按 topic/clientId 配）
  · 入队：分发到该 clientId 但其通道未连接 / 来不及消费时
  · 出队：通道连接时 flush（§7.2 registerChannel）

> **v1.11 实现**: `PushService` 已实现慢消费者四态状态机 (§13.6.2): HEALTHY → SLOW (buffer≥80%) → STALLED (连续3次slow) → EVICTED (连续10次stalled → 自动 unsubscribe)。`getClientState(clientId)` 供 admin 查询。

溢出策略（按订阅属性选，非全局一刀切）：
  ┌──────────────┬──────────────────────────────┬─────────────────────┐
  │ 策略         │ 行为                          │ 适用                │
  ├──────────────┼──────────────────────────────┼─────────────────────┤
  │ DROP_OLDEST  │ 丢最旧消息 + 记 metric        │ 实时流（行情/状态）  │
  │ DROP_NEWEST  │ 丢新消息 + 记 metric          │ 保历史完整性场景     │
  │ BLOCK        │ 暂停对该 clientId 分发         │ 不可丢（配合超时踢） │
  │ TO_DLQ       │ 溢出消息转 DLQ                │ 关键业务消息        │
  └──────────────┴──────────────────────────────┴─────────────────────┘
  默认 DROP_OLDEST + metric；TO_DLQ 复用 §13.3.2 重试/DLQ 通路
```

**② 慢消费者检测算法（基于 ACK 速率 + 队列水位）**

```
慢消费者判定指标（每 clientId 周期采样，sampleInterval=10s）：
  · ackRate：单位时间 ACK 数（吞吐）
  · queueLag：当前 pendingEvents 水位
  · pollInterval：最近两次 poll 间隔
  · ackLatency：消息入队到 ACK 的延迟

慢消费者状态机：
  HEALTHY  ──queueLag > highWatermark(80%) 持续 2 周期──→  SLOW
  SLOW     ──queueLag < lowWatermark(50%) 持续 3 周期──→  HEALTHY
  SLOW     ──连续 N(=5) 周期仍 SLOW ──────────────────→  STALLED
  STALLED  ──连续 M(=12) 周期无 ACK ──────────────────→  EVICTED

各级动作：
  SLOW     ：降配额（分发速率上限砍半）+ 告警 metric
  STALLED  ：暂停分发（不再入队，已有队列按溢出策略处理）+ 告警
  EVICTED  ：自动 unsubscribe（防泄漏）+ 通知客户端重连
```

**③ 与 ACK / 重试 / DLQ 的联动**

```
分发 → 入 pendingEvents → 通道推送 → 客户端 ACK（§13.3.1）
  ├─ ACK 到达 → 出队，offset 推进
  ├─ ACK 超时 → 重投（§13.3.2），原消息重入队尾
  └─ 重投超 maxRetries → 转 DLQ

背压与重试的冲突处理：
  · 慢消费者 STALLED 期间，新消息不分发 → 不触发 ACK 超时 → 不重投
    （避免对慢消费者雪崩重投）
  · STALLED 解除后，从队列恢复分发；队列溢出的按溢出策略（DROP/TO_DLQ）
  · EVICTED 后，其未 ACK 消息：LOAD_BALANCE 由其他订阅者接管；BROADCAST 转 DLQ
```

**④ 广播场景的隔离（§12.4 落实）**

```
1000 订阅者广播：
  · 线程池（虚拟线程，§15.8）并发下发，每订阅者独立队列
  · 单个慢消费者阻塞在自己队列，不阻塞其他 999 个
  · 慢消费者走 ② 状态机：SLOW 降配额 → STALLED 暂停 → EVICTED 踢出
  · 踢出后广播集缩为 999，不影响其余
  · 线程池隔离：每 topic 独立线程池，防某 topic 慢消费者拖垮全 Runtime
```

**⑤ 配置项**

```properties
# 背压与慢消费者
eventmesh.backpressure.maxPending=10000              # 每 clientId 队列上限
eventmesh.backpressure.overflowPolicy=DROP_OLDEST    # DROP_OLDEST/NEWEST/BLOCK/TO_DLQ
eventmesh.backpressure.sampleIntervalMs=10000        # 采样周期
eventmesh.backpressure.highWatermark=0.8             # 慢消费者高水位
eventmesh.backpressure.lowWatermark=0.5              # 恢复低水位
eventmesh.backpressure.stalledCycles=5               # STALLED 阈值
eventmesh.backpressure.evictCycles=12                # EVICTED 阈值（无 ACK）
eventmesh.backpressure.threadPoolPerTopic=true       # 每 topic 独立线程池
```

#### 13.6.3 动态配置与热更新

复用 `EventMeshDynamicConfigManager` + MetaService：
- 订阅关系、限流规则、过滤规则、ACL 规则**热更新**，不重启。
- 配置变更经 MetaService watch 推送到所有实例。

#### 13.6.4 优雅停机

§9 仅一句 `ctx::shutdown`，未提停机语义。补：

```
shutdown 顺序：
  1. 停止接受新 publish / 新 poll 请求（返回 503）
  2. drain pendingEvents（尽力下发已缓冲消息）
  3. 等待 in-flight 消息 ACK（graceful timeout，如 10s）
  4. flush offset（本地 RocksDB + 远程同步）
  5. 释放分区租约（通知 Admin Server 重新分配 → 其他实例接管）
  6. 关闭 Storage / HTTP Server

关键：步骤 4-5 保证不丢进度、不丢分区归属。
```

#### 13.6.5 连接生命周期管理

- **poll 超时清理**：Long-Polling 超时返回空响应，释放挂起的 AsyncContext。
- **僵尸 poll 检测**：客户端长期不 poll → 按 `lastHeartbeat` 清理订阅。
- **客户端断开**：emitter.send() 异常 → 移除订阅 + 释放资源。
- **连接泄漏防护**：poll channel 总数上限，超限拒绝新 poll。

### 13.7 接入能力补充

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：❌/✅ 混合。§13.7.1 **WebSocket（默认主传输）未实现（G1）**，仅 SSE+Long-Polling；§13.7.2 WebHook✅（`WebHookChannel` 签名+重试+DLQ）；§13.7.3 批量发送❌；§13.7.4 MQTT 不支持已声明✅。

#### 13.7.1 三种传输按场景选（用户可选，默认 WebSocket）

§12.2 自承 Long-Polling 有 1 RTT 延迟。结合  负载（毫秒级延迟 + 高吞吐 + TCP 同步调用），传输层抽象为三种**用户可选**的 HTTP 家族协议，默认 WebSocket（详见 §5.1.1 / §7.2 / §15.6）：

```
Transport 接口（抽象，三种实现）：
  ├─ WebSocketTransport  （默认推送主传输：持久双向订阅流，高吞吐，同连接发控制命令）
  ├─ SSETransport        （单向流式输出：LLM token 流、A2A agent 流式回传，穿墙极佳）
  └─ LongPollingTransport（降级备选：WS 被防火墙拦截时）

SDK: CloudEventsClient.builder().transport(WEBSOCKET).build()  // 或 SSE / LONG_POLLING
Runtime: PushService 经 TransportChannel 统一三种传输（§7.2）
```

| 传输 | 适用场景 | 选型逻辑 |
|------|---------|---------|
| **WebSocket**（默认） | 持久订阅事件流、双向控制（unsubscribe/ACK）、高吞吐 | 双向长连接持续推多消息，无 1 RTT 间隙，对齐 TCP 推送实时性 |
| **SSE** | 一次请求触发的单向流式回传（LLM token 流、A2A 流式） | 单向服务端→客户端，穿墙最好，浏览器/LLM 生态成熟，自动重连 |
| **Long-Polling** | WS 被企业网络拦截的降级 | 兼容性最好，1 RTT 间隙延迟（机房内 ~1ms） |

> **为什么不是 gRPC**：WebSocket 给到与 gRPC 流式同等的低延迟+高吞吐，但属 HTTP 家族（HTTP 升级），不需要 proto+流+独立 SDK，不违背 §15.3 "无 gRPC"铁律。SSE 比 WS 更轻、穿墙更好，适合单向流式。三者各有所长，故支持三种让用户按场景选，而非"只选一个"。

> **虚拟线程的影响（§15.8）**：Java 21 虚拟线程下，挂起连接（Long-Polling、WS、SSE 的每个待推通道）的成本从"占一个平台线程"降到"占一个轻量虚拟线程"。这使得 Long-Polling 在大连接数下的阻塞成本接近 0，但 **WebSocket 在"消息间隙无 1 RTT 空窗 + 高吞吐多路复用"上的优势依然成立**——故默认仍 WebSocket，虚拟线程是吞吐/连接规模的红利，不改变默认选型。

#### 13.7.2 WebHook 主动推送

> **适用场景边界**：WebHook 是**可选旁路投递通道**，非核心数据通路。与主线推送（WebSocket/SSE/Long-Polling）分工：
> - **主线推送**：订阅者**主动连** EventMesh（维持 WS/poll），高吞吐、低延迟、双向控制——用于**能跑 SDK 的自家业务服务**。
> - **WebHook**：EventMesh **主动 POST** 到订阅者的 deliveryUrl，订阅者被动接——用于**不能/不会主动连 EventMesh 的第三方**（企微/钉钉/Slack 机器人、第三方 SaaS、告警系统、外部 HTTP API）。
>
> **何时不用 WebHook**：下游全是自家服务、能跑 SDK 维持连接 → 用主线 WebSocket 推送，WebHook 是多余复杂度。WebHook 每条一次 HTTP 往返，吞吐低、延迟高，**不适合高吞吐数据流**，只适合低频通知/第三方集成/告警链路。 高吞吐毫秒级场景以 WebSocket 为主力，WebHook 排 Phase 8.5 低优先级。

纯 pull 模式下，EventMesh 无法主动推送到**不 poll 的第三方**（如第三方 HTTP API、企微机器人）。develop 有 `WebhookPushRequest`。补 push 模式：

```
subscribe 时可配 deliveryUrl：
  POST /events/subscribe
  { "topic":"orders", "mode":"BROADCAST",
    "delivery": { "type":"webhook", "url":"https://third-party/cb",
                  "secret":"...", "retry":5 } }

EventMesh 主动 HTTP POST 推送到 deliveryUrl：
  · 带 x-em-signature 签名
  · 失败重试（指数退避）+ 转 DLQ
  · 与 WebSocket/SSE/Long-Polling 并存（订阅者按需选一种 delivery 方式）
```

**WebHook 签名规范细化：**

```
签名算法：HMAC-SHA256(secret, canonical)
  canonical = method + "\n"
            + url_path + "\n"
            + timestamp + "\n"
            + sha256(body)
  其中 body = CloudEvent 的 structured JSON

请求头：
  X-Em-Signature: sha256=<hex>      // HMAC 结果
  X-Em-Timestamp: <epoch_ms>         // 防重放（接收方校验时间窗，如 ±5min）
  X-Em-Delivery-Id: <uuid>           // 每次 delivery 唯一，接收方据此去重

接收方验证：
  1. 校验 X-Em-Timestamp 在 ±5min 窗口内（防重放）
  2. 用预共享 secret 重算 HMAC，比对 X-Em-Signature（常量时间比较）
  3. 记录 X-Em-Delivery-Id 做去重（因重试可能重复投递）
  4. 返回 2xx 视为成功，非 2xx 或超时视为失败
```

**重试与 DLQ 集成：**

```
WebHook 投递失败处理（复用 §13.3.2 Retryer）：
  T0  HTTP POST 推送 → 失败（非 2xx / 超时 / 连接拒绝）
  T1  入 Retryer，指数退避（1s/2s/4s/8s/16s）
      · 重试时 X-Em-Delivery-Id 不变（同一 delivery，便于接收方去重）
      · retryCount 写入 CloudEvent extension x-em-retry-count
  T2  重试超 maxRetries（subscribe 时配的 retry，默认 5）
      → 转 DLQ topic: <topic>.DLQ
      → DLQ 消息带 x-em-dlq-reason=webhook_failed, x-em-webhook-url, x-em-dlq-retry-count
  T3  Admin 面（§13.5.4）可浏览 DLQ + 手动 replay（重新触发 WebHook）

与 ACK 的关系：
  · WebHook 模式下，HTTP 2xx = ACK（等价 §13.3.1 的 ACK）
  · offset 在 2xx 后推进；重试期间 offset 不推进
  · 与 Long-Polling/WS/SSE 的 ACK 语义统一，仅 delivery 通道不同
```

**幂等与去重：**

```
WebHook 可能重复投递（重试 + at-least-once）：
  · 接收方按 X-Em-Delivery-Id 去重（短期，如 1h 窗口）
  · 或按 CloudEvent id 幂等（长期，业务幂等，§13.3.5）
  · EventMesh 侧不做全局去重，靠接收方（与下发侧 at-least-once 同理）
```

**配置项：**

```properties
eventmesh.webhook.connectTimeoutMs=5000
eventmesh.webhook.readTimeoutMs=10000
eventmesh.webhook.maxRetries=5                # 默认重试（可被 subscribe 配覆盖）
eventmesh.webhook.backoff=EXPONENTIAL
eventmesh.webhook.timestampWindowMs=300000    # ±5min 防重放窗口
eventmesh.webhook.threadPoolSize=64           # WebHook 投递线程池（虚拟线程可放大）
```

#### 13.7.3 批量发送

develop 有 `BatchSendAsyncEventProcessor`。`send()` 单条，高 TPS 场景需批量：

```java
// SDK
CompletableFuture<Void> publish(List<CloudEvent> events);  // 批量

// Storage
void sendBatch(String topic, List<CloudEvent> events, SendCallback callback);
```

降低每条消息的 RTT 开销。

#### 13.7.4 MQTT（声明不支持）

develop/当前分支已无 MQTT 能力。**明确声明不支持 MQTT 协议接入**。如需 MQTT，由独立网关转 CloudEvents 再接入（不在 Runtime 范围）。

### 13.8 协议与格式工程化细节

#### 13.8.1 CloudEvents 编码模式

`parseCloudEvent` 需区分两种编码：

| 编码 | Content-Type | 特点 |
|------|-------------|------|
| **binary** | `application/cloudevents+json`（或具体 data 类型） | CloudEvents 属性在 HTTP header，data 在 body |
| **structured** | `application/cloudevents-batch+json` | 整个 CloudEvent（含 data）在 body |

Runtime 两种都支持，SDK 默认 structured（简单）。

#### 13.8.2 大消息与分片

- 单消息上限（如 `maxMessageSize=1MB`，可配）。
- 超限：**拒绝 + 明确错误**（413 Payload Too Large），不自动分片。
- 大数据建议：data 放外部存储（OSS/S3），CloudEvents data 仅放引用 URL。

#### 13.8.3 Topic 映射规则统一（消除 §6.2 与 §12.5 矛盾）

§6.2 用 `subject` extension 当 topic，§12.5 又把 `subject` 当"业务标识/MQTT 映射"——**语义冲突**。统一：

```
CloudEvents 字段语义（最终）：
  type     → 事件类型（如 "order.created"）→ MULTICAST 过滤用
  source   → 事件来源（如 "/orderservice"）→ MULTICAST 过滤用
  subject  → 业务实体标识（如 "order-123"）→ 仅标识，不作 topic
  id       → 消息唯一 ID → 去重 dedupId
  partitionkey → 分区/粘性路由 key → STICKY 负载均衡用
  x-em-topic   → EventMesh topic（显式指定）→ 路由用

determineTopic(event) 优先级：
  1. x-em-topic extension（显式）
  2. 配置默认 topic
  （不再用 subject 当 topic）
```

#### 13.8.4 过滤表达式语法

§12.5 列了 MULTICAST 4 级匹配优先级，但 `CloudEventFilter.match()` 语法未定义。补：

```
v1 语法：精确匹配 + 通配
  filter: { "type": "order.created" }              // 精确
  filter: { "type": "order.*" }                    // 前缀通配
  filter: { "source": "/orderservice" }            // 精确
  filter: { "x-em-tenantid": "tenantA" }           // extension 精确
  filter: { "type":"order.*", "source":"/svc" }    // AND 组合

匹配规则：
  · 所有条件 AND
  · 值支持前缀通配 *（仅尾部）
  · 不支持正则/CEL（列入后续 roadmap）

match(event) = filter 所有 key 都匹配 event 对应属性
```

#### 13.8.5 幂等去重键

- CloudEvents `id` = dedupId，重试/重投时 `id` 不变。
- 客户端按 `id` 幂等处理。
- EventMesh 不做全局去重（§13.3.5）。

### 13.9 对实施计划（§11）的影响

§11 原 Phase 1–8 只覆盖"减法"与单实例核心数据通路，未含上述缺口。本节列出的 7 类缺口已作为补充阶段正式纳入 §11 实施计划（见 §11「阶段总览与依赖」表），与原 Phase 交错排布：

| 缺口阶段 | 内容 | 详细设计 | 优先级 |
|---------|------|---------|--------|
| **Phase 2.5 多实例协调** | 分区分配 + 租约 + offset 集中存储 + 订阅关系同步 + 实例间转发 | §13.2 | 🔴 阻断（生产 HA 前必做） |
| **Phase 4.5 安全** | TLS/mTLS + AuthFilter + AclFilter + 租户隔离 + 签名 | §13.4 | 🔴 高 |
| **Phase 5.5 下发可靠性** | ACK + 重试 + DLQ + STICKY 顺序 + 去重 | §13.3 | 🔴 高 |
| **Phase 5.6 可观测性** | metrics + trace 传播 + 消息轨迹 | §13.5.1–13.5.3 | 🟠 中 |
| **Phase 6.6 运维** | 限流 + 背压 + 动态配置 + 优雅停机 + 连接管理 | §13.6 | 🟠 中 |
| **Phase 7.5 Admin 重做** | 新 Admin 面（替换依赖 TCP/Group 的旧 handler） | §13.5.4 | 🔴 高 |
| **Phase 8.5 接入扩展** | WS/SSE + WebHook 推送 + 批量发送 | §13.7 | 🟡 中低 |

> **关键结论**：§11 的 Phase 1–8 完成后，方案仅具备"单实例快乐路径"能力，**不可直接上生产**。生产就绪门槛 = Phase 1–8 + Phase 2.5 + 3.5 + 4.5 + 5.5 + 7.5 全部完成（§11 依赖图末行）。

---

## 十四、与 unified-runtime-design.md 的关系

本重构方案是 **unified-runtime-design.md** 的进一步演进，核心差异：

| 维度 | unified-runtime-design.md（v2.1） | 本文档（简化版） |
|------|-----------------------------------|-----------------|
| SDK 协议 | TCP / HTTP / gRPC / A2A 四种 | **仅 HTTP** |
| 消息格式 | CloudEvents + EventMeshMessage + Package + proto 混用 | **全链路 CloudEvents** |
| MQ 角色 | 存储 + 部分语义（分区/广播/订阅） | **纯存储（无 Producer/Consumer Group）** |
| 订阅分发 | Consumer Group 继承 MQ 语义 | **EventMesh 自主实现（LOAD_BALANCE/BROADCAST/MULTICAST）** |
| ProtocolAdaptor | 5 个（HTTP/CloudEvents/MeshMessage/OpenMessage/A2A） | **3 个（HTTP CloudEvents/A2A，直接 CloudEvents）** |
| Processor 类 | 102 个 Processor → IngressProcessor | **~10 个 Handler** |
| OpenMessaging | 保留（与 MQ Group 解耦后作为 API 层） | **删除**（SDK 简化为 CloudEvents-only） |
| A2A | A2A Protocol Layer（编程模型） | **保留**（A2A → CloudEvents → Pipeline） |
| Connector | Source/Sink → Pipeline → Storage | **独立**（Connector Runtime 与 EventMesh Runtime 完全独立，通过 HTTP/CloudEvents 通信） |
| Admin Server | gRPC BiStream 管理面 | **重做**（旧 v1 handler 依赖 TCP/Group 已删，详见 §13.5.4 / Phase 7.5） |
| 目标代码量 | ~70,000 行 | **~29,000 行（-59%）** |

**两文档的共同点（不变的部分）：**

1. 统一 Runtime（单进程，替代 v1 + v2 双运行时）
2. Ingress/Egress Pipeline（Filter → Transformer → Router）
3. AdminClient（gRPC BiStream 管理面，通信通道不变；其上的 admin handler 业务接口重做，见 §13.5.4）
4. Offset 管理（RocksDB + 批量异步写，参考 RocketMQ Client OffsetStore）
5. A2A Agent 通信（基于 HTTP + CloudEvents）
6. 可降级部署（Admin Server 不可用时 Runtime 独立运行）
7. Connector Runtime 独立部署（不与 EventMesh Runtime 合并，通过 HTTP 接口通信）

---

---

## 十五、用户决策记录（v1.2）

> 以下为方案根基决策，定于 2026-07-02 讨论确认。后续设计与实施以本节为准；如需变更须在此追加新决策并标注取代关系。

### 15.1 MQ 语义边界：完全自主协调

**决策**：严格遵守"MQ 无语义"铁律，EventMesh **完全自主**实现多实例消费协调。

- **不**采用"内部复用 MQ Consumer Group rebalance"的折中方案。
- Phase 2.5 全量开发：自写分区分配协议 + 租约 + offset 集中存储 + 订阅关系集群同步 + 实例间转发。
- 代价：开发成本最高。收益：自主性最强、不绑死 MQ rebalance 行为、§3/§4 "自主订阅分发"核心价值完整成立。
- **影响**：§3.2 Storage Plugin 接口必须扩展 `assignPartitions` / 按 offset 范围 `poll`；§13.2 全部成立。

### 15.2 下发交付语义：至少一次 + 客户端幂等

**决策**：客户端下发侧采用 **At-Least-Once**，配合客户端按 CloudEvents `id` 幂等。**不**对下发侧实现 Exactly-Once。

- §13.3 全部成立：ACK + 重试 + DLQ + STICKY 顺序 + 客户端去重。
- **与迁移计划文档 EO 的关系**：见 §16。EO 仅适用于 Connector offset 管理（Source/Sink 的内部进度），不延伸到"客户端下发交付"。
- 代价：客户端需实现幂等（业务侧幂等表 / 本地去重窗口）。收益：避免事务性下发 + 全局去重存储的极高成本。

### 15.3 SDK 删除范围：全删 TCP+gRPC，仅 HTTP

**决策**：保留单一 HTTP-only CloudEvents SDK，**全删** TCP SDK 与 gRPC SDK。

- §5 / §10 全部成立。TCP/gRPC/OpenMessaging 代码标记 `@Deprecated` 后删除。
- 这是**不可逆动作**，所有存量 TCP/gRPC 客户端必须迁移到 HTTP SDK。
- 前置假设：业务方接受迁移成本；低延迟场景靠 Phase 8.5 的 WebSocket/SSE 可选传输覆盖。
- **风险点**：若有强依赖 gRPC 流式 / TCP 低延迟的存量客户端，迁移前需逐户确认（落地基线决策后启动排查）。

### 15.4 落地基线：基于当前工作区（-1.15.0-port），按新架构重调

**决策（2026-07-03 确定）**：代码落地基线 = **当前工作区**（`apache-eventmesh` / `feature/-1.15.0-port` 分支）。不基于 old 仓库（`apache-eventmesh-old`，纯 Apache 1.15.0）也不基于 Apache develop 干净重写。

**依据**（经 old 仓库 masa 子模块盘点得出）：
1. 当前工作区已吸收新架构前置模块——`eventmesh-meta`（5 后端含 raft）/ `eventmesh-openconnect` / `eventmesh-protocol-a2a` / CloudEvents / `eventmesh-retry`——**这些正是新架构要用的**（meta 控制面 / connector / A2A / 可靠性），基线已有，无需重搬。基于 old 仓库要重新搬这些。
2.  定制集中在 old 仓库的 **`masa-eventmesh` 子模块**（connector-wemq / trace-weapm / trace-mss / registry-namesrv / registry-nacos / security-acl / wemq-access-starter / logappender），是**插件化**实现（EventMesh SPI 接口），边界清晰，可跨基线移植。基线选择不影响定制迁移。
3.  定制基于**老接口**（MeshProducer / EventMeshMessage / RegistryService），新架构要按新接口（MeshStoragePlugin / MetaService / AclFilter）重写——**这步工基线无关，基于谁都要做**。

**关键原则：已吸收模块"认可但重调"**：
- meta/openconnect/A2A/raft 等**保留**，但**按新架构重新调整实现**（非原样用）：
  - meta：从"元数据 KV + 限流规则"**扩展为全局控制面**（分区分配/订阅视图/offset 远程/规则，§13.2.7）
  - openconnect：保留 connector 框架，Connector Runtime **独立进程化**（§8）
  - A2A：保留，转 CloudEvents → Pipeline（§6.3）
  - raft：保留作 meta 后端之一
- TCP/gRPC/OpenMessaging/Group 语义/双 runtime/空模块：**删除**（§10）

** 定制迁移**：masa 子模块作为**定制参考 + 按新接口重写**塞入：
- connector-wemq → 按 `MeshStoragePlugin` 重写（去 EventMeshMessage/TCP，保留 WeMQ 协议对接、配置中心、RSA 等业务语义）
- trace-weapm/mss → 按 `EventMeshTraceService`（trace-plugin）重写
- registry-namesrv/nacos → 并入 `MetaService`（统一控制面，§13.2.7）
- security-acl → 按 `AclFilter`（§13.4.2）重写
- wemq-access-starter → 弃（薄壳，新入口用 `EventMeshApplication`，§9）

**上游同步策略**（已分叉，不追求完全合并）：
- 选择性 backport develop 的 security fix / 严重 bugfix / 用到模块的新特性
- 新架构减法（删 59% + 重写核心）会自动消除大量分叉点，收敛后 backport 更干净
- develop 作"能力参照"，不作地基

**落地前置（必做）**：
1. 固化当前工作区未提交改动（一堆 `M` 文件）到独立分支，避免与新架构改动混淆
2. 盘点  定制清单（masa 子模块，已部分完成，见附录 E）
3. 排查存量 TCP/gRPC 客户端（支撑 §15.3 不可逆决策）

### 15.5 全局控制面：Meta 注册中心（而非 Admin Server）

**决策**：全局控制面归 **Meta 注册中心**（复用 develop `eventmesh-meta`，nacos/etcd/consul/zk/raft），Admin Server 仅做管理面。详见 §13.2.7。

- Meta 存：实例注册心跳、分区分配表、clientId 路由表、集群订阅视图、offset 远程副本、动态规则。
- Admin Server 存：Job 管理、DLQ 浏览重投、运维指令、指标聚合展示——**不**承担强一致协调。
- 降级：Meta 不可用 → 实例自洽分区分配（§13.2.3 C）；Admin 不可用 → 运维受影响但 Runtime 正常。
- **develop 两套注册抽象梳理（v1.9 决策：弃用 Registry，只用 Meta）**：`eventmesh-meta`（MetaService SPI，5 后端）+ `eventmesh-registry`（仅 nacos，偏实例发现）。**决定弃用 `eventmesh-registry` 模块**（含 `registry-api` / `registry-nacos`），全局控制面与实例发现**只用 `eventmesh-meta`（MetaService）**。registry 的实例发现能力由 MetaService 承担，避免两套注册抽象并存。 老 `registry-namesrv` / `registry-nacos` 定制按 MetaService 重写，不保留 RegistryService 路径。

### 15.6 传输层：HTTP 家族三选，默认 WebSocket

**决策**：SDK 传输层支持三种 HTTP 家族协议，用户按场景选，默认 WebSocket。

- **WebSocket**（默认）：持久双向订阅流，高吞吐，同连接发控制命令——对齐  TCP 推送实时性。
- **SSE**：单向流式输出（LLM token 流、A2A 流式回传），穿墙极佳。
- **Long-Polling**：WS 被防火墙拦截时的降级。
- **无 gRPC**（§15.3 铁律不变）：WebSocket 给到与 gRPC 流式同等的低延迟+高吞吐，但属 HTTP 家族，不需 proto+独立 SDK。
- **request-reply（§15.7）**：控制面 + 同步调用走 HTTP 请求-响应，与推送传输正交。
- 详见 §5.1.1 / §7.2 / §13.7.1。

### 15.7 新增 request-reply 同步调用

**决策**：新增 `request()` API（§5.1 / §17），对齐  TCP 同步调用语义。

- 请求-应答走 HTTP 请求-响应（挂起），复用 correlationId 匹配 + Meta 路由表做跨实例应答。
- **超时处理**：超时即失败，**迟到应答默认丢弃**（语义清晰，不写 DLQ）。
- **语义边界**：request-reply 是 RPC 语义，**不重投、不进 DLQ**（避免重复执行副作用），与 §15.2 的 at-least-once pub/sub 独立。

### 15.8 存储层：S3Stream 多后端 + Java 21 虚拟线程

**决策**：实现基于 **Java 21**；存储层保留多后端，**新增 S3Stream 作为 StoragePlugin 实现**，与 Kafka/RocketMQ 并列。

- **S3Stream 集成姿态（姿态 A，最小集成）**：新增 `S3StreamStoragePlugin` 实现 `MeshStoragePlugin`，EventMesh **仍完全自主协调分区**（§15.1 不变）。S3Stream 作为数据 WAL，其 compute 调度能力不被复用——EventMesh 在其上叠自己的分区分配协议。
- **保留多后端**：S3Stream + Kafka + RocketMQ 并列，`eventmesh.storage.type` 选择。§3 的 plugin 抽象保留。
- **offset 真相源不变**：仍存 **Meta**（§13.2.4 不变），S3Stream 只做数据 WAL，不存 EventMesh 分发 offset。RocksDB 作为本地完整副本（见下）。
- **RocksDB 定位（澄清）**：在 offset 两级存储中，RocksDB 是"**本地完整副本 + Meta 写卸载 + 降级兜底**"，非冗余：
  - 高频写本地 RocksDB（每批 ACK，亚毫秒），低频刷 Meta（批量异步）→ Meta 不被 offset 写压垮。
  - crash 恢复读本地完整 offset，零重放（即使 Meta flush 滞后）。
  - Meta 不可用时本地兜底。
  - （Connector Runtime 的 offset 仍 RocksDB + Admin 双写，§8/§16 层面 A，独立。）
- **Java 21 虚拟线程**：推送服务（PushService 的挂起通道、Long-Polling/WS/SSE 连接）用虚拟线程处理，挂起连接成本接近 0。这是吞吐/连接规模红利，**不改变默认 WebSocket 选型**（WS 在消息间隙无 1 RTT 空窗 + 多路复用的优势仍成立）。

### 15.9 决策对文档各章的影响校验

| 决策 | 现有文档章节 | 是否一致 | 备注 |
|------|-------------|---------|------|
| 15.1 完全自主协调 | §13.2 / Phase 2.5 | ✅ 一致 | S3Stream 姿态 A 不变 |
| 15.2 至少一次+幂等 | §13.3 | ✅ 一致 | §16 已澄清边界 |
| 15.3 全删 TCP+gRPC | §5 / §10 / Phase 3/8 | ✅ 一致 | §5.4 已补不可逆风险注记 |
| 15.4 暂不锁定基线 | 全文 | ✅ 一致 | 不动代码 |
| 15.5 Meta 控制面 | §13.2.7 | ✅ 一致 | §13.2.2/3/4/5 已改 |
| 15.6 三传输默认 WS | §5.1.1 / §7.2 / §13.7.1 | ✅ 一致 | §12.2 旧选型表需补注（见下） |
| 15.7 request-reply | §5.1 / §17 | ✅ 一致 | §17 已新增 |
| 15.8 S3Stream+J21 | §3 / §13.2.4 | ✅ 一致 | §3.5 配置需补 S3Stream 项；§12.6 RocksDB 定位需改 |

> **遗留 TODO**（非阻断）：① §12.2 旧的"Long-Polling vs WebSocket/SSE"选型表与 §15.6 默认 WS 冲突，需补注"已由 §15.6 取代"；② §3.5 配置简化补 S3Stream 存储类型项；③ §12.6 把 RocksDB 从"主存储"改为"本地完整副本"定位。

---

## 十六、交付语义边界：At-Least-Once vs Exactly-Once

> §15.2 选定下发侧为 At-Least-Once，但 `eventmesh-unified-runtime-migration-plan.md` 将 Exactly-Once 列为核心卖点。两份文档不矛盾——它们描述的是**两个不同层面**。本节明确边界，避免混淆。

### 16.1 两个层面的交付语义

```
层面 A：Connector 内部进度（offset 持久化）
  范围：Source Connector 拉取 offset / Sink Connector 写入确认 offset
  位置：Connector Runtime 内部，不涉及外部客户端
  语义：Exactly-Once（本地 RocksDB + 远程 Admin Server 双写 offset）
  目的：Connector 重启/故障后不丢、不重复处理外部数据
  依据：migration-plan §1.2 / §2.2 / Phase 3

层面 B：客户端下发交付（订阅者收到消息）
  范围：EventMesh → SDK 订阅者（publish 后的下发）
  位置：SubscriptionManager → PushService → 客户端 poll/ACK
  语义：At-Least-Once + 客户端幂等（本次决策 §15.2）
  目的：消息不丢；可能重复，靠客户端按 id 幂等
  依据：本文件 §13.3
```

### 16.2 为什么下发侧不沿用 Connector 的 EO

| 维度 | Connector offset（EO 可行） | 客户端下发（EO 代价过高） |
|------|---------------------------|------------------------|
| 状态存储 | 单点 offset，RocksDB 双写即可 | 需全局去重表（每 clientId×每 id），存储与查询成本高 |
| 确认点 | Connector 内部 commit，进程可控 | 客户端 ACK 跨网络，确认语义弱（ACK 丢失=状态不确定） |
| 重复代价 | 重复处理外部数据（如重复写 DB）需 Connector 幂等 | 重复下发，客户端幂等即可，EO 边际收益低 |
| 事务性 | offset 与数据处理可本地事务化 | 下发 + offset 推进跨网络，无法本地事务 |

→ 下发侧实现 EO 需"事务性下发 + 全局去重存储"，成本与收益不匹配。故 **B 层用 At-Least-Once**。

### 16.3 边界声明（写入文档约束）

- **Connector 内部**（层面 A）：保留 migration-plan 的 Exactly-Once offset 双写设计，本文件不推翻。
- **客户端下发**（层面 B）：明确 **At-Least-Once**，文档与 SDK 须如实声明交付语义，不承诺恰好一次。
- **两层面独立**：Connector offset 的 EO 不蕴含下发侧 EO；反之亦然。
- 若未来某 topic 需下发侧 EO，按 §13.3.5 备注"可配置"路径演进，但 v1 不实现。

---

## 十七、request-reply 同步调用（v1.3）

> **🔎 实现状态（v1.11 / 2026-07-06 盘点）**：⚠️ §17.1–17.5（HTTP 挂起 + correlationId 匹配 + 超时丢弃 + 不进 DLQ）已实现（`UniIngressService.request/reply` + `/events/request` `/events/reply`）；**§17.6 自寻址跨实例路由（`x-em-reply-instance` + `/internal/reply-forward`）未实装（G10）**——请求方与响应方跨实例时应答丢失。

>  TCP 协议有同步调用语义（请求方发事件 + 阻塞等应答），简化方案须有等价能力。本节定义 RPC-over-bus 的 request-reply 语义，对齐 §5.1 的 `request()` API。

### 17.1 语义

TCP 同步调用 = 请求方发事件并阻塞等应答，总线按 topic 路由到响应方，响应方处理后回带 correlationId 的应答，总线再路由回请求方解阻塞。这是 **RPC 语义**，与 §15.2 的 at-least-once pub/sub 是**不同语义面**。

### 17.2 HTTP 映射（请求-响应天然挂起）

```
请求方: POST /events/request
        body: CloudEvent { ..., x-em-reply-to: "reply.<reqId>", x-em-correlation-id: reqId }
        (HTTP 请求挂起，阻塞等应答或超时)
            ↓ EventMesh 按 event 路由（同 publish 路径：IngressPipeline → Storage.send）
响应方: 订阅了请求 topic，收到后处理
        POST /events/reply
        body: CloudEvent { x-em-correlation-id: reqId, data: 应答体 }
            ↓ EventMesh 按 correlationId 匹配到挂起的 HTTP 请求
请求方: 原挂起请求返回应答 ← 解阻塞
        超时 → 请求失败（与 TCP 同步调用语义一致）
```

### 17.3 关键设计点

1. **匹配表**：`correlationId → 挂起的 AsyncContext`，内存表 + 超时清理。
2. **多实例路由**：应答可能打到非请求实例。复用 §13.2.5 的 clientId→instance 路由表（存 Meta）——按 correlationId 找到"开请求的实例"，把应答转发过去。或请求方持 WS/SSE 通道时，应答走通道推回（§7.2 TransportChannel 复用）。
3. **超时处理（决策 §15.7）**：请求自带 timeout，超时即失败，清理挂起上下文。**迟到应答默认丢弃**（最简单、语义清晰：RPC 一次一应答）。不写 DLQ（避免复杂度）。响应方超时后仍在处理的结果丢失，由业务层重试请求覆盖。
4. **语义边界（与 at-least-once 隔离）**：request-reply **不重投、不进 DLQ**——重投会导致重复执行副作用（RPC 的副作用通常不可重复）。这与 pub/sub 的 at-least-once+重试+DLQ 是独立路径。
5. **超时≠丢消息**：请求已路由到响应方，超时只是请求方不等了；响应方可能仍在处理。

### 17.4 API（对齐 §5.1）

```java
// 同步请求-应答
CompletableFuture<CloudEvent> request(CloudEvent event, Duration timeout);
// 内部：POST /events/request 挂起，收到 /events/reply 或超时
```

### 17.5 与传输层的关系

- 请求方若持 WebSocket/SSE 通道，应答可走通道推回（低延迟）。
- 否则走原挂起的 HTTP 请求响应返回（Long-Polling 式挂起）。
- 控制面（publish/request/reply/subscribe/unsubscribe）始终走 HTTP 请求-响应，与推送传输（WS/SSE/LP）正交。

### 17.6 request-reply 路由：自寻址，无需 Meta

§17.3 初版说"复用 §13.2.5 的 clientId→instance 路由表做跨实例应答"——**这会过度耦合**。request-reply 的应答路由其实**不需要 Meta 全局路由表**，用**自寻址（self-addressed）**即可，比 pub/sub 分发更简单：

**① 原理：应答目标由请求方自带，不查表**

```
请求方（实例 A 上的 clientId-R）发 request：
  CloudEvent extensions:
    x-em-correlation-id: reqId
    x-em-reply-to:        reply.<reqId>          ← 应答"投递地址"
    x-em-reply-instance:  <instanceId-A>         ← 请求方所在实例（自寻址关键）
    x-em-reply-channel:   ws | sse | http        ← 应答回传方式

应答方（实例 B）处理完，发 reply：
  POST /events/reply  （可打到任意实例）
  CloudEvent extensions:
    x-em-correlation-id: reqId
    → EventMesh 不查 Meta，直接按 x-em-reply-instance 转发到实例 A
```

**② 两层映射（仅请求方实例本地，不进 Meta）**

```
实例 A 本地表（内存，request 发出时建，超时清理）：
  reqId → { AsyncContext(挂起请求), clientId-R, expireAt }

应答到达实例 A（经 /events/reply 或跨实例转发）：
  按 reqId 查本地表 → 匹配到挂起请求 → 返回应答 → 清理条目
  超时未匹配 → 丢弃（§15.7）

→ 路由状态只在"开请求的实例"本地，无需 Meta 全局可见
→ 与 §13.2.5 的 clientId→instance 路由表（用于 pub/sub 推送分发）是两回事
```

**③ 跨实例应答转发（轻量，无 Meta 查询）**

```
应答 /events/reply 打到实例 C（≠ 实例 A）：
  实例 C 读 x-em-reply-instance = A
  → HTTP POST /internal/reply-forward 到实例 A（实例列表从 Meta 拿，但只是"找 A 在哪"，非 per-reqId 路由）
  → 实例 A 收到 → 本地表匹配 → 解阻塞

实例列表查询：复用 Meta 的实例注册（§13.2.7），但不为每个 reqId 建路由条目
→ Meta 只存"实例 A 的地址"，不存"reqId 在哪个实例"
```

**④ 与 pub/sub 推送路由的对比**

| | pub/sub 推送（§13.2.5） | request-reply 应答（本节） |
|---|---|---|
| 路由依据 | clientId → instance（订阅者长期位置） | x-em-reply-instance（请求方自带） |
| 路由表 | 存 Meta（全局，订阅者迁移要更新） | **不存 Meta**，请求方本地内存表 |
| 转发 | 实例间按 clientId 路由表转发 | 实例间按 reply-instance 一次性转发 |
| 状态生命周期 | 长期（订阅期） | 短期（reqId 超时即清） |
| 复杂度 | 高（订阅迁移、全局一致） | 低（自寻址，无迁移） |

**⑤ 请求方实例 crash 的处理**

```
请求方实例 A 在等待应答时 crash：
  · 本地 reqId 表随进程消失
  · 应答迟到到达（转发到 A 的接管者或 A 重启后）→ 本地表无 reqId → 丢弃（§15.7 超时丢弃语义）
  · 请求方业务层感知请求超时 → 自行重试（新 reqId）
  → 无需持久化 reqId 表，crash 清理自然
```

**小结**：request-reply 用自寻址（reply-to + reply-instance），路由状态本地化，**不依赖 Meta 全局路由表**。这纠正了 §17.3 第 2 点的过度耦合——应答路由比 pub/sub 简单，不应套用订阅者路由模型。

> **勘误（§17.3 第 2 点）**：原文"复用 §13.2.5 的 clientId→instance 路由表（存 Meta）做跨实例应答"已被本节取代。正确机制是自寻址：请求方在 CloudEvent 自带 `x-em-reply-instance`，应答按此转发，reqId 匹配表仅存请求方实例本地内存。

---

## 十八、端到端测试用例设计（v1.6）

> 覆盖核心数据通路与关键质量属性。每用例标注：前置条件 / 步骤 / 预期 / 覆盖设计章节。供 §11 Phase 8 集成测试套件落地。

### 18.1 基础数据通路

| ID | 用例 | 前置 | 步骤 | 预期 | 覆盖 |
|----|------|------|------|------|------|
| E2E-01 | publish→subscribe 单实例 | SDK 连 Runtime | publish(order.created)→subscribe | 订阅者收到，CloudEvent 字段完整 | §4/§5/§6 |
| E2E-02 | 三传输推送 | WS/SSE/LP 各一订阅者 | publish 1 条 | 三者均收到同消息 | §5.1.1/§7.2 |
| E2E-03 | 批量 publish | — | publish(List<100>) | 全部落 MQ，TPS > 单条×N | §13.7.3 |
| E2E-04 | 二进制 vs JSON 编码 | — | 同消息两种编码 publish | 均能被订阅者解析 | §13.8.1 |

### 18.2 分发模式

| ID | 用例 | 前置 | 步骤 | 预期 | 覆盖 |
|----|------|------|------|------|------|
| E2E-05 | LOAD_BALANCE RoundRobin | 3 订阅者同 mode | publish 6 条 | 各收 2 条，无重复 | §4.2.1 |
| E2E-06 | BROADCAST | 4 订阅者 | publish 1 条 | 全收，各 1 份 | §4.2.2 |
| E2E-07 | MULTICAST 按 type | 3 订阅者各订不同 type | publish 混合 type | 仅匹配 type 的订阅者收 | §4.2.3/§13.8.4 |
| E2E-08 | STICKY 顺序 | partitionkey=order-1 发 5 条 | — | 同 worker 顺序收到 | §13.3.3 |

### 18.3 request-reply 同步调用

| ID | 用例 | 前置 | 步骤 | 预期 | 覆盖 |
|----|------|------|------|------|------|
| E2E-09 | 正常请求-应答 | 响应方订阅请求 topic | request(order.query) | 收到应答，correlationId 匹配 | §17 |
| E2E-10 | 超时丢弃 | 响应方不处理 | request(timeout=2s) | 2s 后失败，迟到应答丢弃 | §15.7 |
| E2E-11 | 跨实例应答 | 请求方/响应方不同实例 | request | 应答自寻址路由正确 | §17.6 |
| E2E-12 | request 不进 DLQ | 响应方持续失败 | request 超时 | 不触发 DLQ（与 pub/sub 隔离） | §17.3 |

### 18.4 可靠性（ACK/重试/DLQ/幂等）

| ID | 用例 | 前置 | 步骤 | 预期 | 覆盖 |
|----|------|------|------|------|------|
| E2E-13 | ACK 推进 offset | — | 下发后不 ACK | 超时重投；ACK 后 offset 推进 | §13.3.1 |
| E2E-14 | 重试退避 | 订阅者持续失败 | 下发 1 条 | 按 1s/2s/4s/8s/16s 重投 | §13.3.2 |
| E2E-15 | 转 DLQ | 订阅者失败超 maxRetries | — | 消息进 `<topic>.DLQ`，带 reason/retry-count | §13.3.2 |
| E2E-16 | 幂等去重 | 订阅者按 id 去重 | 重投同 id | 业务不重复执行 | §13.3.5 |
| E2E-17 | crash 恢复不丢 | 下发后 Runtime crash | 重启 | offset 未推进的消息重放，幂等收敛 | §13.3.2 |

### 18.5 多实例与协调

| ID | 用例 | 前置 | 步骤 | 预期 | 覆盖 |
|----|------|------|------|------|------|
| E2E-18 | 分区不重叠 | 3 实例 | publish 大量 | 每条仅一实例拉取，无重复消费 | §13.2.3 |
| E2E-19 | gen fencing 防脑裂 | 实例 A 网络分区 | A 恢复 | A gen 过期自停 poll，B 唯一 poller | §13.2.8 ④ |
| E2E-20 | clientId 迁移不丢进度 | clientId 连 A | A crash，重连 B | B 从 Meta 读 offset 继续，零丢 | §13.2.4 |
| E2E-21 | 跨实例转发 | 订阅者在 B，拉取在 A | publish | A 转发到 B，订阅者收到 | §13.2.5 |
| E2E-22 | 实例扩缩容 | 2→3 实例 | 增实例 | 分区重分配，约 1/n 迁移，无丢 | §13.2.8 |

### 18.6 降级与恢复

| ID | 用例 | 前置 | 步骤 | 预期 | 覆盖 |
|----|------|------|------|------|------|
| E2E-23 | Meta 挂降级 | 运行中 | 断 Meta | 自洽分配，publish/已有下发不中断 | §13.2.9 |
| E2E-24 | 降级期 offset | Meta 挂 | ACK 多条 | 仅写本地 RocksDB，crash 不丢 | §13.2.9 |
| E2E-25 | Meta 恢复对齐 | 降级中 | 恢复 Meta | offset/订阅/分配渐进对齐，无丢 | §13.2.9 |
| E2E-26 | 降级期新订阅 | Meta 挂 | subscribe | 本实例可见，恢复后集群对齐 | §13.2.9 |

### 18.7 安全

| ID | 用例 | 前置 | 步骤 | 预期 | 覆盖 |
|----|------|------|------|------|------|
| E2E-27 | TLS 强制 | ENFORCING 模式 | 明文请求 | 拒连 | §13.4.1 |
| E2E-28 | mTLS 双向 | client.auth=REQUIRE | 无客户端证书 | 拒连 | §13.4.1 |
| E2E-29 | 认证失败 | — | 无凭证 publish | 401 | §13.4.2 |
| E2E-30 | ACL 越权 | tenantB 用户 | 访问 tenantA topic | 403（DENY 优先） | §13.4.2 |
| E2E-31 | 签名验签 | — | 篡改 CloudEvent | 拒收 | §13.4.4 |
| E2E-32 | 租户隔离 | tenantA/B 各订阅 | 互查 | 看不到对方订阅/消息 | §13.4.3 |

### 18.8 背压与慢消费者

| ID | 用例 | 前置 | 步骤 | 预期 | 覆盖 |
|----|------|------|------|------|------|
| E2E-33 | 队列溢出策略 | DROP_OLDEST | 灌满 maxPending | 丢最旧 + metric | §13.6.2 |
| E2E-34 | 慢消费者状态机 | 1 慢 + 999 快（广播） | 持续不 ACK | 慢者 SLOW→STALLED→EVICTED，快者不受影响 | §13.6.2 |
| E2E-35 | 限流 429 | RateLimitFilter | 超 TPS 上限 | 429，不丢已入队 | §13.6.1 |
| E2E-36 | 优雅停机 | 运行中 | shutdown | drain→等ACK→flush offset→释放租约，offset 不丢 | §13.6.4 |

### 18.9 可观测性

| ID | 用例 | 前置 | 步骤 | 预期 | 覆盖 |
|----|------|------|------|------|------|
| E2E-37 | metrics 暴露 | — | 抓 /metrics | 16 项指标含标签 | §13.5.1 |
| E2E-38 | trace 全链路 | — | publish→下发 | zipkin/jaeger 见完整 span 链 | §13.5.2 |
| E2E-39 | 按 id 查轨迹 | — | 记 CloudEvent id | 查到全链路 span | §13.5.3 |

### 18.10 Connector

| ID | 用例 | 前置 | 步骤 | 预期 | 覆盖 |
|----|------|------|------|------|------|
| E2E-40 | Source→EventMesh | MySQL Source | binlog 变更 | EventMesh 收到 CloudEvent | §8.3 |
| E2E-41 | EventMesh→Sink | Redis Sink | publish | Sink 写入 Redis | §8.4 |
| E2E-42 | Source EO | publish 失败 | — | source offset 不推进，重拉同批 | §8.9/§16 |

### 18.11 WebHook

| ID | 用例 | 前置 | 步骤 | 预期 | 覆盖 |
|----|------|------|------|------|------|
| E2E-43 | WebHook 推送 | deliveryUrl 配置 | publish | 第三方收到 POST + 签名 | §13.7.2 |
| E2E-44 | WebHook 重试 | 第三方返回 500 | — | 指数退避重试，超阈值转 DLQ | §13.7.2 |
| E2E-45 | WebHook 去重 | — | 重试同 Delivery-Id | 接收方按 id 去重 | §13.7.2 |

> **测试套件组织**：按 §11 Phase 对应——核心通路(E2E-01~08)在 Phase 2/3/5；可靠性(13~17)在 Phase 5.5；多实例(18~22)在 Phase 2.5；降级(23~26)在 Phase 2.5；安全(27~32)在 Phase 4.5；背压(33~36)在 Phase 6.6；可观测(37~39)在 Phase 5.6；Connector(40~42)在 Phase 6；WebHook(43~45)在 Phase 8.5。全量通过 = Phase 8 收尾 DoD。

---

## 附录 A：配置项总表（v1.7）

> 汇总散落各章的配置项，按模块分类。`#` 列为默认值，章节列指向设计依据。

### A.1 存储（Storage Plugin）

| 配置项 | 默认值 | 说明 | 章节 |
|--------|--------|------|------|
| `eventmesh.storage.type` | kafka | kafka / rocketmq / pulsar / s3stream | §3.5/§15.8 |
| `eventmesh.storage.bootstrap.servers` | localhost:9092 | Kafka/RocketMQ bootstrap | §3.5 |
| `eventmesh.storage.consumer.auto.offset.reset` | earliest | offset 重置策略 | §3.5 |
| `eventmesh.storage.s3stream.endpoint` | — | S3Stream endpoint（type=s3stream） | §3.6.3 |
| `eventmesh.storage.s3stream.bucket` | — | S3 bucket | §3.6.3 |
| `eventmesh.storage.s3stream.region` | — | S3 region | §3.6.3 |

### A.2 SDK / 传输

| 配置项 | 默认值 | 说明 | 章节 |
|--------|--------|------|------|
| `eventmesh.sdk.transport` | WEBSOCKET | WEBSOCKET / SSE / LONG_POLLING | §5.1.1/§15.6 |
| `eventmesh.sdk.encoding` | BINARY | BINARY / STRUCTURED | §5.1.1/§15.8 |

### A.3 下发可靠性（重试 / DLQ）

| 配置项 | 默认值 | 说明 | 章节 |
|--------|--------|------|------|
| `eventmesh.retry.maxRetries` | 5 | 最大重试次数 | §13.3.2 |
| `eventmesh.retry.backoff` | EXPONENTIAL | EXPONENTIAL/FIXED/LINEAR | §13.3.2 |
| `eventmesh.retry.initialDelayMs` | 1000 | 初始退避 | §13.3.2 |
| `eventmesh.retry.maxDelayMs` | 60000 | 退避封顶 | §13.3.2 |
| `eventmesh.retry.jitterRatio` | 0.2 | ±20% jitter | §13.3.2 |
| `eventmesh.retry.ackTimeoutMs` | 30000 | ACK 超时 | §13.3.2 |
| `eventmesh.dlq.topicSuffix` | .DLQ | 死信 topic 后缀 | §13.3.2 |

### A.4 背压与慢消费者

| 配置项 | 默认值 | 说明 | 章节 |
|--------|--------|------|------|
| `eventmesh.backpressure.maxPending` | 10000 | 每 clientId 队列上限 | §13.6.2 |
| `eventmesh.backpressure.overflowPolicy` | DROP_OLDEST | DROP_OLDEST/NEWEST/BLOCK/TO_DLQ | §13.6.2 |
| `eventmesh.backpressure.sampleIntervalMs` | 10000 | 慢消费者采样周期 | §13.6.2 |
| `eventmesh.backpressure.highWatermark` | 0.8 | 慢消费者高水位 | §13.6.2 |
| `eventmesh.backpressure.lowWatermark` | 0.5 | 恢复低水位 | §13.6.2 |
| `eventmesh.backpressure.stalledCycles` | 5 | STALLED 阈值 | §13.6.2 |
| `eventmesh.backpressure.evictCycles` | 12 | EVICTED 阈值（无 ACK） | §13.6.2 |
| `eventmesh.backpressure.threadPoolPerTopic` | true | 每 topic 独立线程池 | §13.6.2 |

### A.5 安全（TLS / 认证 / ACL）

| 配置项 | 默认值 | 说明 | 章节 |
|--------|--------|------|------|
| `eventmesh.server.tls.mode` | DISABLED | DISABLED/PERMISSIVE/ENFORCING | §13.4.1 |
| `eventmesh.server.tls.ssl.protocol` | TLSv1.3 | TLS 协议 | §13.4.1 |
| `eventmesh.server.tls.keystore.path` | — | keystore 路径 | §13.4.1 |
| `eventmesh.server.tls.keystore.password` | — | keystore 密码 | §13.4.1 |
| `eventmesh.server.tls.client.auth` | NONE | NONE/OPTIONAL/REQUIRE（mTLS） | §13.4.1 |
| `eventmesh.server.tls.truststore.path` | — | truststore 路径（mTLS） | §13.4.1 |
| `eventmesh.auth.type` | token | auth-token / auth-http-basic | §13.4.2 |

### A.6 WebHook

| 配置项 | 默认值 | 说明 | 章节 |
|--------|--------|------|------|
| `eventmesh.webhook.connectTimeoutMs` | 5000 | 连接超时 | §13.7.2 |
| `eventmesh.webhook.readTimeoutMs` | 10000 | 读超时 | §13.7.2 |
| `eventmesh.webhook.maxRetries` | 5 | 默认重试（可被 subscribe 覆盖） | §13.7.2 |
| `eventmesh.webhook.backoff` | EXPONENTIAL | 退避策略 | §13.7.2 |
| `eventmesh.webhook.timestampWindowMs` | 300000 | ±5min 防重放窗口 | §13.7.2 |
| `eventmesh.webhook.threadPoolSize` | 64 | 投递线程池 | §13.7.2 |

### A.7 多实例协调（Meta / 租约 / 分配）

| 配置项 | 默认值 | 说明 | 章节 |
|--------|--------|------|------|
| `eventmesh.meta.type` | nacos | nacos/etcd/consul/zookeeper/raft | §13.2.7/§15.5 |
| `eventmesh.meta.address` | — | Meta 注册中心地址 | §13.2.7 |
| `eventmesh.coordinator.leaseRenewIntervalMs` | 5000 | 租约续约间隔 | §13.2.8 |
| `eventmesh.coordinator.leaseTtlMs` | 15000 | 租约 TTL（3 倍续约） | §13.2.8 |
| `eventmesh.coordinator.degradedMetaTimeoutMs` | 15000 | 进入降级的 Meta 超时 | §13.2.9 |

### A.8 Offset 存储

| 配置项 | 默认值 | 说明 | 章节 |
|--------|--------|------|------|
| `eventmesh.offset.local.path` | ./data/offset | 本地 RocksDB 路径 | §12.6/§13.2.4 |
| `eventmesh.offset.remoteFlushIntervalMs` | 1000 | 刷 Meta 间隔（写卸载） | §13.2.4 |
| `eventmesh.offset.remoteFlushBatch` | 100 | 刷 Meta 批量大小 | §13.2.4 |

### A.9 运行时

| 配置项 | 默认值 | 说明 | 章节 |
|--------|--------|------|------|
| `eventmesh.server.http.port` | 8080 | HTTP Server 端口 | §6 |
| `eventmesh.runtime.nodeId` | — | 实例 ID | §9 |
| `eventmesh.gracefulShutdown.timeoutMs` | 10000 | 优雅停机等待 ACK | §13.6.4 |

---

## 附录 B：CloudEvents extension 字段全集（v1.7）

> 规范化所有 `x-em-*` 自定义 extension 字段。CloudEvents 1.0 标准字段（id/source/type/time/data/specversion/datacontenttype）不在此列。`必填` 列：请求方填 / 响应方填 / 系统填 / 可选。

### B.1 路由与分发

| 字段 | 类型 | 含义 | 必填 | 章节 |
|------|------|------|------|------|
| `x-em-topic` | String | EventMesh topic（显式路由） | 可选（默认 topic 时省略） | §13.8.3 |
| `partitionkey` | String | 分区/粘性路由 key（CloudEvents 规范字段，非 x-em-） | 可选 | §13.3.3 |
| `x-em-tenantid` | String | 租户 ID（隔离 + ACL） | 可选（单租户省略） | §13.4.2/§13.4.3 |
| `x-em-userid` | String | 用户/调用方 ID（ACL 主体） | 可选 | §13.4.2 |
| `x-em-distribution-mode` | Enum | LB/LB_STICKY/BROADCAST/MULTICAST | 系统填（subscribe 时定） | §4.2/§13.3.3 |

### B.2 request-reply

| 字段 | 类型 | 含义 | 必填 | 章节 |
|------|------|------|------|------|
| `x-em-correlation-id` | String | 请求-应答关联 ID | 请求方填 | §17 |
| `x-em-reply-to` | String | 应答投递地址（reply.\<reqId\>） | 请求方填 | §17.6 |
| `x-em-reply-instance` | String | 请求方所在实例（自寻址） | 请求方填 | §17.6 |
| `x-em-reply-channel` | Enum | ws/sse/http（应答回传方式） | 请求方填 | §17.6 |

### B.3 可靠性

| 字段 | 类型 | 含义 | 必填 | 章节 |
|------|------|------|------|------|
| `x-em-retry-count` | Int | 已重试次数 | 系统填 | §13.3.2 |
| `x-em-dlq-reason` | String | 死信原因（ack_timeout/webhook_failed/...） | 系统填（DLQ 消息） | §13.3.2 |
| `x-em-dlq-retry-count` | Int | 进 DLQ 时的总重试次数 | 系统填（DLQ 消息） | §13.3.2 |
| `x-em-ttl` | Duration | 消息存活时间（过期丢弃） | 可选 | §13.3.4 |

### B.4 MQ 坐标（内部透传）

| 字段 | 类型 | 含义 | 必填 | 章节 |
|------|------|------|------|------|
| `x-em-mq-offset` | Long | MQ 物理 offset（Storage.poll 注入） | 系统填 | §12.6.6 |
| `x-em-mq-partition` | Int | MQ 分区号 | 系统填 | §12.6.6 |

### B.5 WebHook 投递（HTTP 头，非 CloudEvent extension）

| 头 | 类型 | 含义 | 章节 |
|----|------|------|------|
| `X-Em-Signature` | String | sha256=\<hex\> HMAC 签名 | §13.7.2 |
| `X-Em-Timestamp` | Long | epoch_ms（防重放） | §13.7.2 |
| `X-Em-Delivery-Id` | UUID | 每次 delivery 唯一（去重） | §13.7.2 |

### B.6 Trace（CloudEvents Distributed Tracing extension）

| 字段 | 类型 | 含义 | 章节 |
|------|------|------|------|
| `traceparent` | String | W3C Trace Context（00-traceId-spanId-flags） | §13.5.2 |
| `tracestate` | String | 厂商扩展（可选） | §13.5.2 |
| `baggage` | String | 跨服务业务上下文（k=v,k=v） | §13.5.2 |

### B.7 多播过滤

| 字段 | 类型 | 含义 | 必填 | 章节 |
|------|------|------|------|------|
| `x-em-subscriptions` | List\<String\> | 显式订阅目标列表（多播优先级 1） | 可选 | §12.5 |

> **命名规范**：所有 EventMesh 自定义字段用 `x-em-` 前缀（CloudEvents extension 约定）。`partitionkey`/`traceparent`/`tracestate`/`baggage` 为 CloudEvents 规范字段，不加前缀。MQ 坐标类（`x-em-mq-*`）为内部透传，不下发客户端。

---

## 附录 C：部署拓扑示例（v1.7）

> 四种典型部署拓扑，标注组件、适用场景、关键取舍。

### C.1 单实例（开发/小规模/PoC）

```
┌─────────────┐     HTTP/CloudEvents     ┌─────────────────────────┐
│ SDK 客户端   │ ───────────────────────→ │ EventMesh Runtime (单实例)│
│ (WS/SSE/LP) │ ←─────────────────────── │ · Ingress/Egress Pipeline│
└─────────────┘                          │ · SubscriptionManager   │
                                         │ · PushService           │
                                         │ · 本地 RocksDB offset    │
                                         │ · Meta(可选,单实例可省)  │
                                         └──────────┬──────────────┘
                                                    │ Storage.send/poll
                                                    ▼
                                         ┌─────────────────────────┐
                                         │ Kafka/RocketMQ/S3Stream  │
                                         └─────────────────────────┘
```
- **适用**：开发调试、PoC、小规模（单分区足够）、无 HA 要求
- **取舍**：无多实例协调（§13.2 可省），Meta 可选（无分区分配需求）；crash 即不可用
- **配置**：`eventmesh.storage.type=kafka`，无 Meta 配置

### C.2 多实例（生产 HA，推荐基线）

```
        ┌─────────────────────────────────────────┐
        │  Meta 注册中心 (nacos/etcd，HA 集群)      │
        │  · 实例注册/分区分配/订阅视图/offset远程  │
        └───┬──────────────┬──────────────┬───────┘
            │              │              │
   ┌────────▼───┐  ┌───────▼────┐  ┌──────▼─────┐
   │ Runtime A  │  │ Runtime B  │  │ Runtime C  │
   │ 持 p0,p1   │  │ 持 p2      │  │ 持 p3,p4   │
   │ 本地RocksDB│  │ 本地RocksDB│  │ 本地RocksDB│
   └──────┬─────┘  └──────┬─────┘  └──────┬─────┘
          │               │               │
          └───────────────┼───────────────┘
                          │ Storage.send/poll (各自负责分区)
                          ▼
              ┌───────────────────────────┐
              │ Kafka/RocketMQ/S3Stream    │
              │ (多分区,EventMesh 单Producer单Consumer模式)│
              └───────────────────────────┘
            ┌───────────────────────────────────┐
            │ SDK 客户端 (连任意 Runtime, WS/SSE)│
            └───────────────────────────────────┘
```
- **适用**：生产、需 HA、中等规模
- **取舍**：Meta 强依赖（降级为自洽，§13.2.9）；实例间转发跨实例订阅；gen fencing 防脑裂
- **配置**：`eventmesh.meta.type=nacos`，`eventmesh.coordinator.leaseTtlMs=15000`
- **关键**：SDK 连任意实例，订阅视图集群级，跨实例转发保证下发

### C.3 多租户（SaaS / 多业务线隔离）

```
        ┌─────────────────────────────────────────┐
        │  Meta (含 ACL 规则 + 租户隔离订阅视图)    │
        └───┬─────────────────────────────────────┘
            │
   ┌────────▼─────────────────────────────────────┐
   │  EventMesh Runtime 集群 (多实例,共享)          │
   │  · AclFilter 按 tenant 隔离 (§13.4.2)         │
   │  · SubscriptionManager 按 tenant 过滤订阅视图  │
   │  · topic 命名: <tenantId>.<topic>             │
   └────────┬─────────────────────────────────────┘
            │
   ┌────────▼─────────┐  ┌──────────────────────┐
   │ tenantA.orders   │  │ tenantB.orders       │  ← topic 按租户隔离
   │ tenantA.events   │  │ tenantB.events       │
   └──────────────────┘  └──────────────────────┘
   ┌────────────────┐         ┌────────────────┐
   │ tenantA 客户端  │         │ tenantB 客户端  │
   │ (token+tenantid)│        │ (token+tenantid)│
   └────────────────┘         └────────────────┘
```
- **适用**：SaaS 平台、多业务线共享集群、需租户隔离
- **取舍**：共享 Runtime 集群降成本；ACL 必须严格（DENY 跨租户优先级最高）；topic 带租户前缀
- **配置**：`eventmesh.auth.type=token`，ACL 规则经 Meta 下发
- **关键**：tenantA 看不到 tenantB 的订阅/消息（§13.4.3）

### C.4 S3Stream 存算分离（云原生/低成本/弹性）

```
   ┌──────────────────────────────────────────────────┐
   │  Meta (nacos/etcd)                                │
   └───┬──────────────────────────────────────────────┘
       │
   ┌───▼──────────────────────────────────────────────┐
   │  EventMesh Runtime 集群 (无状态 compute,可秒级扩缩)│
   │  · 分区分配协议 (§13.2.8, 不用 S3Stream 调度)     │
   │  · 本地 RocksDB offset + Meta 远程                │
   └───┬──────────────────────────────────────────────┘
       │ S3StreamStoragePlugin (v1 Kafka线协议薄包装/v2 原生)
       ▼
   ┌──────────────────────────────────────────────────┐
   │  S3Stream (无状态 broker)                          │
   │   · 热数据: 本地 SSD 缓存                          │
   │   · 冷数据: S3 对象存储 (分级,低成本长保留)        │
   └──────────────────────────────────────────────────┘
       │
       ▼
   ┌──────────────┐
   │  S3 对象存储  │  ← 数据真相源,offset 持久化
   └──────────────┘
```
- **适用**：云原生部署、追求存储成本（S3 比 Kafka broker 便宜）、长保留期（合规审计）、弹性扩缩存算分离
- **取舍**：S3Stream compute 调度能力不用（姿态 A，§15.8），EventMesh 叠自己分区协议；延迟略高于本地 broker（S3 RTT）
- **配置**：`eventmesh.storage.type=s3stream`，`eventmesh.storage.s3stream.endpoint=...`
- **关键**：Runtime 无状态可随意扩缩（compute 弹性），存储在 S3（成本+持久）；v1 薄包装起步，压测后可迁 v2 原生（§3.6.6）

### C.5 拓扑选型矩阵

| 拓扑 | 规模 | HA | 隔离 | 成本 | 典型场景 |
|------|------|----|------|------|---------|
| C.1 单实例 | 小 | ❌ | — | 低 | 开发/PoC |
| C.2 多实例 | 中大 | ✅ | — | 中 | 生产基线 |
| C.3 多租户 | 中大 | ✅ | ✅ | 中（共享降本） | SaaS/多业务线 |
| C.4 S3Stream | 中大 | ✅ | — | 低（S3） | 云原生/长保留 |

> 可组合：C.2+C.3（多实例多租户）、C.2+C.4（多实例+S3Stream）、C.2+C.3+C.4（全量）。

---

## 附录 D：develop 代码迁移映射表（v1.7）

> 基于 develop 分支现有代码（经 §13 前置盘点），映射到新架构的处理方式：删除 / 替换 / 保留 / 新增。供实施时按模块迁移。标注 `→` 指迁移目标。

### D.1 SDK（eventmesh-sdk-java）

| develop 现有 | 处理 | 迁移目标 | 章节 |
|-------------|------|---------|------|
| `tcp/` 全子包（Proxy/Package） | 删除 | — | §5/§10 |
| `grpc/` 全子包 | 删除 | — | §5/§10 |
| `EventMeshHttpClient` | 替换 | `CloudEventsClient`（4 API + 三传输） | §5.1 |
| `EventMeshMessage` | 删除 | CloudEvent | §5/§10 |
| `Package` / `Command` | 删除 | — | §10 |
| `HttpCommand` | 删除 | — | §10 |
| `tcp/impl/openmessage/OpenMessageTCPClient` | 删除 | — | §10 |
| `http/producer/OpenMessageProducer` | 删除 | `CloudEventsClient.publish` | §5.1 |

### D.2 协议插件（eventmesh-protocol-plugin）

| develop 现有 | 处理 | 迁移目标 | 章节 |
|-------------|------|---------|------|
| `eventmesh-protocol-api`（ProtocolAdaptor） | 保留 | toCloudEvent/fromCloudEvent 统一入口 | §6.3 |
| `eventmesh-protocol-cloudevents` | 保留 | 主流（HTTP SDK 直发 CloudEvents） | §6.3 |
| `eventmesh-protocol-http` | 保留 | HTTP→CloudEvent | §6.3 |
| `eventmesh-protocol-a2a`（EnhancedA2A+MCP） | 保留 | A2A→CloudEvent | §6.3/§14 |
| `eventmesh-protocol-meshmessage` | 删除 | —（TCP SDK 已删） | §6.3/§10 |
| `eventmesh-protocol-openmessage` | 删除 | —（OpenMessaging 已删） | §6.3/§10 |
| `eventmesh-protocol-grpc` / `grpcmessage` | 删除 | —（gRPC SDK 已删） | §10 |

### D.3 存储（eventmesh-storage-*）

| develop 现有 | 处理 | 迁移目标 | 章节 |
|-------------|------|---------|------|
| `MeshMQProducer`（含 producerGroup） | 替换 | `MeshStoragePlugin.send`（无 Group） | §3.2 |
| `MeshMQConsumer`（含 consumerGroup） | 替换 | `MeshStoragePlugin.poll`（无 Group） | §3.2 |
| `createTransactionProducer()` | 删除 | —（不支持事务，§13.3.6） | §3.2/§10 |
| `subscribe(topic, subExpression)` | 替换 | `poll(topic, partition, offset)`（无 Tag） | §3.2/§13.8.3 |
| `eventmesh-storage-kafka` | 改造 | `KafkaStoragePlugin`（单 P+C，无 Group） | §3.3 |
| `eventmesh-storage-rocketmq` | 改造 | `RocketMQStoragePlugin`（单 P+C，无 Group） | §3.4 |
| `eventmesh-storage-standalone` | 保留/改造 | 单机实现 | §3 |
| —（新增） | 新增 | `S3StreamStoragePlugin`（v1 薄包装/v2 原生） | §3.6 |

### D.4 运行时（eventmesh-runtime）

| develop 现有 | 处理 | 迁移目标 | 章节 |
|-------------|------|---------|------|
| `protocol/tcp/` 全子包 | 删除 | — | §7/§10 |
| `protocol/grpc/` 全子包 | 删除 | — | §7/§10 |
| `processor/tcp/` 全部 | 删除 | — | §10 |
| `processor/grpc/` 全部 | 删除 | — | §10 |
| `processor/http/` 24 个 | 替换 | `UnifiedIngressHandler` | §6 |
| `Session`/`ClientSession`/`ClientSessionGroupMapping` | 删除 | `TransportChannel`（§7.2） | §7/§10 |
| `ClientGroupPack`/`ClientGroupPackManagement` | 删除 | `SubscriptionManager`（集群级视图） | §4/§13.2.5 |
| `EventMeshTcpServer`/`EventMeshGrpcServer` | 删除 | `EventMeshHttpServer`（唯一传输） | §7/§10 |
| `Hello/Goodbye/Subscribe/UnSubscribeProcessor` | 删除 | HTTP subscribe/unsubscribe + 心跳即 poll | §6/§10 |
| `tcp/client/forward/CrossClusterForwardService` | 重设计 | 跨集群转发（去 TCP 依赖） | §13.2 |
| `tcp/client/session/retry/TcpRetryer` | 替换 | `Retryer`（HashedWheelTimer，§13.3.2） | §13.3.2 |
| `tcp/client/session/Session`（RateLimiter） | 替换 | `RateLimitFilter`（HTTP 侧） | §13.6.1 |
| `http/consumer/EventMeshConsumer`（LRUCache） | 替换 | `PushService`（TransportChannel） | §7.2 |
| `admin/handler/v1/` 19 个 | 重做 | 新 Admin 8 接口（§13.5.4） | §13.5.4 |
| `boot/EventMeshTlsConfig`/`SslContextFactory` | 保留 | TLS/mTLS（HTTP 侧复用） | §13.4.1 |
| `configuration/EventMeshDynamicConfigManager` | 保留 | 动态配置热更新 | §13.6.3 |

### D.5 公共（eventmesh-common）

| develop 现有 | 处理 | 迁移目标 | 章节 |
|-------------|------|---------|------|
| `protocol/tcp/Package.java` | 删除 | — | §10 |
| `protocol/http/HttpCommand.java` | 删除 | — | §10 |
| `common/Message.java` | 删除 | CloudEvent | §10 |
| `protocol/asm/`（TCP ASM 加密） | 删除 | — | §10 |
| `ssl/SslContextFactory` | 保留 | TLS/mTLS | §13.4.1 |

### D.6 安全（不用 SPI 插件，filter 链扩展）

> **v1.9 决策：security 不用 SPI 插件扩展，用内置 FilterChain + IngressFilter。** `eventmesh-security-plugin` 模块（及其 SPI jar）不接线。

| develop 现有 | 处理 | 迁移目标 | 章节 |
|-------------|------|---------|------|
| `eventmesh-security-api`（AclService/AuthService） | **不复用** | 用内置 `IngressFilter`/`FilterContext`/`FilterVerdict` | §13.4.2 |
| `eventmesh-security-acl`（AclServiceImpl） | **不复用** | 内置 `AclFilter`（规则模型 §13.4.2） | §13.4.2 |
| `eventmesh-security-auth-token` | **不复用** | 内置 `TokenAuthFilter` | §13.4.2 |
| `eventmesh-security-auth-http-basic` | **不复用** | 新增一个 `IngressFilter` 实现（代码扩展，非 SPI 插件） | §13.4.2 |
| `runtime/acl/Acl.java` | **不复用** | `AclFilter` 内置实现 | §13.4.2 |

扩展方式：新增安全能力 = 新增一个 `org.apache.eventmesh.runtime.uni.security.IngressFilter` 实现并注册进 `FilterChain`，无需打 SPI 插件包。

### D.7 元数据/注册（eventmesh-meta / eventmesh-registry）

> **v1.9 决策：弃用 Registry，只用 Meta。**

| develop 现有 | 处理 | 迁移目标 | 章节 |
|-------------|------|---------|------|
| `eventmesh-meta-api`（MetaService SPI） | 保留+扩展 | 唯一全局控制面（分区/订阅/offset/规则/实例发现） | §13.2.7/§15.5 |
| `eventmesh-meta-nacos/etcd/consul/zookeeper/raft` | 保留 | Meta 5 后端 | §13.2.7 |
| `eventmesh-meta-api/RateLimiterRulerListener` | 保留 | 限流规则动态下发 | §13.6.1 |
| `eventmesh-registry-api`（RegistryService） | **弃用** | 实例发现并入 MetaService，删除 RegistryService | §13.2.7 |
| `eventmesh-registry-nacos` | **弃用** | 由 `eventmesh-meta-nacos` 承担，删除该模块 | §13.2.7 |

### D.8 可观测性（只用 OpenTelemetry，默认 Prometheus）

> **v1.9 决策：可观测只用 OpenTelemetry（默认接 Prometheus），legacy metrics/trace 插件不接线、无 SPI 扩展点。** metrics 内置为 OTel 仪表（`UniMetrics`），默认经 OTel Prometheus exporter 暴露 `/metrics`；trace 走 OTel Tracer。不再有 `eventmesh-metrics-plugin` / `eventmesh-trace-plugin` 的 SPI 插件扩展形式。

| develop 现有 | 处理 | 迁移目标 | 章节 |
|-------------|------|---------|------|
| `eventmesh-metrics-api`（MetricsRegistry） | **不复用** | 内置 OTel Meter 仪表（`UniMetrics`），默认 Prometheus exporter | §13.5.1 |
| `eventmesh-metrics-prometheus` | **不复用** | 由 OTel Prometheus exporter 承担（内置，非 SPI 插件） | §13.5.1 |
| `eventmesh-trace-api`（EventMeshTraceService） | **不复用** | 内置 OTel Tracer 直接建 Span | §13.5.2 |
| `eventmesh-trace-zipkin/jaeger/pinpoint` | **不复用** | 由 OTel exporter（OTLP 等）承担 | §13.5.2 |
| 各 processor 的 trace 接入 | 迁移 | 新 Handler/Pipeline 节点用 OTel Span 埋点 | §13.5.2 |

### D.9 重试（内置，不用 SPI 插件）

> **v1.9 决策：retry 内置 `ReliableDispatcher`，不暴露 SPI 插件扩展。** `eventmesh-retry` 模块不接线。

| develop 现有 | 处理 | 迁移目标 | 章节 |
|-------------|------|---------|------|
| `eventmesh-retry-api`（Retryer/HashedWheelTimer） | **不复用** | 内置 `ReliableDispatcher`（指数退避 + DLQ） | §13.3.2 |
| `eventmesh-retry-rocketmq`（RocketMQRetryStrategyImpl） | **不复用** | 不需要（重试策略固定，无 SPI 扩展） | §13.3.2 |

### D.10 Connector（eventmesh-openconnect / eventmesh-connectors）

| develop 现有 | 处理 | 迁移目标 | 章节 |
|-------------|------|---------|------|
| `eventmesh-openconnect-java`（SourceWorker/SinkWorker） | 保留 | Connector Runtime 基类 | §8 |
| `eventmesh-openconnect-offsetmgmt-*` | 保留 | Connector 自有 OffsetStore | §8.9 |
| 24 个 connector（kafka/mysql/redis/...） | 保留 | 独立维护 | §8.8 |
| `eventmesh-runtime` 内 `ConnectorRuntimeService` | 删除/移出 | 移到独立 Connector Runtime 进程 | §8.7/§8.9 |
| `BlockingQueue<ConnectRecord>` 旁路 | 删除 | 走 HTTP（§8.7） | §8.7 |
| Source/Sink `System.exit(-1)` Bug | 修复 | 重写后消除 | §8.7 |

### D.11 SPI（eventmesh-spi）

| develop 现有 | 处理 | 迁移目标 | 章节 |
|-------------|------|---------|------|
| `eventmesh-spi`（EventMeshSPI/EventMeshExtensionFactory） | 保留 | 插件加载机制 | — |
| 各 `XxxPluginFactory`（Trace/Metrics/Protocol/Storage/Registry） | 保留 | 静态获取封装 | — |

### D.12 启动入口

| develop 现有 | 处理 | 迁移目标 | 章节 |
|-------------|------|---------|------|
| `EventMeshStartup` | 替换 | `EventMeshApplication` | §9 |
| `EventMeshBootstrap`（Http/Tcp/Grpc/Admin） | 替换 | `RuntimeContext` | §9 |
| `EventMeshTcpBootstrap`/`EventMeshGrpcBootstrap` | 删除 | — | §9/§10 |
| `RuntimeInstanceStarter`（v2） | 删除 | 整合到统一 Runtime | §9/§10 |

### D.13 迁移优先级

```
第一批（Phase 1-2，底层+核心）:
  Storage Plugin 改造（D.3）→ SubscriptionManager 新增
第二批（Phase 2.5-3.5，多实例+SDK）:
  Meta 控制面（D.7）→ SDK 替换（D.1）→ request-reply
第三批（Phase 4-5，入出方向）:
  UnifiedIngressHandler（D.4）→ PushService（D.4）→ 删 TCP/gRPC（D.4）
第四批（Phase 4.5-5.6，质量属性）:
  安全（D.6）→ 可靠性（D.9）→ 可观测（D.8）
第五批（Phase 6-7.5，Connector+Admin）:
  Connector 独立（D.10）→ Admin 重做（D.4）→ 启动统一（D.12）
第六批（Phase 8-8.5，收尾）:
  删 OpenMessaging/旧协议（D.1/D.2）→ WebHook → 测试套件（§18）
```

> **迁移原则**：先底层后上层、先核心后质量属性、先新增后删除（新通路跑通再删旧）。每批对应 §11 Phase，DoD 达标方进入下一批。删除类操作集中在 Phase 8（旧通路确认无引用后）。

---

## 附录 E： 定制（masa 子模块）迁移清单

> 已拆分为独立文档：[`-masa-migration-inventory.md`](./-masa-migration-inventory.md)。该文档盘点 `apache-eventmesh-old/masa-eventmesh` 子模块的 8 个  定制（connector-wemq / trace-weapm / trace-mss / registry-namesrv / registry-nacos / security-acl / wemq-access-starter / logappender），映射到新架构的处理方式（重写/合并/保留/弃）、重写要点、迁移批次（对应 §11 Phase）与风险点，作为 §15.4 基线决策的迁移资产登记。

---

## 附录 F：实现差距分析（v1.11，2026-07-06）

> 本附录是设计文档对照实际代码（`uni-architecture` 分支，66 个主类 + 24 个测试类）逐项核对的结果，记录**截至核对时点的实现缺口**。前文 §1–§18 + 附录 A–E 为目标设计；本附录标注"代码现状"，二者差异即为后续实施 backlog。复核时须以代码为准重新核对，勿仅凭本附录断言。

> **🔄 v1.12 进度（2026-07-06 实施第一批 quick win，已编译+测试通过）**：**G7**（Kafka `group.id` 已删，消除 MQ 无语义矛盾）、**G8**（`ClusterCoordinator` STICKY 改 partitionkey hash，多实例保序）、**G13**（`ReliableDispatcher` nack 重试加 ±20% jitter，7 参构造启 0.2、6 参=0 保持测试确定性）、**G14-TLS**（`TlsContextFactory` 升 TLSv1.3 + 独立 truststore 密码 + `EventMeshApplication.main` 读 `-Deventmesh.tls.*` 接线 HTTPS）。下表/正文标注保留 v1.11 盘点原文作历史，已修复项以本注记为准。G14 的 Filter/Legacy boot 接线、G1 WebSocket SDK 客户端、G3 fencing / G4 实例间转发 / G6 etcd 后端为后续批次。

> **🔄 v1.12 第二批（多实例核心，已编译+测试通过）**：**G1 服务端**（netty `UniWsServer` + `WsConnection` + boot `-Deventmesh.ws.port` 接线；SDK 客户端归 G16）、**G2** 分区不重叠拉取（`PartitionOwnership` 心跳 + 确定性 assign + `MeshStoragePlugin.partitionCount` + pollLoop 按 owned 分区）、**G12** 优雅停机释放分区租约（`ClusterMembership.leave`）、**G5** offset 两级存储（`MetaBackedOffsetStore` 本地 RocksDB + 异步刷 Meta，`readOffset=max(local,remote)`）。多实例核心（分区不重叠 + 租约 + offset 远程恢复）已具备；G3/G4 已于第三批完成，仅剩 G6 etcd 后端。

> **🔄 v1.12 第三批（多实例协调完整，已编译+测试通过）**：**G3** gen fencing（`PartitionOwnership` 分配表带 generation，Meta 存 `/em/assignments/<topic#partition>=<gen>|<owner>`，被接管时 gen 更大则自 fencing，§13.2.8④）、**G4+G10** 实例间转发（`HttpForwarder` POST `/internal/forward` + `ClusterMembership.addressOf`（heartbeat 值带 `timestamp|address`）+ `UniHttpServer` 端点 + reply 自寻址 `/internal/reply-forward`，§13.2.5/§17.6）。多实例 Phase 2.5 核心完整：分区不重叠 + 租约 + gen fencing + offset 两级 + 跨实例转发 + reply 自寻址。仅剩 G6 etcd Meta 后端（Nacos 的 prefix-scan/CAS 缺陷由此根治）。

> **🔄 v1.12 第四批（P1 安全+运维，已编译+测试通过）**：**G9** AclFilter 规则模型（新增 `AclRule`：principal/resource/action/effect/priority + `*`/`tenantId.*` 通配 + priority 降序 + DENY 优先 + 默认 DENY 白名单 + `setRules` 热更新供 Meta watch 下发，§13.4.2）、**G11** 慢消费者溢出策略矩阵（`PushService.OverflowPolicy` BLOCK/DROP_OLDEST/DROP_NEWEST/TO_DLQ + STALLED/EVICTED 暂停入队避免雪崩重投，§13.6.2①③；独立周期采样线程待后续）。剩余 P2：G15 已于第五批完成；G16 SDK / F.5 零散项待做。

> **🔄 v1.12 第五批（P2 Admin，已编译+测试通过）**：**G15** Admin 补全（`dlqBrowse` 浏览死信 + `/admin/dlq/browse`、`setRateLimit` 下发限流 + `/admin/ratelimit` PUT、`/admin/health` 加分区分配视图）。Admin 现覆盖 §13.5.4 全部 8 接口（subscriptions / offsets / clients / reject / dlq-browse / dlq-replay / ratelimit / health）。剩余 G16 SDK（WS/批量/重连/手动ACK） / F.5 零散项——批量/TTL/大消息已于第六批完成，其余待做。

> **🔄 v1.12 第六批（P2 批量+TTL+大消息，已编译+测试通过）**：**G16 批量** SDK `CloudEventsClient.publish(List)` + runtime `UniIngressService.publishBatch` + `/events/publish-batch` 端点（§13.7.3）；**F.5 TTL** `emttl` 过期事件在 dispatch 时丢弃（§13.3.4）；**F.5 大消息** `Content-Length > 1MB` 返回 413 Payload Too Large（§13.8.2，不自动分片）。剩余：G16 手动 ACK / poll 重连 / SSE+WS 客户端传输；F.5 租户命名空间 `<tenant>.<topic>` / 动态配置热更新 / 僵尸 poll 清理 / metrics 余 8 项（offset_lag / active_subscribers / pending_queue / slow_consumer / partition_owner 等）——手动 ACK / poll 重连 / request_reply metric 已于第七批完成，其余待做。

> **🔄 v1.12 第七批（P2 metrics+SDK，已编译+测试通过）**：**F.5 metrics** `eventmesh_request_reply_count` counter（§13.5.1，ingress `request` finally 埋点）；**G16 手动 ACK** SDK `CloudEventsClient.subscribeWithAck(Predicate)`——handler 返回 `true`=ack（offset 推进）/`false`=不 ack 等 dispatcher 超时重投，给客户端幂等窗口（§13.3.5），旧 `subscribe(Consumer)` 自动 ACK 保留；**G16 poll 重连** 异常后 backoff 1s 重试，避免服务器宕机时紧循环（Phase 3 DoD 自动重连）。剩余：G16 SSE+WS 客户端传输；F.5 租户/动态配置/僵尸poll/metrics Gauge 已于第八批完成（剩 4 项需复杂数据源的 Gauge）；P0 G6 etcd Meta 后端。

> **🔄 v1.12 第八批（F.5 运维+可观测，已编译+测试通过）**：**F.5 僵尸 poll 清理** `PushService.lastPollTime` + `getStaleClientIds` + `UniRuntime` 周期 60s 清理过期订阅（§13.6.5）；**F.5 metrics Gauge** `UniMetrics.registerGauge`（OTel ObservableGauge）+ `pending_queue_size` / `slow_consumer_count` / `active_topics` 三个 gauge（§13.5.1）；**F.5 动态配置热更新** `DynamicConfigWatcher` watch Meta `/em/ratelimit/rules` → `setTopicRateLimit`（§13.6.3 限流热更新；ACL 规则热更新待 G14 Filter boot 接线后接 `AclFilter.setRules`）；**F.5 租户命名空间** topic `<tenant>.<topic>` 前缀约定 + G9 `AclFilter` 跨租户 DENY 拦截（代码侧 topic 字符串隔离已够，§13.4.3）。剩余：metrics 3 项 Gauge（`offset_lag` / `partition_owner` / `poll_idle_ratio`，需带标签 ObservableGauge / MQ-offset 对比 / poll 统计，复杂，标注 roadmap）；P0 G6 etcd。G16 SSE+WS 客户端已于第九批完成。

> **🔄 v1.12 第九批（P2 收尾：SDK 传输客户端，已编译+测试通过）**：**G16 SSE 客户端** `CloudEventsClient.subscribeSse`（HttpURLConnection 读 `text/event-stream` 流，解析 `data:` 帧）+ **G16 WS 客户端** `subscribeWs`（`java.net.http.WebSocket`，§15.6 默认主传输，onText 回调解析帧 + shutdown 正常关闭）+ **F.5 `active_subscribers` gauge**。G16 三传输客户端齐全（Long-Polling / SSE / WebSocket），§5.1.1 三传输选型完整落地。F.5 3 项复杂 Gauge 已于第十批完成（F.5 全部完成，§13.5.1 全 16 项 metrics 落地）。

> **🔄 v1.12 第十批（F.5 可观测收尾：3 项复杂 Gauge，已编译+测试通过）**：**F.5 metrics** 3 项带标签 ObservableGauge——`poll_idle_ratio`（per topic，poll 空闲比例 per-mille）+ `partition_owner`（per `topic#partition#instance`，本实例 owned 分区标记 1）+ `offset_lag`（per `topic#partition`，MQ `endOffset` − 分发 offset），需 `UniMetrics.registerLabelledGauge` + `LabelledLong` + `MeshStoragePlugin.endOffset`（Kafka 用 `consumer.endOffsets` 实装，default -1 兜底）。§13.5.1 全 16 项 metrics 落地。**F.5 全部完成，P2 全部收尾**；G6 已于第十一批完成（复用 Nacos NamingService）。

> **🔄 v1.12 第十一批（G6 多实例收尾：复用 Nacos NamingService，已编译+测试通过）**：**G6** `NacosMetaStore` 对 `/em/instances/` prefix 改用 Nacos **NamingService**（`registerInstance` / `selectInstances(svc, healthy)` / `deregisterInstance`）做实例发现——根治 ConfigService 无 prefix-scan 导致多实例 `liveInstances()` 拿不全、分区分配失效（每实例全量拉取重复消费）的缺陷。其他 prefix（`/em/assignments/*` / `/em/offsets/*` / `/em/acl/rules`）仍走 ConfigService（单 key get/put + per-key watch，无 prefix-scan 需求）。复用现有 `nacos-client` 依赖，无新依赖；生产用 Nacos 直接可用，多实例 HA 名副其实。**多实例 Phase 2.5 全部完成，所有 P0/P1/P2 缺口收尾。**

### F.1 总体状态

新架构已是**可运行系统**：`EventMeshApplication.main` 启动 runtime + 流量 HTTP（`UniHttpServer`，JDK `HttpServer` + 虚拟线程）+ 独立 admin HTTP（`UniAdminServer`）。单实例快乐路径完整：publish → `MeshStoragePlugin.send` → `poll` → `SubscriptionManager.targetsFor` → `ReliableDispatcher.deliver` → `PushService` → 客户端 `ack` → `OffsetStore` 推进 offset。可靠性（ACK/指数退避重试/DLQ/STICKY）、SSE、TLS、TCP 兼容桥、HTTP 兼容桥均有真实代码。

但距离 §11 自定的**生产就绪门槛**（Phase 1–8 + 2.5 + 3.5 + 4.5 + 5.5 + 7.5）仍有差距，集中在 **Phase 2.5（多实例协调）整体只是骨架**——而这是文档自己定的 🔴 阻断项。下文按"严重程度 + 是否文档-代码矛盾"分类。

### F.2 🔴 阻断生产 / 文档-代码矛盾

| # | 缺口 | 文档要求 | 代码现状 | 证据 |
|---|------|---------|---------|------|
| G1 | **WebSocket 传输缺失** | §5.1.1/§15.6："默认 WebSocket 推送主传输"，三传输 WS/SSE/LP 用户可选 | 全仓无 WebSocket/WsConnection/handshaker 代码（grep 仅命中 LICENSE 文档）。实际只有 `SseConnection`（`/events/stream`）+ `LongPollingChannel`。SSE 实现为 `while(isOpen){pumpOnce(100); Thread.sleep(20)}` 忙等 | `UniHttpServer.stream`；SDK `CloudEventsClient` 仅 long-polling |
| G2 | **多实例分区不重叠未实装** | §13.2.3/§13.2.8：每实例只 assign 自己负责的分区 | 每实例 `assign(全部分区)` 全量拉取；`EventMeshApplication.enableCluster` 未建 `PartitionAssigner`、未调 `storage.assignPartitions` | `KafkaMeshStoragePlugin.poll`（lazy assign 全部分区）；`EventMeshApplication.enableCluster` |
| G3 | **gen fencing 防脑裂未实装** | §13.2.8④：poll 前查 generation/owner，过期自停 poll | 无 generation 概念。`ClusterMembership` 只有心跳时间戳 + TTL，`PartitionAssigner` 注释自承"production Meta-led path 可以覆盖"=未做 | `ClusterMembership`；`PartitionAssigner` |
| G4 | **实例间转发未实装** | §13.2.5：跨实例 HTTP POST `/internal/forward` | `Forwarder` 直接 `log.warn("...not yet implemented"); return false` | `EventMeshApplication` 第 69–74 行 |
| G5 | **offset 两级存储未实装** | §13.2.4：本地 RocksDB + 远程 Meta，`readOffset=max(local,remote)` | `OffsetStore` 接口纯本地，只有 `InMemoryOffsetStore`/`RocksDBOffsetStore`，无远程 Meta 层 | `OffsetStore` 接口；`UniAdminService` 注释"reflect the local instance until that layer lands" |
| G6 | **Meta 后端仅 Nacos（且 Nacos 实现有缺陷）** | §15.5/§13.2.7：nacos/etcd/consul/zk/raft **5 后端** | 仅 `NacosMetaStore`（真接 nacos `ConfigService`）+ `InMemoryMetaStore`，etcd/consul/zk/raft 全无。且 Nacos 配置模型有结构性缺陷：无 prefix-scan（`getWithPrefix` 仅返回本地见过的 key，非集群视图）、无 CAS（`putIfAbsent` 非原子）、无真 prefix-watch | `NacosMetaStore`（注释自承限制） |
| G7 | **Kafka 设了 group.id，违反"MQ 无语义"铁律** | §3.2/§13.2 铁律：不暴露 Consumer Group | `KafkaMeshStoragePlugin.init` 设 `GROUP_ID_CONFIG="eventmesh-storage-internal"`，而同类注释第 72 行写"NO group.id"。虽 `enable.auto.commit=false`，但同 group.id 的多实例 consumer 会触发 Kafka rebalance，与"自主协调、不用 MQ rebalance"冲突 | `KafkaMeshStoragePlugin` 第 80 行 |

> **影响**：E2E-02（三传输，仅 2 种）、E2E-18/19/20/21/23–26（多实例、fencing、迁移、转发、降级）在当前代码下**均不成立**。多实例部署 = 每实例全量拉取 + 重复消费。即 §11 自定的"生产 HA 前必做"的 Phase 2.5 实质未完成。

### F.3 🟠 已实现但方案需完善

| # | 缺口 | 文档要求 | 代码现状 | 证据 |
|---|------|---------|---------|------|
| G8 | **STICKY 多实例破坏顺序** | §13.3.3：同 partitionkey → 同 worker，保序 | 单实例 `SubscriptionManager.stickyIndex` 按 `partitionkey` hashCode 取模（✓）；但 `ClusterCoordinator.selectByMode` 的 `LOAD_BALANCE_STICKY` 走 RoundRobin（与 LB 同分支） | `ClusterCoordinator` 第 96–98 行 vs `SubscriptionManager.stickyIndex` |
| G9 | **AclFilter 是骨架** | §13.4.2：规则模型（principal/resource/action/effect/priority + DENY 优先 + 默认 DENY + Meta watch 下发） | `Map<String,Set<String>>` 静态 map，注释自承"skeleton... static map for simplicity"。无 priority/DENY/action 区分/Meta 下发 | `AclFilter` |
| G10 | **request-reply 跨实例自寻址未实装** | §17.6：`x-em-reply-instance` + 跨实例 `/internal/reply-forward` | `UniIngressService.reply` 只查本地 `pendingRequests`；`UniHttpServer.reply` 无 reply-instance 转发 | `UniIngressService.reply`；`UniHttpServer.reply` |
| G11 | **慢消费者状态机有缺陷** | §13.6.2②：周期采样 ackRate/queueLag + 溢出策略矩阵 | (a) 阈值用 offer **之前**的 size，80% 判定滞后；(b) 只由 `offer` 驱动，无独立周期采样→客户端不 poll 时永远 HEALTHY；(c) 溢出只有"nack 重投"一种，无 DROP_OLDEST/NEWEST/BLOCK/TO_DLQ 矩阵；(d) STALLED 期间仍 offer，与"暂停分发避免雪崩重投"矛盾 | `PushService.updateClientState` |
| G12 | **优雅停机缺"释放分区租约"** | §13.6.4 第 5 步：flush offset 后释放租约、通知 Meta 重分配 | `UniRuntime.shutdown` 有 drain+等 ACK+flush+close，无租约释放→多实例下停机分区需等 TTL（默认 15s）超时才被接管 | `UniRuntime.shutdown` |
| G13 | **重试无 jitter** | §13.3.2/A.3：`jitterRatio=0.2` ±20% | `backoffMs` 纯 `2^n`，无 jitter，重试风暴风险 | `ReliableDispatcher.backoffMs` |
| G14 | **TLS 小问题** | A.5：TLSv1.3 + 独立 truststore 密码 | `TlsContextFactory` 硬编码 `TLSv1.2`；truststore 用 `keystorePass` 解密（不能独立配）；`EventMeshApplication.main` 默认不接线 TLS/FilterChain/LegacyBridge → 默认启动明文+无鉴权 | `TlsContextFactory` 第 57、62 行；`EventMeshApplication.main` |

### F.4 Admin / SDK / Connector 完整度

| # | 项 | 文档 | 代码现状 |
|---|------|------|---------|
| G15 | **Admin 8 接口仅 ~5 个** | §13.5.4：subscriptions/offsets/clients/reject/dlq-browse/dlq-replay/ratelimit/health | `UniAdminService` 有 subscriptions/offsets/pendingDeliveries/rejectClient/dlqReplay/metrics；**缺** clients/dlq-browse/ratelimit 下发/health。数据为**进程内本地视图**，非集群级（Meta 聚合） |
| G16 | **SDK 仅 Long-Polling、无批量、无重连、ACK 不可控** | §5.1.1 三传输、§13.7.3 批量、Phase3 自动重连、§13.3.5 客户端幂等 | `CloudEventsClient` 4 API 齐全✓，但仅 long-polling、无 `publish(List)`、poll 异常只 log 不重连、ACK 在 handler 返回后自动触发（客户端无法控 ACK 时机做幂等窗口） |
| G17 | **Connector Runtime ✓ 基本到位** | §8/§8.9 | 独立模块 `eventmesh-connector-runtime` + `ConnectorApplication`（独立 main）✓；`RemoteOffsetStore`+`RocksDBConnectorOffsetStore`（EO 双写）✓；at-least-once commit-on-success 框架在。完成度最高 |

### F.5 🟡 其他缺失

| 项 | 文档 | 代码 |
|----|------|------|
| 批量 sendBatch（storage + SDK） | §13.7.3 | ❌ 均无 |
| TTL 过期丢弃（emttl） | §13.3.4 | ❌ 无处理 |
| 大消息限制 413 | §13.8.2 | ❌ 无 maxMessageSize 检查 |
| 租户 topic 命名空间 `<tenant>.<topic>` + 订阅视图 tenant 过滤 | §13.4.3 | ❌ SubscriptionManager 无 tenant 过滤 |
| 动态配置热更新 | §13.6.3 | ❌ 无 EventMeshDynamicConfigManager 接线 |
| 僵尸 poll 清理 / poll channel 上限 | §13.6.5 | ❌ 无 lastHeartbeat 清理、无连接上限 |
| 16 项 metrics | §13.5.1 | ⚠️ 8 项核心有，8 项带 *（lag/active_subscribers/pending_queue/slow_consumer/...）无 |
| S3Stream 存储后端 | §3.6/§15.8 | ❌ 仅文档规划，无实现 |
| HTTP server 生产化 | §6（注释提 netty 可换） | ⚠️ 用 JDK `com.sun.net.httpserver.HttpServer`，注释承认"production 可换 netty" |

### F.6 已确认实现（对照 backlog 避免误判）

以下项经核对**确已实现**，复核时可跳过：单实例三分发模式（LB/BROADCAST/MULTICAST/STICKY）、`ReliableDispatcher`（ACK 推进 offset + 指数退避 + DLQ + trace 埋点）、`OffsetStore`（RocksDB key=`topic#clientId#partition`）、`UniTrace`（OTel span：publish/dispatch/ack/retry/dlq）、`TokenBucketRateLimiter`、`FilterChain`/`IngressFilter` 体系、`SignatureVerifierFilter`（HMAC-SHA256）、TCP 兼容桥（`UniTcpServer`+翻译层）、HTTP 兼容桥（`LegacyHttpBridge`）、`WebHookChannel`（签名头+重试+DLQ）、`ClusterCoordinator`（本地/转发路由框架，转发本身见 G4）、Java 21 虚拟线程（`UniHttpServer` 用 `newVirtualThreadPerTaskExecutor`）。

### F.7 建议优先级

**P0（修矛盾 / 阻断项声明）**
- G7 删 Kafka `group.id`（1 行，铁律矛盾）或文档说明其用途
- G1 WebSocket：补实现 **或** 修文档降级为 roadmap（当前文档-代码背离最严重）
- G2–G6 Phase 2.5：补 fencing+转发+两级 offset+多 Meta 后端 **或** 文档明确"当前仅单实例可用，Phase 2.5 待完成"，避免误判可上生产

**P1（已实现但方案完善）**
- G8 ClusterCoordinator STICKY 改 partitionkey hash
- G10 request-reply 跨实例 reply-forward
- G9 AclFilter 规则模型 / G11 慢消费者周期采样 / G13 重试 jitter / G14 TLS 独立 truststore 密码 + TLSv1.3
- G14 `EventMeshApplication` boot 接线 TLS/FilterChain/Legacy（配置驱动）

**P2（补全）**
- G15 Admin 补 clients/dlq-browse/ratelimit/health + 集群级视图
- G16 SDK 批量 + 重连 + 手动 ACK
- G6 补 etcd Meta 后端 或 文档限定 Nacos 缺陷
- F.5 其余项

> **说明**：本附录是**核对时点快照**。代码持续演进，复核前请重读相关源码——本文档正文 §1–§18 + 附录 A–E 是目标设计，本附录 F 是"代码现状"，二者差异随实现推进收敛。

---

## 十九、架构深化：内部 EventMeshFrame + FrameAdaptor SPI + 全面粘性（v2.0，2026-08-13）

> 本节整合自 `eventmesh-architecture-refinement.md` v2.0。三块深化落地全绿（全 unit + 5.x E2E，含 RocketMQ5BrokerIntegrationTest 普通 pub/sub 2/2 + LegacyTcpClientIntegrationTest 旧 TCP SDK）。

### 19.1 内部全程 EventMeshFrame

#### 架构分层

```
┌─ 对外协议层(FrameAdaptor SPI)──────────────────────────────────────┐
│  CloudEvents(HTTP/SSE/WS)  → CloudEventsFrameAdaptor  → EventMeshFrame │
│  MeshMessage(legacy TCP)   → MeshMessageFrameAdaptor   → EventMeshFrame │
│  A2A(JSON-RPC 2.0)         → A2AFrameAdaptor           → EventMeshFrame │
│  未来新协议                → 新 FrameAdaptor            → EventMeshFrame │
└───────────────────────────────┬───────────────────────────────────────┘
                                │ 对外协议直接转 Frame,不经 CloudEvent 互转
┌───────────────────────────────▼───────────────────────────────────────┐
│  内部(runtime + storage):全程 EventMeshFrame                            │
│  ingress publish → Frame → storage.send(Frame) → MQ 字节                 │
│  MQ 字节 → storage.poll()→Frame → dispatch/filter/TTL → Frame → egress   │
└───────────────────────────────┬───────────────────────────────────────┘
                                │ egress: Frame → 对应 FrameAdaptor → 客户端协议
┌───────────────────────────────▼───────────────────────────────────────┐
│  egress(FrameAdaptor SPI,按客户端连接协议)                                │
│  Frame → CloudEvents-JSON(SSE/WS/HTTP poll)                              │
│  Frame → MeshMessage Package(legacy TCP)                                  │
│  Frame → A2A JSON-RPC bytes(A2A 回调)                                     │
└────────────────────────────────────────────────────────────────────────┘
```

**CloudEvent 不是内部表示**——它是 CloudEvents 客户端的对外入口格式,经 `CloudEventsFrameAdaptor` 转 Frame 后进入内部。MeshMessage 和 A2A 同理,各自有独立 adaptor,互不经过 CloudEvent。

#### EventMeshFrame wire 格式

```
定长头 14B: [magic=0xEF][ver][msgType:1][flags:1][seq:4][keyCount:2][dataLen:4]
KV 属性段:  keyCount × [nameLen:2][name][valLen:4][value]   ← 通用,所有 msgType 共用
data:       raw bytes(streaming 的 chunk/prompt / 事件的业务 data)

msgType = STREAM_REQ | STREAM_CHUNK | EVENT
flags   = done | hasError | hasMeta(streaming 用)
```

- **STREAM_REQ**:KV(`sid`/`replyTo`/`model`/`conv`)+ data=prompt。
- **STREAM_CHUNK**:`seq`(定长)+ `done`(flag)+ KV(`sid`/`etype`/`err`/`meta`)+ data=chunk。一个 `"He"` token ~25B vs CE-JSON ~250B(**~10×**)。
- **EVENT**:KV(`id`/`type`/`subject`/`time`/`emttl`/`emcorrelationid`/用户扩展...)+ data=业务载荷。

#### WireCodec SPI（内部 MQ wire 编码）

内部 MQ wire 的字节编解码由 `WireCodec` SPI 定义(`eventmesh-common/.../wire/`),默认实现 `EventMeshFrameCodec`(EventMeshFrame↔byte[]),可通过 `-Deventmesh.wire.codec=<fqcn>` 替换。

#### 落地范围

- **storage SPI**:`MeshStoragePlugin.send/poll` + `LiteTopicCapable.sendLite/pullLite` 全改 EventMeshFrame(3 插件迁,带 legacy CE-JSON fallback)。
- **runtime dispatch 管线**:Delivery/BufferedEvent/PushChannel/Connection/DeadLetterSink/ReliableDispatcher/PushService/所有 channel + CloudEventFilter/SubscriptionManager 全翻 Frame。
- **streaming**:Mode-1(runtime↔agent 跨进程)+ Mode-2(runtime 内部 pub/sub)全用 EventMeshFrame。
- **legacy 连接器 SPI**(Producer/Consumer)不动(独立子系统,说 CE,边界转换)。

### 19.2 FrameAdaptor SPI（协议转换收进插件）

对外协议 ↔ EventMeshFrame 的双向转换由 `FrameAdaptor` SPI 定义(`eventmesh-protocol-api/.../FrameAdaptor.java`),各协议插件各自实现。runtime 不直接调 `EventMeshFrame.fromCloudEvent()` / `.toCloudEvent()` / `MeshMessageFrameCodec`——全部经 `FrameAdaptors.get(协议名)` 加载对应 adaptor。**加新协议只需实现 `FrameAdaptor` + 注册 SPI,不改 runtime 代码。**

#### 协议插件模块清单

| 模块 | 职责 |
|------|------|
| **eventmesh-protocol-api** | SPI 接口(`FrameAdaptor` + `ProtocolAdaptor`)+ `FrameAdaptors` 加载器(零 adaptor 实现) |
| **eventmesh-protocol-cloudevents** | `CloudEventsFrameAdaptor`:CloudEvents-JSON ↔ EventMeshFrame(注册 `cloudevents`) |
| **eventmesh-protocol-meshmessage** | `MeshMessageFrameAdaptor`:Package ↔ EventMeshFrame(注册 `meshmessage`,零 CE 中转)+ 旧 `MeshMessageProtocolAdaptor`(兼容) |
| **eventmesh-protocol-a2a** | `A2AFrameAdaptor`:JSON-RPC ↔ EventMeshFrame(注册 `a2a`,零 CE 中转)+ `EnhancedA2AProtocolAdaptor`(兼容) |
| ~~eventmesh-protocol-grpc~~ | **已删除**(空壳无源码,gRPC 解析在 meshmessage 模块) |

#### MeshMessage ↔ Frame 直接转换（零 CloudEvent）

`MeshMessageFrameAdaptor`(meshmessage 插件)直接映射 Package ↔ EventMeshFrame,不经 CloudEvent:

- **ingress**:`MeshMessagePackageRouter` → `FrameAdaptors.get("meshmessage").toFrameSilent(pkg)` → EventMeshFrame。字段映射:topic→`subject`,body→`data`,header.seq→`id`,properties→KV。
- **egress**:`NettyTcpPushChannel` → `FrameAdaptors.get("meshmessage").fromFrameSilent(frame)` → Package。字段反向映射。

旧 TCP SDK 全程 MeshMessage,内部全程 EventMeshFrame,零 CloudEvent 中转。

### 19.3 Offset 路②：自管 + MQ 重放接管

#### 两层 offset

| 层 | 含义 | key 维度 | 实现 |
|----|------|----------|------|
| **pull offset** | 从 MQ 存储拉到哪 | `parent#lite@queue` / `topic#partition` | storage 插件本地(properties 文件) |
| **deliver/ack offset** | 投递给 client 到哪 + client ack 到哪 | `topic#clientId#partition` | `OffsetStore`(RocksDB 本地) |

两层**不强行合并**(语义不同、解耦),各留各的本地存储。

#### 路②细则

- **不给 group.id**,EventMesh 完全自管 offset(MQ 仅作持久 FIFO)。
- **offset 纯本地**,**meta 上报默认关**(`-Deventmesh.offset.meta=true` 显式开)。淘汰 `MetaBackedOffsetStore`(百万 key 上报),类保留备用。
- **接管 = MQ 重放(无状态迁移)**:实例挂 → client 经 §19.4 负载推荐重连新实例 → 从业务 topic 重拉 → in-flight 走 at-least-once 重投(业务幂等兜底)。
- 核实结论:`pullAndDispatchPartition` 传 `startOffset=-1` 给 `storage.poll`(插件自管游标),`OffsetStore` 不在接管路径上;rocketmq5 用 POP(broker 侧 at-least-once),`assignPartitions` 是 no-op。

### 19.4 负载均衡：session 分配层 + 全面粘性

#### 架构定位

> **均衡做在 session 分配层(入口 recommend),不在拉取/分发层。**

实例只为自己代理的 client 被动按需拉取,不对等、不转发。先均衡"哪个 client 归哪个实例"(session 层),自然导致"各实例拉取量大致相当"。

#### 客户端零负担——负载全自采

EventMesh 实例本地自采负载指标(`LoadMeter`):
- `activeSessions` ← `SessionRouter` sinks/subscribeSinks size
- `inflowBytes/s`、`outflowBytes/s` ← `UniIngressService` 入口/出口 data 字节累加(按 clientId 分桶)
- `cpuLoad` ← `OperatingSystemMXBean`

随 5s 心跳写入 `/em/instances/<id>` = `<ts>|<addr>|<activeSessions>|<byteRate>|<cpuLoad>`。

#### 均衡闭环

- `/session/recommend`:读集群全局负载评分,返回推荐实例 `instanceUrl`(score = sessions + byteRate + cpuLoad 加权,过载负反馈)。
- `/session/open` + `/events/subscribe`:返回 `instanceUrl`,SDK pin 后续 turn/close/poll/ack。
- 大 client 分散:recommend 检查 client 现有 session 分布,散到多实例。
- SDK 失败跨实例转移:open 失败重新 recommend。
- `advertisedAddr` 默认空(单实例/测试/LB 兼容),显式配 `-Deventmesh.http.advertisedAddr` 才 pin。

#### 全面粘性（广播域也走实例自治）

`enableCluster` 不再接线 `PartitionOwnership`/`ClusterCoordinator`/`HttpForwarder`(类保留备用),只保留 `ClusterMembership` 心跳(供 recommend 评分)。实例拉全分区本地分发,**不跨实例转发**。

### 19.5 验证

1. **单测**:`EventMeshFrame` 全 msgType 互转(12 例);`OffsetStore` 两 key 空间共存;`LoadMeter` 指标 + 每 client 画像;`ClusterMembership` 心跳负载;dispatch 管线 Frame 化(ReliableDispatcher/SubscriptionManager/ClusterCoordinator)。
2. **E2E**(真 broker):streaming 多轮 + Mode 2 pub/sub + **普通 pub/sub(RocketMQ5BrokerIntegrationTest 2/2)**全绿,内部全程 EventMeshFrame 往返正确;**LegacyTcpClientIntegrationTest(旧 TCP SDK)全绿**(MeshMessage↔Frame 直接转换)。
3. **构建**:系统 gradle 8.5 + WEOA Nexus(offline)。

---

## 二十、流式调用设计（整合 streaming-session-design + sdk-streaming-call-design + lite-streaming-call）

> 本节整合自 `streaming-session-design.md`、`sdk-streaming-call-design.md`、`lite-streaming-call.md`(POC v1 参考)。

### 20.1 概述

EventMesh 流式调用让客户端发起请求后，**持续接收一串文本分片（token / delta）**，直到收到结束标记——和 OpenAI Chat Completions 的 `stream: true`、SSE 打字机效果一致。

两种正交模式：

| 模式 | 用途 | 入口 | 机制 |
|------|------|------|------|
| **Mode 1 — 流式调用** | 客户端发起请求，Agent（接 LLM）逐 token 回复 | `openSession` → `call` → `forEach` | runtime 中介，通道多路复用 + client 亲和 |
| **Mode 2 — 发布/订阅** | 生产者往 session lite 写 chunks，消费者 SSE 订阅 | `subscribeSession` / `openSessionPublisher` | 确定性 lite 命名，无 agent、无撮合 |

**SDK 零 MQ 依赖**——只发 HTTP/SSE，runtime 是唯一接 MQ 的。内部全程 EventMeshFrame（§19.1），streaming 帧 msgType = STREAM_REQ / STREAM_CHUNK。

### 20.2 Mode 1 — 流式调用

#### 通道拓扑（AgentAnchoredStrategy）

| 通道 | lite | 用途 |
|------|------|------|
| 请求 | `agent.<agentId>`（挂 `agent-parent-<i>`） | agent **启动时订阅一次**，所有 session 请求进这条、按 sessionId 解复用 |
| 回复 | `client.<clientId>`（挂 `client-parent`） | runtime 持有该 client SSE 的实例消费，同 client 多 session 共用、按 sessionId 解复用 |

**sessionId 格式 = `<agentId>:<uuid>`**（runtime 从 `:` 前缀零查表路由到 agent）。

#### Session 生命周期

```
① agent 上线:   POST /agent/register → runtime 分配 parent + 写 MetaStore → 回 parent
                  → agent subscribe (agent-parent-<i>, agent.<agentId>) + ready
② 握手(open):   client POST /session/open {clientId}
                  runtime 查 MetaStore clientId→agent: 撮合或复用 → 生成 sessionId
                  回 {sessionId, agentId, instanceUrl}
③ 流式(stream): client POST /session/stream/{sessionId} {prompt}
                  runtime: 注册 StreamSink → publish STREAM_REQ → agent lite
                  agent: 取上下文 + 调 LLM → chunk 发到 replyTo(client.<clientId>)
                  runtime: poll reply lite → demux → SSE 给 client
                  done: endTurn（drop sink，保留 consumer 跨 turn 复用）
④ 多轮:         同 sessionId 再 POST /session/stream → 同 agent + 累积上下文
⑤ 关闭(close):  POST /session/close/{sessionId} → cancel（清本地状态）
```

**关键分离**: `endTurn`（自然终止，drop sink 保留 consumer）vs `cancel`（销毁 session）。修复了多轮重复 chunk bug。

#### agent 注册协议（ready-before-route）

agent 是薄 HTTP 客户端，不直连 broker/MetaStore。`status:READY` 必须在 subscribe 之后才置位——撮合只选 `READY && 心跳新鲜` 的 agent，保证不丢首包。

#### 内部 wire（EventMeshFrame）

- STREAM_REQ: `WireCodec.encode(StreamRequest)` → EventMeshFrame(STREAM_REQ)。KV(`sid`/`replyTo`/`model`/`conv`)+ data=prompt。
- STREAM_CHUNK: `WireCodec.encode(StreamChunk)` → EventMeshFrame(STREAM_CHUNK)。`seq`(定长)+ `done`(flag)+ KV(`sid`/`etype`/`err`/`meta`)+ data=chunk。

### 20.3 Mode 2 — 发布/订阅

**确定性 lite 命名**（无绑定表、无撮合）：
- **parent** = 固定配置常量（`sessionStreamParent`，6 参 SessionRouter 构造器传入）
- **liteKey** = `"session." + sessionId`（sessionId 的纯函数）

两端用同一规则算出相同 (parent, liteKey) → 同一物理 lite topic。

| 入口 | 方法 | 作用 |
|------|------|------|
| `/session/publish/{sessionId}` | POST | 发布一帧 chunk → `publishLite(parent, "session."+sid)`；首次懒惰 `createLiteTopic`；返回 201 |
| `/session/subscribe/{sessionId}` | GET | SSE 订阅 → `pollLite(parent, "session."+sid)` → SSE 流；每 session 至多一个订阅消费者 |

**单游标 + 免费重放**:lite 单分区 1-queue，崩溃重启可从 lite 头重新播放。

### 20.4 SessionRouter 内部机制

#### 构造器

```java
// 4 参：Mode 1 专用
SessionRouter(ingress, registry, strategy, defaultTimeoutMs)
// 6 参：Mode 1 + Mode 2
SessionRouter(ingress, registry, strategy, defaultTimeoutMs, sessionTtlMs, sessionStreamParent)
```

#### 核心方法

| 方法 | 模式 | 作用 |
|------|------|------|
| `startStream` | 1 | 发 STREAM_REQ 到 agent lite，创建 StreamSink |
| `endTurn` | 1 | 自然结束：drop sink + demux 移除（保留 reply consumer 跨 turn） |
| `cancel` | 1 | 销毁 session：drop sink + demux + 清 SessionRegistry |
| `addReplySession` | 1 | 注册 demux 映射（clientId → sessionId → StreamSink） |
| `ensureReplyConsumer` / `runReplyConsumer` | 1 | poll reply lite → 按 sessionId demux → StreamSink |
| `publishSession` | 2 | 懒惰 createLiteTopic + publishLite |
| `startSubscribe` / `cancelSubscribe` | 2 | putIfAbsent 守卫单订阅 + pollLite 循环 → StreamSink |

#### StreamSink（有界队列）

容量 1024(`MAX_BUFFERED_CHUNKS`)。SSE 写出线程 drain。队满丢帧（记录 warning，bounded memory）。

#### Session Reaper

后台定时（`Math.min(sessionTtlMs/2, 60s)`）回收 idle session（`registry.expireStaleSessions` → `cancel`）。

### 20.5 消息协议（CloudEvents 分帧）

内部 wire 只有 EventMeshFrame（STREAM_REQ / STREAM_CHUNK）。**没有独立的 OPEN/CLOSE/DONE 帧走 MQ**。终止（DONE）折叠进 STREAM_CHUNK 的 `done=true`。session 的 open/close 是 HTTP 控制面操作。

```java
STREAM_REQ(msgType=1):
  data = prompt(text/plain)
  KV: sid(=sessionId), replyTo(=parent#lite), model?, conv?

STREAM_CHUNK(msgType=2):
  data = chunk text
  KV: sid, etype?, err?, meta?(JSON)
  seq = 流内序号(定长头)
  done = flag bit
```

### 20.6 上下文存储（Mode 1）

| 方案 | 绑定 | 上下文 | 抗 agent 重启 |
|------|------|------|------|
| **A（当前）** | MetaStore | agent 进程内（ConversationStore） | ❌ 重启丢（re-handshake 或降级） |
| B | MetaStore | MetaStore（截断/限轮） | ✅ 受大小限制 |
| C | MetaStore | Redis | ✅ 但引入 Redis |

agent 重启则该 agent 的上下文丢失（绑定仍在）。接口预留方案 C（Redis）。

### 20.7 POC v1 历史（lite-streaming-call）

v1（POC）已被 v2 取代。v1 问题：每流一个 lite（高基数）、req 广播（多 agent 重复处理）、无持久 session、无 client→agent 绑定。v2 用通道多路复用 + client 亲和 + 持久 sessionId 解决。v1 文档保留作历史参考。

---

## 二十一、SDK 流式调用接口设计（整合 sdk-streaming-call-design）

> 本节整合自 `sdk-streaming-call-design.md`，描述客户端侧 SDK 封装。

### 21.1 架构分层

```
┌─ SDK (eventmesh-sdk-java) ─────────────────────────────────┐
│  CloudEventsClient.streaming() → HTTP + SSE 客户端          │
│  • Mode 1: openSession → call → forEach                     │
│  • Mode 2: subscribeSession / openSessionPublisher          │
└───────────────────────┬────────────────────────────────────┘
                        │ POST /session/*  (HTTP + SSE)
┌───────────────────────▼────────────────────────────────────┐
│  Runtime session 层 (SessionRouter / Matchmaker /           │
│  AgentRegistrar / ChannelStrategy)                          │
└───────────────────────┬────────────────────────────────────┘
                        │ publishLite / pollLite (EventMeshFrame)
┌───────────────────────▼────────────────────────────────────┐
│  Storage (RocketMQ5RemotingStoragePlugin, LiteTopicCapable) │
└───────────────────────▲────────────────────────────────────┘
                        │ publish CHUNKs → reply lite
┌───────────────────────┴────────────────────────────────────┐
│  Agent (eventmesh-agent: StreamingAgent)                    │
│   订阅 req lite → 调 LLM → chunk 流 publish 到 replyTo       │
└─────────────────────────────────────────────────────────────┘
```

### 21.2 SDK 消费侧

**唯一消费姿态 `forEach`**：

```java
public interface StreamingResponse extends AutoCloseable {
    String sessionId();
    String agentId();
    CompletableFuture<Void> forEach(Consumer<StreamChunk> onChunk);
    @Override void close();
}
```

**设计决策——为什么只有 `forEach`**：
- `forEach` 覆盖所有场景：回调消费、`.join()` 阻塞、`.orTimeout()` 超时、`CompletableFuture` 组合。
- 删除了 `subscribe(StreamHandler)`、`iterator()`、`publisher(Flow)`、`blockAsString()`——API 表面积最小。
- 删除 `callOneShot`——`openSession` + `call` + `forEach` + `close` 更清晰（session 生命周期显式）。

### 21.3 多轮入口

```java
public interface StreamingOperations {
    StreamingSession openSession(OpenSession req);
}

public interface StreamingSession extends AutoCloseable {
    String sessionId();
    String agentId();
    String instanceUrl();        // pin 的实例 URL（空=用原 baseUrl）
    StreamingResponse call(String prompt);
    StreamingResponse call(StreamRequest req);
    @Override void close();
}
```

- `openSession` → `POST /session/open` → 匹配 Agent → `{sessionId, agentId, instanceUrl}`
- `session.call(prompt)` → `POST /session/stream/{sessionId}` → SSE 流
- `session.close()` → `POST /session/close/{sessionId}` → 销毁会话
- 若 `instanceUrl` 非空，`StreamingSession` 用 `client.withBaseUrl(instanceUrl)` pin 后续 turn/close

### 21.4 内部机制：SSE 读取 → 有界队列 → `forEach`

```
SSE 读取 VT ──offer──►  [有界 LinkedBlockingQueue (1024)]
 (POST /session/stream)     │
                            └── forEach: 独立 VT drain 队列 → 调 onChunk
```

- 队满丢帧（bounded memory）。
- `forEach` 在独立虚拟线程上 drain；收到终止帧后 complete future。
- 单消费者守卫：每个 `StreamingResponse` 只能调一次 `forEach`。

### 21.5 Mode 2 SDK

```java
// 订阅（消费）
StreamingResponse sub = client.subscribeSession("my-session-id");
sub.forEach(c -> ...).join();

// 发布（生产）
SessionPublisher pub = client.openSessionPublisher("my-session-id");
pub.publish("Hello", false);
pub.publish("", true);  // 终止帧
```

`subscribeSession` 返回的 `StreamingResponse` 与 Mode 1 完全相同——`forEach` 是唯一消费方式。`SessionPublisher` 每次 `publish()` 发一个独立 HTTP POST，拥有 seq 计数器。

### 21.6 SDK 零 MQ 依赖

SDK 只发 HTTP/读 SSE，**不引入任何 MQ 客户端依赖**。这是硬性设计规则。Mode 1: `POST /session/open` → `POST /session/stream/{sid}` → 读 SSE → `POST /session/close/{sid}`。Mode 2: `POST /session/publish/{sid}`（生产）或 `GET /session/subscribe/{sid}`（消费 SSE）。

### 21.7 HTTP 端点

| 路径 | 方法 | 作用 |
|------|------|------|
| `/session/open` | POST | 开 session（握手 + 匹配 Agent），返回 `{sessionId, agentId, instanceUrl}` |
| `/session/stream/{sessionId}` | POST | 发起一轮流式调用（Mode 1），返回 SSE 流 |
| `/session/close/{sessionId}` | POST | 关闭 session |
| `/session/publish/{sessionId}` | POST | 发布一帧 chunk（Mode 2），返回 201 |
| `/session/subscribe/{sessionId}` | GET | 订阅 session 流（Mode 2），返回 SSE 流 |
| `/session/recommend` | GET | 推荐实例（负载均衡），返回 `{instanceUrl}` |

---

*文档版本：v2.1 | EventMesh 简化重构方案 | 基于 unified-runtime-design.md v2.1 演进 + architecture-refinement v2.0 + streaming docs 整合 | 2026-08-13*
*v1.10 变更：**老协议不直接删——保留为边缘协议适配器以兼容老客户端。** ① TCP 退化为新架构 ingress/egress 传输（与 HTTP/WebHook/长轮询并列）：保留线协议(`Package`/`Command`/`Codec`)+翻译层(`TcpMessageProtocolResolver`/`MeshMessageProtocolAdaptor`)+netty server 骨架，删除 TCP 自有核心(`ClientSession`/`ClientGroupPack`/rebalance、Consumer Group)。新增 `transport.tcp` 包(`TcpPushChannel`/`TcpAckRegistry`/`TcpIngressBridge`/`TcpFrameCodec`+`TcpFrameDecoder`)。② legacy HTTP(`EventMeshHttpClient`/`EventMeshMessage`/`/eventmesh/*` webhook-push)同理：`transport.http.legacy` 包(`LegacyHttpBridge`/`LegacyHttpCodec`)，老 HTTP subscribe(url+topics) 映射为 `WebHookChannel` 出向推送，publish 翻译为 CloudEvent→核心。老 TCP/HTTP 客户端零改动。更新 Phase 8 DoD。*
*v1.9 变更：四项决策固化——① **可观测只用 OpenTelemetry，默认接 Prometheus**（metrics 走 OTel Meter 仪表、trace 走 OTel Tracer Span，默认经 OTel Prometheus exporter 暴露；legacy `eventmesh-metrics-prometheus` / `eventmesh-trace-plugin`(zipkin/jaeger/pinpoint) 不再接线、无 SPI 扩展点，附录 D.8）；② **弃用 `eventmesh-registry`，只用 `eventmesh-meta`（MetaService）** 作为唯一控制面（附录 D.7 registry 行改为弃用）；③ **retry 内置 `ReliableDispatcher`，不暴露 SPI 插件扩展**（`eventmesh-retry` 不接线，附录 D.9）；④ **security 不用 SPI 插件扩展，用内置 FilterChain + IngressFilter**（`eventmesh-security-plugin` 不接线，扩展 = 新增 IngressFilter 实现，附录 D.6）。更新 §13.5、§13.3.2、§13.4.2、§13.2.7、§15.5、Phase 4.5/5.5/5.6。实现侧 `UniMetrics`（OTel 仪表）、`ReliableDispatcher`（内置重试）、`FilterChain`/`IngressFilter`（filter 安全）均已落地。*
*v1.1 变更：新增 §13「能力缺口与设计补充」——对照 develop 分支现有能力，补齐多实例协调、下发可靠性、安全、可观测性、运维、接入扩展、协议工程化 7 类缺口设计，并更新 §14 实施 Phase 影响。*
*v1.2 变更：新增 §15「用户决策记录」（固化 MQ 完全自主协调 / At-Least-Once+幂等 / 全删 TCP+gRPC / 暂不锁定落地基线 四项根基决策）、§16「交付语义边界」（澄清 Connector offset EO 与客户端下发 at-least-once 为不同层面，消除与 migration-plan 的表面矛盾）；修正 §14 Admin Server 对比行为"重做"并澄清 AdminClient 通道不变。*
*v1.3 变更：SDK 传输层升级为 HTTP 家族三选（WebSocket 默认/SSE 单向流/Long-Polling 降级，§5.1.1/§7.2/§13.7.1）；新增 §17「request-reply 同步调用」（对齐 TCP 同步调用语义，超时丢弃）；§13.2 控制面归 Meta 注册中心（新增 §13.2.7 Meta vs Admin 边界）；新增 §15.6–15.8 决策（三传输 / S3Stream 多后端 / Java21 虚拟线程）；明确 RocksDB 在 offset 两级存储中的"本地完整副本+Meta写卸载+降级兜底"定位；§11 Phase 计划更新。*
*v1.4 变更：深化三处核心设计——新增 §13.2.8「分区分配协议细节」（确定性分配+租约心跳+gen fencing 防脑裂+offset 迁移+v2 leader 增强，MQ 无语义下自实现 fencing）；新增 §17.6「request-reply 路由自寻址」（纠正 §17.3 过度耦合，应答无需 Meta 全局路由表，用 reply-to+reply-instance 自寻址）；新增 §3.6「S3Stream 存储后端」（v1 Kafka 线协议薄包装 / v2 原生 SDK、跨后端语义对齐表、三种后端统一抹平 MQ 语义）。*
*v1.5 变更：深化五处——§13.6.2 背压细化（有界队列+溢出策略矩阵+慢消费者状态机 HEALTHY/SLOW/STALLED/EVICTED+与 ACK/重试/DLQ 联动+广播隔离+配置项）；§13.4.2 ACL 规则模型（主体/客体/操作/效果+priority 匹配+DENY 优先+Meta 存储与 watch 下发+租户隔离联动）；§13.5 可观测性（16 项 metrics 指标定义表含标签、traceparent+tracestate+baggage 透传、关键节点 Span 链路设计）；§8.9 Connector 与 Meta/S3Stream 集成（只读 Meta 不参与分区协调、经 Runtime 间接用存储、Connector offset 独立 EO 本地RocksDB+AdminServer）；§13.2.9 降级端到端时序（Meta 挂→自洽各路径行为→恢复对齐→保证矩阵）。*
*v1.6 变更：深化五处——§13.3.2 重试器时间轮实现（HashedWheelTimer+退避策略+jitter+与 ACK/offset/DLQ 时序+crash 靠 offset 不超前恢复+配置项）；§13.7.2 WebHook 签名/重试/DLQ 集成（HMAC-SHA256 canonical 签名+X-Em-Timestamp 防重放+X-Em-Delivery-Id 去重+2xx=ACK+offset 推进+配置项）；§3.6.6 v2 原生 S3Stream SDK（Stream/Offset API+与 Kafka 兼容层差异表+partition↔Stream 映射策略+分级存储利用）；§11 各 Phase 补 DoD 验收标准（16 个 Phase 全覆盖，🔴 阶段详列）；新增 §18 端到端测试用例设计（45 个用例分 11 类，标注前置/步骤/预期/覆盖章节+Phase 对应）。*
*v1.7 变更：§13.7.2 补 WebHook 适用场景边界说明（主线推送 vs WebHook 旁路，何时用/不用）；新增四附录——附录 A 配置项总表（9 类配置项汇总，散落各章归一）、附录 B CloudEvents extension 字段全集（x-em-* 规范化，7 类字段含类型/必填/章节）、附录 C 部署拓扑示例（单实例/多实例/多租户/S3Stream 四拓扑+选型矩阵+可组合）、附录 D develop 代码迁移映射表（13 类模块的删除/替换/保留/新增+6 批迁移优先级）。*
*v1.8 变更：§15.4 落地基线决策确定（基于当前工作区 -1.15.0-port，认可已吸收的 meta/openconnect/A2A/raft 但按新架构重调， 定制按新接口重写，上游选择性 backport 不追求完全合并）；新增附录 E「 定制（masa 子模块）迁移清单」（盘点 apache-eventmesh-old/masa-eventmesh 8 个子模块：connector-wemq/trace-weapm/trace-mss/registry-namesrv/registry-nacos/security-acl/wemq-access-starter/logappender，标注老接口→新架构对应、重写要点、迁移批次、风险点，最复杂为 connector-wemq 内嵌 RocketMQ patch+EventMeshMessage 依赖）。*
