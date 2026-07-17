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

package org.apache.eventmesh.runtime.connector;

import java.util.Map;

/**
 * Offset store interface — for managing connector source consumption progress.
 *
 * <p>Implementations:
 * <ul>
 *   <li>In-memory (test/dev)</li>
 *   <li>File-based (local persistence)</li>
 *   <li>RocksDB (local, production-ready)</li>
 *   <li>Remote (Admin Server sync)</li>
 * </ul>
 */
public interface OffsetStore {

    /**
     * Save offset for a specific partition.
     * @param connectorName connector name
     * @param topic         source topic
     * @param partition     partition number
     * @param position      offset position string
     */
    void save(String connectorName, String topic, int partition, String position);

    /**
     * Load offset for a specific partition.
     * @return offset position string, or null if not found
     */
    String load(String connectorName, String topic, int partition);

    /**
     * Load all offsets for a connector.
     * @return map of key (topic:partition) → position
     */
    Map<String, String> loadAll(String connectorName);

    /**
     * Flush buffered writes to persistent storage.
     */
    void flush();

    /**
     * Close the store, releasing resources.
     */
    void close();
}
