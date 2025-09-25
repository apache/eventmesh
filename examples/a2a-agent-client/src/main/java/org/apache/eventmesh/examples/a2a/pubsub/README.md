# A2A Publish/Subscribe Demo

This demo showcases the refactored A2A (Agent-to-Agent) protocol that fully leverages EventMesh's publish/subscribe infrastructure instead of point-to-point messaging.

## Key Features Demonstrated

### 1. 🚀 True Publish/Subscribe Pattern
- **Anonymous Task Publishing**: Publishers don't need to know specific consumer agents
- **Topic-Based Routing**: Tasks are published to EventMesh topics by task type
- **Automatic Load Balancing**: Multiple agents with same capabilities share the workload

### 2. 🏗️ EventMesh Integration
- **EventMesh Producer/Consumer**: Uses actual EventMesh infrastructure
- **Storage Plugin Support**: Leverages RocketMQ, Kafka, Pulsar, or Redis for message persistence
- **CloudEvents Compliance**: All messages follow CloudEvents 1.0 specification

### 3. 🎯 Capability-Based Routing
- **Requirement Matching**: Tasks specify required capabilities
- **Agent Filtering**: Only agents with matching capabilities receive tasks
- **Intelligent Distribution**: Tasks routed based on agent availability and capabilities

### 4. 🔧 Fault Tolerance
- **Automatic Retries**: Failed tasks are automatically retried with exponential backoff
- **Timeout Handling**: Tasks that exceed timeout are properly handled
- **Error Propagation**: Failures are published to result topics for monitoring

## Architecture Overview

```
Publisher Agents              EventMesh Topics              Consumer Agents
     |                             |                             |
[Task Publisher]          [a2a.tasks.data-collection]    [Data Collector 1]
     |                             |                             |
     |-----> Publish Task -------> |                             |
                                   | -----> Route Task -------> |
                                                                 |
                                                       [Data Collector 2]
                                                                 |
[Workflow Manager]        [a2a.tasks.data-processing]   [Data Processor]
     |                             |                             |
     |-----> Publish Task -------> | -----> Route Task -------> |


                         [a2a.events.task-results]
                                   ^
                                   |
                              Result Topic
                         (All results published here)
```

## Running the Demo

### Prerequisites
- EventMesh runtime with A2A protocol enabled
- Message queue (RocketMQ, Kafka, etc.) configured
- Java 8+ environment

### Execution Steps

1. **Start EventMesh Runtime**
   ```bash
   cd eventmesh-runtime
   ./start.sh
   ```

2. **Run the Demo**
   ```bash
   cd examples/a2a-agent-client
   ./gradlew run -Pmain=org.apache.eventmesh.examples.a2a.pubsub.A2APublishSubscribeDemo
   ```

3. **Expected Output**
   ```
   🚀 A2A Publish/Subscribe Demo - EventMesh Integration
   ============================================================
   
   🔧 Initializing EventMesh A2A Publish/Subscribe Service...
   ✅ A2A Publish/Subscribe service initialized
   
   🤖 Starting subscriber agents...
   ✅ Agent data-collector-001 subscribed to task type data-collection
   ✅ Agent data-collector-002 subscribed to task type data-collection  
   ✅ Agent data-processor-001 subscribed to task type data-processing
   ✅ Agent analytics-engine-001 subscribed to task type data-analysis
   ✅ All subscriber agents started and registered
   
   📤 Publishing tasks to EventMesh topics...
   📊 Published user data collection task
   💰 Published sales data collection task
   ⚙️ Published data processing task
   🔍 Published data analysis task
   📋 All tasks published to EventMesh topics
   
   ⏳ Waiting for task processing...
   📊 data-collector-001 processing data collection task: a2a-task-1234
   📊 data-collector-002 processing data collection task: a2a-task-1235
   ⚙️ data-processor-001 processing data processing task: a2a-task-1236
   🔍 analytics-engine-001 processing analysis task: a2a-task-1237
   
   📈 Processing Results Summary:
   ----------------------------------------
   ✅ Data collection tasks: Load balanced across 2 collectors
   ✅ Data processing task: Processed by available processor
   ✅ Analysis task: Completed by analytics engine
   ```

## Key Benefits vs Point-to-Point Model

| Feature | Point-to-Point (Old) | Publish/Subscribe (New) |
|---------|---------------------|-------------------------|
| **Scalability** | Limited by direct connections | Unlimited horizontal scaling |
| **Fault Tolerance** | Single point of failure | Automatic failover & retry |
| **Load Distribution** | Manual agent selection | Automatic load balancing |
| **Decoupling** | Tight coupling between agents | Complete decoupling via topics |
| **Persistence** | In-memory only | Persistent message queues |
| **Monitoring** | Limited visibility | Full observability via metrics |

## Configuration

The demo uses the following EventMesh topic structure:

- `a2a.tasks.data-collection` - Data collection tasks
- `a2a.tasks.data-processing` - Data processing tasks  
- `a2a.tasks.data-analysis` - Analysis tasks
- `a2a.events.task-results` - All task results
- `a2a.events.agent-status` - Agent status updates

## Extending the Demo

To add new task types:

1. **Define Task Type**: Add to `A2ATaskRequest.taskType`
2. **Create Handler**: Implement `A2ATaskHandler` interface
3. **Subscribe Agent**: Call `pubSubService.subscribeToTaskType()`
4. **Publish Tasks**: Use `pubSubService.publishTask()`

Example:
```java
// Add new agent for image processing
pubSubService.subscribeToTaskType(
    "image-processor-001",
    "image-processing", 
    Arrays.asList("image-processing", "computer-vision"),
    new ImageProcessingTaskHandler("image-processor-001")
);

// Publish image processing task
A2ATaskRequest imageTask = A2ATaskRequest.builder()
    .taskType("image-processing")
    .payload(Map.of("imageUrl", "https://example.com/image.jpg"))
    .requiredCapabilities(Arrays.asList("image-processing"))
    .build();
    
pubSubService.publishTask(imageTask);
```

This demo shows how A2A protocol has evolved from a simple point-to-point communication system to a sophisticated, EventMesh-native publish/subscribe platform suitable for large-scale multi-agent architectures.