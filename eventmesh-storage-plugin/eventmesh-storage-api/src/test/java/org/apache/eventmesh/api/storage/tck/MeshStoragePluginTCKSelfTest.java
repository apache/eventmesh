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

package org.apache.eventmesh.api.storage.tck;

import org.apache.eventmesh.api.storage.InMemoryStoragePlugin;
import org.apache.eventmesh.api.storage.StorageCapabilities;
import org.apache.eventmesh.api.storage.StorageCapabilities.AlignPullOffset;
import org.apache.eventmesh.api.storage.StorageCapabilities.DeferredPopAck;
import org.apache.eventmesh.api.storage.StorageCapabilities.EndOffsetQuery;
import org.apache.eventmesh.api.storage.StorageCapabilities.ExplicitOffsetCommit;
import org.apache.eventmesh.api.storage.StorageCapabilities.LiteTopic;
import org.apache.eventmesh.api.storage.StorageCapabilities.PartitionAssignment;
import org.apache.eventmesh.api.storage.StorageCapabilities.TopicManagement;

import java.util.Set;

/**
 * Self-test for the {@link MeshStoragePluginTCK}: runs the TCK against {@link InMemoryStoragePlugin}
 * which implements <b>all 7</b> capabilities. If the TCK is well-formed, every test passes.
 *
 * <p>This test is the TCK's "canary in the coal mine" — if it fails, the TCK is broken (too
 * strict, wrong signature, or makes an assumption that doesn't hold for a real plugin).</p>
 */
public class MeshStoragePluginTCKSelfTest extends MeshStoragePluginTCK<InMemoryStoragePlugin> {

    @Override
    protected InMemoryStoragePlugin newPlugin() {
        return new InMemoryStoragePlugin();
    }

    @Override
    protected Set<Class<?>> expectedCapabilities() {
        return Set.of(
            TopicManagement.class,
            PartitionAssignment.class,
            ExplicitOffsetCommit.class,
            EndOffsetQuery.class,
            AlignPullOffset.class,
            DeferredPopAck.class,
            LiteTopic.class
        );
    }

    /** Convenience to also touch the {@link StorageCapabilities} outer interface for static-imports. */
    @SuppressWarnings("unused")
    private static final Class<?> OUTER = StorageCapabilities.class;
}
