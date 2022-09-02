/*
 * Copyright 2016-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.connector.kafka.consumer;

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

public class KafkaConsumerRunner implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(KafkaConsumerRunner.class);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final KafkaConsumer<String, CloudEvent> consumer;
    private EventListener listener;
    private AtomicInteger offset;

    public KafkaConsumerRunner(KafkaConsumer<String, CloudEvent> kafkaConsumer) {
        this.consumer = kafkaConsumer;
    }

    public void setListener(EventListener listener) {
        this.listener = listener;
    }

    @Override
    public void run() {
        try {
            while (!closed.get()) {
                ConsumerRecords<String, CloudEvent> records = consumer.poll(Duration.ofMillis(10000));
                // Handle new records
                records.forEach(rec -> {
                    CloudEvent cloudEvent = rec.value();
                    String topicName = cloudEvent.getSubject();
                    EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext = new EventMeshAsyncConsumeContext() {
                        @Override
                        public void commit(EventMeshAction action) {
                            switch (action) {
                                case CommitMessage:
                                    // update offset
                                    logger.info("message commit, topic: {}, current offset:{}", topicName,
                                        offset.get());
                                    break;
                                case ReconsumeLater:
                                    // don't update offset
                                    break;
                                case ManualAck:
                                    // update offset
                                    offset.incrementAndGet();
                                    logger
                                        .info("message ack, topic: {}, current offset:{}", topicName, offset.get());
                                    break;
                                default:
                            }
                        }
                    };
                    if (listener != null) {
                        listener.consume(cloudEvent, eventMeshAsyncConsumeContext);
                    }
                });
            }
        } catch (WakeupException e) {
            // Ignore exception if closing
            if (!closed.get()) throw e;
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



