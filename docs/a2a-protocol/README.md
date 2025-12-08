# EventMesh A2A (Agent-to-Agent Communication Protocol)

## 概述

A2A (Agent-to-Agent Communication Protocol) 是 EventMesh 的一个高性能协议插件，专门设计用于支持智能体（Agents）之间的异步通信、协作和任务协调。该协议采用 **MCP (Model Context Protocol)** 架构理念，结合 EventMesh 的事件驱动特性，打造了一个**异步、解耦、高性能的智能体协作总线**。

A2A 协议不仅支持传统的 FIPA-ACL 风格语义，更全面拥抱现代大模型（LLM）生态，通过支持 **JSON-RPC 2.0** 标准，实现了对工具调用（Tool Use）、上下文共享（Context Sharing）等 LLM 核心场景的原生支持。

## 核心特性

### 1. MCP over CloudEvents
- **标准兼容**: 完全支持 **MCP (Model Context Protocol)** 定义的 `tools/call`, `resources/read` 等标准方法。
- **事件驱动**: 将同步的 RPC 调用映射为异步的 **Request/Response 事件流**，充分利用 EventMesh 的高并发处理能力。
- **协议无关**: 所有的 MCP 消息都被封装在 **CloudEvents** 标准信封中，可以在 HTTP, TCP, gRPC, Kafka 等任意 EventMesh 支持的传输层上运行。

### 2. 混合架构设计 (Hybrid Architecture)
- **双模支持**: 
    - **Modern Mode**: 支持 MCP 标准的 JSON-RPC 2.0 消息，面向 LLM 应用。
    - **Legacy Mode**: 兼容旧版 A2A 协议（基于 `messageType` 和 FIPA 动词），保障存量业务平滑迁移。
- **自动识别**: 协议适配器根据消息内容特征（如 `jsonrpc` 字段）自动智能选择处理模式。

### 3. 高性能与路由
- **批量处理**: 原生支持 JSON-RPC Batch 请求，EventMesh 会将其自动拆分为并行事件流，极大提升吞吐量。
- **智能路由**: 支持从 MCP 请求参数（如 `_agentId`）中提取路由线索，自动注入 CloudEvents 扩展属性 (`targetagent`)，实现零解包路由。

### 4. CloudEvents 集成
- **类型映射**: 自动将 MCP 方法映射为 CloudEvent Type (例如 `tools/call` -> `org.apache.eventmesh.a2a.tools.call.req`)。
- **上下文传递**: 利用 CloudEvents Extension 传递 `traceparent`，实现跨 Agent 的全链路追踪。

## 架构设计

### 核心组件

```
┌─────────────────────────────────────────────────────────────┐
│                EventMesh A2A Protocol v2.0                │
│              (MCP over CloudEvents Architecture)            │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ MCP/JSON-RPC│  │ Legacy A2A  │  │  Protocol   │         │
│  │   Handler   │  │   Handler   │  │  Delegator  │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
├─────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────┐  │
│  │           Enhanced A2A Protocol Adaptor               │  │
│  │      (Intelligent Parsing & CloudEvent Mapping)       │  │
│  └───────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│              EventMesh Protocol Infrastructure             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ CloudEvents │  │    HTTP     │  │    gRPC     │         │
│  │  Protocol   │  │  Protocol   │  │  Protocol   │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

### 异步 RPC 模式

为了在事件驱动架构中支持 MCP 的 Request/Response 模型，A2A 协议定义了以下映射规则：

| MCP 概念 | CloudEvent 映射 | 说明 |
| :--- | :--- | :--- |
| **Request** (`tools/call`) | `type`: `org.apache.eventmesh.a2a.tools.call.req` <br> `mcptype`: `request` | 这是一个请求事件 |
| **Response** (`result`) | `type`: `org.apache.eventmesh.a2a.common.response` <br> `mcptype`: `response` | 这是一个响应事件 |
| **Correlation** (`id`) | `extension`: `collaborationid` / `id` | 用于将 Response 关联回 Request |
| **Target** | `extension`: `targetagent` | 路由目标 Agent ID |

## 协议消息格式

### 1. MCP Request (JSON-RPC 2.0)

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "get_weather",
    "arguments": {
      "city": "Shanghai"
    },
    "_agentId": "weather-service" // 路由提示
  },
  "id": "req-123456"
}
```

