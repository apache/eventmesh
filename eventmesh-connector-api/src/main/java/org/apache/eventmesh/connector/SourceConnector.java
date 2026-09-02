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

package org.apache.eventmesh.connector;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

/**
 * Source-side connector: pulls CloudEvents from an external system (MySQL binlog, a source MQ, an
 * HTTP feed, …) for the {@code ConnectorRuntime} to publish into EventMesh over HTTP (§8).
 *
 * <p>Implementations own their own offset (e.g. binlog position); {@link #commit(CloudEvent)} is the
 * at-least-once checkpoint — only called after EventMesh has accepted the publish.</p>
 */
public interface SourceConnector {

    /** Initialize with config properties (bootstrap, topic, credentials…). */
    void init(Properties props);

    /**
     * Resume from a runtime-managed offset (the last-committed marker). Connectors with native
     * offset (Kafka commitSync) may ignore this; connectors without native offset use it to seek.
     */
    default void resume(String lastOffset) {
        // no-op by default — connectors override if they support runtime-managed offset
    }

    /** Pull the next batch from the external system (empty list when nothing ready). */
    List<CloudEvent> poll();

    /**
     * Checkpoint the source offset up to and including {@code lastPublished}.
     */
    void commit(CloudEvent lastPublished);
}
