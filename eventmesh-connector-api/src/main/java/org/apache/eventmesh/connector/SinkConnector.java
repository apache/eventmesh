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
 * Sink-side connector: writes CloudEvents (received from EventMesh over HTTP long-poll) into an
 * external system (Redis, HTTP API, a target MQ, …), §8. Owns its own write-ack offset.
 */
public interface SinkConnector {

    /** Initialize with config properties. */
    void init(Properties props);

    /** Write a batch to the external system. Throw to signal failure (runtime will not ACK → redelivery). */
    void put(List<CloudEvent> events);

    /**
     * Checkpoint the sink write offset up to the last event in {@code written}.
     */
    void commit(List<CloudEvent> written);
}
