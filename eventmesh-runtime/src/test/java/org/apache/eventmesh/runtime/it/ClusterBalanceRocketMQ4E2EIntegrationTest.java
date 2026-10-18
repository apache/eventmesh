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
 * Cluster balance, takeover and abnormal-case e2e, against <b>RocketMQ 4.9.5</b> and a real Nacos
 * MetaStore. The eleven scenarios live in {@link ClusterBalanceE2ESuite}; this class only pins the
 * backend.
 *
 * <p>RocketMQ 4.x queues are addressed as partitions and assigned client-side, exactly like Kafka
 * partitions, so the ownership table is populated and asserted here:</p>
 * <pre>
 *   gradle :eventmesh-runtime:e2eTest4 \
 *     --tests "org.apache.eventmesh.runtime.it.ClusterBalanceRocketMQ4E2EIntegrationTest" \
 *     -Dit.nacos=127.0.0.1:8848 -Dit.namesrv=127.0.0.1:9876
 * </pre>
 */
@EnabledIfSystemProperty(named = "it.nacos", matches = ".+")
class ClusterBalanceRocketMQ4E2EIntegrationTest extends ClusterBalanceE2ESuite {

    @Override
    protected ClusterStorageBackend backend() {
        return ClusterStorageBackend.ROCKETMQ4;
    }
}
