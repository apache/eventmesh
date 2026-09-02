# EventMesh Offset 管理 · 负载均衡 · Frame 协议转换 设计总结

> **状态说明**：本文是 offset/负载均衡/Frame 的设计文档（历史记录）。各能力的当前实现状态
> 以主 README 的[能力状态表](../README.md#能力状态capability-status)为准。

> 整合 offset 管理、全面粘性负载均衡、EventMeshFrame 协议转换三块设计的完整方案（2026-08-13）。
> 关联文档：[`eventmesh-uni-architecture-redesign.md`](./eventmesh-uni-architecture-redesign.md) §19 架构深化。

---

## 目录

- [一、Offset 管理](#一offset-管理)
- [二、负载均衡](#二负载均衡)
- [三、Frame 协议转换](#三frame-协议转换)

---

## 一、Offset 管理

### 1.1 架构定位

**EventMesh 完全自管 offset，不给 group.id，不上报 meta。** MQ（RocketMQ/Kafka）仅作持久 FIFO 日志，不使用其 Consumer Group 语义。

### 1.2 两层 offset

| 层 | 含义 | 存储 | 接管行为 |
|---|---|---|---|
| **pull offset**（从 MQ 拉到哪） | `parent#lite@queue`（lite）/ `topic#partition`（普通） | storage 插件本地（rocketmq5 = properties 文件，5s 原子写 checkpoint） | 实例挂 → 新实例从 head 重拉 |
| **deliver/ack offset**（投递+ACK 进度） | `topic#clientId#partition` | `RocksDBOffsetStore`（本地） | 实例挂 → 新实例 readOffset=-1 → 从 head 重拉 → in-flight 重投 |

两层**不强行合并**（语义不同、解耦）：pull 层在 storage 插件内部（自管游标），deliver/ack 层在 runtime OffsetStore。`pullAndDispatchPartition` 传 `startOffset=-1` 给 `storage.poll`（插件自管），OffsetStore 不在接管路径上。

### 1.3 核心机制

**正常路径**：

```
storage.poll()
  → pull offset 自管（不向 broker 提交 commitOffset）
  → pullAndDispatchPartition（传 startOffset=-1，插件自管游标）
  → ReliableDispatcher.deliver()
    → Delivery（携带 event + mqAckCallback）
    → PushChannel.deliver() → 投递给客户端
  → 客户端 ACK → ReliableDispatcher.ack()
    → offsetStore.writeOffset()（CAS max，只进不退）
    → storage.ackPulledMessage(topic, popCk)（RocketMQ 5.x：此时才 ACK broker）
```

**接管（实例挂）**：

```
实例挂
  → client 经 /session/recommend 重连新实例（§二负载均衡）
  → 新实例从业务 topic 重拉
    · lite：pollLite 从 lite 头重放（单游标天然可重放）
    · 普通：poll 从最早未 ACK/head 起
  → in-flight 未 ACK 消息 → at-least-once 重投（业务幂等兜底）
  → 不写快照、不迁移 offset 状态、不上报 meta
```

### 1.4 三后端差异

| 后端 | pull 机制 | ACK 机制 | 重启恢复 |
|---|---|---|---|
| **RocketMQ 5.x** | POP_MESSAGE（broker 管分配，partitionCount=-1 → poll-all） | **延迟 ACK**：poll() 不立即 ACK broker；`ackPulledMessage()` 在客户端 ACK 后触发 broker ACK。崩溃不丢（broker invisibleTime 30s 过期自动重投）。 | pull offset 本地 properties 文件续传（5s checkpoint） |
| **RocketMQ 4.x** | PULL_MESSAGE（自管游标，固定 CONSUMER_GROUP，不提交 commitOffset） | 无 MQ ACK（PULL 模式） | pull offset 本地 properties 文件续传 |
| **Kafka** | assign+seek（无 group.id，ENABLE_AUTO_COMMIT=false） | 无 MQ ACK | pull offset 本地 properties 文件续传 |

### 1.5 延迟 ACK 详解（RocketMQ 5.x，P2 修复）

**问题**：原来 `poll()` 内立即 `ackNormal(brokerAddr, msg)` → broker 认为已消费 → EventMesh 崩溃丢消息（At-Most-Once）。

**修复链路**：

```
① poll()
   → POP_MESSAGE 拉取消息
   → 不立即 ACK broker
   → 存 deferred ACK callback（key = PROPERTY_POP_CK）
   → EventMeshFrame 属性 stamp "empopck" = POP check key

② pullAndDispatchPartition()
   → 读取 frame.attributes().get("empopck")
   → 构建 mqAck = () → storage.ackPulledMessage(topic, popCk)
   → dispatcher.deliver(topic, partition, offset, frame, clientId, channel, mqAck)
   → Delivery 存储 mqAckCallback

③ 客户端 ACK → ReliableDispatcher.ack(deliveryId)
   → offsetStore.writeOffset()（offset 推进）
   → delivery.getMqAckCallback().run() → storage.ackPulledMessage(topic, popCk)
   → pendingPopAcks.remove(popCk) → 执行 broker ACK_MESSAGE
   → broker 确认消费完成
```

**at-least-once 保证**：如果 EventMesh 在 ① 和 ③ 之间崩溃，broker 的 invisibleTime（30s）过期 → 自动重投 → 新实例重新拉取处理。

### 1.6 OffsetStore 单调写入（P4 修复）

**问题**：`writeOffset` 直接覆盖（`set(offset)`），重启后快消费组的 offset 被慢消费组的重放消息拉低。

**修复**：
- `InMemoryOffsetStore`：`AtomicLong.accumulateAndGet(offset, Math::max)` —— CAS max
- `RocksDBOffsetStore`：read current → if `offset <= current` skip → else put —— 条件写

### 1.7 已淘汰

- **MetaBackedOffsetStore**（百万 key 1s 上报 meta）→ 默认关（`-Deventmesh.offset.meta=true` 显式开），类保留备用。
- 不给 group.id（EventMesh 自主 offset 铁律不变）。

### 1.8 待办

- **P1：RocketMQ 4.x ConsumeQueue 清理** —— `commitOffset` 仍是 no-op；需实际验证 RocketMQ 4.x 的 ConsumeQueue 清理是否真依赖消费位点（CommitLog 基于时间清理 `fileReservedTime`，可能不依赖）。

---

## 二、负载均衡

### 2.1 架构定位

> **均衡做在 session 分配层（入口 recommend），不在拉取/分发层。**

实例只为自己代理的 client 被动按需拉取，不对等、不转发。先均衡"哪个 client 归哪个实例"（session 层），自然导致"各实例拉取量大致相当"——而不是"先对等拉取再均衡分发"。

### 2.2 全面粘性模型

```
旧模型（已停用）：                          新模型（全面粘性）：
  PartitionOwnership（partition%n 分配）      × 无分区归属
  ClusterCoordinator（跨实例转发）            × 不转发
  HttpForwarder（HTTP 转发到 peer）           × 不转发
  ClusterSubscriptionStore（集群订阅表）      × 本地订阅

enableCluster 只保留：
  ClusterMembership（心跳 + 负载指标）         供 /session/recommend 全局评分
```

转发类（PartitionOwnership / ClusterCoordinator / HttpForwarder）保留不删（备用 + 测试）。

### 2.3 客户端零负担——负载全自采

EventMesh 实例本地自采负载指标（`LoadMeter`），客户端不发任何上报。

| 指标 | 采集来源 |
|---|---|
| `activeSessions` | `SessionRouter` sinks / subscribeSinks size |
| `inflowBytes/s` | `UniIngressService` publish 字节累加（按 clientId 分桶 → 每 client 流量画像） |
| `outflowBytes/s` | SSE / poll 出口字节累加 |
| `cpuLoad` | `OperatingSystemMXBean` |

随现有 5s 心跳（`PartitionOwnership` 线程）写入 `/em/instances/<id>` = `<ts>|<addr>|<activeSessions>|<byteRate>|<cpuLoad>`。

### 2.4 均衡闭环

```
① client 首次连接任意实例
   │
② GET /session/recommend?clientId=xxx
   │ → 该实例读集群全局 /em/instances/（全部实例 + 负载）
   │ → 评分：score = activeSessions×w1 + byteRate×w2 + cpuLoad×w3
   │ → 过载负反馈：cpuLoad>0.8 或 inflow>5MB/s → score += 10000（让出）
   │ → 大 client 分散：检查 client 现有 session 分布，优先未占满实例
   │
③ 返回推荐 instanceUrl
   │
④ POST /session/open → {sessionId, agentId, instanceUrl}
   │ → SDK pin 后续 turn/close 到该实例
   │
⑤ POST /events/subscribe → {subscriptionId, instanceUrl}
   │ → SDK pin 后续 poll/ack 到该实例
   │
⑥ 后续所有请求直连该实例
   │ → 实例只为自己 client 拉，不对等，不转发
   │
⑦ 失败/实例不可达 → SDK 重新 GET /session/recommend 拿另一实例
```

### 2.5 session 粒度粘性

- `/session/open` 和 `/events/subscribe` 返回 `instanceUrl`。
- SDK `SessionHandle.instanceUrl` → `StreamingSession` 用 `client.withBaseUrl(instanceUrl)` pin 后续 turn/close。
- SDK `subscribe` → `capturePollInstance` 设 `pollBaseUrl`，后续 poll+ack 走该实例。
- **`advertisedAddr` 默认空**（单实例/测试/LB 兼容）；显式配 `-Deventmesh.http.advertisedAddr=host:port` 才 pin。

### 2.6 代价

- 无转发 = 订阅者必须粘性（否则 poll 落别的实例收不到）——由 subscribe→instanceUrl 保证。
- 同 topic 多订阅者散在多实例 → 每实例各自拉全分区（MQ 读放大，N 实例 = N 份读）——换掉转发跳 + 全局协调复杂度。
- 单 session 流量超单实例上限（不可拆）→ 实例限流/拒绝——无解边界。

---

## 三、Frame 协议转换

### 3.1 架构分层

```
┌─ 对外协议层(FrameAdaptor SPI)──────────────────────────────────────┐
│                                                                     │
│  CloudEvents(HTTP/SSE/WS)  → CloudEventsFrameAdaptor  → EventMeshFrame │
│  MeshMessage(legacy TCP)   → MeshMessageFrameAdaptor   → EventMeshFrame │
│  A2A(JSON-RPC 2.0)         → A2AFrameAdaptor           → EventMeshFrame │
│  未来新协议                → 新 FrameAdaptor            → EventMeshFrame │
│                                                                     │
│  三种协议平级，各自直接转 Frame，互不经过 CloudEvent                   │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ 对外协议直接转 Frame
┌───────────────────────────────▼─────────────────────────────────────┐
│  内部(runtime + storage)：全程 EventMeshFrame                          │
│                                                                       │
│  ingress publish → Frame → storage.send(Frame) → MQ 字节              │
│  MQ 字节 → storage.poll()→Frame → dispatch/filter/TTL → Frame         │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ egress：Frame → 对应 FrameAdaptor
┌───────────────────────────────▼─────────────────────────────────────┐
│  egress(FrameAdaptor SPI，按客户端连接协议)                              │
│                                                                       │
│  Frame → CloudEvents-JSON（SSE / WS / HTTP poll）                      │
│  Frame → MeshMessage Package（legacy TCP）                             │
│  Frame → A2A JSON-RPC bytes（A2A 回调）                                │
└───────────────────────────────────────────────────────────────────────┘
```

**CloudEvent 不是内部表示**——它是 CloudEvents 客户端的对外入口格式之一，经 `CloudEventsFrameAdaptor` 转 Frame 后进入内部。MeshMessage 和 A2A 同理，各自有独立 adaptor，互不经过 CloudEvent。

### 3.2 EventMeshFrame wire 格式

```
定长头 14B:
  [magic:1=0xEF][ver:1=1][msgType:1][flags:1][seq:4][keyCount:2][dataLen:4]

KV 属性段（keyCount ×）:
  [nameLen:2][name:UTF-8][valLen:4][value:UTF-8]

data:
  raw bytes（streaming 的 chunk/prompt / 事件的业务 data）

msgType = STREAM_REQ(1) | STREAM_CHUNK(2) | EVENT(3)
flags   = bit0 done | bit1 hasError | bit2 hasMeta（streaming 用）
```

### 3.3 三种 msgType 的字段映射

| msgType | 定长头字段 | KV 属性 | data |
|---|---|---|---|
| **STREAM_REQ** | — | `sid`=sessionId, `replyTo`=回复地址, `model`?, `conv`? | prompt 文本 |
| **STREAM_CHUNK** | `seq`=流内序号, `flags.done`=终止标记 | `sid`, `etype`?, `err`?, `meta`?(JSON) | chunk 文本 |
| **EVENT** | — | `id`/`type`/`source`/`subject`/`time`/`emttl`/`emcorrelationid`/`empopck`/用户扩展 | 事件 payload |

### 3.4 FrameAdaptor SPI

对外协议 ↔ EventMeshFrame 的双向转换由 `FrameAdaptor` SPI 定义（`eventmesh-protocol-api/.../FrameAdaptor.java`）：

```java
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.PROTOCOL)
public interface FrameAdaptor {
    EventMeshFrame toFrame(ProtocolTransportObject proto);       // ingress
    ProtocolTransportObject fromFrame(EventMeshFrame frame);     // egress
    String getProtocolType();
}
```

runtime 不直接调 `EventMeshFrame.fromCloudEvent()` / `.toCloudEvent()` / `MeshMessageFrameCodec`——全部经 `FrameAdaptors.get(协议名)` 加载对应 adaptor。**加新协议只需实现 `FrameAdaptor` + 注册 SPI，不改 runtime 代码。**

### 3.5 协议插件模块

| 模块 | FrameAdaptor | 对外协议 | 转换方式 |
|---|---|---|---|
| **protocol-api** | —（只 SPI 接口 + `FrameAdaptors` 加载器） | — | 零实现 |
| **protocol-cloudevents** | `CloudEventsFrameAdaptor` | CloudEvents-JSON（HTTP/SSE/WS） | CE-JSON bytes ↔ Frame（经 CE 对象做字段映射） |
| **protocol-meshmessage** | `MeshMessageFrameAdaptor` | MeshMessage Package（TCP） | Package ↔ Frame（直接字段映射，零 CE 中转） |
| **protocol-a2a** | `A2AFrameAdaptor` | A2A JSON-RPC 2.0 | JSON-RPC bytes ↔ Frame（直接字段映射，零 CE 中转） |
| ~~protocol-grpc~~ | — | — | **已删除**（空壳无源码） |

### 3.6 WireCodec SPI（内部 MQ wire 编码）

内部 MQ wire 的字节编解码由 `WireCodec` SPI 定义（`eventmesh-common/.../wire/`）：

```java
public interface WireCodec {
    byte[] encode(StreamRequest request);
    StreamRequest decodeRequest(byte[] bytes);
    byte[] encode(StreamChunk chunk);
    StreamChunk decodeChunk(byte[] bytes);
    byte[] encode(CloudEvent event);
    CloudEvent decodeEvent(byte[] bytes);
}
```

- 默认实现 `EventMeshFrameCodec`（EventMeshFrame ↔ byte[]）。
- 可通过 `-Deventmesh.wire.codec=<fqcn>` 替换。

### 3.7 各协议的完整转换链路

#### CloudEvents（HTTP/SSE/WS 客户端）

**Ingress**（客户端 → runtime）：
```
SDK 发 CloudEvents-JSON bytes
  → UniHttpServer.publish: body = CE-JSON bytes
  → UniIngressService.publish(topic, CloudEvent): CE → EventMeshFrame.fromCloudEvent(event)
  → storage.send(topic, frame): frame.encode() → MQ 字节
```

**Egress**（runtime → 客户端）：
```
storage.poll() → frame (EventMeshFrame)
  → SseConnection.send / WsConnection.send / UniHttpServer.poll
  → FrameAdaptors.toCloudEventsJson(frame): frame.toCloudEvent() → CE-JSON serialize
  → 写 SSE data: / WS TextFrame / HTTP JSON
```

#### MeshMessage（legacy TCP 客户端）

**Ingress**（客户端 → runtime）：
```
旧 TCP SDK 发 Package（EventMeshMessage）
  → MeshMessagePackageRouter.route(pkg)
  → FrameAdaptors.get("meshmessage").toFrameSilent(pkg)
  → MeshMessageFrameAdaptor.toFrame:
      topic → attributes["subject"]
      body → data
      header.seq → attributes["id"]
      properties → attributes KV
  → TcpRequest.publish(topic, frame)
  → UniIngressService.publish(topic, frame)（Frame 重载，跳过 CE 转换）
  → storage.send(topic, frame)
```

**Egress**（runtime → 客户端）：
```
ReliableDispatcher.deliver → NettyTcpPushChannel.deliver(deliveryId, frame, callback)
  → FrameAdaptors.get("meshmessage").fromFrameSilent(frame)
  → MeshMessageFrameAdaptor.fromFrame:
      attributes["subject"] → topic
      data → body
      attributes KV → properties
  → Package(header=ASYNC_MESSAGE_TO_CLIENT, body=EventMeshMessage)
  → channel.writeAndFlush(pkg)
```

#### A2A（JSON-RPC 客户端）

**Ingress**：
```
A2A 客户端发 JSON-RPC 2.0 bytes
  → FrameAdaptors.get("a2a").toFrameSilent(new ByteTransport(jsonBytes))
  → A2AFrameAdaptor.toFrame:
      parse JSON-RPC → extract method/params/_topic
      method → attributes["ema2amethod"]
      params._topic → attributes["subject"]
      raw JSON → data
  → EventMeshFrame
```

**Egress**：
```
EventMeshA2ATransport 收到 frame
  → frame.data() = 原始 A2A JSON-RPC bytes（ingress 时原样保存）
  → ByteTransport(jsonBytes) → A2A 客户端
```

### 3.8 Storage SPI 的 Frame 转换

storage 插件（`MeshStoragePlugin` + `LiteTopicCapable`）的 send/poll 直接携带 EventMeshFrame：

```
send(topic, EventMeshFrame frame):
  → rocketmq5: frame.encode() → MQ message body bytes
  → kafka: frame.encode() → ProducerRecord value
  → rocketmq4: frame.encode() → MQ message body bytes

poll(topic, partition, ...):
  → MQ bytes → EventMeshFrame.decode(bytes)
  → 带 legacy CE-JSON fallback（旧消息 → EventMeshFrame.fromCloudEvent(deserialize)）
```

### 3.9 落地范围

| 层 | 改动 | 状态 |
|---|---|---|
| storage SPI | `MeshStoragePlugin.send/poll` + `LiteTopicCapable.sendLite/pullLite` 全改 EventMeshFrame | ✅ |
| runtime dispatch | Delivery/BufferedEvent/PushChannel/Connection/DeadLetterSink/ReliableDispatcher/PushService + CloudEventFilter/SubscriptionManager 全翻 Frame | ✅ |
| streaming | Mode-1（runtime↔agent 跨进程）+ Mode-2（runtime 内部 pub/sub）全用 EventMeshFrame | ✅ |
| legacy 连接器 | Producer/Consumer SPI 不动（独立子系统，说 CE，边界转换） | ✅ 保留 |
| WireCodec SPI | 默认 EventMeshFrameCodec，可替换 | ✅ |
| FrameAdaptor SPI | CloudEvents 独立插件 + MeshMessage + A2A 各有独立 adaptor | ✅ |

---

**文档版本**: v1.0 · **整理时间**: 2026-08-13 · 关联: [`eventmesh-uni-architecture-redesign.md`](./eventmesh-uni-architecture-redesign.md) §19
