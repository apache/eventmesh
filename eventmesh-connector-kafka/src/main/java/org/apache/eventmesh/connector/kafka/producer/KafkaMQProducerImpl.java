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

import io.openmessaging.api.Message;
import io.openmessaging.api.MessageBuilder;
import io.openmessaging.api.OnExceptionContext;
import io.openmessaging.api.Producer;
import io.openmessaging.api.SendCallback;
import io.openmessaging.api.SendResult;
import io.openmessaging.api.exception.OMSRuntimeException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

public class KafkaMQProducerImpl implements Producer {

    private Logger logger = LoggerFactory.getLogger(KafkaMQProducerImpl.class);

    private final Properties properties;

    private KafkaProducer<String, Message> kafkaProducer;

    private volatile boolean isStart = false;

    private String clientId;

    /**
     * More detail about the properties, you can see EventMeshProducer
     * <ol>
     *     <li>producerGroup</li>
     *     <li>instanceName</li>
     *     <li>eventMeshIDC</li>
     * </ol>
     *
     * @param properties
     */
    public KafkaMQProducerImpl(final Properties properties) {
        this.properties = properties;
    }

    public void init(KafkaProducerConfig kafkaProducerConfig) {
        HashMap<String, Object> kafkaProperties = new HashMap<>();
        if (kafkaProducerConfig.getServers() != null) {
            kafkaProperties.put(KafkaProducerConfig.ProducerConfigKey.servers, kafkaProducerConfig.getServers());
        }
        if (kafkaProducerConfig.getAcks() != null) {
            kafkaProperties.put(KafkaProducerConfig.ProducerConfigKey.acks, kafkaProducerConfig.getAcks());
        }
        if (kafkaProducerConfig.getBufferMemory() != null) {
            kafkaProperties.put(KafkaProducerConfig.ProducerConfigKey.bufferMemory, kafkaProducerConfig.getBufferMemory());
        }
        if (kafkaProducerConfig.getRetries() != null) {
            kafkaProperties.put(KafkaProducerConfig.ProducerConfigKey.retries, kafkaProducerConfig.getRetries());
        }
        if (kafkaProducerConfig.getBatchSize() != null) {
            kafkaProperties.put(KafkaProducerConfig.ProducerConfigKey.batchSize, kafkaProducerConfig.getBatchSize());
        }
        if (kafkaProducerConfig.getClientId() != null) {
            kafkaProperties.put(KafkaProducerConfig.ProducerConfigKey.clientId, kafkaProducerConfig.getClientId());
            clientId = kafkaProducerConfig.getClientId();
        }
        if (kafkaProducerConfig.getKeySerialize() != null) {
            kafkaProperties.put(KafkaProducerConfig.ProducerConfigKey.keySerializer, kafkaProducerConfig.getKeySerialize());
        }
        if (kafkaProducerConfig.getValueSerialize() != null) {
            kafkaProperties.put(KafkaProducerConfig.ProducerConfigKey.valueSerializer, kafkaProducerConfig.getValueSerialize());
        }
        // init properties
        kafkaProducer = new KafkaProducer<>(kafkaProperties);
    }

    @Override
    public SendResult send(Message message) {
        try {
            ProducerRecord<String, Message> record = new ProducerRecord<>(message.getTopic(), message);
            RecordMetadata recordMetadata = kafkaProducer.send(record).get();
            return transformToSendResult(recordMetadata);
        } catch (Exception ex) {
            logger.error("Send message: {} error", message, ex);
            throw new OMSRuntimeException(ex);
        }
    }

    @Override
    public void sendOneway(Message message) {
        try {
            ProducerRecord<String, Message> record = new ProducerRecord<>(message.getTopic(), message);
            kafkaProducer.send(record);
        } catch (Exception ex) {
            logger.error("Send message: {} error", message, ex);
            throw new OMSRuntimeException(ex);
        }
    }

    @Override
    public void sendAsync(Message message, SendCallback sendCallback) {
        ProducerRecord<String, Message> record = new ProducerRecord<>(message.getTopic(), message);
        kafkaProducer.send(record, (metadata, exception) -> {
            if (exception != null) {
                // send error
                sendCallback.onSuccess(transformToSendResult(metadata));
            } else {
                OnExceptionContext exceptionContext = new OnExceptionContext();
                exceptionContext.setTopic(message.getTopic());
                exceptionContext.setMessageId(message.getMsgID());
                exceptionContext.setException(new OMSRuntimeException(exception));
                sendCallback.onException(exceptionContext);
            }
        });
    }

    @Override
    public void setCallbackExecutor(ExecutorService callbackExecutor) {
        //
    }

    @Override
    public void updateCredential(Properties credentialProperties) {
        //
    }

    @Override
    public boolean isStarted() {
        return isStart;
    }

    @Override
    public boolean isClosed() {
        return !isStart;
    }

    @Override
    public void start() {
        isStart = true;
    }

    @Override
    public void shutdown() {
        kafkaProducer.close();
        isStart = false;
    }

    @Override
    public <T> MessageBuilder<T> messageBuilder() {
        return null;
    }

    public String buildMQClientId() {
        return clientId;
    }

    private SendResult transformToSendResult(RecordMetadata recordMetadata) {
        SendResult sendResult = new SendResult();
        sendResult.setMessageId(String.valueOf(recordMetadata.offset()));
        sendResult.setTopic(recordMetadata.topic());
        return sendResult;
    }
}
