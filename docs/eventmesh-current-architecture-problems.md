# EventMesh 当前架构问题与统一化必要性

> 文档目的：基于 `qqeasonchen/eventmesh` 的 `develop` 分支代码，梳理当前 EventMesh 架构（含 `eventmesh-runtime` + `eventmesh-runtime-v2` 双运行时）存在的系统性缺陷，解释为什么需要统一运行时重构。
>
> 对应蓝图：[EventMesh 统一运行时架构蓝图 v2.0](./eventmesh-unified-runtime-architecture-review.md)
>
> Review 范围：`develop` 分支的 `eventmesh-runtime`、`eventmesh-runtime-v2`、`eventmesh-connector-*`、`eventmesh-protocol-plugin` 相关实现。

---

## 结论先行

**当前 EventMesh 有 6 个架构级问题，核心矛盾是「双运行时 + 协议割裂 + Connector 安全旁路」。统一运行时不是可选优化——是解决这些问题的唯一路径。**

本次基于 `develop` 分支代码继续 review 后，结论进一步加重：

1. `develop` 上已经出现 A2A 相关代码，但主进程 `EventMeshServer` 没有把 A2A 服务接入生命周期，说明「继续外挂新能力」已经开始制造半集成状态。
2. `eventmesh-runtime-v2` 的 `MeshRuntime` 仍是空实现，v2 名义上的三种 Runtime 并未真正闭环。
3. `ConnectorRuntime` / `FunctionRuntime` 复制了大段 Runtime 骨架，但都没有接入 v1 的 ACL/Auth/RateLimit/Trace 体系。
4. `ConnectorRuntime.start()` 的 Source/Sink 线程 `finally` 中存在 `System.exit(-1)`，属于架构分裂诱发的高危稳定性问题：运行时内部线程异常会直接杀掉整个 JVM。

| # | 问题 | 严重程度 | develop 代码现状 | 影响 |
|---|------|----------|-------------------|------|
| 1 | 双运行时进程隔离开销 | 🔴 高 | `eventmesh-runtime` 与 `eventmesh-runtime-v2` 仍是两套启动、两套生命周期 | 运维复杂、资源浪费、部署耦合 |
| 2 | 每协议独立 Processor 链，逻辑大量重复 | 🔴 高 | HTTP/TCP/gRPC 仍各自维护 Processor 链 | 改一个安全策略要改多处入口 |
| 3 | Connector 数据流绕过安全策略 | 🔴 致命 | `ConnectorRuntime` 仍通过 `BlockingQueue<ConnectRecord>` 串 Source/Sink | ACL/AuthFilter/RateLimit 对 Connector 无效 |
| 4 | 没有统一 Pipeline 抽象 | 🟡 中 | `FilterEngine` 只是 Function pattern 过滤，不是统一安全责任链 | 不同入口的消息处理路径不一致 |
| 5 | 代码量膨胀，Bug 修复面扩散 | 🟡 中 | `ConnectorRuntime` 与 `FunctionRuntime` 重复初始化 gRPC、Storage、队列、线程池 | 改一处，查三处；容易产生生命周期 Bug |
| 6 | A2A Agent 协议缺乏原生 Pipeline 承载 | 🟡 中 | A2A 代码已在 `runtime/a2a`，但未挂入 `EventMeshServer` 主生命周期 | Agent 通信被迫外挂，安全/观测/路由难统一 |

---

## 一、双运行时架构概览

### 1.1 当前部署模型

```
┌──────────────────────────────┐  ┌──────────────────────────────┐
│   eventmesh-runtime (v1)     │  │  eventmesh-runtime-v2         │
│   start.sh                   │  │  start-v2.sh                  │
│   EventMeshStartup           │  │  RuntimeInstanceStarter       │
│                              │  │                              │
│   TCP Server  ·  HTTP Server │  │  ConnectorRuntime             │
│   gRPC Server ·  Admin Server│  │  FunctionRuntime              │
│   A2A code directory         │  │  MeshRuntime(empty)           │
└──────────────┬───────────────┘  └──────────────┬───────────────┘
               │                                  │
               └──────────┬───────────────────────┘
                          │
                    Admin Server / Storage / Meta
```

**两个独立 JVM 进程，两套启动脚本，两套配置，两个类加载器空间。**

