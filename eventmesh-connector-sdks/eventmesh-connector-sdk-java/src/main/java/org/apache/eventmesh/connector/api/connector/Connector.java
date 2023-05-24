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

package org.apache.eventmesh.connector.api.connector;

import org.apache.eventmesh.connector.api.config.Config;
import org.apache.eventmesh.connector.api.data.ConnectRecord;

/**
 * Connector
 */
public interface Connector {

    /**
     * Returns the class type of the configuration for this Connector.
     *
     * @return Class type of the configuration
     */
    Class<? extends Config> configClass();

    /**
     * Initializes the Connector with the provided configuration.
     *
     * @param config Configuration object
     * @throws Exception if initialization fails
     */
    void init(Config config) throws Exception;

    /**
     * Starts the Connector.
     *
     * @throws Exception if the start operation fails
     */
    void start() throws Exception;

    /**
     * Commits the specified ConnectRecord object.
     *
     * @param record ConnectRecord object to commit
     */
    void commit(ConnectRecord record);

    /**
     * Returns the name of the Connector.
     *
     * @return String name of the Connector
     */
    String name();

    /**
     * Stops the Connector.
     *
     * @throws Exception if stopping fails
     */
    void stop() throws Exception;

}
