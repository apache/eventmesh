# EventMesh 统一运行时架构设计

> 分支：`refactor/unified-runtime-pipeline`
> 目标：以单一 EventMesh Runtime 替代 runtime-v1/runtime-v2 双运行时，承载全部消息处理 + Connector 管理 + A2A 协议

---

## 结论先行

**统一运行时是 EventMesh 从"协议网关"演进为"A2A 消息总线"的正确路径。**

它按照五层协议栈架构组织：编程模型层（OpenMessaging API / A2A Protocol）→ 数据格式层（CloudEvents Envelope）→ 传输协议层（TCP/HTTP/gRPC）→ 处理引擎层（Pipeline Filter → Transformer → Router）→ 存储层（Kafka/RocketMQ/Pulsar）。所有协议入口和 Connector 数据流都经过统一的 Pipeline 处理链，消除 runtime-v1 和 runtime-v2 之间的代码重复和进程隔离开销，同时保留并增强原 Runtime V2 的管理面能力（Admin Server 通信、动态 Job 调度、Offset 管理、指标上报、数据校验）。

一次性完成迁移，不留兼容尾巴，最终形态只有 **一个 Runtime，一套 Pipeline，一个部署模型**。

---

## 一、目标架构总览

```
                              Admin Server (独立进程)
                              gRPC BiStream 双向流
                                      │
            ┌─────────────────────────┼─────────────────────────┐
            │                         ▼                         │
            │  ┌──────────────────────────────────────────────┐ │
            │  │        Admin Client (运行时管理面)             │ │
            │  │  Heartbeat │ Monitor │ Status │ Verify       │ │
            │  │  Job Dispatch │ Config Sync │ Offset Sync    │ │
            │  └──────────────────────────────────────────────┘ │
            │                                                  │
            │  ┌──────────────────────────────────────────────┐ │
            │  │         编程模型层 (Programming Model)        │ │
            │  │  ┌──────────────┐  ┌─────────────────────┐    │ │
            │  │  │OpenMessaging │  │  A2A Protocol       │    │ │
            │  │  │Producer/Push │  │  Agent Card/Task    │    │ │
            │  │  │Consumer/Sub  │  │  SSE Streaming      │    │ │
            │  │  └──────┬───────┘  └──────────┬──────────┘    │ │
            │  │         │                      │ 基于 HTTP     │ │
            │  └─────────┼──────────────────────┼──────────────┘ │
            │            │         编码为        │                │
            │  ┌─────────┴──────────────────────┴──────────────┐ │
            │  │         数据格式层 (Data Format)               │ │
            │  │  CloudEvents Envelope                        │ │
            │  │  id · source · type · specversion · data     │ │
            │  └──────────────────────┬───────────────────────┘ │
            │                         │ 序列化                   │
            │  ┌──────────────────────────────────────────────┐ │
            │  │         传输协议层 (Transport)                │ │
            │  │  TCP Endpoint · HTTP Endpoint · gRPC Endpoint│ │
            │  └──────────────────────────────────────────────┘ │
            │                       │                            │
            │  ┌──────────────────────────────────────────────┐ │
            │  │         Ingress/Egress Pipeline              │ │
            │  │  FilterEngine → TransformerEngine → Router   │ │
            │  │    ↑ 所有传输层和 Connector 数据必经          │ │
            │  └──────────────────────────────────────────────┘ │
            │                                                  │
            │  ┌──────────────────────────────────────────────┐ │
            │  │       Connector Runtime Service              │ │
            │  │  · 多 Connector 并行 (Source + Sink)         │ │
            │  │  · 动态 Job 生命周期管理                     │ │
            │  │  · 本地 Offset 存储 (RocksDB)                │ │
            │  └──────────────────────────────────────────────┘ │
            │                                                  │
            │              EventMesh Runtime (单进程)            │
            └──────────────────────────────────────────────────┘
                                      │
                         ┌───────────┼───────────┐
                         ▼           ▼           ▼
                      Kafka      RocketMQ     Pulsar
                    (Storage Plugin 可插拔)
```

### 五层协议栈

OpenMessaging、CloudEvents、A2A 不是平级竞争关系——它们是**不同维度的协议规范**，在 EventMesh 中分层协作：

| 层 | 协议/规范 | 角色 | 解决什么问题 |
|---|----------|------|-------------|
| **Layer 5 · 编程模型** | OpenMessaging API + A2A Protocol | 客户端编程接口 | 开发者用什么 API 收发消息 |
| **Layer 4 · 数据格式** | CloudEvents | 统一事件信封 | 消息在系统间传输的标准格式 |
| **Layer 3 · 传输** | TCP / HTTP / gRPC | 字节搬运 | 消息怎么从 A 点到达 B 点 |
| **Layer 2 · 处理引擎** | Pipeline (Filter → Transformer → Router) | 安全 + 路由 + 转换 | 消息在处理过程中做什么校验和转换 |
| **Layer 1 · 存储** | Kafka / RocketMQ / Pulsar | 持久化 + 消费 | 消息存在哪里、怎么消费 |

**关键区分：**

```
                    ┌─────────────────────────────────┐
                    │  OpenMessaging API               │  ← "怎么发消息" (编程模型)
                    │  Producer.send(message)          │
                    │  Consumer.subscribe(topic)       │
                    ├─────────────────────────────────┤
                    │  A2A Protocol                    │  ← "Agent 怎么通信" (编程模型)
                    │  agentCard.discover()            │
                    │  task.create(message)            │
                    └────────────┬────────────────────┘
                                 │ 两者最终都编码为 CloudEvents
                    ┌────────────▼────────────────────┐
                    │  CloudEvents Envelope            │  ← "消息长什么样" (数据格式)
                    │  {                               │
                    │    "id": "xxx",                  │
                    │    "source": "/service/order",   │
                    │    "type": "order.created",      │
                    │    "specversion": "1.0",         │
                    │    "data": { ... }               │
                    │  }                               │
                    └────────────┬────────────────────┘
                                 │ 序列化后经传输协议发出
                    ┌────────────▼────────────────────┐
                    │  TCP / HTTP / gRPC               │  ← "怎么搬运" (传输)
                    └─────────────────────────────────┘
```

