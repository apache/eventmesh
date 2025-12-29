# Unified Runtime Design & Usage Guide

## 1. Overview
The EventMesh Unified Runtime consolidates the capabilities of the core EventMesh Runtime (Protocol handling), Connectors (Source/Sink), and Functions (Filter/Transformer/Router) into a single, cohesive process. This eliminates the need for separate deployments for Connectors ("Runtime V2") and simplifies the architecture.

## 2. Architecture: The Unified Processing Pipeline

The system implements a symmetrical processing chain for both event production (Ingress) and consumption (Egress), but the entry/exit points differ based on the client type (SDK vs. Connector).

### 2.1 Ingress Pipeline (Production)

**Entry Points:**
*   **SDK Client**: Interacts with the Runtime via **Protocol Servers** (TCP/HTTP/gRPC). The Protocol Server receives the request and passes the event to the pipeline.
*   **Source Connector**: Loaded directly into the Runtime as a **Plugin**. The Source Connector pulls data from external systems and internally injects events into the pipeline.

**Flow:**
`[Entry: Protocol Server (SDK) OR Source Plugin (Connector)] -> [IngressProcessor] -> [Storage]`

**IngressProcessor Pipeline:**
`[Filter] -> [Transformer] -> [Router]`

1.  **Entry**:
    *   **SDK**: Request received by `EventMeshTCPServer`, `EventMeshHTTPServer`, or `EventMeshGrpcServer`.
    *   **Connector**: `SourceWorker` pulls data and converts it to a CloudEvent.
2.  **IngressProcessor**: Encapsulates the unified 3-stage pipeline:
    *   **Filter**: The `FilterEngine` evaluates the event against configured rules. If unmatched, returns null (event dropped).
    *   **Transformer**: The `TransformerEngine` transforms the event payload (e.g., JSON manipulation) if a rule exists.
    *   **Router**: The `RouterEngine` determines the target topic/destination.
3.  **Storage**: The processed event is persisted to the Storage Plugin (RocketMQ, Kafka, etc.).

### 2.2 Egress Pipeline (Consumption)

**Exit Points:**
*   **SDK Client**: The Runtime pushes events to connected SDK clients via the active **Protocol Server** connection.
*   **Sink Connector**: Loaded directly into the Runtime as a **Plugin**. The Runtime passes events to the `SinkWorker`, which writes to external systems.

**Flow:**
`[Storage] -> [EgressProcessor] -> [Exit: Protocol Server (SDK) OR Sink Plugin (Connector)]`

**EgressProcessor Pipeline:**
`[Filter] -> [Transformer]`

1.  **Storage**: Event retrieved from the storage queue.
2.  **EgressProcessor**: Encapsulates the 2-stage pipeline (no Router on egress):
    *   **Filter**: Evaluated against the consumer group's filter rules. If unmatched, returns null (event not delivered).
    *   **Transformer**: Payload transformed according to the consumer group's needs.
3.  **Exit**:
    *   **SDK**: Event pushed to client via TCP/HTTP/gRPC session.
    *   **Connector**: Event passed to `SinkWorker` for external delivery.

### 2.3 Protocol Processor Migration Status

All protocol processors now use the unified IngressProcessor/EgressProcessor architecture:

**TCP Protocol**: ✅ Complete
*   `ClientGroupWrapper` - Integrated both Ingress (send) and Egress (consume)

**HTTP Protocol**: ✅ Complete
*   `SendAsyncEventProcessor` - Ingress pipeline
*   `SendAsyncMessageProcessor` - Ingress pipeline
*   `SendSyncMessageProcessor` - Ingress pipeline
*   `BatchSendMessageProcessor` - Ingress pipeline with batch statistics
*   `BatchSendMessageV2Processor` - Ingress pipeline with batch statistics

**gRPC Protocol**: ✅ Complete
*   `PublishCloudEventsProcessor` - Ingress pipeline
*   `BatchPublishCloudEventProcessor` - Ingress pipeline with batch statistics
*   `RequestCloudEventProcessor` - Bidirectional (Ingress for request, Egress for response)

**Connectors**: ✅ Complete
*   `SourceWorker` - Ingress pipeline
*   `SinkWorker` - Egress pipeline

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
*   **`IngressProcessor`**: Unified processor for all upstream message flows (SDK → Storage). Executes Filter → Transformer → Router pipeline.
*   **`EgressProcessor`**: Unified processor for all downstream message flows (Storage → SDK/Connector). Executes Filter → Transformer pipeline (no Router).
*   **`BatchProcessResult`**: Utility class for tracking batch processing statistics (success/filtered/failed counts).
*   **`ClientGroupWrapper`**: Handles the processing logic for TCP clients. Modified to execute the pipeline during `send` (Ingress) and `consume` (Egress).
*   **`SourceWorker`**: Modified to support a pluggable `Publisher`, allowing it to inject events directly into the `EventMeshServer` pipeline instead of using a remote TCP client.

### 4.2 Pipeline Integration Pattern

All protocol processors follow this pattern:

**For Ingress (Publishing)**:
```java
// 1. Construct pipeline key
String pipelineKey = producerGroup + "-" + topic;

// 2. Apply IngressProcessor
CloudEvent processedEvent = eventMeshServer.getIngressProcessor()
    .process(cloudEvent, pipelineKey);

// 3. Check if filtered (null means filtered)
if (processedEvent == null) {
    // Return success for filtered messages
    return;
}

// 4. Use routed topic (Router may have changed it)
String finalTopic = processedEvent.getSubject();

// 5. Send to storage
producer.send(processedEvent, callback);
```

**For Egress (Consuming)**:
```java
// 1. Construct pipeline key
String pipelineKey = consumerGroup + "-" + topic;

// 2. Apply EgressProcessor
CloudEvent processedEvent = eventMeshServer.getEgressProcessor()
    .process(cloudEvent, pipelineKey);

// 3. Check if filtered
if (processedEvent == null) {
    // Commit offset but don't deliver to client
    return;
}

// 4. Deliver to client
client.send(processedEvent);
```

### 4.3 Batch Processing Pattern

For batch processors, use `BatchProcessResult` to track statistics:
```java
BatchProcessResult batchResult = new BatchProcessResult(totalCount);

for (CloudEvent event : events) {
    try {
        CloudEvent processed = ingressProcessor.process(event, pipelineKey);
        if (processed == null) {
            batchResult.incrementFiltered();
            continue;
        }

        producer.send(processed, new SendCallback() {
            public void onSuccess(SendResult result) {
                batchResult.incrementSuccess();
            }
            public void onException(OnExceptionContext ctx) {
                batchResult.incrementFailed(event.getId());
            }
        });
    } catch (Exception e) {
        batchResult.incrementFailed(event.getId());
    }
}

// Return summary: "success=5, filtered=2, failed=1"
String summary = batchResult.toSummary();
```

### 4.4 Adding New Tests
When modifying the pipeline, ensure to add unit tests in:
*   `org.apache.eventmesh.runtime.core.protocol.IngressProcessorTest`
*   `org.apache.eventmesh.runtime.core.protocol.EgressProcessorTest`
*   `org.apache.eventmesh.runtime.core.protocol.BatchProcessResultTest`
*   `org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientGroupWrapperTest`
*   `org.apache.eventmesh.runtime.boot.EventMeshConnectorBootstrapTest`
*   Protocol-specific processor tests (e.g., `SendAsyncEventProcessorTest`)