本次 review 确认：`develop` 并没有把 v2 能力收回 v1 主进程，也没有把 v1 的协议网关能力下沉成可复用 Pipeline。相反，A2A 又被追加进 `eventmesh-runtime/src/main/java/org/apache/eventmesh/runtime/a2a/`，进一步证明当前架构仍在按「新增目录 + 独立服务」方式演进。

### 1.2 为什么会有两个 Runtime？

历史演进导致的分裂：

| 版本 | 定位 | 核心职责 | 当前问题 |
|------|------|----------|----------|
| **runtime-v1** | 消息协议网关 | TCP/HTTP/gRPC 消息接入、路由、分发、Admin | 协议 Processor 逻辑重复，缺统一 Pipeline |
| **runtime-v2** | 连接器 + 函数运行时 | Connector(Source/Sink)、Function、Mesh 编排 | 独立进程、独立队列、独立存储连接，绕过 v1 安全链 |
| **A2A 目录** | Agent 通信入口 | Agent Card、Task、Gateway、SSE | 代码存在，但未接入主进程生命周期和统一 Pipeline |

v2 是在 v1 基础上「加塞」出来的——没有重构 v1，而是新建模块走独立进程。A2A 又是在 v1 中追加目录实现，而不是复用统一协议栈。这直接导致问题 1-6。

---

## 二、问题 1：双运行时进程隔离开销

### 2.1 运维复杂度

```
生产环境部署需要同时启动：
  start.sh      → EventMeshStartup         (v1, 主进程)
  start-v2.sh   → RuntimeInstanceStarter   (v2, 独立进程)
```

| 运维负担 | 表现 |
|----------|------|
| **两套启停脚本** | `start.sh`/`stop.sh` 和 `start-v2.sh`/`stop-v2.sh`，分别写 pid 文件 |
| **两套日志目录** | 各自输出到 `logs/`，排查问题时要在两份日志间跳转 |
| **两套 JVM 配置** | v1 和 v2 各占用堆内存，合计浪费 2 个 JVM 的 metaspace + direct memory 开销 |
| **健康检查碎片化** | 需要监控两个进程的存活，任一挂掉都影响 Connector 服务 |
| **版本不一致风险** | v1 和 v2 可能从不同构建产物部署，接口不兼容难以发现 |

### 2.2 资源浪费

```java
// v1: EventMeshServer 拥有完整的 ACL、MetaStorage、Trace、Metrics 体系
this.acl = Acl.getInstance(...);
this.metaStorage = MetaStorage.getInstance(...);
trace = Trace.getInstance(...);
this.storageResource = StorageResource.getInstance(...);

// v2: ConnectorRuntime 也独立初始化 gRPC channel、Storage Plugin、Offset 存储
channel = ManagedChannelBuilder.forTarget(adminServerAddr).build();
producer = StoragePluginFactory.getMeshMQProducer(...);
consumer = StoragePluginFactory.getMeshMQPushConsumer(...);
offsetManagementService.initialize(...);
```

**同一个物理节点上，对 Kafka/RocketMQ 的连接数翻倍，gRPC channel 翻倍，存储插件初始化翻倍——完全不需要。**

本次 review 还确认 `FunctionRuntime` 也复制了类似初始化骨架：独立 gRPC stub、独立 Source/Sink 线程、独立队列。这不是「Connector 特例」，而是 v2 Runtime 抽象本身的重复。

### 2.3 进程间通信开销

v2 的 ConnectorRuntime 通过 gRPC 与 Admin Server 通信：

```java
// ConnectorRuntime: 通过 gRPC fetch job 配置
Payload response = adminServiceBlockingStub.invoke(request);

// 健康检查心跳也是 gRPC
healthService = new HealthService(adminServiceStub, adminServiceBlockingStub, ...);
```

如果在同一 JVM 内，这应该是本地方法调用，而非序列化 → 网络 → 反序列化往返。

### 2.4 新发现：v2 内部线程可直接杀死整个 JVM

`ConnectorRuntime.start()` 的 Source/Sink 执行逻辑在 `finally` 中调用 `System.exit(-1)`：

```java
sinkService.execute(() -> {
    try {
        startSinkConnector();
    } finally {
        System.exit(-1);
    }
});

sourceService.execute(() -> {
    try {
        startSourceConnector();
    } finally {
        System.exit(-1);
    }
});
```

这类代码在双运行时架构下尤其危险：

