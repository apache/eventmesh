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

package com.webank.runtime.core.plugin;

import com.webank.runtime.configuration.CommonConfiguration;
import com.webank.runtime.configuration.PropInit;
import com.webank.runtime.core.plugin.impl.DeFiBusConsumerImpl;
import com.webank.runtime.core.plugin.impl.MeshMQConsumer;
import com.webank.runtime.patch.ProxyConsumeConcurrentlyContext;
import connector.rocketmq.consumer.PushConsumerImpl;
import io.openmessaging.*;
import io.openmessaging.consumer.MessageListener;
import io.openmessaging.consumer.PushConsumer;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ServiceLoader;

public class MQConsumerWrapper extends MQWrapper {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    protected MessagingAccessPoint messagingAccessPoint;

    public String namesrv = "oms:rocketmq://IP1:9876,IP2:9876/namespace";

    protected PushConsumer meshMQConsumer;

    public void setInstanceName(String instanceName) {

//        meshMQConsumer.setInstanceName(instanceName);
    }

    public void subscribe(String topic) throws Exception {

//        meshMQConsumer.attachQueue(topic, ((meshMQConsumer.getClass().getName())meshMQConsumer).get);
    }

    public void unsubscribe(String topic) throws Exception {
//        meshMQConsumer.detachQueue(topic);
    }

    public boolean isPause() {
        return meshMQConsumer.isSuspended();
    }

    public void pause() {
        meshMQConsumer.suspend();
    }

    public synchronized void init(boolean isBroadcast, CommonConfiguration commonConfiguration,
                                  String consumerGroup) throws Exception {

        PropInit driverProp = getDriverProp();
        KeyValue prop = driverProp.initProp();
        prop.put(OMSBuiltinKeys.CONSUMER_ID, consumerGroup);
        messagingAccessPoint = OMS.getMessagingAccessPoint(namesrv, prop);
        meshMQConsumer = messagingAccessPoint.createPushConsumer(prop);
//        meshMQConsumer = getMeshMQConsumer();
//        meshMQConsumer.init(isBroadcast, commonConfiguration, consumerGroup);
        inited.compareAndSet(false, true);
    }

    private PropInit getDriverProp() {
        ServiceLoader<PropInit> propInitServiceLoader = ServiceLoader.load(PropInit.class);
        if (propInitServiceLoader.iterator().hasNext()){
            return propInitServiceLoader.iterator().next();
        }
        return null;
    }

//    private MeshMQConsumer getMeshMQConsumer() {
//        ServiceLoader<MeshMQConsumer> meshMQConsumerServiceLoader = ServiceLoader.load(MeshMQConsumer.class);
//
//        if (meshMQConsumerServiceLoader.iterator().hasNext()){
//            return  meshMQConsumerServiceLoader.iterator().next();
//        }
//        return new DeFiBusConsumerImpl();
//    }

    public synchronized void start() throws Exception {

        meshMQConsumer.startup();
        started.compareAndSet(false, true);
        return;
    }

    public synchronized void shutdown() throws Exception {

        meshMQConsumer.shutdown();
        inited.compareAndSet(false, true);
        started.compareAndSet(false, true);
    }

    public void registerMessageListener(MessageListenerConcurrently messageListenerConcurrently) {
//        meshMQConsumer.registerMessageListener(messageListenerConcurrently);
    }

    public void updateOffset(List<MessageExt> msgs, ProxyConsumeConcurrentlyContext proxyConsumeConcurrentlyContext) {

//        meshMQConsumer.updateOffset(msgs, proxyConsumeConcurrentlyContext);
        return;
    }
}
