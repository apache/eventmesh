# Implementation Summary: EventMesh A2A Protocol v2.0 (MCP Edition)

## Key Achievements

The A2A protocol has been successfully refactored to adopt the **MCP (Model Context Protocol)** architecture, positioning EventMesh as a modern **Agent Collaboration Bus**.

### 1. Core Protocol Refactoring (`EnhancedA2AProtocolAdaptor`)
- **Hybrid Engine (JSON-RPC & CloudEvents)**: Implemented a smart parsing engine that supports:
    - **MCP/JSON-RPC 2.0**: For LLM-friendly, low-code integration.
    - **Native CloudEvents**: For advanced, protocol-compliant integration.
    - The adaptor automatically delegates processing based on the payload content (`jsonrpc` detection).
- **Async RPC Mapping**: Established a bridge between synchronous RPC semantics and asynchronous Event-Driven Architecture (EDA).
    - **Requests** map to `*.req` events with `mcptype=request`.
    - **Responses** map to `*.resp` events with `mcptype=response`.
    - **Correlation** is handled by mapping JSON-RPC `id` to CloudEvent `collaborationid`.
- **Routing Optimization**: Implemented "Deep Body Routing" extraction:
    - `params._agentId` -> CloudEvent Extension `targetagent` (P2P).
    - `params._topic` -> CloudEvent Subject (Pub/Sub).

### 2. Native Pub/Sub & Streaming
- **Pub/Sub**: Added support for O(1) broadcast complexity by mapping `_topic` to CloudEvent Subject.
- **Streaming**: Added support for `message/sendStream` operation, mapping to `.stream` event type and preserving sequence via `_seq` -> `seq` extension.

### 3. Standardization & Compatibility
- **Models**: Defined `JsonRpcRequest`, `JsonRpcResponse`, `JsonRpcError` POJOs compliant with JSON-RPC 2.0 spec.
- **Methods**: Introduced `McpMethods` constants for standard operations like `tools/call`, `resources/read`.
- **AgentCard Models**: Implemented `AgentCard`, `AgentSkill`, `AgentInterface`, `AgentCapabilities` for complete agent capability description.

### 4. Gateway Runtime Architecture (`eventmesh-runtime`)

A standalone HTTP Gateway service bridging external clients to the A2A event bus.

#### Core Components

| Component | Responsibility |
| :--- | :--- |
| `A2AGatewayServer` | Netty HTTP server entry point, pre-registers mock agents, wires all components |
| `A2AGatewayHttpHandler` | HTTP request router, supports SSE streaming responses |
| `A2AGatewayService` | Core orchestration: task submission, response handling, status subscription, SSE push |
| `TaskRegistry` | In-memory task state machine + TTL auto-cleanup |
| `A2APublishSubscribeService` | AgentCard registration, discovery, heartbeat management |
| `InMemoryA2AMessageTransport` | In-memory pub/sub (replaceable by EventMesh broker) |
| `A2ACardHttpHandler` | AgentCard CRUD REST endpoints |
| `A2AClient` | Java SDK with typed API |

#### REST API

| Method | Path | Description |
| :--- | :--- | :--- |
| `POST` | `/a2a/tasks?mode=sync` | Submit task synchronously |
| `POST` | `/a2a/tasks?mode=async` | Submit task asynchronously |
| `GET` | `/a2a/tasks/{taskId}` | Get task status |
| `DELETE` | `/a2a/tasks/{taskId}` | Cancel a task |
| `GET` | `/a2a/tasks/{taskId}/wait` | Long-poll wait for result |
| `GET` | `/a2a/tasks/{taskId}/stream` | SSE stream of task status updates |
| `GET` | `/a2a/agents` | List registered agents |
| `POST` | `/a2a/heartbeat` | Agent heartbeat |
| `GET` | `/a2a/cards/list` | List all AgentCards |
| `POST` | `/a2a/cards/card/{org}/{unit}/{agent}` | Register an AgentCard |

### 5. Key Improvements

#### 5.1 TaskRegistry TTL Auto-Cleanup
- **Problem**: Terminal-state tasks (COMPLETED/FAILED/CANCELLED) accumulate indefinitely, causing memory leaks.
- **Solution**: A daemon `ScheduledExecutorService` runs every 60 seconds, removing terminal-state tasks older than the TTL (default: 5 minutes).
- **Configuration**: `TaskRegistry(taskTtlMs, cleanupIntervalMs)` constructor allows custom tuning.

#### 5.2 Race Condition Fix
- **Problem**: `InMemoryTransport` delivers messages synchronously. If `transport.publish()` executes before `pendingTasks.put()`, `handleResponse()` runs before `put()` and the future never completes.
- **Solution**: Ensure `pendingTasks.put(taskId, future)` is called **before** `transport.publish()`, with comments documenting the ordering importance.

#### 5.3 A2AClient Typed Returns
- **Improvement**: `getTaskStatus()` returns `TaskResult` object (instead of raw JSON string), `listAgents()` returns `List<String>` (instead of raw JSON).
- **Compatibility**: `TaskResult.data` field uses `@JsonAlias("result")` annotation to handle the server's `result` field name.

#### 5.4 SSE Streaming Response
- **Endpoint**: `GET /a2a/tasks/{taskId}/stream`
- **Implementation**: Handler writes `DefaultHttpContent` chunks directly to the Netty channel, returning `null` to skip the standard `FullHttpResponse` path. Uses `StatusSubscriber` callbacks for real-time state push.

#### 5.5 Usage Documentation
- Created `eventmesh-examples/.../demo/README.md` with architecture diagram, API table, curl examples, SDK usage, and run instructions.

### 6. Testing & Quality
- **Protocol Unit Tests**: `EnhancedA2AProtocolAdaptorTest` covers Request/Response cycles, Error handling, Notifications, and Batching.
- **Topic Utility Tests**: `A2ATopicFactoryTest` covers topic generation and parsing.
- **Gateway Runtime Tests**:
    - `TaskRegistryTest` — Task state machine + TTL cleanup verification
    - `InMemoryA2AMessageTransportTest` — In-memory transport delivery
    - `A2AGatewayServiceTest` — Gateway service layer
    - `A2AGatewayEndToEndTest` — In-process end-to-end flow
    - `A2AClientServerIntegrationTest` — Real HTTP client-server integration test
- **Integration Demos**: `McpIntegrationDemoTest`, `McpPatternsIntegrationTest`, `McpComprehensiveDemoTest`, `CloudEventsComprehensiveDemoTest`
- **Total**: 73 test scenarios, all passing.

## Next Steps

1. **EventMesh Broker Integration**: Replace `InMemoryA2AMessageTransport` with the real EventMesh broker for production deployment.
2. **Router Integration**: Update EventMesh Runtime Router to leverage `targetagent` and `a2amethod` extension attributes for advanced routing rules.
3. **Schema Registry**: Implement a "Registry Agent" that allows agents to publish their MCP capabilities (`methods/list`) dynamically.
4. **Sidecar Support**: Expose the A2A adaptor logic in the Sidecar proxy to allow non-Java agents (Python, Node.js) to interact via simple HTTP/JSON.
5. **WebSocket Streaming**: Extend SSE to bidirectional WebSocket for real-time agent dialogue.
6. **Task Persistence**: Persist `TaskRegistry` state to a durable store (Redis/DB) for crash recovery.
7. **Authentication**: Add API key / JWT authentication to the Gateway REST API.
