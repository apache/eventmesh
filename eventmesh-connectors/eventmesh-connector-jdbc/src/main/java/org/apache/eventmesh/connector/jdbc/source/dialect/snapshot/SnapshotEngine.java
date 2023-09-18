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

package org.apache.eventmesh.connector.jdbc.source.dialect.snapshot;

import org.apache.eventmesh.connector.jdbc.JdbcContext;
import org.apache.eventmesh.connector.jdbc.event.EventConsumer;
import org.apache.eventmesh.connector.jdbc.source.Engine;

/**
 * The SnapshotEngine interface extends the Engine interface and represents an engine capable of performing snapshots.
 *
 * @param <Jc> The type of JdbcContext used for the snapshot.
 */
public interface SnapshotEngine<Jc extends JdbcContext> extends Engine, AutoCloseable {

    /**
     * Executes the snapshot operation and returns the result containing the snapshot offset.
     *
     * @return The SnapshotResult containing the snapshot offset.
     */
    SnapshotResult<Jc> execute();

    /**
     * Registers a SnapshotEventConsumer to receive snapshot events from the engine.
     *
     * @param consumer The SnapshotEventConsumer to register.
     */
    void registerSnapshotEventConsumer(EventConsumer consumer);
}

