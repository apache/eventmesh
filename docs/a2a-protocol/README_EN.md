# EventMesh A2A (Agent-to-Agent Communication Protocol)

## Overview

The **EventMesh A2A (Agent-to-Agent) Protocol** is a specialized, high-performance protocol plugin designed to enable asynchronous communication, collaboration, and task coordination between autonomous agents. 

With the release of v2.0, A2A adopts the **MCP (Model Context Protocol)** architecture, transforming EventMesh into a robust **Agent Collaboration Bus**. It bridges the gap between synchronous LLM-based tool calls (JSON-RPC 2.0) and asynchronous Event-Driven Architectures (EDA), enabling scalable, distributed, and decoupled agent systems.

## Core Features

### 1. MCP over CloudEvents
- **Standard Compliance**: Fully supports standard methods defined by **MCP (Model Context Protocol)**, such as `tools/call`, `resources/read`.
- **Event-Driven**: Maps synchronous RPC calls to asynchronous **Request/Response Event Streams**, leveraging EventMesh's high-concurrency processing capabilities.
- **Transport Agnostic**: All MCP messages are encapsulated within standard **CloudEvents** envelopes, running over any transport layer supported by EventMesh (HTTP, TCP, gRPC, Kafka).

### 2. Hybrid Architecture Design
- **Dual-Mode Support**: 
    - **Modern Mode**: Supports standard JSON-RPC 2.0 messages for LLM applications.
    - **Legacy Mode**: Maintains compatibility with the old A2A protocol (based on `messageType` and FIPA verbs) to ensure smooth migration for existing businesses.
- **Automatic Detection**: The protocol adaptor intelligently selects the processing mode based on message content characteristics (e.g., `jsonrpc` field).

### 3. High Performance & Routing
- **Batch Processing**: Natively supports JSON-RPC Batch requests. EventMesh automatically splits them into parallel event streams, significantly increasing throughput.
- **Intelligent Routing**: Extracts routing hints from MCP request parameters (e.g., `_agentId`) and automatically injects them into CloudEvents extension attributes (`targetagent`), enabling zero-decoding routing.

### 4. CloudEvents Integration
- **Type Mapping**: Automatically maps MCP methods to CloudEvent Types (e.g., `tools/call` -> `org.apache.eventmesh.a2a.tools.call.req`).
- **Context Propagation**: Uses CloudEvents Extensions to pass tracing context (like `traceparent`), enabling cross-agent distributed tracing.

## Architecture

### Core Components

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

### Asynchronous RPC Pattern

To support the MCP Request/Response model within an event-driven architecture, A2A defines the following mapping rules:

| MCP Concept | CloudEvent Mapping | Description | 
| :--- | :--- | :--- |
| **Request** (`tools/call`) | `type`: `org.apache.eventmesh.a2a.tools.call.req` <br> `mcptype`: `request` | This is a request event. |
| **Response** (`result`) | `type`: `org.apache.eventmesh.a2a.common.response` <br> `mcptype`: `response` | This is a response event. |
| **Correlation** (`id`) | `extension`: `collaborationid` / `id` | Used to link the Response back to the Request. |
| **Target** | `extension`: `targetagent` | The routing target Agent ID. |

## Protocol Message Format

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
    "_agentId": "weather-service" // Routing hint
  },
  "id": "req-123456"
}
```

**Converted CloudEvent:**
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

**Converted CloudEvent:**
- `id`: `uuid-new-event-id`
- `type`: `org.apache.eventmesh.a2a.common.response`
- `extension: collaborationid`: `req-123456` (Correlation ID)
- `extension: mcptype`: `response`

### 3. Legacy A2A Message (Compatibility Mode)

```json
{
  "protocol": "A2A",
  "messageType": "PROPOSE",
  "sourceAgent": { "agentId": "agent-001" },
  "payload": { "task": "data-process" }
}
```

## Usage Guide

### 1. Initiate MCP Call (As Client)

You only need to send a standard JSON-RPC format message to EventMesh:

```java
// 1. Construct MCP Request JSON
String mcpRequest = "{"
    "jsonrpc": \"2.0\","
    "method": \"tools/call\","
    "params": { \"name\": \"weather\", \"_agentId\": \"weather-agent\" },"
    "id": \"req-001\""
    "}";

// 2. Send via EventMesh SDK
eventMeshProducer.publish(new A2AProtocolTransportObject(mcpRequest));
```

### 2. Handle Request (As Server)

Subscribe to the corresponding topic, process the business logic, and send back the response:

```java
// 1. Subscribe to MCP Request Topic
eventMeshConsumer.subscribe("org.apache.eventmesh.a2a.tools.call.req");

// 2. Handle incoming message...
public void handle(CloudEvent event) {
    // Unpack Request
    String reqJson = new String(event.getData().toBytes());
    // ... Execute business logic ...
    
    // 3. Construct Response
    String mcpResponse = "{"
        "jsonrpc": \"2.0\","
        "result": { \"text\": \"Sunny\" },"
        "id": \"" + event.getId() + "\"" + // Must echo Request ID
        "}";
        
    // 4. Send back to EventMesh
    eventMeshProducer.publish(new A2AProtocolTransportObject(mcpResponse));
}
```

## Extensions

### Custom MCP Methods

A2A protocol does not restrict method names. You can define your own business methods, such as `agents/negotiate` or `tasks/submit`. EventMesh will automatically map them to CloudEvent types like `org.apache.eventmesh.a2a.agents.negotiate.req`.

### Integration with LangChain / AutoGen

Since A2A is compatible with standard JSON-RPC 2.0, you can easily write adaptors to convert LangChain tool calls into EventMesh messages, thereby endowing your LLM applications with distributed, asynchronous communication capabilities.

## Version History

- **v2.0.0**: Fully Embraced MCP (Model Context Protocol)
  - Introduced `EnhancedA2AProtocolAdaptor` supporting JSON-RPC 2.0.
  - Implemented Async RPC over CloudEvents pattern.
  - Supported automatic Request/Response identification and semantic mapping.
  - Maintained full compatibility with Legacy A2A protocol.

## Contribution

Welcome to contribute code and documentation! Please refer to the following steps:

1. Fork the project repository
2. Create a feature branch
3. Submit code changes
4. Create a Pull Request

## License

Apache License 2.0

## Contact

- Project Homepage: https://eventmesh.apache.org
- Issues: https://github.com/apache/eventmesh/issues
- Mailing List: dev@eventmesh.apache.org
