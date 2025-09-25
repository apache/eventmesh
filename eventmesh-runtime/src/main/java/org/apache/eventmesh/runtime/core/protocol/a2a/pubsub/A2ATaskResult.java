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

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A2A Task Result - returned by task handlers
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class A2ATaskResult {
    
    /**
     * Task execution result data
     */
    private Map<String, Object> data;
    
    /**
     * Error message if task failed
     */
    private String error;
    
    /**
     * Task processing time in milliseconds
     */
    private long processingTime;
    
    /**
     * Additional metadata about the result
     */
    private Map<String, Object> metadata;
    
    /**
     * Whether the task completed successfully
     */
    public boolean isSuccess() {
        return error == null || error.trim().isEmpty();
    }
}