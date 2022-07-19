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

package org.apache.eventmesh.client.common.strategy.impl;

import org.apache.eventmesh.client.common.strategy.ClientLoadBalanceStrategy;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomUtils;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RandomLoadBalance<T> implements ClientLoadBalanceStrategy<T> {

    private final Logger logger = LoggerFactory.getLogger(RandomLoadBalance.class);


    @Override
    public T select(List<T> serverList) {
        if (CollectionUtils.isEmpty(serverList)) {
            this.logger.warn("[RandomLoadBalance.select] No servers available");
            return null;
        }

        return serverList.get(RandomUtils.nextInt(0, serverList.size()));
    }
}
