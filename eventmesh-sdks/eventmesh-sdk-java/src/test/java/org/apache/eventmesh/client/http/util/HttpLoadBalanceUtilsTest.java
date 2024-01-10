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

package org.apache.eventmesh.client.http.util;

import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.loadbalance.LoadBalanceSelector;
import org.apache.eventmesh.common.loadbalance.LoadBalanceType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HttpLoadBalanceUtilsTest {

    @Test
    public void testCreateRandomSelector() throws EventMeshException {
        EventMeshHttpClientConfig eventMeshHttpClientConfig = EventMeshHttpClientConfig.builder()
            .liteEventMeshAddr("127.0.0.1:1001;127.0.0.2:1002")
            .build();
        LoadBalanceSelector<String> randomSelector = HttpLoadBalanceUtils
            .createEventMeshServerLoadBalanceSelector(eventMeshHttpClientConfig);
        Assertions.assertEquals(LoadBalanceType.RANDOM, randomSelector.getType());
    }

    @Test
    public void testCreateWeightRoundRobinSelector() throws EventMeshException {
        EventMeshHttpClientConfig eventMeshHttpClientConfig = EventMeshHttpClientConfig.builder()
            .liteEventMeshAddr("127.0.0.1:1001:1;127.0.0.2:1001:2")
            .loadBalanceType(LoadBalanceType.WEIGHT_ROUND_ROBIN).build();
        LoadBalanceSelector<String> weightRoundRobinSelector = HttpLoadBalanceUtils
            .createEventMeshServerLoadBalanceSelector(eventMeshHttpClientConfig);
        Assertions.assertEquals(LoadBalanceType.WEIGHT_ROUND_ROBIN, weightRoundRobinSelector.getType());
    }

    @Test
    public void testCreateWeightRandomSelector() throws EventMeshException {
        EventMeshHttpClientConfig eventMeshHttpClientConfig = EventMeshHttpClientConfig.builder()
            .liteEventMeshAddr("127.0.0.1:1001:1;127.0.0.2:1001:2")
            .loadBalanceType(LoadBalanceType.WEIGHT_RANDOM).build();
        LoadBalanceSelector<String> weightRoundRobinSelector = HttpLoadBalanceUtils
            .createEventMeshServerLoadBalanceSelector(eventMeshHttpClientConfig);
        Assertions.assertEquals(LoadBalanceType.WEIGHT_RANDOM, weightRoundRobinSelector.getType());
    }
}
