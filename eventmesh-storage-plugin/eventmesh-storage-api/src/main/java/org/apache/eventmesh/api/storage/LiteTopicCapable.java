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

package org.apache.eventmesh.api.storage;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.common.wire.EventMeshFrame;

import java.util.List;

/**
 * Optional capability for storage plugins that support RocketMQ 5.x <b>Lite Topic</b> (RIP-83): a
 * secondary message container under a parent topic, where {@code (parentTopic, liteTopic)} uniquely
 * identifies a storage container. Only plugins that implement this interface expose lite-topic ops;
 * callers gate with {@code storage instanceof LiteTopicCapable}.
 *
 * <p>This is a capability marker, NOT an SPI — it is intentionally separate from
 * {@link MeshStoragePlugin} so the shared "MQ has no semantics" WAL contract stays untouched for
 * plugins (4.9, kafka, standalone) that do not support lite topics. Mirrors the existing precedent
 * of concrete-class ops discovered via {@code instanceof} (e.g. {@code createTopic} on the 4.9
 * remoting plugin) and the optional {@code Admin}/{@code TopicNameHelper} SPI pattern.</p>
 */
public interface LiteTopicCapable {

    /**
     * Declare a lite topic under a parent. In RocketMQ 5.x lite sub-topics auto-materialize on first
     * send, so this is best-effort (typically a GET_LITE_TOPIC_INFO probe / no-op). Implementations
     * may simply return.
     */
    void createLiteTopic(String parentTopic, String liteTopic) throws Exception;

    /**
     * Declare a lite topic under a parent and (re)create the parent with the given queue count. The
     * queue count controls sharding: {@code sendLite} round-robins across parent queues and
     * {@code pullLite} drains them in index order, so cross-queue send order is lost. Use
     * {@code queueCount=1} when the caller needs strict in-order delivery (e.g. a streaming-call
     * response channel); the default 2-arg form keeps the storage default (4). Idempotent.
     */
    default void createLiteTopic(String parentTopic, String liteTopic, int queueCount) throws Exception {
        createLiteTopic(parentTopic, liteTopic);
    }

    /**
     * Publish one {@link EventMeshFrame} to a lite topic. The broker routes it into the lite topic's
     * LMQ consume queue under the parent; the frame's encoded bytes are the stored message body.
     */
    void sendLite(String parentTopic, String liteTopic, EventMeshFrame frame, SendCallback callback) throws Exception;

    /**
     * Pull a batch of {@link EventMeshFrame}s from a lite topic (5.x pop semantics — broker pops from
     * the lite topic this client has subscribed to). Never {@code null}.
     */
    List<EventMeshFrame> pullLite(String parentTopic, String liteTopic, int maxEvents, long timeoutMs);

}
