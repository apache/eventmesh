# Unified Runtime Design & Usage Guide

## 1. Overview
The EventMesh Unified Runtime consolidates the capabilities of the core EventMesh Runtime (Protocol handling), Connectors (Source/Sink), and Functions (Filter/Transformer/Router) into a single, cohesive process. This eliminates the need for separate deployments for Connectors ("Runtime V2") and simplifies the architecture.

## 2. Architecture: The Unified Processing Pipeline

The system implements a symmetrical processing chain for both event production (Ingress) and consumption (Egress).

### 2.1 Ingress Pipeline (Production)
Applies when an event is received from a **Source Connector** or an **SDK Producer**.

**Flow:**
`[Source: SDK/Connector] -> [Filter] -> [Transformer] -> [Router] -> [Storage]`

1.  **Source**: Event received via TCP/HTTP/gRPC or pulled by a Source Connector.
2.  **Filter**: The `FilterEngine` evaluates the event against configured rules. If unmatched, the event is dropped.
3.  **Transformer**: The `TransformerEngine` transforms the event payload (e.g., JSON manipulation) if a rule exists.
4.  **Router**: The `RouterEngine` determines the target topic/destination.
5.  **Storage**: The processed event is persisted to the Storage Plugin (RocketMQ, Kafka, etc.).

### 2.2 Egress Pipeline (Consumption)
Applies when an event is pushed to a **Sink Connector** or an **SDK Consumer**.

**Flow:**
`[Storage] -> [Filter] -> [Transformer] -> [Sink: SDK/Connector]`

1.  **Storage**: Event retrieved from the storage queue.
2.  **Filter**: Evaluated against the consumer group's filter rules.
3.  **Transformer**: Payload transformed according to the consumer group's needs.
4.  **Sink**: The event is pushed to the connected SDK client or the Sink Connector.

## 3. Configuration

### 3.1 Enabling Connectors
To enable the embedded Connector runtime, update `eventmesh.properties`:

```properties
# Enable the connector plugin
eventMesh.connector.plugin.enable=true

# Specify the connector type (source or sink) and name (SPI name)
eventMesh.connector.plugin.type=source
eventMesh.connector.plugin.name=my-source-connector
```

### 3.2 Configuring Functions
Functions are dynamic and configured via the **MetaStorage** (e.g., Nacos, Etcd).

*   **Prefixes**:
    *   Filter: `filter-{group}-{topic}`
    *   Transformer: `transformer-{group}-{topic}`
    *   Router: `router-{group}-{topic}`

**Example Nacos Config (Filter):**
Key: `filter-myGroup-myTopic`
Value:
```json
[
  {
    "topic": "myTopic",
    "condition": "{\"dataList\":[{\"key\":\"$.type\",\"value\":\"sometype\",\"operator\":\"EQ\"}]}"
  }
]
```

## 4. Developer Guide

### 4.1 Key Components
*   **`EventMeshConnectorBootstrap`**: Bootstraps the Connector `SourceWorker` or `SinkWorker` within the EventMeshServer process.
*   **`ClientGroupWrapper`**: Handles the processing logic for TCP clients. Modified to execute the pipeline during `send` (Ingress) and `consume` (Egress).
*   **`SourceWorker`**: Modified to support a pluggable `Publisher`, allowing it to inject events directly into the `EventMeshServer` pipeline instead of using a remote TCP client.

### 4.2 Adding New Tests
When modifying the pipeline, ensure to add unit tests in:
*   `org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientGroupWrapperTest`
*   `org.apache.eventmesh.runtime.boot.EventMeshConnectorBootstrapTest`
