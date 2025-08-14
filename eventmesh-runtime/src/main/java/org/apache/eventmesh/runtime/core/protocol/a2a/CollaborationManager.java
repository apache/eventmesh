package org.apache.eventmesh.runtime.core.protocol.a2a;

import org.apache.eventmesh.runtime.core.protocol.a2a.A2AProtocolAdaptor.A2AMessage;
import org.apache.eventmesh.runtime.core.protocol.a2a.A2AProtocolAdaptor.AgentInfo;
import org.apache.eventmesh.runtime.core.protocol.a2a.A2AProtocolAdaptor.MessageMetadata;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.UUID;

/**
 * A2A Collaboration Manager
 * Manages agent collaboration, task coordination, and workflow orchestration
 */
public class CollaborationManager {
    
    private static final CollaborationManager INSTANCE = new CollaborationManager();
    private final AgentRegistry agentRegistry = AgentRegistry.getInstance();
    private final MessageRouter messageRouter = MessageRouter.getInstance();
    private final Map<String, CollaborationSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, WorkflowDefinition> workflows = new ConcurrentHashMap<>();
    private final ExecutorService collaborationExecutor = Executors.newFixedThreadPool(5);
    
    private CollaborationManager() {}
    
    public static CollaborationManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Start a collaboration session between agents
     */
    public String startCollaboration(String workflowId, List<String> agentIds, Map<String, Object> parameters) {
        String sessionId = UUID.randomUUID().toString();
        
        WorkflowDefinition workflow = workflows.get(workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }
        
        // Validate that all required agents are available
        List<AgentInfo> availableAgents = new ArrayList<>();
        for (String agentId : agentIds) {
            AgentInfo agent = agentRegistry.getAgent(agentId);
            if (agent != null && agentRegistry.isAgentAlive(agentId)) {
                availableAgents.add(agent);
            } else {
                throw new IllegalArgumentException("Agent not available: " + agentId);
            }
        }
        
        CollaborationSession session = new CollaborationSession(sessionId, workflow, availableAgents, parameters);
        activeSessions.put(sessionId, session);
        
        // Start workflow execution
        collaborationExecutor.submit(() -> executeWorkflow(session));
        
        System.out.println("Started collaboration session: " + sessionId + " with workflow: " + workflowId);
        return sessionId;
    }
    
    /**
     * Execute workflow steps
     */
    private void executeWorkflow(CollaborationSession session) {
        try {
            WorkflowDefinition workflow = session.getWorkflow();
            List<WorkflowStep> steps = workflow.getSteps();
            
            for (int i = 0; i < steps.size(); i++) {
                WorkflowStep step = steps.get(i);
                session.setCurrentStep(i);
                
                // Execute step
                boolean stepSuccess = executeStep(session, step);
                
                if (!stepSuccess) {
                    session.setStatus(CollaborationStatus.FAILED);
                    System.err.println("Workflow step failed: " + step.getName());
                    return;
                }
                
                // Wait for step completion if needed
                if (step.getWaitForCompletion()) {
                    boolean completed = waitForStepCompletion(session, step);
                    if (!completed) {
                        session.setStatus(CollaborationStatus.TIMEOUT);
                        return;
                    }
                }
            }
            
            session.setStatus(CollaborationStatus.COMPLETED);
            System.out.println("Workflow completed successfully: " + session.getSessionId());
            
        } catch (Exception e) {
            session.setStatus(CollaborationStatus.FAILED);
            System.err.println("Workflow execution failed: " + e.getMessage());
        }
    }
    
