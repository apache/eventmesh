# EventMesh 生产准入与功能测试总结

> 更新: 2026-08-14 — 全 P0-P2 代码质量修复完成 + offset/ACK/负载均衡修复。基于全量 unit + 5.x E2E 验证。
> 架构: 内部全程 EventMeshFrame（详见 `eventmesh-uni-architecture-redesign.md` §19）。

---

## 1. 架构定位

**MQ-as-stateless-WAL + HTTP SDK（CloudEvents/MeshMessage/A2A 多协议）+ EventMesh 自管订阅/offset + 内部 EventMeshFrame** 的重写（详见 `docs/eventmesh-uni-architecture-redesign.md`）。采用叠加式策略：新核心并行存在，旧 TCP/HTTP 保留为兼容适配层（老客户端零改动）。

> **能力状态定级**：各能力（HTTP+CloudEvents / 存储 / SSE·WS / Connector / A2A / Legacy SDK）
> 的状态、建议与迁移目标以主 README 的[能力状态表](../README.md#能力状态capability-status)为准；
> 本文档记录生产准入的验证与运维细节。

## 2. 模块（活跃 gradle）

| 类别 | 模块 |
|---|---|
| 核心 | `eventmesh-runtime`(~90 主类)、`common`、`spi` |
| SDK | `sdk-java`（新 `CloudEventsClient` 含 streaming + lite + 旧 TCP/HTTP/gRPC SDK） |
| 存储 | `storage-api`、`-kafka`、`-rocketmq`、`-rocketmq5`（含 `LiteTopicCapable`） |
| 协议 | `protocol-api`（SPI 接口+加载器）、`-cloudevents`（FrameAdaptor）、`-meshmessage`、`-a2a` |
| 连接器 | `connector-runtime` + 24 个 connector 插件 |
| Wire | `common/.../wire/`（EventMeshFrame + WireCodec + MeshMessageFrameCodec） |

## 3. 核心功能（runtime 按包）

- **boot**: `UniRuntime`（pull/tick 调度循环）、`EventMeshApplication`（runtime+http+admin+session+streaming 一体启动）
- **ingress**: `UniIngressService` — publish / subscribe / poll / ack / request-reply / **streaming session**（`LoadMeter` 自采负载）
- **session**: `SessionRouter` — Mode-1 流式调用（多路复用+client 亲和）+ Mode-2 发布/订阅（确定性 lite 命名）
- **subscription**: LOAD_BALANCE / BROADCAST / MULTICAST / STICKY + 心跳清理（全翻 EventMeshFrame）
- **delivery**: `ReliableDispatcher` — ACK 跟踪 + 指数退避重试 + DLQ（全翻 EventMeshFrame + **延迟 MQ ACK**）
- **push**: 长轮询 / SSE / WS（egress 处 Frame→CloudEvents-JSON via FrameAdaptor SPI + **写失败 nack dispatcher**）
- **cluster**: `ClusterMembership`（心跳+负载指标）/ `LoadMeter` / `/session/recommend`（全面粘性，停用转发层）
- **wire**: `EventMeshFrame`（统一内部帧）+ `WireCodec` SPI + `FrameAdaptor` SPI（协议转换收进插件）

## 4. 已验证 ✅

| 能力 | 验证方式 | 结果 |
|---|---|---|
| 发布/订阅/轮询/ACK | RealBrokerIntegrationTest (真 RocketMQ) | ✅ 全链路 |
| 客户端 SDK (HTTP CloudEvents) | ClientBrokerIntegrationTest | ✅ publish/subscribe + request/reply |
| 老 TCP 协议兼容 | LegacyTcpClientIntegrationTest | ✅ 老 SDK 零改动, MeshMessage↔Frame 直接转换 |
| 普通 pub/sub (EventMeshFrame) | RocketMQ5BrokerIntegrationTest (-Dit.storage5) | ✅ 2/2 真 broker, 内部 Frame 往返 |
| 流式调用 Mode-1 | StreamingSdkE2ETest$Mode1 (真 broker + mock LLM) | ✅ 单轮+多轮+session 契约 |
| 流式调用 Mode-2 | StreamingSdkE2ETest$PubSub (真 broker) | ✅ pub/sub 有序传递 |
| Lite topic HTTP | RocketMQ5LiteHttpIntegrationTest | ✅ publish/poll lite |
| 负载均衡推荐 | ClusterMembershipLoadTest + LoadMeterTest + LoadBalancingScoringTest | ✅ 负载采集+心跳+评分 |
| Offset 单调写入 (P4) | OffsetMonotonicAndRecoveryTest | ✅ 6 例: 单调/多 client/重启恢复 |
| 延迟 ACK (P2) | DeferredAckDispatcherTest | ✅ 4 例: 客户端ACK触发/超时不触发/DLQ不触发/null兼容 |
| Frame 协议转换 | FrameProtocolConversionTest | ✅ 7 例: CE round-trip/filter/TTL/correlation/POP_CK |
| PushService 缓冲 (P1-4/5) | PushServiceBufferOverflowTest | ✅ 3 例: DROP_OLDEST nack/BLOCK 拒绝/ack 移除 |
| SessionRegistry 原子性 (P1-2) | SessionRegistryAtomicityTest | ✅ 3 例: immutable bean 更新 |
| Delivery 可见性+复活 (P0-3/P1-6) | DeliveryVolatileAndResurrectTest | ✅ 2 例: volatile/tick 不复活 |
| 多实例无重复消费 | MultiInstanceRocketMqIntegrationTest (2 实例) | ✅ 5 条恰好投递一次 |
| ACK 超时重投递 + DLQ | AckTimeoutRedeliveryIntegrationTest | ✅ at-least-once + DLQ |
| 限流 (429) | RateLimitIntegrationTest | ✅ admin + metrics |
| TLS/HTTPS | TlsIntegrationTest | ✅ 端到端 |
| 吞吐 | LoadThroughputIntegrationTest (真实多 broker) | ~101 ev/s, 0 丢失, 0 重复 |

## 5. 测试总览

### 单元/进程内测试（无 broker，全绿）
- runtime: ~50 测试类 / ~150 @Test, **0 failures**
- sdk-java: 29+ @Test | connector-runtime: 13 @Test | common(wire): 12+ @Test(EventMeshFrame)
- 合计 **~160 @Test**
- 覆盖: subscription / offset(RocksDB+单调) / delivery(ACK+DLQ+退避+延迟MQACK) / push(缓冲+溢出) / ratelimit / security / cluster / TLS / DLQ / EventMeshFrame 全 msgType / WireCodec / FrameAdaptor / LoadMeter / ClusterMembership 负载 / SessionRouter / SessionRegistry 原子性 / Delivery volatile

### 真 broker E2E（gated on `rocketmq5Available()`，全绿）
- `RocketMQ5BrokerIntegrationTest` ✅ (普通 pub/sub, EventMeshFrame 往返, 2/2)
- `RocketMQ5LiteHttpIntegrationTest` ✅ (lite publish/poll)
- `StreamingSdkE2ETest` ✅ (Mode-1 streaming ×3 + Mode-2 pub/sub ×1 + Demo ×1)
- `LiteStreamCallIntegrationTest` ✅ (Mode-1 裸 HTTP streaming ×2)
- `LegacyTcpClientIntegrationTest` ✅ (旧 TCP SDK, MeshMessage↔Frame 直接转换)
- `KafkaClientE2EIntegrationTest` ✅ (真 Kafka 3-broker SASL, HTTP publish→subscribe 全链路, EventMeshFrame 往返)

## 6. 已解决的问题

### 6.1 消息丢失（订阅/重启窗口）✅
- **真因**: `RocketMQRemotingStoragePlugin` pull 游标卡 offset 0 + 多 broker queue 寻址错误。
- **修复**: 始终用 `nextBeginOffset` 推进 + `QueueLoc(brokerAddr, localQueueId)` 局部寻址。
- **验证**: ClientBrokerIntegrationTest GREEN（真实多 broker）。

### 6.2 offset 持久化（重启恢复）✅
- **修复**: remoting 插件自管 pull offset（properties 文件），重启加载续传。

### 6.3 P2: RocketMQ 5.x 延迟 ACK ✅
- **修复**: poll 不立即 ACK broker；`ReliableDispatcher.ack()` → `ackPulledMessage()` 触发 broker ACK。恢复 at-least-once。

### 6.4 P4: OffsetStore 非单调写入 ✅
- **修复**: `writeOffset` 改 CAS max（InMemory `accumulateAndGet(Math::max)`，RocksDB read-conditional-put）。

### 6.5 代码质量全面修复（P0-P2, 15 项）✅
**P0（3 项）**：
- SSE/WS push 写失败 → ConnectionPushPump nack dispatcher（原来静默丢失到超时）
- SDK HttpURLConnection 泄漏 → finally disconnect
- Delivery.attempt/nextAttemptAtMs → volatile

**P1（9 项）**：
- UniIngressService/ClusterMembership 字段 → volatile
- SessionRegistry → immutable bean 更新（不原地修改共享对象）
- PushService.offer → synchronized(queue)（TOCTOU + 状态机竞态）
- PushService.DROP_OLDEST → nack 丢弃回调
- ReliableDispatcher.tick → putIfAbsent（防复活已 ACK）
- SDK TCP reconnect → log.error（不再空 catch）
- RocketMQ5 ack → 3 次重试 + 指数退避
- RocksDBOffsetStore → offsetWriteFailures 计数器

**P2（3 项）**：
- SseConnection → synchronized(out)（防并发写交错）
- UniRuntime shutdown → log.debug
- SubscriptionManager.pollAndDispatch → log.warn

## 7. 已知限制 ⚠️ (可灰度, 需文档化)

| 项 | 说明 |
|---|---|
| RocketMQ 4.x ConsumeQueue | ✅ **已验证为误报**:RocketMQ 存储清理不依赖消费位点——CommitLog 按 `fileReservedTime`(72h)+ 磁盘水位(75%/90%)删除,ConsumeQueue 经 `minPhysicOffset` 随 CommitLog 联动删除;`OFFSET_MOVED` 行为本身证明删除不被消费阻塞。`commitOffset` no-op 无存储增长风险 |
| WebSocket 推送 | ✅ 已有集成测试(WebSocketPushIntegrationTest 1/1 通过, subscribeWs 端到端 push) |
| Kafka 分区 | assign+seek 自管; ✅ 已对真 Kafka 实测(KafkaClientE2EIntegrationTest, 3-broker SASL 集群, 2026-08-14) |
| Nacos watch 时序 | naming.subscribe push 延迟波动;多实例 watch 组合跑偶发 flaky |

## 8. 可观测性 📊

### 8.1 Metrics (已有)
- **`GET /metrics`** → **Prometheus scrape 端点**(text exposition v0.0.4):7 counter + 1 gauge,名称与告警规则(§9.3)一致
- `GET /admin/metrics` → JSON: publishCount/publishFailed/rateLimited/eventsDispatched/ackCount/redeliveries/dlqCount/pendingDeliveries
- OTel instruments 已埋点(部署侧可挂 OTel SDK/agent 增强)
- `RocksDBOffsetStore.getOffsetWriteFailures()` — offset 写失败计数（P1-9 加）

### 8.2 需补
- [x] ~~OTel metrics exporter 接入~~ → **已加 `/metrics` Prometheus 端点**(PrometheusEndpointTest 验证)
- [ ] 端到端 distributed tracing(OTel spans 已埋;需接 collector)
- [x] Grafana dashboard 模板 / 告警规则 / SLO 定义(§9)

### 8.3 Admin API 一览
| Endpoint | 方法 | 用途 |
|---|---|---|
| `/admin/metrics` | GET | 运行指标 |
| `/admin/health` | GET | 存活 + pending + 分区 |
| `/admin/subscriptions?topic=` | GET | 活跃订阅 |
| `/admin/offsets?topic=` | GET | 分发 offset |
| `/admin/clients?topic=` | GET | 在线客户端 + pending |
| `/admin/client/reject?clientId=` | POST | 驱逐客户端 |
| `/admin/dlq/replay?topic=&max=` | POST | 死信回放 |
| `/admin/dlq/browse?topic=&max=` | GET | 死信浏览 |
| `/admin/ratelimit` | PUT | 设限流规则 |
| `/admin/connectors` | GET/POST/DELETE | connector CRUD |
| `/admin/connector-workers` | GET/POST | worker 注册/心跳 |

## 9. SLO 定义

### 9.1 指标采集
| 指标 | 来源 | 用途 |
|---|---|---|
| `publishCount` / `publishFailed` | `/admin/metrics` | 发布成功率 |
| `eventsDispatched` / `ackCount` | `/admin/metrics` | 投递成功率 |
| `redeliveries` / `dlqCount` | `/admin/metrics` | 可靠性（重投/DLQ 率）|
| `pendingDeliveries` | `/admin/metrics` | 积压 |
| `rateLimited` | `/admin/metrics` | 限流频率 |
| `consumeLag` | `storage.poll` offset vs `endOffset` | 消费延迟 |

### 9.2 SLO 目标（建议初值，灰度后调整）
| SLO | 目标 | 告警阈值 |
|---|---|---|
| 发布可用性 | ≥ 99.9% | publishFailed/publishCount > 0.1% 持续 5min |
| 投递延迟 P99 | ≤ 500ms | P99 > 1s 持续 5min |
| 端到端延迟 P99 | ≤ 2s | P99 > 5s 持续 5min |
| DLQ 率 | ≤ 0.01% | dlqCount/eventsDispatched > 0.1% |
| 积压 | ≤ 1000 | pendingDeliveries > 5000 持续 5min |
| 消费延迟 | ≤ 10s | consumeLag > 60s 持续 5min |
| 限流频率 | ≤ 1% | rateLimited/publishCount > 5% |
| 可用性 | ≥ 99.9% | `/admin/health` 非 UP 持续 3min |

### 9.3 告警规则（Prometheus / Alertmanager）
```yaml
- alert: PublishFailureRateHigh
  expr: rate(eventmesh_publish_failed_total[5m]) / rate(eventmesh_publish_count_total[5m]) > 0.001
  for: 5m
  labels: { severity: critical }
- alert: DlqRateHigh
  expr: rate(eventmesh_dlq_count_total[5m]) / rate(eventmesh_events_dispatched_total[5m]) > 0.001
  for: 5m
  labels: { severity: warning }
- alert: PendingDeliveriesHigh
  expr: eventmesh_pending_deliveries > 5000
  for: 5m
  labels: { severity: warning }
- alert: EventMeshDown
  expr: up{job="eventmesh"} == 0
  for: 3m
  labels: { severity: critical }
```

## 10. 运维 Runbook

### 10.1 部署
```bash
# runtime 镜像
docker run -p 8080:8080 -p 8081:8081 \
  -e EVENTMESH_STORAGE_TYPE=rocketmq5 \
  -e EVENTMESH_ROCKETMQ5_NAMESRV=broker:9876 \
  -e JAVA_OPTS="-Deventmesh.meta.type=nacos -Deventmesh.meta.addr=nacos:8848" \
  eventmesh:uni

# connector 镜像
docker run -e EVENTMESH_RUNTIME_URL=http://runtime:8080 \
  eventmesh-connector:uni
```

### 10.2 关键配置
| 配置 | 默认 | 说明 |
|---|---|---|
| `eventmesh.storage.type` | standalone | rocketmq5 / rocketmq / kafka |
| `eventmesh.http.port` | 8080 | 流量 HTTP |
| `eventmesh.admin.port` | 8081 | 管理 HTTP |
| `eventmesh.offset.path` | ./data/offset | offset 目录 |
| `eventmesh.meta.type` | (空) | nacos (空=单实例) |
| `eventmesh.meta.addr` | (空) | nacos 地址 |
| `eventmesh.tls.keystore` | (空) | TLS |
| `eventmesh.ws.port` | (空) | WebSocket |
| `eventmesh.rocketmq5.lite.checkpoint.interval.ms` | 5000 | lite offset checkpoint |
| `eventmesh.offset.meta` | false | meta-backed offset(显式开) |
| `eventmesh.http.advertisedAddr` | (空) | instanceUrl pin(显式配) |
| `eventmesh.wire.codec` | EventMeshFrameCodec | 内部 wire 编码 |
| `eventmesh.storage5` / `eventmesh.namesrv5` | (空) | 5.x E2E gating |

### 10.3 常见故障
| 症状 | 排查 | 处理 |
|---|---|---|
| 启动报 "no MeshStoragePlugin" | `eventmesh.storage.type` 错或 SPI jar 不在 classpath | 设正确 type + 确保 storage jar |
| 启动报 "topic not exist" (CODE 17) | broker autoCreateTopicEnable=false | 预建 topic (4+ queues) |
| 消息不投递 | `/admin/health` 查 pendingDeliveries | 消费者未分配 / ACK 积压 |
| DLQ 堆积 | `/admin/dlq/browse?topic=` | 修消费者 → `/admin/dlq/replay` |
| 限流 | `/admin/metrics` 查 rateLimited | `PUT /admin/ratelimit` |
| offset 写失败持续增长 | 查 `RocksDBOffsetStore.getOffsetWriteFailures()` | 检查磁盘/权限 |
| SSE 客户端收不到 | 连接写失败现在会 nack dispatcher → 立即重投 | 检查客户端网络 |

### 10.4 升级/回滚
- 滚动重启: 逐实例 stop→run。offset 持久化+续传已修，不丢窗口内消息。
- 回滚: 换旧镜像。offset RocksDB 格式不变。

## 11. 下一步优先级

1. ✅ ~~消息丢失 / offset-seek~~ — pull 游标 + 多 broker 寻址修复
2. ✅ ~~offset persist + 续传~~ — properties 文件续传
3. ✅ ~~P2: RocketMQ 5.x 延迟 ACK~~ — ReliableDispatcher.ack() 触发 ackPulledMessage()
4. ✅ ~~P4: OffsetStore 单调写入~~ — CAS max
5. ✅ ~~P0-P2 代码质量(15 项)~~ — volatile/immutable/synchronized/nack/retry 全修
6. ✅ ~~Kafka 真实实测~~ — KafkaClientE2EIntegrationTest 通过(3-broker SASL)
7. ✅ ~~OTel exporter 接入~~ — `/metrics` Prometheus scrape 端点(PrometheusEndpointTest 验证)
8. ✅ ~~mTLS 双向认证~~ — `-Deventmesh.tls.needClientAuth=true` + truststore → HttpsConfigurator needClientAuth
9. ✅ ~~WebSocket 集成测试~~ — WebSocketPushIntegrationTest 1/1 通过
10. ✅ ~~RocketMQ 4.x ConsumeQueue 清理~~ — **验证为误报**(CommitLog 时间清理 + ConsumeQueue 联动,不依赖消费位点;OFFSET_MOVED 行为佐证)
11. 🧪 broker/Nacos failover 混沌测试 — 需要破坏性环境
12. 📊 端到端 distributed tracing — OTel spans 已埋,需接 collector
