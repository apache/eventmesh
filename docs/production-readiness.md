# EventMesh Uni-Architecture 生产准入清单

> 状态: 2026-07-10 (更新: §2 阻塞点全部解决 — remoting 插件 offset 游标 + 多 broker queue 寻址修复并验证). 基于真 RocketMQ (127.0.0.1:9876) + 真 Nacos (127.0.0.1:8848) 集成测试.

## 1. 已验证 ✅

| 能力 | 验证方式 | 结果 |
|---|---|---|
| 发布/订阅/轮询/ACK | RealBrokerIntegrationTest (真 RocketMQ) | ✅ 全链路 |
| 客户端 SDK (HTTP CloudEvents) | ClientBrokerIntegrationTest | ✅ publish/subscribe + request/reply |
| 老 TCP 协议兼容 | LegacyTcpClientIntegrationTest + LegacyTcpClusterBrokerIntegrationTest | ✅ 老 SDK 零改动 |
| 动态 connector 调度 | ConnectorSchedulingIntegrationTest | ✅ runtime push /control/start |
| 多实例无重复消费 | MultiInstanceRocketMqIntegrationTest (2 实例) | ✅ 5 条恰好投递一次 |
| 跨实例 forward (Nacos) | NacosClusterForwardIntegrationTest | ✅ sub 在 A, publish 在 B, A 收到 |
| ACK 超时重投递 + DLQ | AckTimeoutRedeliveryIntegrationTest | ✅ at-least-once + DLQ |
| 限流 (429) | RateLimitIntegrationTest | ✅ admin + metrics |
| TLS/HTTPS | TlsIntegrationTest | ✅ 端到端 |
| 死信 replay | DlqIntegrationTest | ✅ DLQ -> 回原 topic |
| 吞吐 | LoadThroughputIntegrationTest (真实多 broker, 修复后实测) | ~101 ev/s, 0 丢失, 0 重复 (500 事件突发) |

## 2. 阻塞点 ⛔ → ✅ 已全部解决

> 截至 2026-07-10，针对 **RocketMQ 部署无已知硬阻塞**。以下两项曾阻塞上生产，现已定位真因并修复 + 验证。

### 2.1 消息丢失（订阅/重启窗口）✅ 已解决
- **最初现象** (2026-07-08, 旧 DefaultLitePullConsumer 插件): 500 条突发, 57 条 (11%) 丢失, 0 重复。当时归因为 RocketMQ `CONSUME_FROM_LAST_OFFSET` + rebalance 延迟。
- **真因** (2026-07-10 定位): `RocketMQRemotingStoragePlugin` 两个 latent bug —— remoting 插件号称"解决 offset-seek 丢失"，实则未根治:
  1. **pull 游标卡在 offset 0**: `poll()` 仅在 `SUCCESS` 时记录 `nextBeginOffset`; broker 对越界 offset 返回 `OFFSET_MOVED` (code 20/21) 被静默丢弃 → 游标反复请求 offset 0, 永远拉不到新消息。(当 topic 有历史、min offset > 0 时触发。)
  2. **多 broker queue 寻址错误**: `poll`/`send` 把全局扁平 queueId 当 `header.queueId`, 但 RocketMQ queueId 按 broker 局部编号 → 跨 broker 的 topic 有 ~80% 队列映射到无效局部 id → 返回 null → **静默不消费** (每跑 2080 条 ERROR 日志)。
- **为何之前没暴露**: LoadThroughput 用无历史的新 topic (min offset=0, 不触发 bug 1) 且多为单 broker 路由 (不触发 bug 2), 故显示 0 丢失; ClientBroker 的 topic `em-it-client` 跨多次运行有历史 + TBW102 自动创建 topic 扩散到 5 个 broker → 同时触发两个 bug → pub-1 收不到。
- **修复**:
  1. 始终用 `nextBeginOffset` 推进游标 (NO_NEW_MSG=空操作 / OFFSET_MOVED=修正 / FOUND=前进) + null-`nextBeginOffset` guard。
  2. `getBrokerForQueue` 返回 `QueueLoc(brokerAddr, localQueueId)` (按 `readQueueNums` 扁平, 每 broker 局部 id 从 0 起), send/poll 用 localQueueId。
  - 附带: `RPC_TIMEOUT_MS` 100→1000ms (100ms 伪超时); 去掉单次 pull 失败永久拉黑 broker。