- Source/Sink 任一线程异常退出，会直接终止 v2 JVM；
- 进程级退出绕过 Runtime 生命周期管理，无法让上层做优雅降级、隔离重启或状态上报；
- 如果未来把 v2 合并进 v1，这段逻辑会直接把整个 EventMesh 主进程杀掉。

这不是单纯 Bug，而是缺少统一 Runtime 生命周期管理导致的典型后果。

---

## 三、问题 2：每协议独立 Processor 链，逻辑大量重复

### 3.1 三层协议，三层 Processor

v1 对每种传输协议都实现了一套完整的 Processor 链：

```
TCP 协议
├── HelloProcessor, GoodbyeProcessor
├── SubscribeProcessor, UnSubscribeProcessor
├── MessageTransferProcessor, MessageAckProcessor
├── HeartBeatProcessor, ListenProcessor, RecommendProcessor

HTTP 协议
├── SendAsyncMessageProcessor, SendSyncMessageProcessor
├── BatchSendMessageProcessor, BatchSendMessageV2Processor
├── SendAsyncEventProcessor, SendAsyncRemoteEventProcessor
├── ReplyMessageProcessor, HeartBeatProcessor
├── SubscribeProcessor, UnSubscribeProcessor
├── CreateTopicProcessor, DeleteTopicProcessor
├── AdminMetricsProcessor, AdminShutdownProcessor
├── LocalSubscribeEventProcessor, RemoteSubscribeEventProcessor
├── LocalUnSubscribeEventProcessor, RemoteUnSubscribeEventProcessor

gRPC 协议
├── RequestCloudEventProcessor, PublishCloudEventsProcessor
├── BatchPublishCloudEventProcessor
├── AbstractPublishCloudEventProcessor, AbstractPublishBatchCloudEventProcessor
├── SubscribeProcessor, SubscribeStreamProcessor, UnsubscribeProcessor
├── HeartbeatProcessor, ReplyMessageProcessor
```

**review `develop` 的 HTTP processor 目录可见，Send/Batch/Reply/Subscribe/Admin/Topic 等入口仍然拆成大量独立 Processor 类。**

### 3.2 每个 Processor 里的重复逻辑

以 `SendAsyncMessageProcessor` (HTTP) 为例，其 `processRequest` 方法包含：

```java
// ① 协议解析 (Protocol Adaptor)
CloudEvent event = httpCommandProtocolAdaptor.toCloudEvent(request);

// ② ACL 检查
this.acl.doAclCheckInHttpSend(remoteAddr, user, pass, subsystem, topic, requestCode);

// ③ TTL 注入
event = CloudEventBuilder.from(event).withExtension(TTL, ttl).build();

// ④ 发送到 Storage
this.eventMeshHTTPServer.getProducer().send(event, callback);
```

TCP 协议的 `MessageTransferProcessor` 和 gRPC 协议的 `RequestCloudEventProcessor` 中，步骤 ②③④ 的逻辑高度相似，区别主要在步骤 ① 的协议解析。

**当需要修改一个安全策略（比如增加新的 Auth plugin）时，需要同步审查多个协议入口。** 这正是统一 Pipeline 应该解决的问题：协议层只负责 `TransportRequest → CloudEvent/Message`，安全、转换、路由、发送不应该散落在每个 Processor 中。

### 3.3 V1 vs V2：两套 Send 逻辑

```java
// v1 SendAsyncMessageProcessor:
eventMeshHTTPServer.getProducer().send(event, callback);

// v2 ConnectorRuntime:
queue.put(record);              // Source 直接往 BlockingQueue 塞
sinkConnector.put(recordList);  // Sink 从 queue 取，直接写外部系统
```

v2 的 Connector 不仅不走 ACL，连消息发送机制都完全不同——它用 `BlockingQueue` 代替 v1 的 Producer/Consumer 路径。两套逻辑互不感知。

---

## 四、问题 3：Connector 数据流绕过安全策略（致命）

### 4.1 数据流对比

```
HTTP/TCP/gRPC 消息流：
  客户端 → Endpoint → Adaptor.toCloudEvent() → ACL/Auth/限流/校验 → Producer.send() → Storage

Connector 消息流：
  Source.poll() → BlockingQueue.put(record) → Sink.put(recordList) → 外部系统
                                   ↑
                         完全绕过了以下所有安全层：
                           · ACL 权限检查
                           · AuthFilter 认证
                           · RateLimit 限流
                           · SizeLimit 大小限制
                           · ProtocolFilter 合规校验
                           · Trace 链路追踪
```

