# EventMesh A2A (Agent-to-Agent Communication Protocol)

## Overview

The **EventMesh A2A (Agent-to-Agent) Protocol** is a specialized, high-performance protocol plugin designed to enable asynchronous communication, collaboration, and task coordination between autonomous agents.

With the release of v2.0, A2A adopts the **MCP (Model Context Protocol)** architecture, transforming EventMesh into a robust **Agent Collaboration Bus**. It bridges the gap between synchronous LLM-based tool calls (JSON-RPC 2.0) and asynchronous Event-Driven Architectures (EDA), enabling scalable, distributed, and decoupled agent systems.

## Core Features

### 1. MCP over CloudEvents
- **Standard Compliance**: Fully supports standard methods defined by **MCP (Model Context Protocol)**, such as `tools/call`, `resources/read`.
- **Event-Driven**: Maps synchronous RPC calls to asynchronous **Request/Response Event Streams**, leveraging EventMesh's high-concurrency processing capabilities.
- **Transport Agnostic**: All MCP messages are encapsulated within standard **CloudEvents** envelopes, running over any transport layer supported by EventMesh (HTTP, TCP, gRPC, Kafka).

### 2. Dual-Mode Support (Hybrid Architecture)

A2A Protocol features a unique **Dual-Mode** architecture that simultaneously supports:

1.  **JSON-RPC 2.0 (MCP Mode)**:
    *   **Target**: LLMs, Scripts (Python/JS), LangChain integration.
    *   **Benefit**: Extremely low barrier to entry. Clients send simple JSON objects; the adaptor automatically wraps them in CloudEvents.
2.  **Native CloudEvents (Power Mode)**:
    *   **Target**: EventMesh native apps, Knative, Serverless functions.
    *   **Benefit**: Full control over event metadata. Allows pass-through of custom or binary data.

**Mechanism**: The `EnhancedA2AProtocolAdaptor` intelligently detects the payload format. If `jsonrpc: "2.0"` is present, it engages the MCP translation engine; otherwise, it treats the payload as a standard CloudEvent (delegating to the underlying CloudEvents adaptor).

### 3. Native Pub/Sub Semantics
- **O(1) Broadcast**: Publishers send messages once to a Topic, and EventMesh efficiently fans out to all subscribers.
- **Decoupling**: Solves the scalability issues of traditional P2P Webhook callbacks.
- **Isolation**: Provides backpressure isolation between publishers and subscribers.

### 3. High Performance & Routing
- **Batch Processing**: Natively supports JSON-RPC Batch requests. EventMesh automatically splits them into parallel event streams.
- **Intelligent Routing**: Extracts routing hints (`_agentId` for P2P, `_topic` for Pub/Sub) from MCP parameters and injects them into CloudEvents attributes for zero-decoding routing.

### 4. Streaming Support
- **Sequencing**: Preserves message order for streaming operations (`message/sendStream`) using sequence IDs.

## Architecture

### Core Components

```
┌─────────────────────────────────────────────────────────────┐
│                EventMesh A2A Protocol v2.0                │
│              (MCP over CloudEvents Architecture)            │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ MCP/JSON-RPC│  │ Native      │  │  Protocol   │         │
│  │   Handler   │  │ Pub/Sub     │  │  Delegator  │         │
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

### Asynchronous RPC Pattern

To support the MCP Request/Response model within an event-driven architecture, A2A defines the following mapping rules:

| MCP Concept | CloudEvent Mapping | Description |
| :--- | :--- | :--- |
| **Request** (`tools/call`) | `type`: `org.apache.eventmesh.a2a.tools.call.req` <br> `mcptype`: `request` | Request event. |
| **Response** (`result`) | `type`: `org.apache.eventmesh.a2a.common.response` <br> `mcptype`: `response` | Response event. |
| **Correlation** (`id`) | `extension`: `collaborationid` / `id` | Links Response to Request. |
| **P2P Target** | `extension`: `targetagent` | Routing target Agent ID. |
| **Pub/Sub Topic** | `subject`: `<topic_name>` | Broadcast Topic. |

## Protocol Message Format

### 1. MCP Request (P2P)

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "get_weather",
    "_agentId": "weather-service" // P2P Routing
  },
  "id": "req-123"
}
```

### 2. MCP Request (Pub/Sub)

```json
{
  "jsonrpc": "2.0",
  "method": "market/update",
  "params": {
    "price": 50000,
    "_topic": "market.btc" // Pub/Sub Routing
  }
}
```

## Usage Guide

### 1. Initiate Call (Client)

```java
// 1. Construct MCP Request JSON
String mcpRequest = "{" +
    "\"jsonrpc\": \"2.0\"," +
    "\"method\": \"tools/call\"," +
    "\"params\": { \"name\": \"weather\", \"_agentId\": \"weather-agent\" }," +
    "\"id\": \"req-001\"" +
    "}";

// 2. Send via EventMesh SDK
eventMeshProducer.publish(new A2AProtocolTransportObject(mcpRequest));
```

### 2. Handle Request (Server)

Subscribe to the topic `org.apache.eventmesh.a2a.tools.call.req`, process logic, and send back response with matching `id`.

## Version History

- **v2.0.0**: Fully Embraced MCP (Model Context Protocol)
  - Introduced `EnhancedA2AProtocolAdaptor` supporting JSON-RPC 2.0.
  - Implemented Async RPC over CloudEvents pattern.
  - Added **Native Pub/Sub** support via `_topic` parameter.
  - Added **Streaming** support via `_seq` parameter.

## Contribution

Welcome to contribute code and documentation!

## License

Apache License 2.0

## Contact

- Project Homepage: https://eventmesh.apache.org
- Issues: https://github.com/apache/eventmesh/issues
- Mailing List: dev@eventmesh.apache.org