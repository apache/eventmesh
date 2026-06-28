# Test Results: EventMesh A2A Protocol v2.0

**Date**: 2026-06-19
**Version**: v2.0.0 (MCP Edition + Gateway Runtime)
**Status**: ✅ **PASS**

## Test Suite Summary

The test suite provides comprehensive coverage across two layers: the **Protocol Adaptor** (JSON-RPC 2.0 & Native CloudEvents) and the **Gateway Runtime** (HTTP REST API, Task lifecycle, SSE streaming, AgentCard discovery).

### Protocol Adaptor Tests

| Test Class | Scenarios | Result | Description |
| :--- | :--- | :--- | :--- |
| `EnhancedA2AProtocolAdaptorTest` | 12 | **PASS** | Unit tests covering core protocol logic, MCP parsing, Batching, Error handling, and A2A Standard Ops. |
| `McpIntegrationDemoTest` | 1 | **PASS** | End-to-end RPC demo using MCP (JSON-RPC). |
| `McpPatternsIntegrationTest` | 2 | **PASS** | End-to-end Pub/Sub and Streaming demos using MCP (JSON-RPC). |
| `McpComprehensiveDemoTest` | 3 | **PASS** | Validation of all 3 patterns in MCP mode. |
| `CloudEventsComprehensiveDemoTest` | 3 | **PASS** | Validation of all 3 patterns in Native CloudEvents mode. |
| `A2ATopicFactoryTest` | 8 | **PASS** | Topic naming and parsing (request/response/status topics). |

### Gateway Runtime Tests

| Test Class | Scenarios | Result | Description |
| :--- | :--- | :--- | :--- |
| `TaskRegistryTest` | 6 | **PASS** | Task state machine transitions, parent-child relationships, TTL auto-cleanup. |
| `InMemoryA2AMessageTransportTest` | 4 | **PASS** | In-memory pub/sub delivery, subscribe/unsubscribe, wildcard topics. |
| `A2AGatewayServiceTest` | 8 | **PASS** | Gateway service layer: task submission (sync/async), response handling, cancel, status subscription. |
| `A2AGatewayEndToEndTest` | 6 | **PASS** | In-process end-to-end: client → gateway → transport → agent → response → client. |
| `A2AClientServerIntegrationTest` | 20 | **PASS** | Real HTTP client-server integration: AgentCard registration, sync/async tasks, status query, cancel, list agents, SSE streaming. |

**Total Scenarios**: 73 (All Passed)

## Detailed Test Cases

### 1. `EnhancedA2AProtocolAdaptorTest` (Unit)
- **MCP Core**: Validated Request/Response/Notification mapping.
- **Error Handling**: Validated JSON-RPC Error object mapping.
- **Batching**: Validated JSON Array splitting.
- **Legacy Removal**: Confirmed legacy A2A format is no longer processed.
- **A2A Ops**: Verified `task/get`, `message/sendStream` mappings.

### 2. `A2ATopicFactoryTest` (Unit)
- Validated topic generation for request, response, and status topics.
- Validated topic parsing (extracting namespace, agent name, task ID, topic type).
- Verified wildcard topic patterns for gateway subscriptions.

### 3. `TaskRegistryTest` (Unit)
- **State Machine**: SUBMITTED → WORKING → COMPLETED/FAILED/CANCELLED transitions.
- **Parent-Child**: Task hierarchy tracking and child task listing.
- **TTL Cleanup**: Verified that terminal-state tasks are removed after TTL expires.
- **Concurrency**: Thread-safe state transitions under concurrent access.

### 4. `InMemoryA2AMessageTransportTest` (Unit)
- Publish/subscribe message delivery.
- Multiple subscribers on the same topic.
- Unsubscribe behavior.
- Wildcard topic matching.

### 5. `A2AGatewayServiceTest` (Integration)
- **Sync Task**: submitTask → publish → handleResponse → future.complete.
- **Async Task**: submitTask returns immediately, status queried separately.
- **Cancel**: cancelTask transitions state and completes future with CANCELLED.
- **Race Condition**: Verified put-before-publish ordering prevents lost responses.
- **Status Subscription**: StatusSubscriber receives state transition callbacks.

### 6. `A2AGatewayEndToEndTest` (Integration)
- Full flow: A2AClient → Gateway HTTP → GatewayService → Transport → Agent → Response → Client.
- Verified task ID correlation across all components.
- Multiple concurrent tasks.
- Error scenarios (unknown agent, task not found).

### 7. `A2AClientServerIntegrationTest` (HTTP Integration)
- **Real HTTP**: Uses Apache HttpClient to hit the real Netty server.
- **AgentCard**: Registration and heartbeat via REST API.
- **Sync Task**: `POST /a2a/tasks?mode=sync` returns completed result.
- **Async Task**: `POST /a2a/tasks?mode=async` returns taskId, then `GET /a2a/tasks/{taskId}` polls status.
- **Cancel**: `DELETE /a2a/tasks/{taskId}` cancels the task.
- **List Agents**: `GET /a2a/agents` returns registered agent list.
- **Typed Returns**: `A2AClient.getTaskStatus()` returns `TaskResult`, `listAgents()` returns `List<String>`.
- **SSE Stream**: `GET /a2a/tasks/{taskId}/stream` receives real-time state updates via `text/event-stream`.

### 8. `McpIntegrationDemoTest` (Integration - RPC)
- Simulated Client → EventMesh → Server flow.
- Verified correlation ID linking (`req-id` <-> `collaborationid`).

### 9. `McpPatternsIntegrationTest` (Integration - Advanced)
- **Pub/Sub**: Verified `_topic` -> `subject` mapping for Broadcast.
- **Streaming**: Verified `_seq` -> `seq` mapping for ordered chunks.

### 10. `McpComprehensiveDemoTest` (Protocol: JSON-RPC)
- **RPC**: Request/Response flow verification.
- **Pub/Sub**: Broadcast to Topic routing verification.
- **Streaming**: Sequence ID preservation verification.

### 11. `CloudEventsComprehensiveDemoTest` (Protocol: Native CloudEvents)
- **RPC**: Verified manual construction of `.req` / `.resp` CloudEvents works.
- **Pub/Sub**: Verified manual setting of `subject` works.
- **Streaming**: Verified manual setting of `seq` extension works.

## Environment

- **JDK**: Java 8 (Source/Target 1.8), Compatible with Java 21 Runtime
- **Build System**: Gradle 7.x+
- **Dependencies**: Jackson 2.18+, CloudEvents SDK 3.0+, Netty 4.1+, Apache HttpClient

## Conclusion

The A2A Protocol v2.0 implementation is stable, functionally complete, and ready for production deployment. It successfully supports:
- **Hybrid Architecture** (MCP & CloudEvents) with all three interaction patterns (RPC, Pub/Sub, Streaming)
- **Gateway Runtime** with full REST API, SSE streaming, task lifecycle management, TTL auto-cleanup, and typed Java SDK
- **73 test scenarios** across protocol and runtime layers, all passing