### 4.2 代码证据

```java
// ConnectorRuntime.java
private final BlockingQueue<ConnectRecord> queue;  // LinkedBlockingQueue(1000)

// Source 直接往队列塞，不经过任何 Filter
queue.put(record);

// Sink 从队列取，直接写外部系统
sinkConnector.put(recordList);  // 没有 ACL/Auth/RateLimit
```

这条链路的问题不是「缺少某个 if 判断」，而是压根没有进入 v1 的协议处理链，也没有进入任何统一 Pipeline。

### 4.3 安全后果

| 攻击向量 | v1 防护 | v2 Connector 防护 |
|----------|---------|-------------------|
| 未授权 Topic 写入 | ACL 检查拦截 | ❌ 无统一防护 |
| 流量洪峰 | RateLimit 限流 | ❌ 无统一防护，仅有本地队列容量 |
| 恶意大数据包 | SizeLimit 拦截 | ❌ 无统一防护 |
| 认证伪造 | AuthFilter 校验 | ❌ 无统一防护 |
| 操作审计 | Trace 链路追踪 | ❌ 无统一追踪 |

**一个恶意或失控的 Source Connector 插件可以持续向 Sink 灌数据，EventMesh 主协议链完全感知不到。**

### 4.4 背压也没有统一语义

`ConnectorRuntime` 使用 `LinkedBlockingQueue(1000)` 作为 Source/Sink 中间缓冲。这个队列只能提供本地阻塞，不能提供统一背压语义：

- 上游 Source 不知道是下游 Sink 慢、外部系统慢，还是 EventMesh 限流；
- 队列满时只是阻塞 Source 线程，没有 Metrics/Trace/告警上下文；
- 不同 Connector 的队列容量、错误恢复、重试策略很难和主 Runtime 统一。

因此 Connector 旁路不仅是安全问题，也是稳定性和可观测性问题。

---

## 五、问题 4：没有统一 Pipeline 抽象

### 5.1 develop 当前状态

`develop` 分支的 `eventmesh-runtime/boot/EventMeshServer.java` 主生命周期只初始化 HTTP/TCP/gRPC/Admin 等 Bootstrap：

```java
BOOTSTRAP_LIST.add(new EventMeshHttpBootstrap(this));
BOOTSTRAP_LIST.add(new EventMeshTcpBootstrap(this));
BOOTSTRAP_LIST.add(new EventMeshGrpcBootstrap(this));
BOOTSTRAP_LIST.add(new EventMeshAdminServer(this));
```

本次 review 未看到它挂载以下统一处理组件：

- `IngressProcessor`
- `EgressProcessor`
- `EventMeshConnectorBootstrap`
- `A2APublishSubscribeService`
- 安全 Filter 责任链

这说明 `develop` 的主进程仍是协议 Bootstrap 聚合器，不是统一 Runtime。

### 5.2 develop 上的 FilterEngine 不是安全 Pipeline

`develop` 中确实存在 `FilterEngine`，但它的职责是 Function 内容过滤：

```java
private Map<String, Pattern> filterPatternMap;

// 定期从 MetaStorage 拉取 function 配置，并编译 pattern
Pattern pattern = Pattern.compile(function.getPattern());
filterPatternMap.put(function.getId(), pattern);
```

它不是统一 Pipeline 的 Filter 责任链，原因很明确：

| 能力 | FilterEngine 当前实现 | 统一 Pipeline 应有实现 |
|------|----------------------|------------------------|
| Auth | ❌ 无 | ✅ AuthFilter |
| ACL | ❌ 无 | ✅ AclFilter |
| RateLimit | ❌ 无 | ✅ RateLimitFilter |
| SizeLimit | ❌ 无 | ✅ SizeLimitFilter |
| Protocol Compliance | ❌ 无 | ✅ ProtocolFilter |
| Trace/Metrics | ❌ 非 Pipeline 级 | ✅ 每个 Stage 可观测 |
| 多 Filter 链式组合 | ❌ 单一 `filterPatternMap` | ✅ Ordered Chain / Responsibility Chain |

当前 `FilterEngine` 更准确地说是 **Function-level pattern filter registry**，不是 **Security + Function Pipeline**。

### 5.3 没有 Pipeline 的后果