- **OpenMessaging** 定义的是 API 契约：`Producer`/`Consumer`/`PushConsumer`/`Subscribe`。它是开发者写代码时调用的接口——"我调用 `producer.send()` 就能发消息"。OpenMessaging 与传输协议无关——同一套 API 可以跑在 TCP 上，也可以跑在 HTTP 上。
- **CloudEvents** 定义的是数据格式：所有消息（不管来自 OpenMessaging 还是 A2A）在进入 Pipeline 之前都会被编码为 CloudEvents 信封。Pipeline 只认 CloudEvents——这也是为什么 Filter/Transformer/Router 能对 TCP/HTTP/gRPC/A2A/Connector 统一处理。
- **A2A** 和 OpenMessaging 处于同一层：都是编程模型。区别在于 OpenMessaging 面向传统消息 Pub/Sub（"发一条消息到 Topic"），A2A 面向 Agent 间通信（"创建一个 Task 让下游 Agent 执行"）。两者都基于 CloudEvents 编码，都走 Pipeline。

**A2A 和 OpenMessaging 的平级关系：**

| 维度 | OpenMessaging | A2A |
|------|--------------|-----|
| 层级 | Layer 5 (编程模型) | Layer 5 (编程模型) |
| 语义 | Pub/Sub 消息 | Agent 任务 |
| 客户端 API | `Producer.send()` / `Consumer.subscribe()` | `AgentCard.discover()` / `Task.create()` |
| 传输绑定 | TCP / HTTP / gRPC 均可 | 仅 HTTP (REST + SSE) |
| 内部编码 | CloudEvents | CloudEvents |
| 典型场景 | 微服务间异步消息 | AI Agent 间协作调用 |

> **核心原则：OpenMessaging 和 A2A 是"客户端怎么说"，CloudEvents 是"数据怎么传"，TCP/HTTP/gRPC 是"字节怎么走"。三者正交，不互斥。**

### 协议对象全枚举

在五层协议栈中，每层都有对应的**具体 Java 对象**。理解这些对象的分层归属，是读懂 EventMesh 协议体系的关键。

#### 对象分层总览

```
Layer 5 编程模型（客户端 API 层）
  ┌──────────────────────────────────────────────────┐
  │  io.openmessaging.api.Message  (OpenMessaging)   │
  │  EventMeshMessage              (简化模型)         │
  │  A2A Message / MCP Request     (Agent 通信)      │
  └─────────────────────┬────────────────────────────┘
                        │ 序列化 / 编码
Layer 4 数据格式（传输载体）
  ┌──────────────────────────────────────────────────┐
  │  ProtocolTransportObject ← 所有传输对象的基接口   │
  │  ┌──────────┬──────────┬──────────┬───────────┐  │
  │  │ TCP:     │ HTTP:    │ gRPC:    │ A2A:      │  │
  │  │ Package  │HttpEvent │CloudEvent│SimpleA2A  │  │
  │  │          │ Wrapper  │Wrapper   │Transport  │  │
  │  └──────────┴──────────┴──────────┴───────────┘  │
  └─────────────────────┬────────────────────────────┘
                        │ ProtocolAdaptor.toCloudEvent()
Layer 3.5 统一内部格式
  ┌──────────────────────────────────────────────────┐
  │  io.cloudevents.CloudEvent                       │
  │  ← 所有 ProtocolAdaptor 的输出，Pipeline 的输入   │
  └──────────────────────────────────────────────────┘
```

#### 逐对象说明

| 对象 | 全类名 | 层级 | 角色 | 字段/说明 |
|------|--------|------|------|----------|
| **OpenMessaging Message** | `io.openmessaging.api.Message` | Layer 5 | 客户端编程模型 | 标准消息 API（topic/body/properties/key/tag），用户写代码时直接操作 |
| **EventMeshMessage** | `o.a.e.common.EventMeshMessage` | Layer 5 | 简化编程模型 | `bizSeqNo` + `uniqueId` + `topic` + `content`(String) + `prop`(Map)。比 OpenMessaging 更轻量，TCP/HTTP/gRPC SDK 通用 |
| **A2A Message** | JSON (MCP/Agent Card 序列化) | Layer 5 | Agent 通信语义 | Agent Card / Task / SSE Event。客户端不直接操作 Java 类，而是通过 JSON 序列化 |
| **Package** | `o.a.e.common.protocol.tcp.Package` | Layer 4 | TCP 传输帧 | `Header`(命令码 + 路由信息) + `body`(Object)。TCP 二进制线的协议帧格式——**不是消息格式，是帧格式** |
| **HttpEventWrapper** | `o.a.e.common.protocol.http.HttpEventWrapper` | Layer 4 | HTTP 传输载体 | `headerMap` + `sysHeaderMap` + `body`(byte[]) + `requestURI`。HTTP 请求的通用载体 |
| **HttpCommand** | `o.a.e.common.protocol.http.HttpCommand` | Layer 4 | HTTP 传输载体(旧) | `opaque` + `requestCode` + `Header` + `Body`。标记 `@Deprecated`，逐步被 `HttpEventWrapper` 替代 |
| **CloudEvent (gRPC)** | `o.a.e.common.protocol.grpc.cloudevents.CloudEvent` | Layer 4 | gRPC 传输 PB | Protobuf 定义的 CloudEvents 1.0 规范消息。注意：这是 **Protobuf 序列化格式**，不是 `io.cloudevents.CloudEvent` |
| **EventMeshCloudEventWrapper** | `o.a.e.common.protocol.grpc.common.EventMeshCloudEventWrapper` | Layer 4 | gRPC 传输载体 | 包裹一个 gRPC CloudEvent，实现 `ProtocolTransportObject` |
| **BatchEventMeshCloudEventWrapper** | `o.a.e.common.protocol.grpc.common.BatchEventMeshCloudEventWrapper` | Layer 4 | gRPC 批量载体 | 包裹一批 gRPC CloudEvent |
| **ProtocolTransportObject** | `o.a.e.common.protocol.ProtocolTransportObject` | Layer 4 | 传输对象基接口 | 标记接口（extends `Serializable`），所有传输对象实现它。**Pipeline 不直接处理这类对象**——由 ProtocolAdaptor 先转换为 CloudEvents |
| **io.cloudevents.CloudEvent** | `io.cloudevents.CloudEvent` | Layer 3.5 | 统一内部格式 | CloudEvents 1.0 规范的标准 Java 接口。**Pipeline 的唯一数据格式**。所有 ProtocolAdaptor 的输出统一为这个格式 |

#### ProtocolAdaptor —— 协议转换枢纽

