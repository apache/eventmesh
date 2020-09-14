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

package com.webank.emesher.core.plugin;

import com.webank.emesher.configuration.CommonConfiguration;
import com.webank.emesher.core.plugin.impl.DeFiMeshMQConsumerImpl;
import com.webank.emesher.core.plugin.impl.RMQMeshMQConsumerImpl;
import com.webank.emesher.patch.ProxyConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MQConsumerWrapper extends MQWrapper {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    protected RMQMeshMQConsumerImpl rmqConsumerImpl;

    protected DeFiMeshMQConsumerImpl deFiConsumerImpl;

    public void setInstanceName(String instanceName) {
        if (useRocket) {
            rmqConsumerImpl.setInstanceName(instanceName);
            return;
        }

        deFiConsumerImpl.setInstanceName(instanceName);
    }

    public void subscribe(String topic) throws Exception {
        if (useRocket) {
            rmqConsumerImpl.subscribe(topic);
            return;
        }
        deFiConsumerImpl.subscribe(topic);
    }

    public void unsubscribe(String topic) throws Exception {
        if (useRocket) {
            rmqConsumerImpl.unsubscribe(topic);
            return;
        }
        deFiConsumerImpl.unsubscribe(topic);
    }

    public boolean isPause() {
        if (useRocket) {
            return rmqConsumerImpl.isPause();
        }

        return deFiConsumerImpl.isPause();
    }

    public void pause() {
        if (useRocket) {
            rmqConsumerImpl.pause();
            return;
        }

        deFiConsumerImpl.pause();
    }

    public synchronized void init(boolean isBroadcast, CommonConfiguration commonConfiguration,
                                  String consumerGroup) throws Exception {
        if (useRocket) {
            rmqConsumerImpl = new RMQMeshMQConsumerImpl();
            rmqConsumerImpl.init(isBroadcast, commonConfiguration, consumerGroup);
            inited.compareAndSet(false, true);
            return;
        }

        deFiConsumerImpl = new DeFiMeshMQConsumerImpl();
        deFiConsumerImpl.init(isBroadcast, commonConfiguration, consumerGroup);
        inited.compareAndSet(false, true);
    }

    public synchronized void start() throws Exception {
        if (useRocket) {
            rmqConsumerImpl.start();
            started.compareAndSet(false, true);
            return;
        }

        deFiConsumerImpl.start();
        started.compareAndSet(false, true);
        return;
    }

    public synchronized void shutdown() throws Exception {
        if (useRocket) {
            rmqConsumerImpl.shutdown();
            inited.compareAndSet(false, true);
            started.compareAndSet(false, true);
            return;
        }

        deFiConsumerImpl.shutdown();
        inited.compareAndSet(false, true);
        started.compareAndSet(false, true);
    }

    public void registerMessageListener(MessageListenerConcurrently messageListenerConcurrently) {
        if (useRocket) {
            rmqConsumerImpl.registerMessageListener(messageListenerConcurrently);
            return;
        }
        deFiConsumerImpl.registerMessageListener(messageListenerConcurrently);
    }

    public void updateOffset(List<MessageExt> msgs, ProxyConsumeConcurrentlyContext proxyConsumeConcurrentlyContext) {
        if (useRocket) {
            rmqConsumerImpl.updateOffset(msgs, proxyConsumeConcurrentlyContext);
            return;
        }

        deFiConsumerImpl.updateOffset(msgs, proxyConsumeConcurrentlyContext);
        return;
    }
}