```
每个协议的消息处理变成了 N 份独立实现：

HTTP Send  → ①httpAdaptor → ②doACL → ③producer.send
TCP  Send  → ①tcpAdaptor  → ②doACL → ③producer.send
gRPC Send  → ①grpcAdaptor → ②doACL → ③producer.send
Connector  → ①source.poll → ②queue.put → ③sink.put
A2A        → ①gateway     → ②task/sse  → ③publish/subscribe?

少了统一 Pipeline 抽象 → 步骤②③无法复用 → 安全策略修改 = 批量改文件
```

统一 Pipeline 的目标应该是：所有入口统一进入 `IngressPipeline`，所有出口统一进入 `EgressPipeline`；协议适配只做格式转换，不能承载安全和路由主逻辑。

---

## 六、问题 5：代码规模膨胀与维护负担

### 6.1 模块职责重叠

```java
// runtime-v1: EventMeshServer 管理 Bootstrap 列表
BOOTSTRAP_LIST.add(new EventMeshHttpBootstrap(this));
BOOTSTRAP_LIST.add(new EventMeshTcpBootstrap(this));
BOOTSTRAP_LIST.add(new EventMeshGrpcBootstrap(this));

// runtime-v2: RuntimeInstance 管理 Runtime 列表
RuntimeFactory factory = new ConnectorRuntimeFactory();
RuntimeFactory factory = new FunctionRuntimeFactory();
RuntimeFactory factory = new MeshRuntimeFactory();
```

两个 Runtime 各自发明了不同的「组件生命周期管理」机制（Bootstrap vs RuntimeFactory），但做的事情很接近。

### 6.2 ConnectorRuntime 与 FunctionRuntime 高度重复

本次 review 进一步确认：`ConnectorRuntime` 与 `FunctionRuntime` 的结构高度相似，重复点包括：

- 读取 Runtime 配置；
- 创建 AdminService gRPC stub；
- 初始化 Source/Sink Connector；
- 使用本地 `LinkedBlockingQueue` 串接 Source/Sink；
- 初始化 Storage producer/consumer；
- 初始化 Offset 管理；
- 启动 Source/Sink 线程池；
- 独立做健康检查和 stop 逻辑。

这说明 v2 并没有抽出稳定的 Runtime Template，而是在不同 Runtime 类型中复制一套骨架。复制越多，生命周期 bug 越容易扩散。

### 6.3 MeshRuntime 名义存在，实际为空

`eventmesh-runtime-v2/src/main/java/org/apache/eventmesh/runtime/mesh/MeshRuntime.java` 在 `develop` 上基本是空实现：

```java
public class MeshRuntime implements Runtime {
    @Override
    public void init(RuntimeInstance runtimeInstance) throws Exception {
    }

    @Override
    public void start() throws Exception {
    }

    @Override
    public void stop() throws Exception {
    }
}
```

这意味着 `RuntimeFactory` 暴露了 `MeshRuntimeFactory`，但真正的 Mesh 编排能力没有落地。架构上看，这是一个「概念已占位、实现未闭环」的信号：继续保留独立 v2 只会让 API/配置/文档先膨胀，实际能力却难以和主链路整合。

### 6.4 Bug 修复面扩散

unified-runtime-pipeline 分支的历史提交记录已经暴露了这个问题：

```
d419b28f8 Fix Source connector filtered-event NPE
34229034a Revert "[ISSUE] Fix all review issues in unified-runtime-pipeline"
03963a188 [ISSUE] Fix all review issues in unified-runtime-pipeline
```

修复一个 NPE 或 review issue 时，需要跨 v1、v2、connector 三个模块同时修改——因为相同逻辑分散在三处。

本次 `develop` review 新发现的 `System.exit(-1)` 也属于同类问题：Runtime 生命周期控制分散在 v2 内部线程中，没有统一 supervision model。

---

## 七、问题 6：A2A Agent 协议缺乏原生 Pipeline 承载

### 7.1 develop 当前 A2A 代码位置

`develop` 分支已经存在 A2A 相关代码目录：

```
eventmesh-runtime/src/main/java/org/apache/eventmesh/runtime/a2a/
├── A2ACardHttpHandler.java
├── A2AGatewayHttpHandler.java
├── A2AGatewayServer.java
├── A2AGatewayService.java
├── A2APublishSubscribeService.java
├── InMemoryA2AMessageTransport.java
└── TaskRegistry.java
```

