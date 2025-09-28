package org.apache.eventmesh.examples.a2a;

import org.apache.eventmesh.runtime.core.protocol.a2a.A2AProtocolProcessor;
import org.apache.eventmesh.protocol.a2a.A2AProtocolAdaptor.A2AMessage;
import org.apache.eventmesh.protocol.a2a.A2AProtocolAdaptor.AgentInfo;
import org.apache.eventmesh.runtime.core.protocol.a2a.CollaborationManager;
import org.apache.eventmesh.runtime.core.protocol.a2a.CollaborationManager.WorkflowDefinition;
import org.apache.eventmesh.runtime.core.protocol.a2a.CollaborationManager.WorkflowStep;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A2A Protocol Complete Example
 * Demonstrates the complete usage of A2A protocol for agent-to-agent communication
 */
public class A2AProtocolExample {
    
    public static void main(String[] args) {
        System.out.println("=== EventMesh A2A Protocol Example ===");
        
        try {
            // 1. 创建多个智能体
            createAndStartAgents();
            
            // 2. 演示智能体注册和发现
            demonstrateAgentDiscovery();
            
            // 3. 演示智能体间通信
            demonstrateAgentCommunication();
            
            // 4. 演示协作工作流
            demonstrateCollaborationWorkflow();
            
            // 5. 演示状态同步
            demonstrateStateSynchronization();
            
            // 6. 清理资源
            cleanup();
            
        } catch (Exception e) {
            System.err.println("Error running A2A protocol example: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 创建并启动多个智能体
     */
    private static void createAndStartAgents() throws InterruptedException {
        System.out.println("\n1. Creating and starting agents...");
        
        // 创建任务执行器智能体
        SimpleA2AAgent taskExecutor = new SimpleA2AAgent(
            "task-executor-001",
            "task-executor",
            new String[]{"data-processing", "image-analysis", "text-generation"}
        );
        
        // 创建数据提供者智能体
        SimpleA2AAgent dataProvider = new SimpleA2AAgent(
            "data-provider-001",
            "data-provider",
            new String[]{"data-collection", "data-storage", "data-retrieval"}
        );
        
        // 创建分析引擎智能体
        SimpleA2AAgent analyticsEngine = new SimpleA2AAgent(
            "analytics-engine-001",
            "analytics-engine",
            new String[]{"ml-inference", "statistical-analysis", "prediction"}
        );
        
        // 启动智能体
        taskExecutor.start();
        dataProvider.start();
        analyticsEngine.start();
        
        // 等待智能体启动完成
        Thread.sleep(5000);
        
        System.out.println("✓ All agents started successfully");
    }
    
    /**
     * 演示智能体发现功能
     */
    private static void demonstrateAgentDiscovery() {
        System.out.println("\n2. Demonstrating agent discovery...");
        
        A2AProtocolProcessor processor = A2AProtocolProcessor.getInstance();
        
        // 获取所有注册的智能体
        List<AgentInfo> allAgents = processor.getMessageHandler().getAllAgents();
        System.out.println("Total registered agents: " + allAgents.size());
        
        // 按类型查找智能体
        List<AgentInfo> taskExecutors = processor.getMessageHandler().findAgentsByType("task-executor");
        System.out.println("Task executors found: " + taskExecutors.size());
        
        // 按能力查找智能体
        List<AgentInfo> dataProcessors = processor.getMessageHandler().findAgentsByCapability("data-processing");
        System.out.println("Data processors found: " + dataProcessors.size());
        
        // 检查智能体状态
        for (AgentInfo agent : allAgents) {
            boolean isAlive = processor.getMessageHandler().isAgentAlive(agent.getAgentId());
            System.out.println("Agent " + agent.getAgentId() + " is " + (isAlive ? "online" : "offline"));
        }
        
        System.out.println("✓ Agent discovery completed");
    }
    
    /**
     * 演示智能体间通信
     */
    private static void demonstrateAgentCommunication() throws InterruptedException {
        System.out.println("\n3. Demonstrating agent communication...");
        
        A2AProtocolProcessor processor = A2AProtocolProcessor.getInstance();
        
        // 创建任务请求消息
        A2AMessage taskRequest = processor.createTaskRequestMessage(
            "task-executor-001",
            "data-provider-001",
            "data-processing",
            Map.of(
                "inputData", "https://example.com/data.csv",
                "processingRules", Arrays.asList("filter", "transform", "aggregate"),
                "outputFormat", "json"
            )
        );
        
        System.out.println("Sending task request from " + taskRequest.getSourceAgent().getAgentId() + 
                          " to " + taskRequest.getTargetAgent().getAgentId());
        
        // 发送任务请求
        processor.getMessageHandler().handleMessage(taskRequest);
        
        // 等待处理完成
        Thread.sleep(3000);
        
        // 发送状态同步消息
        A2AMessage stateSync = processor.createStateSyncMessage(
            "task-executor-001",
            Map.of(
                "status", "BUSY",
                "currentTask", "data-processing",
                "progress", 75,
                "metrics", Map.of(
                    "cpuUsage", 65.5,
                    "memoryUsage", 45.2,
                    "activeConnections", 10
                )
            )
        );
        
        System.out.println("Sending state sync message from " + stateSync.getSourceAgent().getAgentId());
        processor.getMessageHandler().handleMessage(stateSync);
        
        System.out.println("✓ Agent communication completed");
    }
    
    /**
     * 演示协作工作流
     */
    private static void demonstrateCollaborationWorkflow() throws InterruptedException {
        System.out.println("\n4. Demonstrating collaboration workflow...");
        
        CollaborationManager collaborationManager = CollaborationManager.getInstance();
        
        // 定义工作流步骤
        List<WorkflowStep> steps = Arrays.asList(
            new WorkflowStep(
                "data-collection",
                "Collect data from multiple sources",
                Arrays.asList("data-collection"),
                Map.of(
                    "sources", Arrays.asList("source1", "source2", "source3"),
                    "batchSize", 1000
                ),
                true, 30000, 3
            ),
            new WorkflowStep(
                "data-processing",
                "Process collected data",
                Arrays.asList("data-processing"),
                Map.of(
                    "algorithm", "ml-pipeline",
                    "features", Arrays.asList("feature1", "feature2", "feature3")
                ),
                true, 60000, 3
            ),
            new WorkflowStep(
                "analysis",
                "Perform advanced analysis",
                Arrays.asList("ml-inference", "statistical-analysis"),
                Map.of(
                    "modelType", "regression",
                    "confidenceThreshold", 0.95
                ),
                true, 45000, 3
            )
        );
        
        // 创建工作流定义
        WorkflowDefinition workflow = new WorkflowDefinition(
            "data-pipeline",
            "Data Processing Pipeline",
            "End-to-end data processing and analysis workflow",
            steps
        );
        
        // 注册工作流
        collaborationManager.registerWorkflow(workflow);
        System.out.println("Workflow registered: " + workflow.getId());
        
        // 启动协作会话
        String sessionId = collaborationManager.startCollaboration(
            "data-pipeline",
            Arrays.asList("data-provider-001", "task-executor-001", "analytics-engine-001"),
            Map.of(
                "batchSize", 1000,
                "priority", "HIGH",
                "timeout", 300000
            )
        );
        
        System.out.println("Collaboration session started: " + sessionId);
        
        // 监控协作状态
        for (int i = 0; i < 10; i++) {
            CollaborationManager.CollaborationStatus status = collaborationManager.getSessionStatus(sessionId);
            System.out.println("Session " + sessionId + " status: " + status);
            
            if (status == CollaborationManager.CollaborationStatus.COMPLETED) {
                System.out.println("✓ Collaboration workflow completed successfully");
                break;
            } else if (status == CollaborationManager.CollaborationStatus.FAILED) {
                System.out.println("✗ Collaboration workflow failed");
                break;
            }
            
            Thread.sleep(2000);
        }
        
        System.out.println("✓ Collaboration workflow demonstration completed");
    }
    
    /**
     * 演示状态同步
     */
    private static void demonstrateStateSynchronization() throws InterruptedException {
        System.out.println("\n5. Demonstrating state synchronization...");
        
        A2AProtocolProcessor processor = A2AProtocolProcessor.getInstance();
        
        // 模拟多个智能体的状态同步
        String[] agentIds = {"task-executor-001", "data-provider-001", "analytics-engine-001"};
        String[] statuses = {"IDLE", "BUSY", "ERROR"};
        
        for (int i = 0; i < agentIds.length; i++) {
            A2AMessage stateSync = processor.createStateSyncMessage(
                agentIds[i],
                Map.of(
                    "status", statuses[i],
                    "currentTask", "task-" + (i + 1),
                    "progress", (i + 1) * 25,
                    "metrics", Map.of(
                        "cpuUsage", 20.0 + (i * 15),
                        "memoryUsage", 30.0 + (i * 10),
                        "activeConnections", i + 1
                    ),
                    "lastUpdate", System.currentTimeMillis()
                )
            );
            
            System.out.println("Sending state sync from " + agentIds[i] + " with status: " + statuses[i]);
            processor.getMessageHandler().handleMessage(stateSync);
            
            Thread.sleep(1000);
        }
        
        // 广播消息
        A2AMessage broadcastMessage = new A2AMessage();
        broadcastMessage.setMessageType("BROADCAST");
        
        AgentInfo sourceAgent = new AgentInfo();
        sourceAgent.setAgentId("system-coordinator");
        sourceAgent.setAgentType("system");
        broadcastMessage.setSourceAgent(sourceAgent);
        
        AgentInfo targetAgent = new AgentInfo();
        targetAgent.setAgentId("broadcast");
        broadcastMessage.setTargetAgent(targetAgent);
        
        broadcastMessage.setPayload(Map.of(
            "broadcastId", "broadcast-001",
            "content", "System maintenance scheduled for tomorrow at 2 AM",
            "priority", "HIGH",
            "timestamp", System.currentTimeMillis()
        ));
        
        System.out.println("Sending broadcast message to all agents");
        processor.getMessageHandler().handleMessage(broadcastMessage);
        
        System.out.println("✓ State synchronization completed");
    }
    
    /**
     * 清理资源
     */
    private static void cleanup() throws InterruptedException {
        System.out.println("\n6. Cleaning up resources...");
        
        // 停止所有智能体
        // 在实际应用中，这里应该停止所有创建的智能体实例
        
        // 关闭A2A协议处理器
        A2AProtocolProcessor processor = A2AProtocolProcessor.getInstance();
        processor.getMessageHandler().shutdown();
        
        System.out.println("✓ Cleanup completed");
        System.out.println("\n=== A2A Protocol Example Completed ===");
    }
    
    /**
     * 演示错误处理和恢复
     */
    public static void demonstrateErrorHandling() {
        System.out.println("\n7. Demonstrating error handling...");
        
        A2AProtocolProcessor processor = A2AProtocolProcessor.getInstance();
        
        try {
            // 尝试向不存在的智能体发送消息
            A2AMessage invalidRequest = processor.createTaskRequestMessage(
                "task-executor-001",
                "non-existent-agent",
                "data-processing",
                Map.of("test", "data")
            );
            
            processor.getMessageHandler().handleMessage(invalidRequest);
            
        } catch (Exception e) {
            System.out.println("Expected error caught: " + e.getMessage());
        }
        
        // 演示超时处理
        try {
            // 创建一个长时间运行的任务
            A2AMessage longRunningTask = processor.createTaskRequestMessage(
                "task-executor-001",
                "data-provider-001",
                "long-running-task",
                Map.of("timeout", 10000)
            );
            
            processor.getMessageHandler().handleMessage(longRunningTask);
            
        } catch (Exception e) {
            System.out.println("Timeout error caught: " + e.getMessage());
        }
        
        System.out.println("✓ Error handling demonstration completed");
    }
    
    /**
     * 演示性能监控
     */
    public static void demonstratePerformanceMonitoring() {
        System.out.println("\n8. Demonstrating performance monitoring...");
        
        A2AProtocolProcessor processor = A2AProtocolProcessor.getInstance();
        
        // 模拟性能指标收集
        long startTime = System.currentTimeMillis();
        
        // 发送多个消息
        for (int i = 0; i < 100; i++) {
            A2AMessage message = processor.createTaskRequestMessage(
                "task-executor-001",
                "data-provider-001",
                "performance-test",
                Map.of("messageId", "msg-" + i, "timestamp", System.currentTimeMillis())
            );
            
            processor.getMessageHandler().handleMessage(message);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("Sent 100 messages in " + duration + "ms");
        System.out.println("Average message processing time: " + (duration / 100.0) + "ms");
        
        // 获取系统状态
        List<AgentInfo> agents = processor.getMessageHandler().getAllAgents();
        System.out.println("Active agents: " + agents.size());
        
        for (AgentInfo agent : agents) {
            boolean isAlive = processor.getMessageHandler().isAgentAlive(agent.getAgentId());
            System.out.println("Agent " + agent.getAgentId() + " health: " + (isAlive ? "OK" : "FAILED"));
        }
        
        System.out.println("✓ Performance monitoring completed");
    }
}
