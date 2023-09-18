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

package org.apache.eventmesh.storage.kafka.producer;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.api.exception.StorageRuntimeException;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;
import io.cloudevents.kafka.CloudEventSerializer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressWarnings("deprecation")
public class ProducerImpl {

    private final KafkaProducer<String, CloudEvent> producer;
    private final Properties properties = new Properties();
    private final AtomicBoolean isStarted = new AtomicBoolean(false);

    public ProducerImpl(Properties props) {
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                props.getProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CloudEventSerializer.class);
        this.producer = new KafkaProducer<>(properties);
    }

    public boolean isStarted() {
        return isStarted.get();
    }

    public boolean isClosed() {
        return !isStarted.get();
    }

    public void start() {
        isStarted.compareAndSet(false, true);
    }

    public void shutdown() {
        isStarted.compareAndSet(true, false);
    }

    public void init(Properties properties) {
        this.properties.putAll(properties);
    }

    public void send(CloudEvent cloudEvent) {
        try {
            this.producer.send(new ProducerRecord<>(Objects.requireNonNull(cloudEvent.getSubject()), cloudEvent));
        } catch (Exception e) {
            log.error(String.format("Send message oneway Exception, %s", cloudEvent), e);
        }
    }

    public void checkTopicExist(String topic) throws ExecutionException, InterruptedException, StorageRuntimeException {
        try (Admin admin = Admin.create(properties)) {
            Set<String> topicNames = admin.listTopics().names().get();
            boolean exist = topicNames.contains(topic);
            if (!exist) {
                throw new StorageRuntimeException(String.format("topic:%s is not exist", topic));
            }
        }
    }

    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {
        throw new StorageRuntimeException("Request is not supported");
    }

    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        throw new StorageRuntimeException("Reply is not supported");
    }

    public void sendOneway(CloudEvent message) {
    }

    public void sendAsync(CloudEvent cloudEvent, SendCallback sendCallback) {
        try {
            this.producer.send(new ProducerRecord<>(Objects.requireNonNull(cloudEvent.getSubject()), cloudEvent), (metadata, exception) -> {
                if (exception != null) {
                    StorageRuntimeException onsEx = new StorageRuntimeException(exception.getMessage(), exception);
                    OnExceptionContext context = new OnExceptionContext();
                    context.setTopic(cloudEvent.getSubject());
                    context.setException(onsEx);
                    sendCallback.onException(context);
                } else {
                    SendResult sendResult = new SendResult();
                    sendResult.setTopic(cloudEvent.getSubject());
                    sendResult.setMessageId(cloudEvent.getId());
                    sendCallback.onSuccess(sendResult);
                }
            });
        } catch (Exception e) {
            log.error(String.format("Send message oneway Exception, %s", cloudEvent), e);
        }
    }
}
