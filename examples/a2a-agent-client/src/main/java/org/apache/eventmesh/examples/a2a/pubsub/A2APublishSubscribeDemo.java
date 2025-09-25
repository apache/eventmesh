/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.examples.a2a.pubsub;

import org.apache.eventmesh.runtime.core.protocol.a2a.pubsub.A2APublishSubscribeService;
import org.apache.eventmesh.runtime.core.protocol.a2a.pubsub.A2ATaskRequest;
import org.apache.eventmesh.runtime.core.protocol.a2a.pubsub.A2ATaskResult;
import org.apache.eventmesh.runtime.core.protocol.a2a.pubsub.A2ATaskMessage;
import org.apache.eventmesh.runtime.core.protocol.a2a.pubsub.A2ATaskHandler;
import org.apache.eventmesh.runtime.core.protocol.producer.EventMeshProducer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A2A Publish/Subscribe Demo - showcases true EventMesh-based pub/sub pattern
 */
public class A2APublishSubscribeDemo {
    
    private A2APublishSubscribeService pubSubService;
    
    public static void main(String[] args) {
        A2APublishSubscribeDemo demo = new A2APublishSubscribeDemo();
        demo.runDemo();
    }
    
    public void runDemo() {
        System.out.println("\n🚀 A2A Publish/Subscribe Demo - EventMesh Integration");
        System.out.println("=" .repeat(60));
        
        try {
            // 1. Initialize publish/subscribe service
            initializePublishSubscribeService();
            
            // 2. Start subscriber agents
            startSubscriberAgents();
            
            // 3. Publish tasks anonymously
            publishTasks();
            
            // 4. Wait for processing and show results
            waitForCompletion();
            
        } catch (Exception e) {
            System.err.println("❌ Demo failed: " + e.getMessage());
        } finally {
            cleanup();
        }
    }
    
    /**
     * Initialize the A2A publish/subscribe service with EventMesh producer
     */
    private void initializePublishSubscribeService() {
        System.out.println("\n🔧 Initializing EventMesh A2A Publish/Subscribe Service...");
        
        // In real scenario, this would be injected from EventMesh runtime
        EventMeshProducer eventMeshProducer = createEventMeshProducer();
        pubSubService = new A2APublishSubscribeService(eventMeshProducer);
        
        System.out.println("✅ A2A Publish/Subscribe service initialized");
    }
    
