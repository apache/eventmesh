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

package org.apache.eventmesh.connector.kafka.producer;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.api.exception.OnExceptionContext;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.protocol.Message;

import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.kafka.CloudEventSerializer;
import io.cloudevents.kafka.KafkaMessageFactory;

import org.apache.kafka.common.serialization.StringSerializer;
import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressWarnings("deprecation")
public class ProducerImpl {
    private Logger logger = LoggerFactory.getLogger(ProducerImpl.class);
    private KafkaProducer<String, CloudEvent> producer;
    Properties properties;

    private AtomicBoolean isStarted;

    public ProducerImpl(Properties props) {
        this.isStarted = new AtomicBoolean(false);

        properties = new Properties();
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

    public ProducerImpl init(Properties properties) throws Exception {
        return new ProducerImpl(properties);
    }

    public void send(CloudEvent cloudEvent) {
        // ProducerRecord<Void, byte[]> msg =
        //    KafkaMessageFactory.createWriter(Objects.requireNonNull(cloudEvent.getSubject()))
        //    .writeBinary(cloudEvent);
        try {
            this.producer.send(new ProducerRecord<>(cloudEvent.getSubject(), cloudEvent));
        } catch (Exception e) {
            log.error(String.format("Send message oneway Exception, %s", cloudEvent), e);
        }
    }

    public void checkTopicExist(String topic) throws ExecutionException, InterruptedException, ConnectorRuntimeException {
        Admin admin = Admin.create(properties);
        Set<String> topicNames = admin.listTopics().names().get();
        admin.close();
        boolean exist = topicNames.contains(topic);
        if (!exist) {
            throw new ConnectorRuntimeException(String.format("topic:%s is not exist", topic));
        }
    }

    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {
        throw new ConnectorRuntimeException("Request is not supported");
    }

    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        throw new ConnectorRuntimeException("Reply is not supported");
    }

    public void sendOneway(CloudEvent message) {
    }

    public void sendAsync(CloudEvent cloudEvent, SendCallback sendCallback) {
        try {
            this.producer.send(new ProducerRecord<>(cloudEvent.getSubject(), cloudEvent));
        } catch (Exception e) {
            log.error(String.format("Send message oneway Exception, %s", cloudEvent), e);
        }
    }
}