**所有 `ProtocolTransportObject` 必须先通过 `ProtocolAdaptor` 才能进入 Pipeline：**

```java
// SPI 接口，关键方法签名
public interface ProtocolAdaptor<T extends ProtocolTransportObject> {

    // 入口：将任意传输对象转换为 CloudEvent
    CloudEvent toCloudEvent(T protocol) throws ProtocolHandleException;

    // 批量入口
    List<CloudEvent> toBatchCloudEvent(T protocol) throws ProtocolHandleException;

    // 出口：将 CloudEvent 转换回传输对象（下发给消费者）
    ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException;

    // 协议标识
    String getProtocolType();   // "cloudevents" / "http" / "meshmessage" / "openmessage" / "a2a"
}
```

**当前注册的 Adaptor 与协议类型映射：**

| Adaptor | `getProtocolType()` | 输入 → 输出 | 用途 |
|---------|---------------------|-------------|------|
| `CloudEventsProtocolAdaptor` | `cloudevents` | gRPC CloudEvent PB → io.cloudevents.CloudEvent | gRPC CloudEvents 入口 |
| `HttpProtocolAdaptor` | `http` | `HttpEventWrapper` → io.cloudevents.CloudEvent | HTTP 入口 |
| `MeshMessageProtocolAdaptor` | `meshmessage` | TCP `Package`(body=EventMeshMessage) → io.cloudevents.CloudEvent | TCP EventMeshMessage 入口 |
| `OpenMessageProtocolAdaptor` | `openmessage` | `io.openmessaging.api.Message` → io.cloudevents.CloudEvent | OpenMessaging 客户端入口 |
| `EnhancedA2AProtocolAdaptor` | `a2a` | A2A JSON → io.cloudevents.CloudEvent（内部委托 CloudEvents + HTTP Adaptor） | A2A Agent 通信入口 |

#### 完整数据流（从客户端到存储再回头）

```
Client SDK
    │
    │  用户操作: producer.send(new EventMeshMessage(topic, content))
    │  或者:      openMessagingProducer.send(new Message(topic, body))
    │  或者:      a2aClient.createTask(agentRequest)
    │
    ▼
[序列化为 Transport]
    ├── TCP:  EventMeshMessage → Package(Header + body)
    ├── HTTP: EventMeshMessage → HttpEventWrapper(headerMap + body)
    ├── gRPC: EventMeshMessage → CloudEvent protobuf → EventMeshCloudEventWrapper
    └── A2A:  JSON string → SimpleA2AProtocolTransportObject
    │
    ▼ 网络传输
    │
[EventMesh Runtime 接收]
    │
    ▼
ProtocolAdaptor.toCloudEvent(ProtocolTransportObject)
    │  各 Adaptor 将传输对象统一转换为 io.cloudevents.CloudEvent
    │
    ▼
Pipeline: Filter → Transformer → Router
    │  ← 只操作 io.cloudevents.CloudEvent，不关心来源协议
    │
    ▼
Storage Plugin → Kafka / RocketMQ / Pulsar
    │
    ▼ (当消费者订阅 Topic 时)
Egress Pipeline: Router → Transformer → Filter
    │
    ▼
ProtocolAdaptor.fromCloudEvent(cloudEvent)
    │  将 CloudEvent 转回消费者期望的传输格式
    │
    ▼
[序列化并发送给消费者]
    ├── TCP Consumer:  Package(Header + EventMeshMessage)
    ├── HTTP Push:     HttpEventWrapper
    ├── gRPC Push:     CloudEvent protobuf
    └── A2A:           JSON (Agent Message)
    │
    ▼
Client 反序列化为编程模型对象 (EventMeshMessage / OpenMessage / A2A Message)
```

> **关键结论：**
> 1. **ProtocolTransportObject 是"信封的外壳"**（TCP 帧 / HTTP 请求体 / gRPC PB），不包含业务语义
> 2. **io.cloudevents.CloudEvent 是"信封的内胆"**（统一的事件数据格式），包含业务语义
> 3. **ProtocolAdaptor 是"开信封的机器"**——拆开各种协议外壳，露出统一的 CloudEvents 内胆给 Pipeline
> 4. **Pipeline 永远不碰 ProtocolTransportObject**——它只处理 `io.cloudevents.CloudEvent`

---

## 二、核心组件设计

### 2.1 Pipeline 核心 —— IngressProcessor / EgressProcessor

**统一消息处理链，所有协议入口和出口共用同一套 Filter → Transformer → Router 管线。**

```
消息流入 (任何协议)
    │
    ▼
IngressProcessor
    ├── Filter Engine   → 鉴权、限流、协议校验、规则匹配
    ├── Transformer Engine → 协议转换、字段映射、消息丰富化
    └── Router Engine    → 按 Topic/Header 路由到目标 MQ Topic
    │
    ▼
  Storage Plugin (Kafka / RocketMQ / Pulsar)
    │
    ▼
EgressProcessor
    ├── Filter Engine   → 消费者过滤、订阅匹配
    ├── Transformer Engine → 消息格式转换、脱敏
    └── Router Engine    → 推送到目标协议处理器 (TCP/HTTP/gRPC/Connector Sink)
```

**Pipeline Context 协议（最终形态）：**

```java
interface PipelineStage {
    PipelineResult process(PipelineContext ctx);
}

class PipelineResult {
    enum Action { CONTINUE, DROP, RETRY, DLQ, FAIL }

    Action action;
    CloudEvent event;
    Throwable cause;
    Map<String, String> metadata;
}
```

- `CONTINUE`：正常流转到下一阶段
- `DROP`：静默丢弃（替代当前 `null` 返回语义）
- `RETRY`：重试（携带重试次数等上下文）
- `DLQ`：路由到死信队列
- `FAIL`：抛出异常/告警

当前 Pipeline 保留 `null` 兼容层，`DROP` 语义等价于返回 `null`，长期以 `PipelineResult.Action` 为规范接口。

**已接入的协议路径：**

