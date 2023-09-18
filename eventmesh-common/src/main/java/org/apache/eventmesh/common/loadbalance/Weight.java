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

import java.util.concurrent.atomic.AtomicInteger;

public class Weight<T> {

    private T target;

    private final int value;

    private final AtomicInteger currentWeight;

    public Weight(T target, int value) {
        this.target = target;
        this.value = value;
        this.currentWeight = new AtomicInteger(0);
    }

    public void decreaseTotal(int total) {
        currentWeight.addAndGet(-1 * total);
    }

    public void increaseCurrentWeight() {
        currentWeight.addAndGet(value);
    }

    public T getTarget() {
        return target;
    }

    public void setTarget(T target) {
        this.target = target;
    }

    public int getValue() {
        return value;
    }

    public AtomicInteger getCurrentWeight() {
        return currentWeight;
    }

    @Override
    public String toString() {
        return "Wight{"
                + "target=" + target
                + ", value=" + value
                + ", currentWeight=" + currentWeight
                + '}';
    }
}
