package org.apache.eventmesh.runtime.core.protocol.a2a;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.core.protocol.a2a.A2AProtocolAdaptor.AgentInfo;
import org.apache.eventmesh.runtime.core.protocol.a2a.A2AProtocolAdaptor.A2AMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Agent Registry for A2A Protocol
 * Manages agent registration, discovery, and metadata
 */
public class AgentRegistry {
    
    private static final AgentRegistry INSTANCE = new AgentRegistry();
    private final Map<String, AgentInfo> registeredAgents = new ConcurrentHashMap<>();
    private final Map<String, Long> agentHeartbeats = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1);
    
    // Heartbeat timeout in milliseconds (30 seconds)
    private static final long HEARTBEAT_TIMEOUT = 30000;
    
    private AgentRegistry() {
        startHeartbeatMonitor();
    }
    
    public static AgentRegistry getInstance() {
        return INSTANCE;
    }
    
    /**
     * Register an agent
     */
    public boolean registerAgent(A2AMessage registerMessage) {
        try {
            AgentInfo agentInfo = (AgentInfo) registerMessage.getPayload();
            String agentId = agentInfo.getAgentId();
            
            registeredAgents.put(agentId, agentInfo);
            agentHeartbeats.put(agentId, System.currentTimeMillis());
            
            System.out.println("Agent registered: " + agentId + " of type: " + agentInfo.getAgentType());
            return true;
        } catch (Exception e) {
            System.err.println("Failed to register agent: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Unregister an agent
     */
    public boolean unregisterAgent(String agentId) {
        AgentInfo removed = registeredAgents.remove(agentId);
        agentHeartbeats.remove(agentId);
        
        if (removed != null) {
            System.out.println("Agent unregistered: " + agentId);
            return true;
        }
        return false;
    }
    
    /**
     * Update agent heartbeat
     */
    public void updateHeartbeat(String agentId) {
        agentHeartbeats.put(agentId, System.currentTimeMillis());
    }
    
    /**
     * Get agent information
     */
    public AgentInfo getAgent(String agentId) {
        return registeredAgents.get(agentId);
    }
    
    /**
     * Get all registered agents
     */
    public List<AgentInfo> getAllAgents() {
        return new ArrayList<>(registeredAgents.values());
    }
    
    /**
     * Find agents by type
     */
    public List<AgentInfo> findAgentsByType(String agentType) {
        return registeredAgents.values().stream()
            .filter(agent -> agentType.equals(agent.getAgentType()))
            .collect(Collectors.toList());
    }
    
    /**
     * Find agents by capability
     */
    public List<AgentInfo> findAgentsByCapability(String capability) {
        return registeredAgents.values().stream()
            .filter(agent -> agent.getCapabilities() != null)
            .filter(agent -> {
                for (String cap : agent.getCapabilities()) {
                    if (capability.equals(cap)) {
                        return true;
                    }
                }
                return false;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Check if agent is alive
     */
    public boolean isAgentAlive(String agentId) {
        Long lastHeartbeat = agentHeartbeats.get(agentId);
        if (lastHeartbeat == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastHeartbeat) < HEARTBEAT_TIMEOUT;
    }
    
    /**
     * Get agent status
     */
    public AgentStatus getAgentStatus(String agentId) {
        AgentInfo agentInfo = registeredAgents.get(agentId);
        if (agentInfo == null) {
            return AgentStatus.UNREGISTERED;
        }
        
        if (isAgentAlive(agentId)) {
            return AgentStatus.ONLINE;
        } else {
            return AgentStatus.OFFLINE;
        }
    }
    
    /**
     * Start heartbeat monitoring
     */
    private void startHeartbeatMonitor() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            List<String> deadAgents = new ArrayList<>();
            
            for (Map.Entry<String, Long> entry : agentHeartbeats.entrySet()) {
                String agentId = entry.getKey();
                Long lastHeartbeat = entry.getValue();
                
                if ((currentTime - lastHeartbeat) > HEARTBEAT_TIMEOUT) {
                    deadAgents.add(agentId);
                }
            }
            
            // Remove dead agents
            for (String agentId : deadAgents) {
                unregisterAgent(agentId);
                System.out.println("Agent marked as dead and unregistered: " + agentId);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }
    
    /**
     * Shutdown registry
     */
    public void shutdown() {
        heartbeatExecutor.shutdown();
        try {
            if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                heartbeatExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Agent Status Enum
     */
    public enum AgentStatus {
        UNREGISTERED,
        ONLINE,
        OFFLINE
    }
}
