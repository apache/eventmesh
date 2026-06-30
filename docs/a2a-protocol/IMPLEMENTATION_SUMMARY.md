# 实现总结：EventMesh A2A 协议 v2.0 (MCP 版)

## 核心成果

A2A 协议已成功重构为采用 **MCP (Model Context Protocol)** 架构，将 EventMesh 定位为现代化的 **智能体协作总线 (Agent Collaboration Bus)**。

### 1. 核心协议重构 (`EnhancedA2AProtocolAdaptor`)
- **混合引擎 (JSON-RPC & CloudEvents)**: 实现了智能解析引擎，支持：
    - **MCP/JSON-RPC 2.0**: 面向 LLM 和脚本的低门槛接入，自动封装 CloudEvent。
    - **原生 CloudEvents**: 面向 EventMesh 原生应用的灵活接入，支持自定义元数据和透传。
    - 适配器根据 `jsonrpc` 字段自动分发处理逻辑。
- **异步 RPC 映射**: 建立了同步 RPC 语义与异步事件驱动架构 (EDA) 之间的桥梁。
    - **请求 (Requests)** 映射为 `*.req` 事件，属性 `mcptype=request`。
    - **响应 (Responses)** 映射为 `*.resp` 事件，属性 `mcptype=response`。
    - **关联 (Correlation)** 通过将 JSON-RPC `id` 映射到 CloudEvent `collaborationid` 来处理。
- **路由优化**: 实现了"深度内容路由提取"：
    - `params._agentId` -> CloudEvent 扩展属性 `targetagent` (P2P)。
    - `params._topic` -> CloudEvent Subject (Pub/Sub)。

### 2. 原生 Pub/Sub 与流式支持
- **Pub/Sub**: 通过将 `_topic` 映射到 CloudEvent Subject，支持 O(1) 广播复杂度。
- **流式 (Streaming)**: 支持 `message/sendStream` 操作，映射为 `.stream` 事件类型，并通过 `_seq` -> `seq` 扩展属性保证顺序。

### 3. 标准化与兼容性
- **数据模型**: 定义了符合 JSON-RPC 2.0 规范的 `JsonRpcRequest`、`JsonRpcResponse`、`JsonRpcError` POJO 对象。
- **方法定义**: 引入了 `McpMethods` 常量，支持标准操作如 `tools/call`、`resources/read`。
- **AgentCard 模型**: 实现了 `AgentCard`、`AgentSkill`、`AgentInterface`、`AgentCapabilities` 等完整的 Agent 能力描述模型。

### 4. Gateway 运行时架构 (`eventmesh-runtime`)

完整的独立 HTTP Gateway 服务，桥接外部客户端到 A2A 事件总线。

#### 核心组件

| 组件 | 职责 |
| :--- | :--- |
| `A2AGatewayServer` | Netty HTTP 服务器入口，预注册 mock agent，组装所有组件 |
| `A2AGatewayHttpHandler` | HTTP 请求路由，支持 SSE 流式响应 |
| `A2AGatewayService` | 核心编排：任务提交、响应处理、状态订阅、SSE 推送 |
| `TaskRegistry` | 内存任务状态机 + TTL 自动清理 |
| `A2APublishSubscribeService` | AgentCard 注册、发现、心跳管理 |
| `InMemoryA2AMessageTransport` | 内存 pub/sub 实现（可替换为 EventMesh broker） |
| `A2ACardHttpHandler` | AgentCard CRUD REST 端点 |
| `A2AClient` | Java SDK，提供类型化 API |

#### REST API

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| `POST` | `/a2a/tasks?mode=sync` | 同步提交任务 |
| `POST` | `/a2a/tasks?mode=async` | 异步提交任务 |
| `GET` | `/a2a/tasks/{taskId}` | 查询任务状态 |
| `DELETE` | `/a2a/tasks/{taskId}` | 取消任务 |
| `GET` | `/a2a/tasks/{taskId}/wait` | 长轮询等待结果 |
| `GET` | `/a2a/tasks/{taskId}/stream` | SSE 流式推送状态更新 |
| `GET` | `/a2a/agents` | 列出已注册 agents |
| `POST` | `/a2a/heartbeat` | Agent 心跳 |
| `GET` | `/a2a/cards/list` | 列出所有 AgentCard |
| `POST` | `/a2a/cards/card/{org}/{unit}/{agent}` | 注册 AgentCard |

