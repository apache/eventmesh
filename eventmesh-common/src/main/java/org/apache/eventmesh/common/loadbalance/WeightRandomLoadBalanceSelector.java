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
import org.apache.commons.lang3.RandomUtils;
import org.apache.eventmesh.common.exception.EventMeshException;

import java.util.List;

/**
 * This selector use the weighted random strategy to select from list.
 * If all the weights are same, it will randomly select one from list.
 * If the weights are different, it will select one by using RandomUtils.nextInt(0, w0 + w1 ... + wn)
 *
 * @param <T> Target type
 */
public class WeightRandomLoadBalanceSelector<T> implements LoadBalanceSelector<T> {

    private final List<Weight<T>> clusterGroup;

    private final int totalWeight;

    private boolean sameWeightGroup = true;

    public WeightRandomLoadBalanceSelector(List<Weight<T>> clusterGroup) throws EventMeshException {
        if (CollectionUtils.isEmpty(clusterGroup)) {
            throw new EventMeshException("clusterGroup can not be empty");
        }
        int totalWeight = 0;
        int firstWeight = clusterGroup.get(0).getWeight();
        for (Weight<T> weight : clusterGroup) {
            totalWeight += weight.getWeight();
            if (sameWeightGroup && firstWeight != weight.getWeight()) {
                sameWeightGroup = false;
            }
        }
        this.clusterGroup = clusterGroup;
        this.totalWeight = totalWeight;
    }

    @Override
    public T select() {
        if (!sameWeightGroup) {
            int targetWeight = RandomUtils.nextInt(0, totalWeight);
            for (Weight<T> weight : clusterGroup) {
                targetWeight -= weight.getWeight();
                if (targetWeight < 0) {
                    return weight.getTarget();
                }
            }
        }
        int length = clusterGroup.size();
        return clusterGroup.get(RandomUtils.nextInt(0, length)).getTarget();
    }

    @Override
    public LoadBalanceType getType() {
        return LoadBalanceType.WEIGHT_RANDOM;
    }
}