| 协议 | 处理器 | 接入方式 | 层级 |
|------|--------|----------|------|
| TCP Producer | `PublishCloudEventsProcessor` | `IngressProcessor.process()` | 传输层 |
| TCP Consumer | `ClientGroupWrapper` | `EgressProcessor.process()` | 传输层 |
| HTTP Send | `SendAsyncMessageProcessor` / `SendSyncMessageProcessor` | `IngressProcessor.process()` | 传输层 |
| HTTP Batch | `BatchSendMessageProcessor` / `BatchSendMessageV2Processor` | `IngressProcessor.process()` | 传输层 |
| gRPC Publish | `RequestCloudEventProcessor` | `IngressProcessor.process()` | 传输层 |
| gRPC Batch | `BatchPublishCloudEventProcessor` | `IngressProcessor.process()` | 传输层 |
| A2A | A2A Gateway → HTTP Endpoint | `IngressProcessor.process()` | 业务协议 (基于 HTTP) |
| Source Connector | `EventMeshConnectorBootstrap` | `IngressProcessor.process()` | 数据源 |
| Sink Connector | `EventMeshConnectorBootstrap` | `EgressProcessor.process()` | 数据目标 |

> **A2A 是业务协议，不是传输协议。** A2A 的 REST API 和 SSE 都走 HTTP Endpoint 进入 Pipeline，A2A Gateway 负责解析 Agent Card / Task 语义，将结构化消息交给 Pipeline 处理。TCP/HTTP/gRPC 是传输层，A2A 在其之上。

---

### 2.2 Connector Runtime Service

**在统一 Runtime 内管理多个 Connector 实例，支持动态注册/启停。**

```java
public class ConnectorRuntimeService {
    // 注册 Connector（动态）
    void registerConnector(ConnectorConfig config) throws Exception;

    // 卸载 Connector（动态）
    void unregisterConnector(String connectorName) throws Exception;

    // 启停
    void startConnector(String connectorName) throws Exception;
    void stopConnector(String connectorName) throws Exception;

    // 状态查询
    List<ConnectorStatus> getConnectorStatuses();
    ConnectorStatus getConnectorStatus(String connectorName);
}
```

| 能力 | 实现方式 |
|------|----------|
| 多 Connector 并行 | 默认独立线程池（可切共享池） |
| 动态注册/卸载 | 通过 HTTP/gRPC 管理 API |
| 故障隔离 | 独立 ClassLoader + try-catch 边界，单 Connector 异常不影响其他 |
| 配置热更新 | 通过 Admin Server 下发，ConnectorRuntimeService 热重载 |
| 插件发现 | SPI 机制 + 配置文件声明 |

**线程池策略设计：**

> 为什么默认独立线程池而非共享池？

| 方案 | 优点 | 缺点 | 适用 |
|------|------|------|------|
| **DEDICATED**（默认） | 故障隔离绝对；背压 per-Connector；出问题秒定位到哪个 Connector | 多 Connector 时空闲线程浪费 | 生产多租户 |
| **SHARED** | 资源利用率高 | 一个慢 Connector 占满池 → 全部堵死；head-of-line blocking | 低负载/同质 Connector |
| VIRTUAL（Java 21+） | 极轻量，百万并发 | EventMesh 需兼容 Java 8/11；`synchronized` 会 pin carrier；不解决故障隔离 | 未来可选 |

核心取舍：Connector 是插件代码，质量不可控——一个 Source 的 `poll()` 阻塞在共享池里会把整个 Runtime 的消息处理全卡死。

**提供两种线程池模式，通过配置切换：**

```properties
# DEDICATED: 每个 Connector 独立线程池（生产推荐）
# SHARED: 所有 Connector 共享一个线程池（轻量场景）
eventmesh.connector.thread.pool.mode=DEDICATED

# DEDICATED 模式下每个 Connector 的线程数（默认 2，非重型 Connector 1-2 足够）
eventmesh.connector.thread.pool.size=2

# SHARED 模式下的全局线程数
eventmesh.connector.thread.pool.shared.size=8
```

**ConnectorConfig 数据模型：**

```java
class ConnectorConfig {
    String connectorName;       // rocketmq-source / http-sink
    ConnectorType type;         // SOURCE / SINK
    String pluginClass;         // 全限定类名
    Map<String, String> props;  // Connector 配置
    ThreadPoolMode poolMode;    // DEDICATED / SHARED（未设置走全局默认）
    int threadPoolSize;         // 线程池大小（DEDICATED 默认 2）
    int maxRetry;               // 最大重试次数
}

enum ThreadPoolMode { DEDICATED, SHARED }
```

**Connector 注册上限：**

当前 Connector 注册无界（`ConcurrentHashMap` 直接 put），这在生产环境下是危险的——100 个 Connector 可能耗尽 JVM 内存/线程/FD。增加可配置上限，reach 上限时 `registerConnector()` 抛出 `ConnectorLimitExceededException`。

```java
public class ConnectorRuntimeService {
    private final int maxConnectors;  // 上限，-1 = 无限制

    void registerConnector(ConnectorConfig config) throws ConnectorLimitExceededException {
        if (maxConnectors > 0 && connectors.size() >= maxConnectors) {
            throw new ConnectorLimitExceededException(
                "Max connectors reached: " + maxConnectors);
        }
        // ...
    }
}
```

```properties
# -1 = 无限制，0 = 仅允许配置文件声明的静态 Connector
eventmesh.connector.max.count=16
```

**上限计算规则（预估）：**

| 资源 | 每 Connector 消耗 | 16 个 Connector 总消耗 |
|------|------------------|----------------------|
| 线程 (DEDICATED) | 2 | 32 threads — 4-core safe |
| 内存 | ~5MB (ClassLoader + buffer) | 80MB — 2GB heap 只占 4% |
| 网络连接 | ≥1 (Source 侧) | 16+ conns |

默认值 `16` 覆盖常见生产场景（4 source + 4 sink + 8 function-connector），保守且安全。可通过 Admin Server 动态调大。

```properties
# 运行时动态调整
eventmesh.connector.max.count=-1    # 关闭上限（开发环境）
eventmesh.connector.max.count=0     # 仅允许配置文件静态声明
eventmesh.connector.max.count=32    # 生产扩容
```

#### Connector 完整数据流

**一个 Connector Job 是 Source+Sink 配对。理想路径是 Source 拉取 → Pipeline → Storage 持久化 → Pipeline → Sink 写入目标——全程经过统一安全链。**

