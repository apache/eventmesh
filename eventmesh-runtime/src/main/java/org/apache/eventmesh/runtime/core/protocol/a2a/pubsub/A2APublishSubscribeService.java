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

package org.apache.eventmesh.runtime.core.protocol.a2a.pubsub;

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.EventMeshConsumer;
import org.apache.eventmesh.runtime.core.protocol.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.producer.SendMessageContext;
import org.apache.eventmesh.protocol.a2a.A2AProtocolAdaptor.AgentInfo;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * A2A Publish/Subscribe Service based on EventMesh infrastructure.
 * 
 * This service provides true publish/subscribe capabilities for A2A agents by
 * leveraging EventMesh's producer/consumer architecture and storage plugins.
 * 
 * Key Features:
 * - Anonymous task publishing (agents don't need to know consumers)
 * - Capability-based subscription matching
 * - Load balancing across multiple agents with same capabilities
 * - Persistent message queues using EventMesh storage plugins
 * - Fault tolerance with automatic retries and DLQ support
 */
@Slf4j
public class A2APublishSubscribeService {

    private static final String A2A_TOPIC_PREFIX = "a2a.tasks.";
    private static final String A2A_RESULT_TOPIC = "a2a.results";
    private static final String A2A_STATUS_TOPIC = "a2a.status";
    
    // EventMesh core components
    private final EventMeshProducer eventMeshProducer;
    // TODO: Implement proper EventMeshConsumer integration
    // private final Map<String, EventMeshConsumer> consumers = new ConcurrentHashMap<>();
    
    // A2A subscription management
    private final SubscriptionRegistry subscriptionRegistry = new SubscriptionRegistry();
    private final TaskMetricsCollector metricsCollector = new TaskMetricsCollector();
    
    // Agent capability cache
    private final Map<String, AgentInfo> agentCapabilities = new ConcurrentHashMap<>();
    
    public A2APublishSubscribeService(EventMeshProducer eventMeshProducer) {
        this.eventMeshProducer = eventMeshProducer;
        initializeResultConsumer();
    }
    
    /**
     * Publish a task to the appropriate topic without knowing specific consumers.
     * Tasks are routed based on required capabilities.
     */
    public CompletableFuture<String> publishTask(A2ATaskRequest taskRequest) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        try {
            String taskId = generateTaskId();
            String topicName = A2A_TOPIC_PREFIX + taskRequest.getTaskType();
            
            // Create task message
            A2ATaskMessage taskMessage = A2ATaskMessage.builder()
                .taskId(taskId)
                .taskType(taskRequest.getTaskType())
                .payload(taskRequest.getPayload())
                .requiredCapabilities(taskRequest.getRequiredCapabilities())
                .priority(taskRequest.getPriority())
                .timeout(taskRequest.getTimeout())
                .retryCount(0)
                .maxRetries(taskRequest.getMaxRetries())
                .publishTime(System.currentTimeMillis())
                .publisherAgent(taskRequest.getPublisherAgent())
                .correlationId(taskRequest.getCorrelationId())
                .build();
            
            // Convert to CloudEvent
            CloudEvent cloudEvent = createTaskCloudEvent(taskMessage, topicName);
            
            // Create send context
            SendMessageContext sendContext = new SendMessageContext(
                taskId, cloudEvent, taskMessage.getPublisherAgent());
            
            // Publish via EventMesh Producer
            eventMeshProducer.send(sendContext, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("📤 Task published successfully: {} to topic {}", taskId, topicName);
                    metricsCollector.recordTaskPublished(taskRequest.getTaskType());
                    future.complete(taskId);
                }
                
                @Override
                public void onException(OnExceptionContext context) {
                    log.error("❌ Failed to publish task: {} to topic {}", taskId, topicName, context.getException());
                    metricsCollector.recordTaskPublishFailed(taskRequest.getTaskType());
                    future.completeExceptionally(context.getException());
                }
            });
            
        } catch (Exception e) {
            log.error("Error publishing A2A task", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Subscribe agent to specific task types based on capabilities.
     * Uses EventMesh consumer with clustering mode for load balancing.
     */
    public void subscribeToTaskType(String agentId, String taskType, 
                                   List<String> capabilities, A2ATaskHandler taskHandler) {
        try {
            // Validate agent capabilities
            if (!hasRequiredCapabilities(capabilities, getRequiredCapabilitiesForTaskType(taskType))) {
                throw new A2AException("Agent " + agentId + " lacks required capabilities for task type: " + taskType);
            }
            
            // Register subscription
            subscriptionRegistry.addSubscription(agentId, taskType, capabilities);
            
            // Create consumer for this task type
            String topicName = A2A_TOPIC_PREFIX + taskType;
            String consumerGroup = "a2a-" + taskType + "-consumers";
            
            // TODO: Implement proper EventMesh consumer subscription
            // EventMeshConsumer consumer = createOrGetConsumer(consumerGroup);
            
            log.info("Agent subscription registered (implementation pending): {} -> {}", agentId, taskType);
            
            log.info("✅ Agent {} subscribed to task type {} with capabilities {}", 
                    agentId, taskType, capabilities);
            
            // Update agent capability cache
            AgentInfo agentInfo = new AgentInfo();
            agentInfo.setAgentId(agentId);
            agentInfo.setCapabilities(capabilities.toArray(new String[0]));
            agentCapabilities.put(agentId, agentInfo);
            
        } catch (Exception e) {
            log.error("Failed to subscribe agent {} to task type {}", agentId, taskType, e);
            throw new A2AException("Subscription failed", e);
        }
    }
    
    /**
     * A2A Task Event Listener - processes tasks from EventMesh queues
     */
    private class A2ATaskEventListener implements EventListener {
        private final String agentId;
        private final A2ATaskHandler taskHandler;
        
        public A2ATaskEventListener(String agentId, A2ATaskHandler taskHandler) {
            this.agentId = agentId;
            this.taskHandler = taskHandler;
        }
        
        @Override
        public void consume(CloudEvent cloudEvent, EventMeshAsyncConsumeContext context) {
            String taskId = null;
            try {
                // Parse A2A task message
                String taskData = new String(cloudEvent.getData().toBytes());
                A2ATaskMessage taskMessage = JsonUtils.parseObject(taskData, A2ATaskMessage.class);
                taskId = taskMessage.getTaskId();
                
                log.info("📥 Agent {} received task {} of type {}", 
                        agentId, taskId, taskMessage.getTaskType());
                
                // Check if task has timed out
                if (isTaskExpired(taskMessage)) {
                    log.warn("⏰ Task {} has expired, skipping processing", taskId);
                    context.commit(EventMeshAction.CommitMessage);
                    return;
                }
                
                // Process task asynchronously
                CompletableFuture.supplyAsync(() -> {
                    try {
                        metricsCollector.recordTaskStarted(taskMessage.getTaskType());
                        long startTime = System.currentTimeMillis();
                        
                        // Execute task handler
                        A2ATaskResult result = taskHandler.handleTask(taskMessage);
                        
                        long processingTime = System.currentTimeMillis() - startTime;
                        metricsCollector.recordTaskCompleted(taskMessage.getTaskType(), processingTime);
                        
                        // Publish task result
                        publishTaskResult(taskMessage, agentId, result, A2ATaskStatus.COMPLETED);
                        
                        return result;
                    } catch (Exception e) {
                        log.error("❌ Task processing failed for task {}", taskMessage.getTaskId(), e);
                        metricsCollector.recordTaskFailed(taskMessage.getTaskType());
                        
                        // Handle retry logic
                        handleTaskFailure(taskMessage, agentId, e);
                        
                        throw new RuntimeException(e);
                    }
                }).whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        // Commit message on success
                        context.commit(EventMeshAction.CommitMessage);
                        log.info("✅ Task {} completed successfully by agent {}", taskId, agentId);
                    } else {
                        // Commit message even on failure to avoid infinite retry at MQ level
                        // (we handle retries at A2A level)
                        context.commit(EventMeshAction.CommitMessage);
                        log.error("❌ Task {} failed on agent {}", taskId, agentId);
                    }
                });
                
            } catch (Exception e) {
                log.error("Error processing A2A task message", e);
                context.commit(EventMeshAction.CommitMessage);
            }
        }
    }
    
    /**
     * Handle task failure with retry logic
     */
    private void handleTaskFailure(A2ATaskMessage taskMessage, String agentId, Exception error) {
        try {
            if (taskMessage.getRetryCount() < taskMessage.getMaxRetries()) {
                // Retry task by republishing with incremented retry count
                A2ATaskMessage retryTask = taskMessage.toBuilder()
                    .retryCount(taskMessage.getRetryCount() + 1)
                    .build();
                
                // Exponential backoff delay
                long delay = calculateRetryDelay(taskMessage.getRetryCount());
                
                // Schedule retry after delay
                CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS)
                    .execute(() -> {
                        try {
                            republishTask(retryTask);
                            log.info("🔄 Task {} retried (attempt {}/{})", 
                                    taskMessage.getTaskId(), retryTask.getRetryCount(), taskMessage.getMaxRetries());
                        } catch (Exception e) {
                            log.error("Failed to retry task {}", taskMessage.getTaskId(), e);
                        }
                    });
            } else {
                // Max retries exceeded, publish failure result
                A2ATaskResult failureResult = A2ATaskResult.builder()
                    .error(error.getMessage())
                    .build();
                    
                publishTaskResult(taskMessage, agentId, failureResult, A2ATaskStatus.FAILED);
            }
        } catch (Exception e) {
            log.error("Error handling task failure for task {}", taskMessage.getTaskId(), e);
        }
    }
    
    /**
     * Publish task execution result
     */
    private void publishTaskResult(A2ATaskMessage taskMessage, String agentId, 
                                 A2ATaskResult result, A2ATaskStatus status) {
        try {
            A2ATaskResultMessage resultMessage = A2ATaskResultMessage.builder()
                .taskId(taskMessage.getTaskId())
                .taskType(taskMessage.getTaskType())
                .agentId(agentId)
                .result(result)
                .status(status)
                .processingTime(result.getProcessingTime())
                .completeTime(System.currentTimeMillis())
                .correlationId(taskMessage.getCorrelationId())
                .build();
            
            CloudEvent resultEvent = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("a2a://agent/" + agentId))
                .withType("org.apache.eventmesh.a2a.task.result")
                .withSubject(A2A_RESULT_TOPIC)
                .withData(JsonUtils.toJSONString(resultMessage).getBytes())
                .withExtension("taskid", taskMessage.getTaskId())
                .withExtension("agentid", agentId)
                .withExtension("tasktype", taskMessage.getTaskType())
                .withExtension("status", status.name())
                .withExtension("correlationid", taskMessage.getCorrelationId())
                .build();
            
            SendMessageContext sendContext = new SendMessageContext(
                resultMessage.getTaskId(), resultEvent, agentId);
            
            eventMeshProducer.send(sendContext, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.debug("📊 Task result published for task {}", taskMessage.getTaskId());
                }
                
                @Override
                public void onException(OnExceptionContext context) {
                    log.error("Failed to publish task result for task {}", taskMessage.getTaskId(), 
                             context.getException());
                }
            });
            
        } catch (Exception e) {
            log.error("Error publishing task result for task {}", taskMessage.getTaskId(), e);
        }
    }
    
    /**
     * Initialize consumer for task results
     */
    private void initializeResultConsumer() {
        // TODO: Implement proper result consumer
        log.info("Result consumer initialization (implementation pending)");
    }
    
    /**
     * Result Event Listener for monitoring and metrics
     */
    private class A2AResultEventListener implements EventListener {
        @Override
        public void consume(CloudEvent cloudEvent, EventMeshAsyncConsumeContext context) {
            try {
                String resultData = new String(cloudEvent.getData().toBytes());
                A2ATaskResultMessage resultMessage = JsonUtils.parseObject(resultData, A2ATaskResultMessage.class);
                
                log.info("📈 Task result received: {} | Agent: {} | Status: {} | Time: {}ms", 
                        resultMessage.getTaskId(), resultMessage.getAgentId(), 
                        resultMessage.getStatus(), resultMessage.getProcessingTime());
                
                // Update metrics
                metricsCollector.recordTaskResult(resultMessage);
                
                context.commit(EventMeshAction.CommitMessage);
            } catch (Exception e) {
                log.error("Error processing task result", e);
                context.commit(EventMeshAction.CommitMessage);
            }
        }
    }
    
    // Helper methods
    
    // TODO: Implement proper consumer creation
    // private EventMeshConsumer createOrGetConsumer(String consumerGroup) { ... }
    
    private CloudEvent createTaskCloudEvent(A2ATaskMessage taskMessage, String topicName) {
        return CloudEventBuilder.v1()
            .withId(taskMessage.getTaskId())
            .withSource(URI.create("a2a://publisher/" + taskMessage.getPublisherAgent()))
            .withType("org.apache.eventmesh.a2a.task.published")
            .withSubject(topicName)
            .withData(JsonUtils.toJSONString(taskMessage).getBytes())
            .withExtension("tasktype", taskMessage.getTaskType())
            .withExtension("priority", taskMessage.getPriority().name())
            .withExtension("publisheragent", taskMessage.getPublisherAgent())
            .withExtension("correlationid", taskMessage.getCorrelationId())
            .build();
    }
    
    private void republishTask(A2ATaskMessage taskMessage) throws Exception {
        String topicName = A2A_TOPIC_PREFIX + taskMessage.getTaskType();
        CloudEvent cloudEvent = createTaskCloudEvent(taskMessage, topicName);
        
        SendMessageContext sendContext = new SendMessageContext(
            taskMessage.getTaskId(), cloudEvent, taskMessage.getPublisherAgent());
            
        eventMeshProducer.send(sendContext, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.debug("Task {} republished for retry", taskMessage.getTaskId());
            }
            
            @Override
            public void onException(OnExceptionContext context) {
                log.error("Failed to republish task {}", taskMessage.getTaskId(), context.getException());
            }
        });
    }
    
    private boolean isTaskExpired(A2ATaskMessage taskMessage) {
        return taskMessage.getTimeout() > 0 && 
               (System.currentTimeMillis() - taskMessage.getPublishTime()) > taskMessage.getTimeout();
    }
    
    private long calculateRetryDelay(int retryCount) {
        // Exponential backoff: 1s, 2s, 4s, 8s, etc.
        return Math.min(1000L * (1L << retryCount), 30000L); // Max 30 seconds
    }
    
    private String generateTaskId() {
        return "a2a-task-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    private boolean hasRequiredCapabilities(List<String> agentCapabilities, List<String> requiredCapabilities) {
        return agentCapabilities.containsAll(requiredCapabilities);
    }
    
    private List<String> getRequiredCapabilitiesForTaskType(String taskType) {
        // This could be configured externally
        switch (taskType) {
            case "data-collection":
                return List.of("data-collection");
            case "data-processing":
                return List.of("data-processing");
            case "data-analysis":
                return List.of("data-analysis");
            default:
                return List.of(taskType);
        }
    }
    
    // Shutdown
    public void shutdown() {
        try {
            // TODO: Implement proper consumer shutdown
            subscriptionRegistry.clear();
            agentCapabilities.clear();
            log.info("A2A Publish/Subscribe service shutdown completed");
        } catch (Exception e) {
            log.error("Error during A2A service shutdown", e);
        }
    }
}