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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * Registry for managing A2A agent subscriptions
 */
@Slf4j
public class SubscriptionRegistry {
    
    // Task type -> Set of agent IDs subscribed to that task type
    private final Map<String, Set<String>> taskTypeSubscriptions = new ConcurrentHashMap<>();
    
    // Agent ID -> Set of task types the agent is subscribed to
    private final Map<String, Set<String>> agentSubscriptions = new ConcurrentHashMap<>();
    
    // Agent ID -> Agent capabilities
    private final Map<String, List<String>> agentCapabilities = new ConcurrentHashMap<>();
    
    /**
     * Add a subscription for an agent to a task type
     */
    public void addSubscription(String agentId, String taskType, List<String> capabilities) {
        // Add to task type subscriptions
        taskTypeSubscriptions.computeIfAbsent(taskType, k -> ConcurrentHashMap.newKeySet())
                             .add(agentId);
        
        // Add to agent subscriptions  
        agentSubscriptions.computeIfAbsent(agentId, k -> ConcurrentHashMap.newKeySet())
                          .add(taskType);
        
        // Store agent capabilities
        agentCapabilities.put(agentId, capabilities);
        
        log.info("Added subscription: Agent {} -> Task type {}", agentId, taskType);
    }
    
    /**
     * Remove a subscription
     */
    public void removeSubscription(String agentId, String taskType) {
        // Remove from task type subscriptions
        Set<String> agents = taskTypeSubscriptions.get(taskType);
        if (agents != null) {
            agents.remove(agentId);
            if (agents.isEmpty()) {
                taskTypeSubscriptions.remove(taskType);
            }
        }
        
        // Remove from agent subscriptions
        Set<String> taskTypes = agentSubscriptions.get(agentId);
        if (taskTypes != null) {
            taskTypes.remove(taskType);
            if (taskTypes.isEmpty()) {
                agentSubscriptions.remove(agentId);
                agentCapabilities.remove(agentId);
            }
        }
        
        log.info("Removed subscription: Agent {} -> Task type {}", agentId, taskType);
    }
    
    /**
     * Remove all subscriptions for an agent
     */
    public void removeAgentSubscriptions(String agentId) {
        Set<String> taskTypes = agentSubscriptions.remove(agentId);
        agentCapabilities.remove(agentId);
        
        if (taskTypes != null) {
            for (String taskType : taskTypes) {
                Set<String> agents = taskTypeSubscriptions.get(taskType);
                if (agents != null) {
                    agents.remove(agentId);
                    if (agents.isEmpty()) {
                        taskTypeSubscriptions.remove(taskType);
                    }
                }
            }
        }
        
        log.info("Removed all subscriptions for agent {}", agentId);
    }
    
    /**
     * Get all agents subscribed to a task type
     */
    public Set<String> getSubscribedAgents(String taskType) {
        return taskTypeSubscriptions.getOrDefault(taskType, Set.of());
    }
    
    /**
     * Get all task types an agent is subscribed to
     */
    public Set<String> getAgentSubscriptions(String agentId) {
        return agentSubscriptions.getOrDefault(agentId, Set.of());
    }
    
    /**
     * Get agent capabilities
     */
    public List<String> getAgentCapabilities(String agentId) {
        return agentCapabilities.get(agentId);
    }
    
    /**
     * Check if an agent is subscribed to a task type
     */
    public boolean isAgentSubscribed(String agentId, String taskType) {
        Set<String> agents = taskTypeSubscriptions.get(taskType);
        return agents != null && agents.contains(agentId);
    }
    
    /**
     * Get subscription statistics
     */
    public SubscriptionStats getStats() {
        return SubscriptionStats.builder()
            .totalAgents(agentSubscriptions.size())
            .totalTaskTypes(taskTypeSubscriptions.size())
            .totalSubscriptions(agentSubscriptions.values().stream()
                              .mapToInt(Set::size).sum())
            .build();
    }
    
    /**
     * Clear all subscriptions
     */
    public void clear() {
        taskTypeSubscriptions.clear();
        agentSubscriptions.clear();
        agentCapabilities.clear();
        log.info("Cleared all subscriptions");
    }
    
    /**
     * Subscription statistics
     */
    @lombok.Data
    @lombok.Builder
    public static class SubscriptionStats {
        private int totalAgents;
        private int totalTaskTypes;
        private int totalSubscriptions;
    }
}