- **验证**: `ClientBrokerIntegrationTest` GREEN (真实多 broker, pub/sub + request-reply); offset 文件从仅 `#0–#3` 变为 `#0–#19` (20 队列全消费); 幽灵队列 ERROR 2080→0; 全套 runtime 82 tests 0 failures。

### 2.2 offset 持久化（重启恢复）✅ 已解决
- **现象**: autoCommit=false + 不 commit MQ offset → 重启后位置不持久 → CONSUME_FROM_LAST_OFFSET (跳到最新, 丢窗口) 或 FIRST_OFFSET (从 0 重放, 重复)。
- **修复**: remoting 插件自管 pull offset (per `topic#queueId`), 持久化到 `./data/offset/rocketmq-pull-offsets.properties`, 重启加载 + 始终从 `nextBeginOffset` 续传 (见 2.1)。
- **验证**: 多次测试运行间 offset 正确续传 (load 1 topic → persist 1 topic, offset 持续前进)。

## 3. 已知限制 ⚠️ (可灰度, 需文档化)

| 项 | 说明 |
|---|---|
| RocketMQ 分区分配 | subscribe-mode + 共享 consumer group -> broker rebalance (稳态无重复). EventMesh PartitionOwnership 旁路 (partitionCount=-1). 设计如此. |
| Gen fencing | 软 fence (Meta gen, 能读 Meta 时生效) + lease gate (Meta 不可达 -> 停 poll). 网络分区期间仍有 at-least-once 重复窗口 (直到 lease 过期). |
| WebSocket 推送 | `UniWsServer` **已实现** (netty 握手 + 每连接 `ConnectionPushPump` + ACK 控制帧 + wss; SDK `subscribeWs`), 但**无集成测试** (端到端 WS 推送未验证); SSE + long-polling 已验证. |
| Nacos watch 时序 | naming.subscribe push 延迟波动 -> 测试需 30s 等待. 多实例 watch 在组合跑时偶发 flaky. |
| Kafka 分区 | assign+seek (EventMesh 自管). 未对真 Kafka 实测 (无 Kafka broker). |

## 4. 可观测性 📊

### 4.1 Metrics (已有)
- `GET /admin/metrics` -> JSON: publishCount, publishFailed, rateLimited, eventsDispatched, ackCount, redeliveries, dlqCount, pendingDeliveries. (**唯一内置读出**)
- OTel instruments (LongCounter/LongHistogram) 已埋点, 名如 `eventmesh_publish_count` / `eventmesh_publish_failed_count` / `eventmesh_rate_limited_count` / `eventmesh_dispatched_count` / `eventmesh_ack_count` / `eventmesh_redeliveries_count` / `eventmesh_dlq_count` / `eventmesh_dispatch_latency_nanos`. **运行时未捆绑 exporter** —— 需部署侧挂 OTel SDK/agent (Prometheus 或 OTLP collector); 旧 `eventmesh-metrics-prometheus` 插件未接.
- `GET /admin/subscriptions?topic=` -> live subscriptions.
- `GET /admin/offsets?topic=` -> distribution offsets.
- `GET /admin/health` -> liveness + pending + partition ownership.

### 4.2 需补
- [ ] OTel metrics exporter 接入 (Prometheus / OTLP) —— 当前仅 `/admin/metrics` JSON, 无 Prometheus scrape 端点.
- [x] Grafana dashboard 模板 (§7.4).
- [x] 告警规则 (§7.3: 发布失败率 / DLQ 率 / 积压 / 实例 down).
- [ ] 端到端 distributed tracing (OTel spans 存在: startPublish/startDispatch/startAck/startDlq; 需接 collector).
- [x] SLO 定义 (§7).

