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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WeightRoundRobinLoadBalanceSelectorTest {

    private WeightRoundRobinLoadBalanceSelector<String> weightRoundRobinLoadBalanceSelector;

    @BeforeEach
    public void before() {
        List<Weight<String>> weightList = new ArrayList<>();
        weightList.add(new Weight<>("A", 10));
        weightList.add(new Weight<>("B", 20));
        weightList.add(new Weight<>("C", 30));
        weightRoundRobinLoadBalanceSelector = new WeightRoundRobinLoadBalanceSelector<>(weightList);
    }

    @Test
    public void testSelect() {
        Map<String, Integer> addressToNum = new HashMap<>();
        for (int i = 0; i < 100_000; i++) {
            String select = weightRoundRobinLoadBalanceSelector.select();
            addressToNum.put(select, addressToNum.getOrDefault(select, 0) + 1);
        }
        addressToNum.forEach((key, value) -> {
            log.info("{}: {}", key, value);
        });
        Assertions.assertTrue(addressToNum.get("B") > addressToNum.get("A"));
        Assertions.assertTrue(addressToNum.get("C") > addressToNum.get("B"));
    }

    @Test
    public void testGetType() {
        Assertions.assertEquals(LoadBalanceType.WEIGHT_ROUND_ROBIN, weightRoundRobinLoadBalanceSelector.getType());
    }
}