### 5. 关键改进

#### 5.1 TaskRegistry TTL 自动清理
- **问题**: 终态任务（COMPLETED/FAILED/CANCELLED）无限累积导致内存泄漏。
- **方案**: 守护线程 `ScheduledExecutorService` 每 60 秒扫描一次，清理超过 TTL（默认 5 分钟）的终态任务。
- **配置**: `TaskRegistry(taskTtlMs, cleanupIntervalMs)` 构造函数支持自定义调优。

#### 5.2 竞态条件修复
- **问题**: `InMemoryTransport` 同步投递消息，若 `transport.publish()` 在 `pendingTasks.put()` 之前执行，`handleResponse()` 会先于 `put()` 运行，导致 future 永不完成。
- **方案**: 严格保证 `pendingTasks.put(taskId, future)` 在 `transport.publish()` 之前执行，并添加注释说明顺序重要性。

#### 5.3 A2AClient 类型化返回
- **改进**: `getTaskStatus()` 返回 `TaskResult` 对象（而非原始 JSON 字符串），`listAgents()` 返回 `List<String>`（而非原始 JSON）。
- **兼容**: `TaskResult.data` 字段使用 `@JsonAlias("result")` 注解，兼容服务端 `result` 字段名。

#### 5.4 SSE 流式响应
- **端点**: `GET /a2a/tasks/{taskId}/stream`
- **实现**: Handler 直接写入 Netty channel（`DefaultHttpContent` chunks），返回 `null` 跳过标准 `FullHttpResponse` 路径。通过 `StatusSubscriber` 回调实时推送状态变更。

#### 5.5 使用文档
- 新建 `eventmesh-examples/.../demo/README.md`，包含架构图、API 表、curl 示例、SDK 用法、运行方式。

### 6. 测试与质量
- **协议层单元测试**: `EnhancedA2AProtocolAdaptorTest` 覆盖请求/响应循环、错误处理、通知和批处理。
- **Topic 工具测试**: `A2ATopicFactoryTest` 覆盖 topic 生成与解析。
- **Gateway 运行时测试**:
    - `TaskRegistryTest` — 任务状态机 + TTL 清理验证
    - `InMemoryA2AMessageTransportTest` — 内存传输投递
    - `A2AGatewayServiceTest` — Gateway 服务层
    - `A2AGatewayEndToEndTest` — 进程内全链路
    - `A2AClientServerIntegrationTest` — 真实 HTTP 客户端-服务端集成测试
- **集成演示**: `McpIntegrationDemoTest`、`McpPatternsIntegrationTest`、`McpComprehensiveDemoTest`、`CloudEventsComprehensiveDemoTest`
- **总计**: 73 个测试场景，全部通过。

## 下一步计划

1. **EventMesh Broker 集成**: 用真实 EventMesh broker 替换 `InMemoryA2AMessageTransport`，实现生产级部署。
2. **路由集成**: 更新 EventMesh Runtime Router，利用 `targetagent` 和 `a2amethod` 扩展属性实现高级路由规则。
3. **Schema 注册中心**: 实现"注册中心智能体 (Registry Agent)"，允许智能体动态发布 MCP 能力 (`methods/list`)。
4. **Sidecar 支持**: 将 A2A 适配器逻辑暴露在 Sidecar 代理中，允许非 Java 智能体通过 HTTP/JSON 交互。
5. **WebSocket 流式**: 将 SSE 扩展为双向 WebSocket，支持实时 agent 对话。
6. **任务持久化**: 将 `TaskRegistry` 状态持久化到 Redis/DB，支持崩溃恢复。
7. **认证授权**: 为 Gateway REST API 添加 API Key / JWT 认证。