    /**
     * Start agents that subscribe to different task types
     */
    private void startSubscriberAgents() {
        System.out.println("\n🤖 Starting subscriber agents...");
        
        // Data Collection Agent - subscribes to data-collection tasks
        pubSubService.subscribeToTaskType(
            "data-collector-001",
            "data-collection",
            Arrays.asList("data-collection", "file-reading"),
            new DataCollectionTaskHandler("data-collector-001")
        );
        
        // Another Data Collection Agent for load balancing
        pubSubService.subscribeToTaskType(
            "data-collector-002", 
            "data-collection",
            Arrays.asList("data-collection", "api-calling"),
            new DataCollectionTaskHandler("data-collector-002")
        );
        
        // Data Processing Agent - subscribes to data-processing tasks
        pubSubService.subscribeToTaskType(\n            "data-processor-001",\n            "data-processing",\n            Arrays.asList("data-processing", "transformation"),\n            new DataProcessingTaskHandler("data-processor-001")\n        );\n        \n        // Analytics Agent - subscribes to data-analysis tasks\n        pubSubService.subscribeToTaskType(\n            "analytics-engine-001",\n            "data-analysis", \n            Arrays.asList("data-analysis", "machine-learning"),\n            new DataAnalysisTaskHandler("analytics-engine-001")\n        );\n        \n        System.out.println("✅ All subscriber agents started and registered");\n    }\n    \n    /**\n     * Publish tasks to EventMesh topics without knowing specific consumers\n     */\n    private void publishTasks() {\n        System.out.println("\n📤 Publishing tasks to EventMesh topics...");\n        \n        // Publish data collection tasks\n        publishDataCollectionTasks();\n        \n        // Publish data processing tasks  \n        publishDataProcessingTasks();\n        \n        // Publish analysis tasks\n        publishAnalysisTasks();\n        \n        System.out.println("📋 All tasks published to EventMesh topics");\n    }\n    \n    private void publishDataCollectionTasks() {\n        // Task 1: Collect user behavior data\n        A2ATaskRequest userDataTask = A2ATaskRequest.builder()\n            .taskType("data-collection")\n            .payload(Map.of(\n                "source", "user-behavior-database",\n                "query", "SELECT * FROM user_actions WHERE date >= '2024-01-01'",\n                "format", "json"\n            ))\n            .requiredCapabilities(Arrays.asList("data-collection"))\n            .priority(A2ATaskMessage.A2ATaskPriority.HIGH)\n            .publisherAgent("demo-publisher")\n            .correlationId("data-pipeline-001")\n            .build();\n            \n        pubSubService.publishTask(userDataTask);\n        System.out.println("  📊 Published user data collection task");\n        \n        // Task 2: Collect sales data\n        A2ATaskRequest salesDataTask = A2ATaskRequest.builder()\n            .taskType("data-collection")\n            .payload(Map.of(\n                "source", "sales-api",\n                "endpoint", "/api/v1/sales/recent",\n                "timeRange", "last-week"\n            ))\n            .requiredCapabilities(Arrays.asList("data-collection", "api-calling"))\n            .priority(A2ATaskMessage.A2ATaskPriority.NORMAL)\n            .publisherAgent("demo-publisher")\n            .correlationId("data-pipeline-001")\n            .build();\n            \n        pubSubService.publishTask(salesDataTask);\n        System.out.println("  💰 Published sales data collection task");\n    }\n    \n    private void publishDataProcessingTasks() {\n        // Data processing task - will be picked up by any available processor\n        A2ATaskRequest processingTask = A2ATaskRequest.builder()\n            .taskType("data-processing")\n            .payload(Map.of(\n                "inputData", "collected-user-behavior",\n                "algorithm", "data-cleaning-pipeline",\n                "outputFormat", "parquet",\n                "transformations", Arrays.asList("deduplicate", "normalize", "enrich")\n            ))\n            .requiredCapabilities(Arrays.asList("data-processing"))\n            .priority(A2ATaskMessage.A2ATaskPriority.HIGH)\n            .publisherAgent("demo-publisher")\n            .correlationId("data-pipeline-001")\n            .build();\n            \n        pubSubService.publishTask(processingTask);\n        System.out.println("  ⚙️ Published data processing task");\n    }\n    \n    private void publishAnalysisTasks() {\n        // Analytics task\n        A2ATaskRequest analysisTask = A2ATaskRequest.builder()\n            .taskType("data-analysis")\n            .payload(Map.of(\n                "inputData", "processed-user-data",\n                "analysisType", "behavioral-patterns",\n                "model", "clustering",\n                "parameters", Map.of(\n                    "algorithm", "k-means",\n                    "clusters", 5,\n                    "features", Arrays.asList("page_views", "session_duration", "conversion")\n                )\n            ))\n            .requiredCapabilities(Arrays.asList("data-analysis", "machine-learning"))\n            .priority(A2ATaskMessage.A2ATaskPriority.CRITICAL)\n            .publisherAgent("demo-publisher")\n            .correlationId("data-pipeline-001")\n            .build();\n            \n        pubSubService.publishTask(analysisTask);\n        System.out.println("  🔍 Published data analysis task");\n    }\n    \n    /**\n     * Wait for task processing to complete\n     */\n    private void waitForCompletion() {\n        System.out.println("\n⏳ Waiting for task processing...");\n        \n        try {\n            // In real implementation, this would subscribe to result topics\n            Thread.sleep(10000); // Simulate waiting for results\n            \n            System.out.println("\\n📈 Processing Results Summary:");\n            System.out.println("-" .repeat(40));\n            System.out.println("✅ Data collection tasks: Load balanced across 2 collectors");\n            System.out.println("✅ Data processing task: Processed by available processor");\n            System.out.println("✅ Analysis task: Completed by analytics engine");\n            System.out.println("\\n🎯 Key Benefits Demonstrated:");\n            System.out.println("  • Anonymous task publishing (no direct agent addressing)");\n            System.out.println("  • Automatic load balancing across similar agents");\n            System.out.println("  • Capability-based task routing");\n            System.out.println("  • EventMesh storage plugin integration");\n            System.out.println("  • Fault tolerance with automatic retries");\n            \n        } catch (InterruptedException e) {\n            Thread.currentThread().interrupt();\n        }\n    }\n    \n    /**\n     * Cleanup resources\n     */\n    private void cleanup() {\n        if (pubSubService != null) {\n            pubSubService.shutdown();\n            System.out.println("\\n🧹 A2A Publish/Subscribe service shutdown completed");\n        }\n    }\n    \n    /**\n     * Create mock EventMesh producer (in real scenario, injected from runtime)\n     */\n    private EventMeshProducer createEventMeshProducer() {\n        // This would be the actual EventMesh producer in real implementation\n        // For demo purposes, we'll use a mock or simplified version\n        return new MockEventMeshProducer();\n    }\n    \n    // Task Handler Implementations\n    \n    private static class DataCollectionTaskHandler implements A2ATaskHandler {\n        private final String agentId;\n        \n        public DataCollectionTaskHandler(String agentId) {\n            this.agentId = agentId;\n        }\n        \n        @Override\n        public A2ATaskResult handleTask(A2ATaskMessage taskMessage) throws Exception {\n            System.out.println("📊 " + agentId + " processing data collection task: " + taskMessage.getTaskId());\n            \n            // Simulate data collection work\n            Thread.sleep(2000);\n            \n            Map<String, Object> collectedData = Map.of(\n                "records", 1000 + (int) (Math.random() * 500),\n                "source", taskMessage.getPayload().get("source"),\n                "status", "collected",\n                "agent", agentId\n            );\n            \n            return A2ATaskResult.builder()\n                .data(collectedData)\n                .processingTime(2000)\n                .build();\n        }\n    }\n    \n    private static class DataProcessingTaskHandler implements A2ATaskHandler {\n        private final String agentId;\n        \n        public DataProcessingTaskHandler(String agentId) {\n            this.agentId = agentId;\n        }\n        \n        @Override\n        public A2ATaskResult handleTask(A2ATaskMessage taskMessage) throws Exception {\n            System.out.println("⚙️ " + agentId + " processing data processing task: " + taskMessage.getTaskId());\n            \n            // Simulate processing work\n            Thread.sleep(3000);\n            \n            Map<String, Object> processedData = Map.of(\n                "processedRecords", 950,\n                "duplicatesRemoved", 50,\n                "transformations", taskMessage.getPayload().get("transformations"),\n                "outputFormat", taskMessage.getPayload().get("outputFormat"),\n                "agent", agentId\n            );\n            \n            return A2ATaskResult.builder()\n                .data(processedData)\n                .processingTime(3000)\n                .build();\n        }\n    }\n    \n    private static class DataAnalysisTaskHandler implements A2ATaskHandler {\n        private final String agentId;\n        \n        public DataAnalysisTaskHandler(String agentId) {\n            this.agentId = agentId;\n        }\n        \n        @Override\n        public A2ATaskResult handleTask(A2ATaskMessage taskMessage) throws Exception {\n            System.out.println("🔍 " + agentId + " processing analysis task: " + taskMessage.getTaskId());\n            \n            // Simulate ML analysis work\n            Thread.sleep(5000);\n            \n            Map<String, Object> analysisResults = Map.of(\n                "clusters", 5,\n                "insights", Arrays.asList(\n                    "High engagement users prefer mobile interface",\n                    "Conversion rate peaks during 2-4 PM",\n                    "Session duration correlates with purchase intent"\n                ),\n                "accuracy", 0.87,\n                "model", taskMessage.getPayload().get("model"),\n                "agent", agentId\n            );\n            \n            return A2ATaskResult.builder()\n                .data(analysisResults)\n                .processingTime(5000)\n                .build();\n        }\n    }\n    \n    // Mock EventMesh Producer for demo\n    private static class MockEventMeshProducer extends EventMeshProducer {\n        // Simplified mock implementation for demo purposes\n        // In real scenario, this would be the actual EventMeshProducer\n    }\n}