**转换后的 CloudEvent:**
- `id`: `req-123456`
- `type`: `org.apache.eventmesh.a2a.tools.call.req`
- `source`: `eventmesh-a2a`
- `extension: a2amethod`: `tools/call`
- `extension: mcptype`: `request`
- `extension: targetagent`: `weather-service`

### 2. MCP Response (JSON-RPC 2.0)

```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Shanghai: 25°C, Sunny"
      }
    ]
  },
  "id": "req-123456"
}
```

**转换后的 CloudEvent:**
- `id`: `uuid-new-event-id`
- `type`: `org.apache.eventmesh.a2a.common.response`
- `extension: collaborationid`: `req-123456` (关联 ID) 
- `extension: mcptype`: `response`

### 3. Legacy A2A Message (兼容模式)

```json
{
  "protocol": "A2A",
  "messageType": "PROPOSE",
  "sourceAgent": { "agentId": "agent-001" },
  "payload": { "task": "data-process" }
}
```

## 使用指南

### 1. 作为 Client 发起 MCP 调用

您只需要发送标准的 JSON-RPC 格式消息到 EventMesh：

```java
// 1. 构造 MCP Request JSON
String mcpRequest = "{"
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": { "name": "weather", "_agentId": "weather-agent" },
    "id": "req-001"
    "}";

// 2. 通过 EventMesh SDK 发送
eventMeshProducer.publish(new A2AProtocolTransportObject(mcpRequest));
```

### 2. 作为 Server 处理请求

订阅相应的主题，处理业务逻辑，并发送回响应：

```java
// 1. 订阅 MCP Request 主题
eventMeshConsumer.subscribe("org.apache.eventmesh.a2a.tools.call.req");

// 2. 收到消息后处理...
public void handle(CloudEvent event) {
    // 解包 Request
    String reqJson = new String(event.getData().toBytes());
    // ... 执行业务逻辑 ...
    
    // 3. 构造 Response
    String mcpResponse = "{"
        "jsonrpc": "2.0",
        "result": { "text": "Sunny" },
        "id": """ + event.getId() + """
        "}";
        
    // 4. 发送回 EventMesh
    eventMeshProducer.publish(new A2AProtocolTransportObject(mcpResponse));
}
```

## 扩展开发

### 自定义 MCP 方法

A2A 协议不限制 method 的名称。您可以定义自己的业务方法，例如 `agents/negotiate` 或 `tasks/submit`。EventMesh 会自动将其映射为 CloudEvent 类型 `org.apache.eventmesh.a2a.agents.negotiate.req`。

### 集成 LangChain / AutoGen

由于 A2A 兼容标准的 JSON-RPC 2.0，您可以轻松编写适配器，将 LangChain 的 Tool 调用转换为 EventMesh 消息，从而让您的 LLM 应用具备分布式、异步的通信能力。

## 版本历史

- **v2.0.0**: 全面拥抱 MCP (Model Context Protocol)
  - 引入 `EnhancedA2AProtocolAdaptor`，支持 JSON-RPC 2.0。
  - 实现异步 RPC over CloudEvents 模式。
  - 支持 Request/Response 自动识别与语义映射。
  - 保留对 Legacy A2A 协议的完全兼容。

## 贡献指南

欢迎贡献代码和文档！请参考以下步骤：

1. Fork项目仓库
2. 创建功能分支
3. 提交代码更改
4. 创建Pull Request

## 许可证

Apache License 2.0

## 联系方式

- 项目主页: https://eventmesh.apache.org
- 问题反馈: https://github.com/apache/eventmesh/issues
- 邮件列表: dev@eventmesh.apache.org