    /**
     * Execute a single workflow step
     */
    private boolean executeStep(CollaborationSession session, WorkflowStep step) {
        try {
            // Find agent with required capabilities
            List<AgentInfo> availableAgents = session.getAvailableAgents();
            AgentInfo targetAgent = findAgentForStep(availableAgents, step);
            
            if (targetAgent == null) {
                System.err.println("No suitable agent found for step: " + step.getName());
                return false;
            }
            
            // Create task request message
            A2AMessage taskRequest = createTaskRequest(session, step, targetAgent);
            
            // Send task request
            messageRouter.routeMessage(taskRequest);
            
            // Store step context
            session.addStepContext(step.getName(), Map.of(
                "targetAgent", targetAgent.getAgentId(),
                "taskId", taskRequest.getMessageId(),
                "startTime", System.currentTimeMillis()
            ));
            
            return true;
            
        } catch (Exception e) {
            System.err.println("Error executing step: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Wait for step completion
     */
    private boolean waitForStepCompletion(CollaborationSession session, WorkflowStep step) {
        long timeout = step.getTimeout() > 0 ? step.getTimeout() : 30000; // Default 30 seconds
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < timeout) {
            Map<String, Object> stepContext = session.getStepContext(step.getName());
            if (stepContext != null && stepContext.containsKey("completed")) {
                return (Boolean) stepContext.get("completed");
            }
            
            try {
                Thread.sleep(1000); // Check every second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        return false; // Timeout
    }
    
    /**
     * Find suitable agent for workflow step
     */
    private AgentInfo findAgentForStep(List<AgentInfo> availableAgents, WorkflowStep step) {
        for (AgentInfo agent : availableAgents) {
            if (agent.getCapabilities() != null) {
                for (String capability : agent.getCapabilities()) {
                    if (step.getRequiredCapabilities().contains(capability)) {
                        return agent;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Create task request message
     */
    private A2AMessage createTaskRequest(CollaborationSession session, WorkflowStep step, AgentInfo targetAgent) {
        A2AMessage taskRequest = new A2AMessage();
        taskRequest.setMessageType("TASK_REQUEST");
        taskRequest.setSourceAgent(createSystemAgent());
        taskRequest.setTargetAgent(targetAgent);
        
        // Create task payload
        Map<String, Object> taskPayload = Map.of(
            "taskId", UUID.randomUUID().toString(),
            "taskType", step.getName(),
            "parameters", step.getParameters(),
            "sessionId", session.getSessionId(),
            "workflowId", session.getWorkflow().getId(),
            "stepIndex", session.getCurrentStep(),
            "constraints", Map.of(
                "timeout", step.getTimeout(),
                "priority", "HIGH",
                "retryCount", step.getRetryCount()
            )
        );
        
        taskRequest.setPayload(taskPayload);
        
        // Add correlation ID for tracking
        MessageMetadata metadata = new MessageMetadata();
        metadata.setCorrelationId(session.getSessionId());
        taskRequest.setMetadata(metadata);
        
        return taskRequest;
    }
    
    /**
     * Create system agent for internal communication
     */
    private AgentInfo createSystemAgent() {
        AgentInfo systemAgent = new AgentInfo();
        systemAgent.setAgentId("system-collaboration-manager");
        systemAgent.setAgentType("system");
        systemAgent.setCapabilities(new String[]{"workflow-orchestration", "task-coordination"});
        return systemAgent;
    }
    
    /**
     * Handle task response from agent
     */
    public void handleTaskResponse(A2AMessage response) {
        String sessionId = response.getMetadata().getCorrelationId();
        CollaborationSession session = activeSessions.get(sessionId);
        
        if (session == null) {
            System.err.println("No active session found for response: " + sessionId);
            return;
        }
        
        // Update step context
        Map<String, Object> responseData = (Map<String, Object>) response.getPayload();
        String taskId = (String) responseData.get("taskId");
        
        // Find step by task ID
        for (Map.Entry<String, Map<String, Object>> entry : session.getStepContexts().entrySet()) {
            Map<String, Object> stepContext = entry.getValue();
            if (taskId.equals(stepContext.get("taskId"))) {
                stepContext.put("completed", true);
                stepContext.put("result", responseData.get("result"));
                stepContext.put("endTime", System.currentTimeMillis());
                break;
            }
        }
    }
    
    /**
     * Register workflow definition
     */
    public void registerWorkflow(WorkflowDefinition workflow) {
        workflows.put(workflow.getId(), workflow);
        System.out.println("Registered workflow: " + workflow.getId());
    }
    
    /**
     * Get collaboration session status
     */
    public CollaborationStatus getSessionStatus(String sessionId) {
        CollaborationSession session = activeSessions.get(sessionId);
        return session != null ? session.getStatus() : null;
    }
    
    /**
     * Cancel collaboration session
     */
    public boolean cancelSession(String sessionId) {
        CollaborationSession session = activeSessions.remove(sessionId);
        if (session != null) {
            session.setStatus(CollaborationStatus.CANCELLED);
            
            // Notify agents about cancellation
            A2AMessage cancelMessage = new A2AMessage();
            cancelMessage.setMessageType("COLLABORATION_CANCELLED");
            cancelMessage.setSourceAgent(createSystemAgent());
            cancelMessage.setPayload(Map.of("sessionId", sessionId, "reason", "Cancelled by user"));
            
            for (AgentInfo agent : session.getAvailableAgents()) {
                cancelMessage.setTargetAgent(agent);
                messageRouter.routeMessage(cancelMessage);
            }
            
            return true;
        }
        return false;
    }
    
    /**
     * Shutdown collaboration manager
     */
    public void shutdown() {
        collaborationExecutor.shutdown();
        try {
            if (!collaborationExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                collaborationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            collaborationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Collaboration Session
     */
    public static class CollaborationSession {
        private final String sessionId;
        private final WorkflowDefinition workflow;
        private final List<AgentInfo> availableAgents;
        private final Map<String, Object> parameters;
        private final Map<String, Map<String, Object>> stepContexts = new ConcurrentHashMap<>();
        private CollaborationStatus status = CollaborationStatus.RUNNING;
        private int currentStep = -1;
        
        public CollaborationSession(String sessionId, WorkflowDefinition workflow, 
                                  List<AgentInfo> availableAgents, Map<String, Object> parameters) {
            this.sessionId = sessionId;
            this.workflow = workflow;
            this.availableAgents = availableAgents;
            this.parameters = parameters;
        }
        
        // Getters and setters
        public String getSessionId() { return sessionId; }
        public WorkflowDefinition getWorkflow() { return workflow; }
        public List<AgentInfo> getAvailableAgents() { return availableAgents; }
        public Map<String, Object> getParameters() { return parameters; }
        public Map<String, Map<String, Object>> getStepContexts() { return stepContexts; }
        public CollaborationStatus getStatus() { return status; }
        public void setStatus(CollaborationStatus status) { this.status = status; }
        public int getCurrentStep() { return currentStep; }
        public void setCurrentStep(int currentStep) { this.currentStep = currentStep; }
        
        public void addStepContext(String stepName, Map<String, Object> context) {
            stepContexts.put(stepName, context);
        }
        
        public Map<String, Object> getStepContext(String stepName) {
            return stepContexts.get(stepName);
        }
    }
    
    /**
     * Workflow Definition
     */
    public static class WorkflowDefinition {
        private final String id;
        private final String name;
        private final String description;
        private final List<WorkflowStep> steps;
        
        public WorkflowDefinition(String id, String name, String description, List<WorkflowStep> steps) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.steps = steps;
        }
        
        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<WorkflowStep> getSteps() { return steps; }
    }
    
    /**
     * Workflow Step
     */
    public static class WorkflowStep {
        private final String name;
        private final String description;
        private final List<String> requiredCapabilities;
        private final Map<String, Object> parameters;
        private final boolean waitForCompletion;
        private final long timeout;
        private final int retryCount;
        
        public WorkflowStep(String name, String description, List<String> requiredCapabilities,
                          Map<String, Object> parameters, boolean waitForCompletion, 
                          long timeout, int retryCount) {
            this.name = name;
            this.description = description;
            this.requiredCapabilities = requiredCapabilities;
            this.parameters = parameters;
            this.waitForCompletion = waitForCompletion;
            this.timeout = timeout;
            this.retryCount = retryCount;
        }
        
        // Getters
        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<String> getRequiredCapabilities() { return requiredCapabilities; }
        public Map<String, Object> getParameters() { return parameters; }
        public boolean getWaitForCompletion() { return waitForCompletion; }
        public long getTimeout() { return timeout; }
        public int getRetryCount() { return retryCount; }
    }
    
    /**
     * Collaboration Status Enum
     */
    public enum CollaborationStatus {
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED,
        TIMEOUT
    }
}