```
Connector Job 完整数据流（最终目标）：

┌────────────────────┐          ┌─────────────────────────────────────────────┐          ┌────────────────────┐
│  MySQL CDC         │          │              EventMesh Runtime               │          │  Snowflake         │
│  (Source)          │          │                                              │          │  (Sink)            │
│                    │  poll()  │  ┌─────────────────────────────────────┐    │          │                    │
│  SourceConnector ──┼──────────┼─→│  Ingress Pipeline (统一入口)         │    │          │                    │
│  · connect()       │          │  │  ┌───────────────────────────────┐  │    │          │                    │
│  · poll()          │          │  │  │ FilterEngine (责任链)         │  │    │          │                    │
│  · commit(offset)  │          │  │  │  ① AuthFilter                 │  │    │          │                    │
│                    │          │  │  │  ② RateLimitFilter            │  │    │          │                    │
│                    │          │  │  │  ③ ProtocolFilter             │  │    │          │                    │
│                    │          │  │  │  ④ RuleFilter                 │  │    │          │                    │
│                    │          │  │  │  ⑤ AclFilter                  │  │    │          │                    │
│                    │          │  │  │  ⑥ SizeLimitFilter            │  │    │          │                    │
│                    │          │  │  └───────────────────────────────┘  │    │          │                    │
│                    │          │  │         ↓ (放行)                     │    │          │                    │
│                    │          │  │  TransformerEngine (消息转换)        │    │          │                    │
│                    │          │  │         ↓                            │    │          │                    │
│                    │          │  │  Router (Topic 路由)                 │    │          │                    │
│                    │          │  │         ↓                            │    │          │                    │
│                    │          │  │  Producer.send(CloudEvent)           │    │          │                    │
│                    │          │  └─────────────┬───────────────────────┘    │          │                    │
│                    │          │                │                             │          │                    │
│                    │          │                ▼                             │          │                    │
│                    │          │  ┌─────────────────────────────────────┐    │          │                    │
│                    │          │  │  Storage Plugin (可插拔)             │    │          │                    │
│                    │          │  │  · Kafka / RocketMQ / Pulsar        │    │          │                    │
│                    │          │  │  · 消息持久化 + Offset 管理          │    │          │                    │
│                    │          │  │  · 支持 Replay / 回溯 / DLQ         │    │          │                    │
│                    │          │  └─────────────┬───────────────────────┘    │          │                    │
│                    │          │                │                             │          │                    │
│                    │          │                ▼                             │          │                    │
│                    │          │  ┌─────────────────────────────────────┐    │          │                    │
│                    │          │  │  Egress Pipeline (统一出口)          │  Consumer │                    │
│                    │          │  │  Filter → Transformer → Router      │  .poll()  │                    │
│                    │          │  └─────────────┬───────────────────────┘    │          │                    │
│                    │          │                │                             │          │                    │
│                    │          │                └─────────────────────────────┼──────────→  SinkConnector
│                    │          │                                              │          │  · put(records)
│                    │          └──────────────────────────────────────────────┘          │  · flush()
│                    │                                                                   │
└────────────────────┘                                                                   └────────────────────┘

关键设计点：
  ① 所有 Connector 数据强制经过 Pipeline → 安全策略对 Connector 与协议消息一视同仁
  ② Storage 持久化 → 消息不丢，Offset 可回溯，Source 和 Sink 彻底解耦
  ③ Ingress/Egress 对称 → Source 走 Ingress, Sink 走 Egress, 同一套 Filter/Transformer/Router
```

```
旧架构对比（当前 v2 ConnectorRuntime 的实际行为）：
 
 Source.poll()
      │
      ▼  ❌ 无 Ingress Pipeline
 BlockingQueue<ConnectRecord>  (纯内存, 容量 1000)
      │
      ▼  ❌ 无 Storage 持久化
 Sink.put()
      │
      ▼  ❌ 无 Egress Pipeline
  外部系统

三个致命问题：
  · 无 Pipeline → ACL/Auth/RateLimit 对 Connector 完全失效
  · 无 Storage → 进程挂了 BlockingQueue 里的数据全丢，Offset 无法恢复
  · Source/Sink 紧耦合 → 换 Sink 要改 Source 配置，无法独立伸缩
```

> **设计原则：Pipeline 是唯一数据面，所有入口（TCP/HTTP/gRPC/A2A/Connector Source）共享同一条 Filter → Transformer → Router 链。**

```

---

### 2.3 Admin Client —— 运行时管理面通信

**内置 Admin Server 客户端，统一 Runtime 通过 gRPC BiStream 双向流与管理面上报状态、接收指令。**

```
Runtime (每个实例)  ──gRPC BiStream──>  Admin Server
    │                                       │
    ├── Heartbeat (5s 间隔)                 ├── Runtime 健康状态汇总
    ├── Monitor Report (30s 间隔)           ├── 指标存储与查询
    ├── Verify Report (按需)                ├── 数据校验汇总
    ├── Status Update (状态变更时)           ├── Runtime 状态看板
    └── Offset Sync (60s 间隔)              ├── 集群级 Offset 管理
                                            └── Job 调度指令下发
```

**AdminClient 能力矩阵：**

| 组件 | 来源 | 职责 |
|------|------|------|
| **HealthService** | 迁移自 runtime-v2 | 心跳上报，携带 runtimeAddress + status + activeJobCount |
| **MonitorService** | 迁移自 runtime-v2 | Pipeline 处理延迟、Connector 吞吐量、TPS/QPS 指标采集上报 |
| **VerifyService** | 迁移自 runtime-v2 | 在 Ingress/Egress 处理链中埋点，支持端到端数据校验 |
| **StatusService** | 迁移自 runtime-v2 | Runtime 级别状态管理（STARTING/RUNNING/DEGRADED/STOPPING） |
| **AdminCommandHandler** | 新增 | 接收 Admin Server 下发的 Job 调度指令 |

**配置项：**

```properties
# Admin Server 通信
eventmesh.admin.server.enabled=true
eventmesh.admin.server.address=localhost:50051
eventmesh.admin.server.heartbeat.interval.seconds=5
eventmesh.admin.server.monitor.report.interval.seconds=30

# 降级模式（Admin Server 不可用时 Runtime 可独立运行）
eventmesh.admin.server.required=false
```

- 当 `required=false` 且 Admin Server 不可达时，Runtime 降级为单机模式，不影响消息处理核心路径
- 当 `required=true` 时，Admin Server 不可达则 Runtime 启动失败

---

### 2.4 Offset 管理 —— Exactly-Once 保障

**双写策略：本地 RocksDB（进程重启不丢） + 远程 Admin Server（集群级恢复）。**

```
Connector Source 消费消息
    │
    ├──► 本地 Offset Store (RocksDB)
    │    Key: {connectorName}:{topic}:{partition}
    │    Value: {position, timestamp}
    │    写入时机: 每条消息处理完成后
    │
    └──► 远程 Offset Manager (Admin Server)
         同步时机: 每 60s 批量上报
         恢复时机: Runtime 启动时从 Admin Server 拉取
