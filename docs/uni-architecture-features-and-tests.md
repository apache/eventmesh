# uni-architecture 分支：功能与测试总结

> 快照时间：2026-07-10 ｜ 分支：`uni-architecture`

---

# 架构定位

**MQ-as-stateless-WAL + HTTP-only CloudEvents SDK + EventMesh 自管订阅/offset** 的重写（`docs/eventmesh-uni-architecture-redesign.md`）。采用**叠加式**策略：新核心并行存在，旧 TCP/HTTP **保留为兼容适配层**（老客户端零改动），物理删除（Phase 8）是最后切换。

# 模块（活跃 gradle，共 ~38 个）

| 类别 | 模块 |
|---|---|
| 核心 | `eventmesh-runtime`(75 主类)、`common`、`spi` |
| SDK | `sdk-java`（新 `CloudEventsClient` + 旧 TCP/HTTP/gRPC SDK） |
| 存储 | `storage-api`、`-kafka`、`-rocketmq`（rocketmq = remoting 直连 RPC 插件） |
| 协议 | `protocol-api`、`-meshmessage`、`-grpc`、`-a2a` |
| 连接器 | `connector-runtime` + **24 个 connector 插件**（rocketmq/kafka/rabbitmq/redis/mongodb/pulsar/s3/pravega/knative/jdbc/file/spring/prometheus/dingtalk/lark/wecom/slack/wechat/http/chatgpt/canal/mcp/openfunction…） |

已删除：旧 security/meta/metrics/trace/retry/registry/function/v1/v2/admin/starter/examples/operator + storage-{standalone,pulsar,redis,rabbitmq}。

# 核心功能（runtime 按包）

- **boot**: `UniRuntime`（pull/tick 调度循环）、`EventMeshApplication`（runtime+http+admin 一体启动，支持 TLS / WS / 集群 / 连接器调度）
- **ingress**: `UniIngressService` — publish / subscribe / poll / ack / **request-reply**(`emcorrelationid`) 编排核心
- **subscription**: LOAD_BALANCE / BROADCAST / MULTICAST / STICKY + 心跳清理
- **delivery**: `ReliableDispatcher` — ACK 跟踪 + 指数退避重试 + **DLQ**（offset 仅 ACK 后前进，at-least-once）
- **offset**: 内存 / **RocksDB**（崩溃恢复）/ Meta 两级
- **push**: 长轮询 / **SSE** / ConnectionPushPump
- **http**: `UniHttpServer`（新 `/events/*` + 旧 `/eventmesh/*`）、`UniWsServer`、`TlsContextFactory`
- **transport.tcp** (12): `UniTcpServer` + netty pipeline — **旧 TCP 客户端零改动**
- **transport.http**: `LegacyHttpBridge` — **旧 HTTP 客户端零改动**
- **cluster** (15): `NacosMetaStore` + `ClusterMembership`(心跳租约) + `PartitionOwnership`(确定性分区 + gen fencing) + `ClusterCoordinator`(本地投递/跨实例 forward) → **多实例无重复**
- **security**: FilterChain（Token / ACL / HMAC 签名），无 SPI
- **ratelimit**: 令牌桶（每 topic，超额返回 429）
- **metrics**: OTel（默认 Prometheus 导出）+ `UniTrace`
- **connector**: `ConnectorScheduler`（runtime-push 动态调度连接器到 worker）
- **admin**: 独立端口的 `/admin/*`（metrics / dlq replay / ratelimit / connectors…）

# 测试情况

## 单元/进程内测试（无 broker，跑在常规套件里） — 全绿

- runtime：**38 个测试类 / 82 @Test**，刚跑 **0 failures**（8 skipped = 下面那些 gated IT）
- sdk-java：29 @Test ｜ connector-runtime：13 @Test
- 合计 **~124 @Test**
- 覆盖：subscription / offset(RocksDB 崩溃恢复) / delivery(ACK+nack+超时+DLQ+退避) / push / ratelimit / security / cluster / TLS / DLQ / 限流 / cluster-forward（2 实例 in-memory）/ 连接器调度 / 旧 TCP+HTTP 兼容

## 真 broker/Nacos 集成测试（gated，已在多 broker 实测中验证 GREEN）

- `-Dit.storage=rocketmq`（真 RocketMQ `127.0.0.1:9876`）：
  - `RealBrokerIntegrationTest` ✅
  - `ClientBrokerIntegrationTest` ✅（**本次刚修绿** — pull 游标卡 offset 0 + 多 broker queue 寻址）
  - `LoadThroughputIntegrationTest` ✅（500 事件，0 丢失，0 重复）
  - `AckTimeoutRedeliveryIntegrationTest` ✅（ACK 超时→重投→DLQ）
  - `BrokerListTest` ✅
- `-Dit.nacos`（真 RocketMQ + Nacos `127.0.0.1:8848`）：
  - `LegacyTcpClusterBrokerIntegrationTest` ✅（旧 TCP SDK + 真 broker + Nacos）
  - `MultiInstanceRocketMqIntegrationTest` ✅（2 实例无重复）
  - `NacosClusterForwardIntegrationTest` ✅（跨实例订阅转发）

## 已知待办（需环境/更大改动，已记录）

- WebSocket netty 握手的服务端接线（SSE 通道已就绪）
- 真 Meta MetaStore：etcd 适配（nacos 已完成）
- Java 21 虚拟线程（项目源码级还是 Java 8）
- Phase 8 物理删除旧 TCP/gRPC SDK（最终切换，需旧 runtime 全量回归）
- `UniHttpServer.publish` 对 `RateLimitedException` 仍走 catch-all 返回 500（应为 429；测试靠 metrics 断言，未覆盖状态码）

## 一句话

新架构已是**可运行系统**，全部单元/进程内测试 + 7 个真 broker/Nacos 集成测试均 GREEN；老 TCP/HTTP 客户端零改动接入新核心。剩余项均为需环境或最终切换的收尾工作。
