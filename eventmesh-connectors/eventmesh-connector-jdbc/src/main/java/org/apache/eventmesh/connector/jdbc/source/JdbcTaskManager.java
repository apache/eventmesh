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

package org.apache.eventmesh.connector.jdbc.source;

/**
 * The JdbcTaskManager interface represents a manager for JDBC tasks. It extends the AutoCloseable interface, allowing the manager to be managed
 * efficiently.
 */
public interface JdbcTaskManager extends AutoCloseable {

    /**
     * Initializes the JDBC task manager, setting up any required configurations or resources.
     */
    void init();

    /**
     * Starts the JDBC task manager, allowing it to begin managing tasks.
     */
    void start();

    /**
     * Shuts down the JDBC task manager, releasing any acquired resources or stopping task management.
     */
    void shutdown();

    /**
     * Registers a listener to receive events and notifications from the JDBC task manager.
     *
     * @param listener The listener to be registered.
     */
    void registerListener(TaskManagerListener listener);

}