```

**OffsetStore 接口：**

```java
public interface OffsetStore {
    void save(String connectorName, String topic, int partition, String position);
    String load(String connectorName, String topic, int partition);
    Map<String, String> loadAll(String connectorName);
    void flush();
    void close();
}
```

恢复优先级：本地 RocksDB > 远程 Admin Server > 配置默认值（latest/earliest）

---

### 2.5 动态 Job 管理

**通过 HTTP REST API 和 gRPC 指令两种方式管理 Connector Job 生命周期。**

**HTTP 管理 API：**

```
POST   /admin/jobs                  # 创建 Job
GET    /admin/jobs                  # 列出所有 Job
GET    /admin/jobs/{jobId}          # 获取 Job 详情
PUT    /admin/jobs/{jobId}/start    # 启动 Job
PUT    /admin/jobs/{jobId}/stop     # 停止 Job
DELETE /admin/jobs/{jobId}          # 删除 Job
GET    /admin/jobs/{jobId}/status   # Job 状态
GET    /admin/health                # Runtime 健康检查
GET    /admin/metrics               # Runtime 指标
```

**JobInfo 数据模型（对齐 Admin Server 的 EventMeshJobInfo）：**

```java
class JobInfo {
    String jobId;
    String jobName;
    ConnectorType connectorType;   // SOURCE / SINK
    String connectorName;
    String config;                 // JSON 格式 Connector 配置
    JobState state;                // CREATED / RUNNING / STOPPED / FAILED
    long createTime;
    long updateTime;
    String errorMessage;           // 失败原因
}
```

---

### 2.6 A2A Protocol Layer

**EventMesh 作为 Agent 消息总线的业务协议实现。A2A 处于协议栈的 Layer 5 (编程模型层)，与 OpenMessaging 平级——都面向开发者定义"怎么用"，只是一个面向 Agent 通信、一个面向传统消息 Pub/Sub。A2A 基于 HTTP 传输（REST API + SSE），复用 HTTP Endpoint 进入 Pipeline，而非另起独立的传输层端口。内部消息编码为 CloudEvents，与其他协议消息共享 Pipeline 处理链。**

```
Agent Client ──HTTP──→ [HTTP Endpoint] ──→ Pipeline
                      ↑
              A2A Gateway 在此层解析
              · Agent Card 注册/发现
              · Task 创建/状态追踪
              · SSE 流式推送
```

| 组件 | 职责 | 传输 |
|------|------|------|
| **Agent Card Registry** | Agent 注册、发现、心跳管理 | `GET/POST /.well-known/agent-card` |
| **A2A REST Gateway** | RESTful API 入口，接收 Agent 间调用 | `POST /a2a/tasks` |
| **SSE Streaming** | Server-Sent Events 流式响应 | `GET /a2a/tasks/{id}/stream` |
| **Task Lifecycle** | 异步 Task 创建、状态跟踪、结果回调 | `GET/POST/DELETE /a2a/tasks/{id}` |
| **Java SDK** | Java Agent 接入 SDK，封装 A2A 协议 | HTTP Client → EventMesh HTTP Endpoint |

A2A 消息流经 Pipeline 处理链，与其他传输层消息共用 Filter/Transformer/Router 管线，确保所有消息遵循统一的安全策略和路由规则。**A2A 不另开端口，不绕过安全链。**

---

## 三、与原 Runtime V2 的能力对齐

**所有 Runtime V2 能力已完整迁移到统一 Runtime，不留缺口。**

| Runtime V2 原能力 | 统一 Runtime 对应实现 | 状态 |
|---|---|---|
| ConnectorRuntime (多 Job 管理) | `ConnectorRuntimeService` | 功能增强（动态 API） |
| FunctionRuntime | Pipeline Transformer Engine | 功能替代 |
| MeshRuntime | EventMeshServer 主进程 | 统一合并 |
| HealthService | AdminClient.HealthService | 迁移 |
| MonitorService (SourceMonitor / SinkMonitor) | AdminClient.MonitorService + PipelineMonitor | 迁移 + 扩展 |
| VerifyService | AdminClient.VerifyService + Pipeline 埋点 | 迁移 |
| StatusService | AdminClient.StatusService | 迁移 |
| MetaStorage | OffsetStore (RocksDB) | 简化重构 |
| RuntimeInstance (启停模型) | EventMeshServer 生命周期 | 统一合并 |
| Admin Server gRPC BiStream | AdminClient | 迁移 |
| 独立进程隔离 | 单进程 + ClassLoader 隔离 + 独立线程池 | 架构简化 |

---

## 四、Pipeline 引擎详细设计

> ⚠️ **安全铁律**：AuthFilter / RateLimitFilter / AclFilter 是 Pipeline 的内置前置 Filter，**所有协议入口（TCP/HTTP/gRPC/A2A）和 Connector 数据流** 都强制经过同一安全链。不存在绕过通道。旧架构 Connector 通过 BlockingQueue 直接旁路的问题是统一 Runtime 的核心修复点。

### 4.1 FilterEngine

**责任链模式，每个 Filter 独立判断是否放行。执行顺序固定，不可跳过。**

```java
interface PipelineFilter {
    /**
     * @return true = 放行，false = 丢弃
     */
    boolean filter(CloudEvent event, PipelineContext ctx);
}
```

**内置 Filter（按执行顺序）：**

| 序号 | Filter | 职责 | 可禁用 |
|------|------|------|--------|
| 1 | AuthFilter | 认证鉴权（Token / AK/SK） | 否（安全强依赖） |
| 2 | RateLimitFilter | 频率限制（per-topic / per-client） | 可（按需关闭） |
| 3 | ProtocolFilter | 协议合规校验（CloudEvents 格式检查） | 否（数据规范） |
| 4 | RuleFilter | 自定义规则匹配（用户配置的匹配规则） | 可（无规则时跳过） |
| 5 | AclFilter | 访问控制列表（IP/Client/Topic 白名单） | 否（安全强依赖） |
| 6 | SizeLimitFilter | 消息体大小限制 | 可（按需关闭） |

**AuthFilter 和 AclFilter 不可被 bypass：** 它们是 Pipeline 链的硬性前置位。即使在 `SHARED` 线程池模式下，消息也必须先经过这两个 Filter 才能进入 Transformer/Router。这是统一 Runtime 相比旧架构（Connector 数据流绕过安全策略）的核心安全保证。

### 4.2 TransformerEngine

**消息格式转换、字段映射、丰富化处理。**

```java
interface PipelineTransformer {
    CloudEvent transform(CloudEvent event, PipelineContext ctx);
}
```

**内置 Transformer：**

| Transformer | 职责 |
|-------------|------|
| ProtocolTransformer | 协议格式互转（HTTP ↔ CloudEvents ↔ gRPC） |
| FieldMappingTransformer | 字段映射（用户配置的字段映射规则） |
| EnrichmentTransformer | 消息丰富化（附加 metadata、时间戳、trace 信息） |
| EncryptionTransformer | 敏感字段加密/脱敏 |
| CompressionTransformer | 消息体压缩 |

### 4.3 RouterEngine

**根据消息属性路由到目标 Topic/Queue。**

```java
interface PipelineRouter {
    /**
     * @return 目标 Topic 列表，空列表 = 不路由
     */
    List<String> route(CloudEvent event, PipelineContext ctx);
}
```

**路由规则：**

| 规则类型 | 说明 |
|----------|------|
| Static Route | 固定 Topic 映射 |
| Header Route | 根据 CloudEvent Header 字段路由 |
| Content Route | 根据消息体内容路由（JSONPath 表达式） |
| Broadcast Route | 广播到多个 Topic |
| Dead Letter Route | 处理失败路由到 DLQ |

---

## 五、配置体系

### 统一配置项（CommonConfiguration）

```properties
# ── Connector Runtime ──
eventmesh.connector.plugin.enabled=true
eventmesh.connector.plugin.type=source              # source / sink
eventmesh.connector.plugin.name=rocketmq-source
eventmesh.connector.plugin.config.path=conf/connectors/
eventmesh.connector.thread.pool.size=4
eventmesh.connector.max.retry=3