这与早期判断「A2A 只在重构分支」相比有变化：**A2A 代码已经进入 develop，但仍是半集成状态。**

### 7.2 A2A 未接入 EventMeshServer 主生命周期

本次 review `EventMeshServer.java` 后，未看到主生命周期中初始化或启动：

- `A2AGatewayServer`
- `A2AGatewayService`
- `A2APublishSubscribeService`

`BOOTSTRAP_LIST` 仍主要是 HTTP/TCP/gRPC/Admin。也就是说，A2A 目录存在，但没有成为 EventMesh 主 Runtime 的一等入口。

### 7.3 为什么需要统一 Pipeline 承载 A2A

A2A 作为 Agent 通信的业务协议，其流量本质上和普通消息流量一样——需要经过 Pipeline：

```
Agent A → A2A REST API → IngressPipeline → Storage/Router → EgressPipeline → Agent B

如果没有 Pipeline：
  · A2A Gateway 需要重复实现 ACL/Auth/RateLimit
  · Trace 无法追踪跨 Agent 调用链
  · 无法对 A2A 流量做统一 Transformer/Filter
  · Agent Task 状态与消息投递状态难以统一观测
```

A2A 的 Agent Card / Task / SSE 可以复用 HTTP Endpoint 的传输层，但业务语义（Task 状态机、Agent 发现、Streaming）需要独立的编程模型层实现——这正是五层协议栈的设计目标。

### 7.4 半集成状态的风险

当前状态最危险的地方不是「没有 A2A」，而是「A2A 代码已经存在，但未进入统一主链路」。这会带来三类风险：

| 风险 | 表现 |
|------|------|
| 安全重复 | A2A Gateway 如果独立暴露 HTTP API，需要单独补 Auth/ACL/RateLimit |
| 可观测性断裂 | Task/SSE/Message 的 trace id、metrics tag 与普通消息链路不一致 |
| 演进分叉 | 后续 A2A 修复会在 `runtime/a2a` 内自成体系，进一步扩大统一成本 |

---

## 八、问题根因分析

### 8.1 技术债务来源

```
项目初期
  ↓
runtime-v1 建成（TCP/HTTP/gRPC 网关）
  ↓
需求：支持 Connector 数据集成
  ↓
  决策：新建 runtime-v2 模块（避免改 v1）
        ↓
        runtime-v2 独立进程 + 独立代码
        ↓
        共享 Admin Server（gRPC 通信）
  ↓
需求：支持 Function/Mesh Runtime
  ↓
  决策：继续在 v2 中扩展 RuntimeFactory
        ↓
        ConnectorRuntime / FunctionRuntime 重复骨架
        ↓
        MeshRuntime 占位但未实现
  ↓
需求：支持 Agent 通信 (A2A)
  ↓
  决策：在 runtime-v1 中追加 A2A Gateway 目录
        ↓
        但没有重构 v1 的协议处理链
        ↓
        也没有合并 v2 的功能
  ↓
现状：协议网关 + Connector + Function + A2A 各自一套代码路径
```

### 8.2 为什么「暂时不改」会越来越糟

| 时间点 | 问题规模 | 修复代价 |
|--------|---------|----------|
| 只有 v1 时 | 协议 Processor 重复 | 中等 |
| v2 加入后 | Runtime 与安全链分裂 | 较高 |
| Function/Mesh 加入后 | v2 内部 Runtime 骨架重复 | 高 |
| A2A 加入后 | 新业务协议半集成 | 更高 |
| 再叠加新协议 (MQTT/WebSocket/更多 Agent 协议) | N × M 倍扩散 | 不可接受 |

**每增加一个新入口，都需要在所有 Processor 或 Gateway 中重复 ACL/Auth/RateLimit/Transformer/Router 逻辑。统一 Pipeline 就是把 N×M 的复杂度降回 N+M。**

---

## 九、统一化的核心收益

### 9.1 对照表

