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

package org.apache.eventmesh.storage.kafka.consumer;

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KafkaConsumerRunner implements Runnable {

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final KafkaConsumer<String, CloudEvent> consumer;
    private ConcurrentHashMap<CloudEvent, Long> cloudEventToOffset;
    private EventListener listener;

    public KafkaConsumerRunner(KafkaConsumer<String, CloudEvent> kafkaConsumer) {
        this.consumer = kafkaConsumer;
        cloudEventToOffset = new ConcurrentHashMap<>();
    }

    public synchronized void setListener(EventListener listener) {
        this.listener = listener;
    }

    public long getOffset(CloudEvent cloudEvent) {
        return cloudEventToOffset.getOrDefault(cloudEvent, 0L);
    }

    @Override
    public void run() {
        try {
            while (!closed.get()) {
                if (consumer.subscription().isEmpty()) {
                    // consumer cannot poll if it is subscribe to nothing
                    continue;
                }
                ConsumerRecords<String, CloudEvent> records = consumer.poll(Duration.ofMillis(10000));
                // Handle new records
                records.forEach(rec -> {
                    try {
                        CloudEvent cloudEvent = rec.value();
                        String topicName = cloudEvent.getSubject();
                        EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext = new EventMeshAsyncConsumeContext() {

                            @Override
                            public void commit(EventMeshAction action) {
                                switch (action) {
                                    case CommitMessage:
                                        // update offset
                                        log.info("message commit, topic: {}, current offset:{}", topicName,
                                                rec.offset());
                                        break;
                                    case ManualAck:
                                        // update offset
                                        log.info("message ack, topic: {}, current offset:{}", topicName, rec.offset());
                                        break;
                                    case ReconsumeLater:
                                    default:
                                }
                            }
                        };
                        cloudEventToOffset.put(cloudEvent, rec.offset());
                        if (listener != null) {
                            listener.consume(cloudEvent, eventMeshAsyncConsumeContext);
                        }
                    } catch (Exception e) {
                        log.info("Error parsing cloudevents: {}", e.getMessage());
                    }
                });
            }
        } catch (WakeupException e) {
            // Ignore exception if closing
            if (!closed.get()) {
                throw e;
            }
        } finally {
            consumer.close();
        }
    }

    // Shutdown hook which can be called from a separate thread
    public void shutdown() {
        closed.set(true);
        consumer.wakeup();
    }
}