# ── Admin Server ──
eventmesh.admin.server.enabled=true
eventmesh.admin.server.required=false               # true = 无 Admin 则启动失败
eventmesh.admin.server.address=localhost:50051
eventmesh.admin.server.registry.type=nacos           # nacos / etcd / static
eventmesh.admin.server.heartbeat.interval.seconds=5
eventmesh.admin.server.monitor.report.interval.seconds=30

# ── Offset Management ──
eventmesh.offset.local.enabled=true
eventmesh.offset.local.path=data/offset/
eventmesh.offset.remote.enabled=false
eventmesh.offset.remote.sync.interval.seconds=60

# ── Verify ──
eventmesh.connector.verify.enabled=false             # 默认关闭，避免性能影响

# ── Pipeline ──
eventmesh.pipeline.ingress.filters=auth,ratelimit,protocol
eventmesh.pipeline.ingress.transformers=protocol,enrichment
eventmesh.pipeline.egress.filters=acl,sizelimit
eventmesh.pipeline.egress.transformers=protocol
eventmesh.pipeline.dlq.enabled=true
eventmesh.pipeline.dlq.topic=eventmesh-dlq

# ── A2A ──
eventmesh.a2a.enabled=false                          # A2A 默认关闭
eventmesh.a2a.gateway.port=8080
eventmesh.a2a.registry.ttl.seconds=30
eventmesh.a2a.sse.max.connections=1000
```

---

## 六、部署模型

### 单进程部署（默认）

```bash
# 启动 EventMesh 统一 Runtime
cd eventmesh-dist
bin/start.sh

# 一个进程包含：
# · TCP Server (端口 10000)
# · HTTP Server (端口 8080)
# · gRPC Server (端口 50051)
# · Connector Runtime (内嵌)
# · Admin Client (gRPC BiStream → Admin Server)
# · A2A Gateway (复用 HTTP Server 端口 8080，路由 /a2a/*)
# · Pipeline 处理链 (Filter → Transformer → Router)
```

### 多实例部署（集群）

```
┌─────────────────────────────────────────┐
│              Admin Server               │
│    (Job 调度 · 指标汇总 · 状态管理)       │
└─────────────────────────────────────────┘
         │              │              │
    gRPC BiStream  gRPC BiStream  gRPC BiStream
         │              │              │
  ┌──────▼──────┐ ┌─────▼──────┐ ┌─────▼──────┐
  │ Runtime #1  │ │ Runtime #2 │ │ Runtime #3 │
  │ Connector A │ │ Connector B│ │ Connector C │
  │ Connector D │ │            │ │ Connector E │
  └─────────────┘ └────────────┘ └────────────┘
```

每个 Runtime 实例独立运行，Admin Server 统一调度 Job 分布。

---

## 七、可观测性

### 指标体系

| 指标类别 | 指标 | 采集方式 |
|----------|------|----------|
| Pipeline | `pipeline.ingress.latency` / `pipeline.egress.latency` | PipelineMonitor |
| Pipeline | `pipeline.ingress.filtered.count` / `pipeline.ingress.total.count` | PipelineMonitor |
| Connector | `connector.source.tps` / `connector.sink.tps` | ConnectorMonitor |
| Connector | `connector.source.lag` / `connector.error.count` | ConnectorMonitor |
| Runtime | `runtime.heap.used` / `runtime.cpu.usage` / `runtime.thread.count` | JVM MXBean |
| A2A | `a2a.task.active` / `a2a.task.latency` / `a2a.sse.connections` | A2A Metrics |

所有指标通过 AdminClient.MonitorService 每 30s 上报到 Admin Server，Admin Server 可对接 Prometheus / Grafana。

### 链路追踪

每个 CloudEvent 携带 `traceparent` 和 `tracestate`（W3C Trace Context），Pipeline 各阶段自动传播 Trace ID，实现端到端链路追踪。

---

## 八、故障处理

### Connector 故障隔离

```
每个 Connector 实例:
  · 独立线程池（可配置大小）
  · ClassLoader 隔离（插件 jar 独立加载）
  · try-catch 边界（异常不传播到主进程）
  · 自动重试（指数退避，可配置最大重试次数）
  · 连续失败 N 次 → 自动暂停 + 告警
