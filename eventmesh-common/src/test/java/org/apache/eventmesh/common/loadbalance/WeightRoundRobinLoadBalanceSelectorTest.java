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

package org.apache.eventmesh.common.loadbalance;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import lombok.extern.slf4j.Slf4j;



@Slf4j
public class WeightRoundRobinLoadBalanceSelectorTest {

    private WeightRoundRobinLoadBalanceSelector<String> weightRoundRobinLoadBalanceSelector;

    @Before
    public void before() {
        List<Weight<String>> weightList = Arrays.asList(
                new Weight<>("A", 10),
                new Weight<>("B", 20),
                new Weight<>("C", 30)
        );
        weightRoundRobinLoadBalanceSelector = new WeightRoundRobinLoadBalanceSelector<>(weightList);
    }

    @Test
    public void testSelect() {
        Map<String, Integer> addressToNum = new HashMap<>(3);
        for (int i = 0; i < 100_000; i++) {
            String select = weightRoundRobinLoadBalanceSelector.select();
            addressToNum.put(select, addressToNum.getOrDefault(select, 0) + 1);
        }
        addressToNum.forEach((key, value) -> {
            log.info("{}: {}", key, value);
        });
        Assert.assertEquals(0.5, (double) addressToNum.get("A") / addressToNum.get("B"), 0.0001);
        Assert.assertEquals(
                (double) 1 / 3, (double) addressToNum.get("A") / addressToNum.get("C"), 0.0001);
        Assert.assertEquals(
                (double) 2 / 3, (double) addressToNum.get("B") / addressToNum.get("C"), 0.0001);
    }

    @Test
    public void testGetType() {
        Assert.assertEquals(LoadBalanceType.WEIGHT_ROUND_ROBIN, weightRoundRobinLoadBalanceSelector.getType());
    }
}
