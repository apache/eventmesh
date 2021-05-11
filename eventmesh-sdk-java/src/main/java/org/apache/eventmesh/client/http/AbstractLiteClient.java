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

package org.apache.eventmesh.client.http;

import org.apache.eventmesh.client.http.conf.LiteClientConfig;
import org.apache.eventmesh.client.http.util.HttpLoadBalanceUtils;
import org.apache.eventmesh.common.loadbalance.LoadBalanceSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractLiteClient {

    public Logger logger = LoggerFactory.getLogger(AbstractLiteClient.class);

    protected LiteClientConfig liteClientConfig;

    protected LoadBalanceSelector<String> eventMeshServerSelector;

    public AbstractLiteClient(LiteClientConfig liteClientConfig) {
        this.liteClientConfig = liteClientConfig;
    }

    public void start() throws Exception {
        eventMeshServerSelector = HttpLoadBalanceUtils.createEventMeshServerLoadBalanceSelector(liteClientConfig);
    }

    public LiteClientConfig getLiteClientConfig() {
        return liteClientConfig;
    }

    public void shutdown() throws Exception {
        logger.info("AbstractLiteClient shutdown");
    }
}
