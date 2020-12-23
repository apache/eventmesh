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

package com.webank.eventmesh.runtime.core.plugin;

import com.webank.eventmesh.api.AbstractContext;
import com.webank.eventmesh.api.consumer.MeshMQPushConsumer;
import io.openmessaging.KeyValue;
import io.openmessaging.Message;
import io.openmessaging.consumer.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.ServiceLoader;

public class MQConsumerWrapper extends MQWrapper {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    protected MeshMQPushConsumer meshMQPushConsumer;

    public void subscribe(String topic, MessageListener listener) throws Exception {
        meshMQPushConsumer.subscribe(topic, listener);
    }

    public void unsubscribe(String topic) throws Exception {
        meshMQPushConsumer.unsubscribe(topic);
    }

    public boolean isPause() {
        return meshMQPushConsumer.isPause();
    }

    public void pause() {
        meshMQPushConsumer.pause();
    }

    public synchronized void init(KeyValue keyValue) throws Exception {
        meshMQPushConsumer = getMeshMQPushConsumer();
        if (meshMQPushConsumer == null){
            logger.error("can't load the meshMQPushConsumer plugin, please check.");
            throw new RuntimeException("doesn't load the meshMQPushConsumer plugin, please check.");
        }

        meshMQPushConsumer.init(keyValue);
        inited.compareAndSet(false, true);
    }

    private MeshMQPushConsumer getMeshMQPushConsumer() {
        ServiceLoader<MeshMQPushConsumer> meshMQPushConsumerServiceLoader = ServiceLoader.load(MeshMQPushConsumer.class);
        if (meshMQPushConsumerServiceLoader.iterator().hasNext()){
            return meshMQPushConsumerServiceLoader.iterator().next();
        }
        return null;
    }

    public synchronized void start() throws Exception {
        meshMQPushConsumer.start();
        started.compareAndSet(false, true);
    }

    public synchronized void shutdown() throws Exception {
        meshMQPushConsumer.shutdown();
        inited.compareAndSet(false, true);
        started.compareAndSet(false, true);
    }

//    public void registerMessageListener(MessageListenerConcurrently messageListenerConcurrently) {
//        meshMQPushConsumer.registerMessageListener(messageListenerConcurrently);
//    }

    public void updateOffset(List<Message> msgs, AbstractContext proxyConsumeConcurrentlyContext) {
        meshMQPushConsumer.updateOffset(msgs, proxyConsumeConcurrentlyContext);
    }

    public AbstractContext getContext(){
        return meshMQPushConsumer.getContext();
    }
}