### 4.3 Admin API 一览
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
| `/connector/offset?connectorId=` | GET/POST | connector offset |

## 5. 运维 Runbook

### 5.1 部署
```
# runtime 镜像
docker run -p 8080:8080 -p 8081:8081 \
  -e EVENTMESH_STORAGE_TYPE=kafka \
  -e JAVA_OPTS="-Deventmesh.meta.type=nacos -Deventmesh.meta.addr=nacos:8848" \
  -v $PWD/conf:/data/app/eventmesh/conf \
  eventmesh:uni

# connector 镜像
docker run -e EVENTMESH_RUNTIME_URL=http://runtime:8080 \
  -e CONNECTOR_OPTS="-Dconnector.1.class=...KafkaSourceConnector -Dconnector.1.mode=source -Dconnector.1.topic=t1" \
  eventmesh-connector:uni
```

### 5.2 关键配置
| 配置 | 默认 | 说明 |
|---|---|---|
| `eventmesh.storage.type` | standalone | kafka/rocketmq (standalone 已删) |
| `eventmesh.http.port` | 8080 | 流量 HTTP |
| `eventmesh.admin.port` | 8081 | 管理 HTTP |
| `eventmesh.offset.path` | ./data/offset | RocksDB offset 目录 |
| `eventmesh.meta.type` | (空) | nacos (空=单实例 InMemory) |
| `eventmesh.meta.addr` | (空) | nacos 地址 |
| `eventmesh.tls.keystore` | (空) | TLS keystore 路径 |
| `eventmesh.ws.port` | (空) | WebSocket (-1=关) |

### 5.3 存储配置
- Kafka: `conf/kafka-client.properties` -> `bootstrap.servers`
- RocketMQ: `conf/eventmesh.properties` -> `eventMesh.server.rocketmq.namesrvAddr`

### 5.4 常见故障
| 症状 | 排查 | 处理 |
|---|---|---|
| 启动报 "no MeshStoragePlugin" | `eventmesh.storage.type` 错或 SPI jar 不在 classpath | 设正确 type + 确保 storage jar 在 `plugin/storage/<type>/` |
| 启动报 "topic not exist" (CODE 17) | broker autoCreateTopicEnable=false | 预建 topic (4+ queues) |
| 消息不投递 | `/admin/health` 查 partitionOwnership + pendingDeliveries | 消费者未分配 / ACK 积压 |
| DLQ 堆积 | `/admin/dlq/browse?topic=` 查死信 | 修消费者 -> `/admin/dlq/replay` 回放 |
| 限流 | `/admin/metrics` 查 rateLimited | `PUT /admin/ratelimit` 调整 |
| 多实例重复 | 查 `partitionOwnership` (null=poll-all, broker rebalance) | 确认 broker rebalance 正常 |
| Meta 不可达 | 日志 "heartbeat failed - lease invalid" | 修 Nacos; 实例自动停 poll (lease gate) |

### 5.5 升级/回滚
- 滚动重启: 逐实例 `docker stop` -> `docker run` (新镜像). Lease gate 确保停 poll 期间不重复.
- 回滚: 同上, 换旧镜像. offset 兼容 (RocksDB 格式不变).
- **注意**: offset 持久化 + 续传已修 (§2.1/§2.2), 滚动重启不丢窗口内消息。Lease gate 确保实例停 poll 期间不重复。

## 6. 下一步优先级

1. ✅ ~~消息丢失 / offset-seek (§2.1)~~ -- 真因: pull 游标卡 offset 0 + 多 broker queue 寻址; 已修并验证 (ClientBroker IT GREEN).
2. ✅ ~~offset persist + 续传 (§2.2)~~ -- pull-offset 持久化 + 始终从 nextBeginOffset 续传.
3. 📊 OTel exporter 接入 (metrics + trace → Prometheus / OTLP collector): 仪器 + spans 已埋点, 但**运行时未捆绑 exporter** (§4.2); dashboard / 告警 / SLO 模板已就绪 (§7), 接 exporter 后才可实测.
4. 🧪 broker/Nacos failover 混沌测试.
5. 🔒 mTLS 双向认证: 单向 TLS 已实现+测试 (`TlsContextFactory`+`withTls`); **client-auth 强制未接** (`UniHttpServer` 用默认 `HttpsConfigurator`, 未设 `needClientAuth`). auth filter (Token/ACL/HMAC) 已实现.
6. 🧪 WebSocket 集成测试: `UniWsServer` 已实现 + wss, 但无 IT (SDK `subscribeWs` 端到端未验证).

