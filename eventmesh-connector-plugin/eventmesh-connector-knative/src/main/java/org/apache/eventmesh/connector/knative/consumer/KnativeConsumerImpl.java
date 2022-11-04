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

package org.apache.eventmesh.connector.knative.consumer;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.connector.knative.config.ClientConfiguration;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KnativeConsumerImpl implements Consumer {

    private PullConsumerImpl pullConsumer;

    private static final Logger logger = LoggerFactory.getLogger(KnativeConsumerImpl.class);

    @Override
    public synchronized void init(Properties properties) throws Exception {
        // Load parameters from properties file:
        final ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.init();
        properties.put("emUrl", clientConfiguration.emurl);
        properties.put("serviceAddr", clientConfiguration.serviceAddr);

        pullConsumer = new PullConsumerImpl(properties);
    }

    @Override
    public void subscribe(String topic) {
        pullConsumer.subscribe(topic);
    }

    @Override
    public void unsubscribe(String topic) {
        try {
            pullConsumer.unsubscribe(topic);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public void registerEventListener(EventListener listener) {
        pullConsumer.registerEventListener(listener);
    }

    @Override
    public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {
        pullConsumer.updateOffset(cloudEvents, context);
    }

    @Override
    public boolean isStarted() {
        return pullConsumer.isStarted();
    }

    @Override
    public boolean isClosed() {
        return pullConsumer.isClosed();
    }

    @Override
    public void start() {
        pullConsumer.start();
    }

    @Override
    public void shutdown() {
        pullConsumer.shutdown();
    }
}
