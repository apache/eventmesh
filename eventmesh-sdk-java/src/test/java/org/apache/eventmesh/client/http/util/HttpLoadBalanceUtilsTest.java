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

import org.apache.eventmesh.client.http.conf.LiteClientConfig;
import org.apache.eventmesh.common.EventMeshException;
import org.apache.eventmesh.common.loadbalance.LoadBalanceSelector;
import org.apache.eventmesh.common.loadbalance.LoadBalanceType;
import org.junit.Assert;
import org.junit.Test;

public class HttpLoadBalanceUtilsTest {

    @Test
    public void testCreateRandomSelector() throws EventMeshException {
        LiteClientConfig liteClientConfig = new LiteClientConfig()
                .setLiteEventMeshAddr("127.0.0.1:1001;127.0.0.2:1002");
        LoadBalanceSelector<String> randomSelector = HttpLoadBalanceUtils
                .createEventMeshServerLoadBalanceSelector(liteClientConfig);
        Assert.assertEquals(LoadBalanceType.RANDOM, randomSelector.getType());
    }

    @Test
    public void testCreateWeightRoundRobinSelector() throws EventMeshException {
        LiteClientConfig liteClientConfig = new LiteClientConfig()
                .setLiteEventMeshAddr("127.0.0.1:1001:1;127.0.0.2:1001:2")
                .setLoadBalanceType(LoadBalanceType.WEIGHT_ROUND_ROBIN);
        LoadBalanceSelector<String> weightRoundRobinSelector = HttpLoadBalanceUtils
                .createEventMeshServerLoadBalanceSelector(liteClientConfig);
        Assert.assertEquals(LoadBalanceType.WEIGHT_ROUND_ROBIN, weightRoundRobinSelector.getType());
    }
}