## 7. SLO 定义

### 7.1 指标采集 (已有)
| 指标 | 来源 | 用途 |
|---|---|---|
| `publishCount` / `publishFailed` | `/admin/metrics` | 发布成功率 |
| `eventsDispatched` / `ackCount` | `/admin/metrics` | 投递成功率 |
| `redeliveries` / `dlqCount` | `/admin/metrics` | 可靠性（重投/DLQ 率）|
| `pendingDeliveries` | `/admin/metrics` | 积压 |
| `rateLimited` | `/admin/metrics` | 限流频率 |
| `consumeLag` | `storage.poll` offset vs `endOffset` | 消费延迟 |

### 7.2 SLO 目标（建议初值，灰度后调整）

| SLO | 目标 | 告警阈值 | 计算方式 |
|---|---|---|---|
| **发布可用性** | ≥ 99.9% | publishFailed / publishCount > 0.1% 持续 5min | `1 - publishFailed / publishCount` |
| **投递延迟 P99** | ≤ 500ms | P99 > 1s 持续 5min | publish 时间戳 -> ACK 时间戳 |
| **端到端延迟 P99** | ≤ 2s | P99 > 5s 持续 5min | publish -> subscriber 收到 |
| **DLQ 率** | ≤ 0.01% | dlqCount / eventsDispatched > 0.1% | `dlqCount / eventsDispatched` |
| **积压** | ≤ 1000 | pendingDeliveries > 5000 持续 5min | `pendingDeliveries` |
| **消费延迟** | ≤ 10s | consumeLag > 60s 持续 5min | `endOffset - pollOffset` |
| **限流频率** | ≤ 1% | rateLimited / publishCount > 5% | `rateLimited / publishCount` |
| **可用性** | ≥ 99.9% | `/admin/health` 非 UP 持续 3min | HTTP 200 |

### 7.3 告警规则（Prometheus / Alertmanager）

> **前提**: 需先接 OTel → Prometheus exporter (§4.2, 当前未捆绑). counter 仪器名 `eventmesh_publish_count` 经 Prometheus exporter 导出为 `eventmesh_publish_count_total` (monotonic counter 自动加 `_total`).

```yaml
# publish failure rate
- alert: PublishFailureRateHigh
  expr: rate(eventmesh_publish_failed_total[5m]) / rate(eventmesh_publish_count_total[5m]) > 0.001
  for: 5m
  labels: { severity: critical }
  annotations: { summary: "Publish failure rate > 0.1%" }

# DLQ rate
- alert: DlqRateHigh
  expr: rate(eventmesh_dlq_count_total[5m]) / rate(eventmesh_events_dispatched_total[5m]) > 0.001
  for: 5m
  labels: { severity: warning }
  annotations: { summary: "DLQ rate > 0.1%" }

# pending deliveries backlog
- alert: PendingDeliveriesHigh
  expr: eventmesh_pending_deliveries > 5000
  for: 5m
  labels: { severity: warning }
  annotations: { summary: "Pending deliveries > 5000" }

# health
- alert: EventMeshDown
  expr: up{job="eventmesh"} == 0
  for: 3m
  labels: { severity: critical }
  annotations: { summary: "EventMesh instance down" }
```

### 7.4 Grafana dashboard 模板

Dashboard 建议包含 4 行:
1. **发布**: publishCount (rate) + publishFailed (rate) + rateLimited (rate)
2. **投递**: eventsDispatched (rate) + ackCount (rate) + redeliveries (rate)
3. **积压**: pendingDeliveries (gauge) + dlqCount (counter)
4. **健康**: health (UP/DOWN) + consumeLag per topic