```

### Admin Server 降级

```
eventmesh.admin.server.required=false:
  Admin Server 不可达 → Runtime 正常运行，无心跳/指标上报
  Admin Server 恢复 → 自动重连，补报积压状态

eventmesh.admin.server.required=true:
  Admin Server 不可达 → Runtime 启动失败（严格模式）
```

### Offset 容灾

```
正常流程:
  写本地 RocksDB → 定时同步远程 Admin Server

故障恢复:
  1. 尝试本地 RocksDB 恢复（最快）
  2. 本地无数据 → 尝试远程 Admin Server 拉取
  3. 远程也无 → 使用 connector 配置的 auto.offset.reset
```

---

## 九、测试覆盖要求

| 测试类别 | 覆盖范围 | 关键场景 |
|----------|----------|----------|
| 单元测试 | Pipeline Engine (Filter/Transformer/Router) | 正常流程、filter 丢弃、transform 异常、路由到 DLQ |
| 单元测试 | ConnectorRuntimeService | 多 Connector 注册/启停/卸载 |
| 单元测试 | OffsetStore | RocksDB 读写、flush、恢复 |
| 单元测试 | AdminClient | 心跳/Monitor/Verify 上报、指令接收 |
| 集成测试 | 端到端消息流 | TCP→Pipeline→MQ→Pipeline→HTTP |
| 集成测试 | Connector Source→Sink | 完整 Source→Pipeline→MQ→Pipeline→Sink 链路 |
| 集成测试 | Offset 恢复 | Connector 重启后 Offset 恢复验证 |
| 集成测试 | Admin Server 降级 | Admin 不可达时 Runtime 正常运行 |
| 集成测试 | 多 Connector 并行 | ≥2 个 Connector 同时运行不互相影响 |
| E2E 测试 | A2A 完整链路 | Agent→Gateway→Pipeline→MQ→Pipeline→Agent |
| 性能测试 | 吞吐量基准 | 对比重构前后 TPS 变化 |
| 性能测试 | Pipeline 延迟 | Filter/Transformer/Router 各阶段延迟 |

---

## 十、删除项清单

以下来自原 `eventmesh-runtime-v2` 的内容已完整删除，功能由统一 Runtime 中的对应组件替代：

| 删除项 | 替代实现 | 功能等价性 |
|--------|----------|------------|
| `eventmesh-runtime-v2` 整个模块 | `eventmesh-runtime` (统一) | ✅ 完整替代 |
| `ConnectorRuntime` + `ConnectorRuntimeConfig` | `ConnectorRuntimeService` + `ConnectorConfig` | ✅ 功能增强 |
| `FunctionRuntime` + `FunctionRuntimeConfig` | Pipeline Transformer Engine | ✅ 功能替代 |
| `MeshRuntime` | EventMeshServer 主进程 | ✅ 统一合并 |
| `RuntimeInstance` / `RuntimeInstanceStarter` | EventMeshServer 生命周期 | ✅ 统一合并 |
| `HealthService` | AdminClient.HealthService | ✅ 完整迁移 |
| `MonitorService` + `SourceMonitor` + `SinkMonitor` | AdminClient.MonitorService + PipelineMonitor | ✅ 扩展迁移 |
| `VerifyService` | AdminClient.VerifyService + Pipeline 埋点 | ✅ 完整迁移 |
| `StatusService` | AdminClient.StatusService | ✅ 完整迁移 |
| `MetaStorage` | OffsetStore (RocksDB) | ✅ 简化替代 |
| `ConnectorManager` / `FunctionManager` | ConnectorRuntimeService 统一管理 | ✅ 功能替代 |
| `start-v2.sh` / `stop-v2.sh` | 统一 `start.sh` / `stop.sh` | ✅ 统一 |
| `runtime.yaml` / `connector.yaml` / `function.yaml` | 统一 `eventmesh.properties` | ✅ 配置统一 |
| `BannerUtil` | EventMeshServer 统一 Banner | ✅ 保留品牌 |

---

## 十一、当前分支实际完成状态

| 功能 | 状态 | 说明 |
|------|------|------|
| Pipeline 核心 (Ingress/EgressProcessor) | ✅ 已完成 | Filter → Transformer → Router |
| TCP/HTTP/gRPC Processor 接入 Pipeline | ✅ 已完成 | 全部协议路径统一 |
| Connector 嵌入 Runtime (EventMeshConnectorBootstrap) | ✅ 已完成 | 单 Connector 支持 |
| RouterEngine + BatchProcessResult | ✅ 已完成 | 路由能力 + 批处理统计 |
| A2A Gateway + Registry + SDK | ✅ 已完成 | REST API + SSE + Task |
| NPE Bug 修复 (Source filtered event) | ✅ 已修复 | |
| Pipeline 单元测试 | ✅ 已完成 | Ingress/Egress/Router/Batch 测试 |
| ConnectorRuntimeService (多 Connector) | ⬜ 待实现 | 当前仅单 Connector |
| AdminClient (Health/Monitor/Status/Verify) | ⬜ 待实现 | 管理面通信 |
| OffsetStore (RocksDB) | ⬜ 待实现 | Exactly-Once 保障 |
| Job Management API (HTTP REST) | ⬜ 待实现 | 动态 Job 管理 |
| AdminCommandHandler (gRPC 指令) | ⬜ 待实现 | Admin Server 下发指令 |
| 集成测试 + 性能测试 | ⬜ 待实现 | 端到端验证 |

---

## 十二、设计原则总结

1. **统一数据面**：所有消息（TCP/HTTP/gRPC/A2A/Connector）走同一 Ingress/Egress Pipeline
2. **完整控制面**：Admin Server 通信能力（Health/Monitor/Status/Verify）完整保留
3. **单进程部署**：一个 JVM 进程承载全部能力，消除 runtime-v1/v2 进程隔离开销
4. **Connector 内嵌 + 隔离**：内嵌到主进程，但 ClassLoader/线程池隔离保证故障不扩散
5. **Exactly-Once**：本地 RocksDB + 远程 Admin Server 双写 Offset
6. **可降级**：Admin Server 不可达时 Runtime 可独立运行（非严格模式）
7. **可观测**：指标 + 链路追踪 + 健康检查全覆盖
8. **A2A 原生**：Pipeline 原生支持 A2A Agent 消息流，不额外建立处理路径

---

*文档版本：v2.0 | 统一运行时目标架构蓝图 | 2026-07-01*
