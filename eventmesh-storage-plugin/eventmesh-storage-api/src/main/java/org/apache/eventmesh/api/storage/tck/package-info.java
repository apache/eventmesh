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

/**
 * Marker package for the MeshStoragePlugin Test Compatibility Kit (TCK).
 *
 * <p>Every storage plugin MUST pass {@link MeshStoragePluginTCK} for the capabilities it
 * declares. The TCK is the single source of truth for the storage SPI contract: any new
 * behavior added to {@link org.apache.eventmesh.api.storage.MeshStoragePlugin} MUST ship with
 * a corresponding TCK test here, and any backend that overrides / implements the behavior
 * MUST extend the TCK.</p>
 *
 * <h2>How a backend wires in</h2>
 * <pre>{@code
 * class KafkaMeshStoragePluginTCKTest
 *         extends MeshStoragePluginTCK<KafkaMeshStoragePlugin> {
 *     &#64;Override
 *     protected KafkaMeshStoragePlugin newPlugin() { return new KafkaMeshStoragePlugin(); }
 *
 *     &#64;Override
 *     protected Set<Class<?>> expectedCapabilities() {
 *         return Set.of(
 *             TopicManagement.class,
 *             PartitionAssignment.class,
 *             ExplicitOffsetCommit.class,
 *             EndOffsetQuery.class,
 *             AlignPullOffset.class
 *         );
 *     }
 * }
 * }</pre>
 */
package org.apache.eventmesh.api.storage.tck;
