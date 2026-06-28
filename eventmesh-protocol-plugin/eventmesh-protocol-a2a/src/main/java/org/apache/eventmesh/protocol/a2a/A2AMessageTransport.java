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

package org.apache.eventmesh.protocol.a2a;

import io.cloudevents.CloudEvent;

/**
 * Abstracts the message transport layer for A2A communication.
 * Implementations can use EventMesh producer/consumer, in-memory, or any other transport.
 */
public interface A2AMessageTransport {

    /**
     * Publishes a CloudEvent to the given topic.
     */
    void publish(String topic, CloudEvent event) throws Exception;

    /**
     * Subscribes to a topic pattern. Returns a subscription ID.
     * The callback is invoked for each matching message.
     */
    String subscribe(String topicPattern, MessageCallback callback) throws Exception;

    /**
     * Unsubscribes by subscription ID.
     */
    void unsubscribe(String subscriptionId) throws Exception;

    @FunctionalInterface
    interface MessageCallback {
        void onMessage(String topic, CloudEvent event);
    }
}
