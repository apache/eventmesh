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

package cn.webank.defibus.client;

import cn.webank.defibus.client.impl.factory.DeFiBusClientInstance;
import cn.webank.defibus.common.util.ReflectUtil;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.client.impl.MQClientManager;
import org.apache.rocketmq.remoting.RPCHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeFiBusClientManager {
    private static DeFiBusClientManager instance = new DeFiBusClientManager();
    private AtomicInteger factoryIndexGenerator = new AtomicInteger();
    private ConcurrentHashMap<String/* clientId */, DeFiBusClientInstance> factoryTable;

    public static final Logger LOGGER = LoggerFactory.getLogger(DeFiBusClientManager.class);

    @SuppressWarnings("unchecked")
    private DeFiBusClientManager() {
        try {
            //factoryTable, shared by all producer and consumer
            //same clientId will return the same MQClientInstance
            //In order to set our own deFiclient instance to all producer and consumer, need to get the pointer of this table
            factoryTable = (ConcurrentHashMap<String/* clientId */, DeFiBusClientInstance>) ReflectUtil.getSimpleProperty(MQClientManager.class,
                MQClientManager.getInstance(), "factoryTable");
        } catch (Exception e) {
            LOGGER.warn("failed to initialize factory in mqclient manager.", e);
        }
    }

    public static DeFiBusClientManager getInstance() {
        return instance;
    }

    public synchronized DeFiBusClientInstance getAndCreateDeFiBusClientInstance(final ClientConfig clientConfig,
        RPCHook rpcHook) {

        String clientId = clientConfig.buildMQClientId();
        DeFiBusClientInstance instance = this.factoryTable.get(clientId);
        if (null == instance) {
            instance =
                new DeFiBusClientInstance(clientConfig.cloneClientConfig(),
                    this.factoryIndexGenerator.getAndIncrement(), clientId, rpcHook);
            DeFiBusClientInstance prev = this.factoryTable.putIfAbsent(clientId, instance);
            if (prev != null) {
                instance = prev;
                LOGGER.warn("Returned Previous MQClientInstance for clientId:[{}]", clientId);
            } else {
                LOGGER.info("new instance activate. " + clientId);
            }
        }
        return instance;
    }
}
