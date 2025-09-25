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

/**
 * Interface for A2A task handlers.
 * Agents implement this interface to process specific task types.
 */
@FunctionalInterface
public interface A2ATaskHandler {
    
    /**
     * Handle an A2A task and return the result.
     * 
     * @param taskMessage the task to process
     * @return the task result
     * @throws Exception if task processing fails
     */
    A2ATaskResult handleTask(A2ATaskMessage taskMessage) throws Exception;
}