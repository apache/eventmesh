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

package org.apache.eventmesh.storage.rabbitmq.consumer;

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.storage.rabbitmq.cloudevent.RabbitmqCloudEvent;
import org.apache.eventmesh.storage.rabbitmq.config.ConfigurationHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RabbitmqConsumerHandler implements Runnable {


    private final Channel channel;
    private final ConfigurationHolder configurationHolder;
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private EventListener eventListener;

    public RabbitmqConsumerHandler(Channel channel, ConfigurationHolder configurationHolder) {
        this.channel = channel;
        this.configurationHolder = configurationHolder;
    }

    @Override
    public void run() {
        while (!stop.get()) {
            try {
                GetResponse response = channel.basicGet(configurationHolder.getQueueName(), configurationHolder.isAutoAck());
                if (response != null) {
                    RabbitmqCloudEvent rabbitmqCloudEvent = RabbitmqCloudEvent.getFromByteArray(response.getBody());
                    CloudEvent cloudEvent = rabbitmqCloudEvent.convertToCloudEvent();
                    final EventMeshAsyncConsumeContext consumeContext = new EventMeshAsyncConsumeContext() {
                        @Override
                        public void commit(EventMeshAction action) {
                            log.info("[RabbitmqConsumerHandler] Rabbitmq consumer context commit.");
                        }
                    };
                    if (eventListener != null) {
                        eventListener.consume(cloudEvent, consumeContext);
                    }
                    if (!configurationHolder.isAutoAck()) {
                        channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
                    }
                }
            } catch (Exception ex) {
                log.error("[RabbitmqConsumerHandler] thread run happen exception.", ex);
            }
        }
    }

    public void setEventListener(EventListener eventListener) {
        this.eventListener = eventListener;
    }

    public void stop() {
        stop.set(true);
    }
}