| 维度 | 当前 | 统一后 |
|------|------|--------|
| JVM 进程数 | 2 (`eventmesh-runtime` + `eventmesh-runtime-v2`) + Admin 相关通信 | 1 个主 Runtime，必要能力组件化 |
| 安全策略维护 | 每个 Processor/Gateway/Connector 各自实现或缺失 | Pipeline 中 Auth/ACL/RateLimit/SizeLimit/ProtocolFilter 统一处理 |
| Connector 是否过安全链 | ❌ `BlockingQueue` 旁路 | ✅ Source/Sink 都经 Ingress/Egress Pipeline |
| 新增协议入口 | 写全套 Processor + ACL + Transformer | 只需实现 Adaptor + Endpoint，主逻辑复用 Pipeline |
| A2A 协议支持 | 代码目录存在但未接主生命周期 | 作为 Programming Model 接入五层协议栈 |
| Function filter | `FilterEngine` pattern 过滤，与安全链无关 | Function Filter 成为 Pipeline Stage 之一 |
| Admin Server 通信 | v2 通过 gRPC 拉取配置/心跳 | 同进程内服务调用或统一控制面 API |
| 运维脚本 | 2 套 start/stop | 1 套 |
| 内存/JVM 开销 | 2× metaspace + direct memory | 1× |
| 生命周期管理 | Bootstrap、RuntimeFactory、线程池各管各的 | 统一 Component/Supervisor 生命周期 |

### 9.2 建议的统一目标

统一运行时不只是「把 v2 代码搬进 v1」，而是要建立清晰的五层协议栈：

```
Programming Model Layer
  - Messaging API
  - Connector Source/Sink
  - Function
  - A2A Agent Task/Card/SSE

Data Format Layer
  - CloudEvents
  - OpenMessaging
  - A2A Message/Task Event

Transport Layer
  - HTTP
  - TCP
  - gRPC
  - Connector Adapter
  - A2A REST/SSE

Pipeline Layer
  - AuthFilter
  - AclFilter
  - RateLimitFilter
  - SizeLimitFilter
  - ProtocolFilter
  - FunctionFilter / Transformer / Router
  - Trace / Metrics Stage

Storage Layer
  - Kafka / RocketMQ / Pulsar / InMemory
```

### 9.3 一句话总结

```
当前架构的问题不是「某个模块写得差」——是「多个运行时和多个入口各搞一套，
互相不认，本该统一的安全策略被 Connector BlockingQueue 旁路掉了，
新增 A2A 又继续走外挂目录模式」。

统一 Pipeline 就是强制所有入口走同一条 Auth → ACL → RateLimit → Transform → Route → Storage 链，
同时把 v1/v2 两个 JVM 进程合并成一个可监督、可观测、可扩展的 Runtime。
```

---

## 十、本次 develop 代码 Review 新增问题清单

| # | 新发现 | 所在位置 | 严重程度 | 建议处理 |
|---|--------|----------|----------|----------|
| N1 | `ConnectorRuntime.start()` Source/Sink 线程 `finally` 调用 `System.exit(-1)` | `eventmesh-runtime-v2/.../connector/ConnectorRuntime.java` | 🔴 高 | 改为 Runtime Supervisor 上报失败并优雅停止，禁止子线程直接退出 JVM |
| N2 | `FunctionRuntime` 与 `ConnectorRuntime` 复制 Runtime 骨架 | `eventmesh-runtime-v2/.../function/FunctionRuntime.java` | 🟡 中 | 抽象 Runtime Template / Component Lifecycle，并纳入统一 Runtime |
| N3 | `MeshRuntime` 空实现 | `eventmesh-runtime-v2/.../mesh/MeshRuntime.java` | 🟡 中 | 要么补齐设计与实现，要么从公开 RuntimeFactory 中移除占位 |
| N4 | A2A 代码已在 `develop`，但未挂入 `EventMeshServer` 生命周期 | `eventmesh-runtime/.../a2a/*` + `boot/EventMeshServer.java` | 🟡 中 | 通过统一 Transport + Pipeline 接入，不建议继续独立 Gateway 化 |
| N5 | `FilterEngine` 仅是 pattern registry，不是安全 Filter 链 | `eventmesh-runtime/.../boot/FilterEngine.java` | 🟡 中 | 保留为 FunctionFilter 能力，并放入统一 Pipeline Stage |
| N6 | Connector 本地队列无统一背压/观测语义 | `ConnectorRuntime.java` | 🟡 中 | 用 Pipeline Context + Metrics/Trace 统一表达限流、阻塞、重试、失败 |

---

**相关文档：**
- [EventMesh 统一运行时架构蓝图 v2.0](./eventmesh-unified-runtime-architecture-review.md) — 目标架构完整设计
- Review 分支：`develop`
- 对照重构分支：`refactor/unified-runtime-pipeline`
