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
 * Session load balancing and instance restart / crash switching, against <b>RocketMQ 4.9.5</b> and a
 * real Nacos MetaStore. The four scenarios live in {@link ClusterFailoverE2ESuite}; this class only
 * pins the backend.
 *
 * <p>Runs through the isolated-classpath task ({@code e2eTest4}), which excludes the 5.x plugin so
 * that RocketMQ 4.9.5 wins the {@code org.apache.rocketmq.*} package split:</p>
 * <pre>
 *   gradle :eventmesh-runtime:e2eTest4 \
 *     --tests "org.apache.eventmesh.runtime.it.ClusterFailoverRocketMQ4E2EIntegrationTest" \
 *     -Dit.nacos=127.0.0.1:8848 -Dit.namesrv=127.0.0.1:9876
 * </pre>
 */
@EnabledIfSystemProperty(named = "it.nacos", matches = ".+")
class ClusterFailoverRocketMQ4E2EIntegrationTest extends ClusterFailoverE2ESuite {

    @Override
    protected ClusterStorageBackend backend() {
        return ClusterStorageBackend.ROCKETMQ4;
    }
}
