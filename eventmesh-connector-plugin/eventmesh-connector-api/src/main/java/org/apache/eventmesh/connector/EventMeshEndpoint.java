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

import io.cloudevents.CloudEvent;

/**
 * HTTP bridge from an independent Connector Runtime process to the EventMesh Runtime (§8).
 *
 * <p>Production does {@code POST /events/publish} (source side) and {@code GET /events/poll} +
 * {@code POST /events/ack} (sink side) against the EventMesh Runtime URL discovered via Meta.
 * Tests substitute an in-process implementation that hands events straight to a
 * {@code org.apache.eventmesh.runtime.ingress.UniIngressService}.</p>
 */
public interface EventMeshEndpoint {

    /**
     * Publish one event (source → EventMesh). @return true on 202 Accepted.
     */
    boolean publish(String topic, CloudEvent event);

    /**
     * Long-poll a batch of buffered deliveries (EventMesh → sink).
     */
    List<PollEntry> pollForSink(String sinkClientId, int maxEvents, long timeoutMs);

    /**
     * Acknowledge a delivery so the EventMesh offset advances.
     */
    boolean ack(String deliveryId);
}
