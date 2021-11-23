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
    private String liteEventMeshAddr = "127.0.0.1:10105";

    @Builder.Default
    private LoadBalanceType loadBalanceType = LoadBalanceType.RANDOM;

    @Builder.Default
    private int consumeThreadCore = 2;

    @Builder.Default
    private int consumeThreadMax = 5;

    @Builder.Default
    private String env = "";

    @Builder.Default
    private String consumerGroup = "DefaultConsumerGroup";

    @Builder.Default
    private String producerGroup = "DefaultProducerGroup";

    @Builder.Default
    private String idc = "";

    @Builder.Default
    private String ip = "127.0.0.1";

    @Builder.Default
    private String pid = "";

    @Builder.Default
    private String sys = "";

    @Builder.Default
    private String userName = "";

    @Builder.Default
    private String password = "";

    @Builder.Default
    private boolean useTls = false;

}
