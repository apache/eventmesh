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

package org.apache.eventmesh.client.http.conf;

import org.apache.eventmesh.common.loadbalance.LoadBalanceType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EventMeshHttpClientConfig {

    /**
     * The event server address list
     * <p>
     * If it's a cluster, please use ; to split, and the address format is related to loadBalanceType.
     * <p>
     * E.g.
     * <p>If you use Random strategy, the format like: 127.0.0.1:10105;127.0.0.2:10105
     * <p>If you use weighted round robin or weighted random strategy, the format like: 127.0.0.1:10105:1;127.0.0.2:10105:2
     */
    @Builder.Default
    private transient String liteEventMeshAddr = "localhost:10105";

    @Builder.Default
    private transient LoadBalanceType loadBalanceType = LoadBalanceType.RANDOM;

    @Builder.Default
    private transient int consumeThreadCore = 2;

    @Builder.Default
    private transient int consumeThreadMax = 5;

    @Builder.Default
    private transient String env = "";

    @Builder.Default
    private transient String consumerGroup = "DefaultConsumerGroup";

    @Builder.Default
    private transient String producerGroup = "DefaultProducerGroup";

    @Builder.Default
    private transient String idc = "";

    @Builder.Default
    private transient String ip = "localhost";

    @Builder.Default
    private transient String pid = "";

    @Builder.Default
    private transient String sys = "";

    @Builder.Default
    private transient String userName = "";

    @Builder.Default
    private transient String password = "";

    @Builder.Default
    private transient boolean useTls = false;

    @Builder.Default
    private transient String sslClientProtocol = "TLSv1.2";

    @Builder.Default
    private transient int maxConnectionPoolSize = 30;

    @Builder.Default
    private transient int connectionIdleTimeSeconds = 10;

}
