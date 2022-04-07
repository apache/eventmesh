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

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingInt;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WeightRandomLoadBalanceSelectorTest {

    private Logger logger = LoggerFactory.getLogger(WeightRandomLoadBalanceSelectorTest.class);

    @Test
    public void testSelect() throws Exception {
        List<Weight<String>> weightList = new ArrayList<>();
        weightList.add(new Weight<>("192.168.0.1", 10));
        weightList.add(new Weight<>("192.168.0.2", 20));
        weightList.add(new Weight<>("192.168.0.3", 40));
        WeightRandomLoadBalanceSelector<String> weightRandomLoadBalanceSelector = new WeightRandomLoadBalanceSelector<>(weightList);
        Assert.assertEquals(LoadBalanceType.WEIGHT_RANDOM, weightRandomLoadBalanceSelector.getType());
        int testRange = 100_000;
        Map<String, Integer> addressToNum = IntStream.range(0, testRange)
                .mapToObj(i -> weightRandomLoadBalanceSelector.select())
                .collect(groupingBy(Function.identity(), summingInt(i -> 1)));

        addressToNum.forEach((key, value) -> {
            logger.info("{}: {}", key, value);
        });
        System.out.printf(addressToNum.toString());
        // the error less than 5%
        Assert.assertTrue(Math.abs(addressToNum.get("192.168.0.3") - addressToNum.get("192.168.0.2") * 2) < testRange / 20);
        Assert.assertTrue(Math.abs(addressToNum.get("192.168.0.3") - addressToNum.get("192.168.0.1") * 4) < testRange / 20);
    }

    @Test
    public void testSameWeightSelect() throws Exception {
        List<Weight<String>> weightList = new ArrayList<>();
        weightList.add(new Weight<>("192.168.0.1", 10));
        weightList.add(new Weight<>("192.168.0.2", 10));
        weightList.add(new Weight<>("192.168.0.3", 10));
        WeightRandomLoadBalanceSelector<String> weightRandomLoadBalanceSelector = new WeightRandomLoadBalanceSelector<>(weightList);
        Assert.assertEquals(LoadBalanceType.WEIGHT_RANDOM, weightRandomLoadBalanceSelector.getType());

        int testRange = 100_000;
        Map<String, Integer> addressToNum = IntStream.range(0, testRange)
                .mapToObj(i -> weightRandomLoadBalanceSelector.select())
                .collect(groupingBy(Function.identity(), summingInt(i -> 1)));

        Field field = WeightRandomLoadBalanceSelector.class.getDeclaredField("sameWeightGroup");
        field.setAccessible(true);
        boolean sameWeightGroup = (boolean) field.get(weightRandomLoadBalanceSelector);
        Assert.assertTrue(sameWeightGroup);

        addressToNum.forEach((key, value) -> {
            logger.info("{}: {}", key, value);
        });
        // the error less than 5%
        Assert.assertTrue(Math.abs(addressToNum.get("192.168.0.3") - addressToNum.get("192.168.0.2")) < testRange / 20);
    }
}
