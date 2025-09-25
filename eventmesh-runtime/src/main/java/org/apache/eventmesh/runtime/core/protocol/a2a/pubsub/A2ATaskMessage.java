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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A2A Task Message - represents a task published to EventMesh topic
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class A2ATaskMessage {
    
    /**
     * Unique task identifier
     */
    private String taskId;
    
    /**
     * Type of task (used for topic routing)
     */
    private String taskType;
    
    /**
     * Task payload/parameters
     */
    private Map<String, Object> payload;
    
    /**
     * Required capabilities to process this task
     */
    private List<String> requiredCapabilities;
    
    /**
     * Task priority
     */
    private A2ATaskPriority priority = A2ATaskPriority.NORMAL;
    
    /**
     * Task timeout in milliseconds (0 = no timeout)
     */
    private long timeout = 0;
    
    /**
     * Current retry count
     */
    private int retryCount = 0;
    
    /**
     * Maximum number of retries allowed
     */
    private int maxRetries = 3;
    
    /**
     * When the task was first published
     */
    private long publishTime;
    
    /**
     * Agent that published this task
     */
    private String publisherAgent;
    
    /**
     * Correlation ID for tracking related tasks
     */
    private String correlationId;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Task priority levels
     */
    public enum A2ATaskPriority {
        LOW(1),
        NORMAL(2), 
        HIGH(3),
        CRITICAL(4);
        
        private final int value;
        
        A2ATaskPriority(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
}