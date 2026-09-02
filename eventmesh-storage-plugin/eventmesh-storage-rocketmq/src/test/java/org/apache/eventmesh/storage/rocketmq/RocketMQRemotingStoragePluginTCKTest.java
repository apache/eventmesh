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

package org.apache.eventmesh.storage.rocketmq;

import org.apache.eventmesh.api.storage.StorageCapabilities;
import org.apache.eventmesh.api.storage.StorageCapabilities.AlignPullOffset;
import org.apache.eventmesh.api.storage.StorageCapabilities.ExplicitOffsetCommit;
import org.apache.eventmesh.api.storage.StorageCapabilities.PartitionAssignment;
import org.apache.eventmesh.api.storage.StorageCapabilities.TopicManagement;
import org.apache.eventmesh.api.storage.tck.MeshStoragePluginTCK;
import org.apache.eventmesh.storage.rocketmq.storage.RocketMQRemotingStoragePlugin;

import java.util.Set;

/**
 * TCK wiring for the RocketMQ 4.x storage plugin. Asserts RocketMQ 4.x declares the
 * capabilities it actually supports: 3 universal + AlignPullOffset. Notably does NOT
 * declare EndOffsetQuery (RocketMQ 4.x has no equivalent of Kafka's high-watermark API).
 */
public class RocketMQRemotingStoragePluginTCKTest extends MeshStoragePluginTCK<RocketMQRemotingStoragePlugin> {

    @Override
    protected RocketMQRemotingStoragePlugin newPlugin() {
        return new RocketMQRemotingStoragePlugin();
    }

    @Override
    protected Set<Class<?>> expectedCapabilities() {
        return Set.of(
            TopicManagement.class,
            PartitionAssignment.class,
            ExplicitOffsetCommit.class,
            AlignPullOffset.class
        );
    }

    @SuppressWarnings("unused")
    private static final Class<?> OUTER = StorageCapabilities.class;
}
