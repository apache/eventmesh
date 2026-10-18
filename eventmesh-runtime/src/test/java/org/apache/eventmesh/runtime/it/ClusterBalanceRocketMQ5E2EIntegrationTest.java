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

package org.apache.eventmesh.runtime.it;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Cluster balance, takeover and abnormal-case e2e, against <b>RocketMQ 5.5.0</b> (POP mode) and a
 * real Nacos MetaStore. The eleven scenarios live in {@link ClusterBalanceE2ESuite}; this class only
 * pins the backend.
 *
 * <p>On this backend the broker assigns messages ({@code partitionCount == -1}), so EventMesh keeps
 * the topic in poll-all mode by design. The suite asserts that contract instead of a partition
 * plan — see {@code ClusterBalanceE2ESuite}'s javadoc.</p>
 * <pre>
 *   gradle :eventmesh-runtime:e2eTest5x \
 *     --tests "org.apache.eventmesh.runtime.it.ClusterBalanceRocketMQ5E2EIntegrationTest" \
 *     -Dit.nacos=127.0.0.1:8848 -Dit.namesrv5=127.0.0.1:19876
 * </pre>
 */
@EnabledIfSystemProperty(named = "it.nacos", matches = ".+")
class ClusterBalanceRocketMQ5E2EIntegrationTest extends ClusterBalanceE2ESuite {

    @Override
    protected ClusterStorageBackend backend() {
        return ClusterStorageBackend.ROCKETMQ5;
    }
}
