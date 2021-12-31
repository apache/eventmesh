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

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This selector use the weighted round robin strategy to select from list.
 * If the weight is greater, the probability of being selected is larger.
 *
 * @param <T> Target type
 */
public class WeightRoundRobinLoadBalanceSelector<T> implements LoadBalanceSelector<T> {

    private final Logger logger = LoggerFactory.getLogger(WeightRoundRobinLoadBalanceSelector.class);

    private final List<Weight<T>> clusterGroup;

    private final int totalWeight;

    public WeightRoundRobinLoadBalanceSelector(List<Weight<T>> clusterGroup) {
        int totalWeight = 0;
        for (Weight<T> weight : clusterGroup) {
            totalWeight += weight.getWeight();
        }
        this.clusterGroup = clusterGroup;
        this.totalWeight = totalWeight;
    }


    @Override
    @SuppressWarnings("ConstantConditions")
    public T select() {
        if (CollectionUtils.isEmpty(clusterGroup)) {
            logger.warn("No servers available");
            return null;
        }
        Weight<T> targetWeight = null;
        for (Weight<T> weight : clusterGroup) {
            weight.increaseCurrentWeight();
            if (targetWeight == null || targetWeight.getCurrentWeight().get() < weight.getCurrentWeight().get()) {
                targetWeight = weight;
            }
        }
        targetWeight.decreaseTotal(totalWeight);
        return targetWeight.getTarget();
    }

    @Override
    public LoadBalanceType getType() {
        return LoadBalanceType.WEIGHT_ROUND_ROBIN;
    }
}
