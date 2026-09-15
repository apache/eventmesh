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

package org.apache.eventmesh.storage.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.api.storage.StorageCapabilities;
import org.apache.eventmesh.api.storage.tck.MeshStoragePluginTCK;
import org.apache.eventmesh.common.wire.EventMeshFrame;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * TCK run for {@link MemoryMeshStoragePlugin}. Extends the abstract
 * {@link MeshStoragePluginTCK} and declares all 7 capabilities.
 */
public class MemoryMeshStoragePluginTCKTest extends MeshStoragePluginTCK<MemoryMeshStoragePlugin> {

    @Override
    protected MemoryMeshStoragePlugin newPlugin() {
        return new MemoryMeshStoragePlugin();
    }

    @Override
    protected Set<Class<?>> expectedCapabilities() {
        return Set.of(
            StorageCapabilities.TopicManagement.class,
            StorageCapabilities.PartitionAssignment.class,
            StorageCapabilities.ExplicitOffsetCommit.class,
            StorageCapabilities.EndOffsetQuery.class,
            StorageCapabilities.AlignPullOffset.class,
            StorageCapabilities.DeferredPopAck.class,
            StorageCapabilities.LiteTopic.class
        );
    }

    @Test
    void sendThenPollRoundTrips() {
        plugin.init(new Properties());
        plugin.createTopic("round-trip", 1);
        EventMeshFrame frame = EventMeshFrame.event(Map.of(), "hello".getBytes());
        plugin.send("round-trip", frame, null);
        List<EventMeshFrame> polled = plugin.poll("round-trip", 0, 0L, 10, 0L);
        assertEquals(1, polled.size());
    }

    @Test
    void commitOffsetRoundTrips() {
        plugin.init(new Properties());
        plugin.createTopic("offsets", 1);
        plugin.commitOffset("offsets", 0, 42L);
        assertTrue(plugin.endOffset("offsets", 0) >= 0L);
    }

    @Test
    void endOffsetUnknownTopicIsMinusOne() {
        plugin.init(new Properties());
        assertEquals(-1L, plugin.endOffset("no-such-topic", 0));
    }

    @Test
    void alignPullOffsetRejectsNegativeAckOffset() {
        plugin.init(new Properties());
        assertFalse(plugin.alignPullOffset("any", 0, -1L));
    }

    @Test
    void ackPulledMessageUnknownKeyIsFalse() {
        plugin.init(new Properties());
        assertFalse(plugin.ackPulledMessage("any", "no-such-ack"));
    }
}
