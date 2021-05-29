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
import io.openmessaging.api.SendResult;
import org.apache.eventmesh.connector.kafka.common.Constants;
import org.apache.eventmesh.connector.kafka.config.ConfigurationWrapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@RunWith(PowerMockRunner.class)
@PrepareForTest({KafkaProducer.class, KafkaMQProducerImpl.class})
public class KafkaMQProducerImplTest {

    private KafkaMQProducerImpl kafkaMQProducer = new KafkaMQProducerImpl(new Properties());

    private KafkaProducer<String, Message> kafkaProducer = PowerMockito.mock(KafkaProducer.class);

    @Before
    public void before() throws Exception {
        PowerMockito.whenNew(KafkaProducer.class)
                .withAnyArguments().thenReturn(kafkaProducer);
        String filePath = this.getClass().getClassLoader().getResource(Constants.KAFKA_CONF_FILE).getPath();
        ConfigurationWrapper configurationWrapper = new ConfigurationWrapper(filePath, false);
        KafkaProducerConfig kafkaProducerConfig = new KafkaProducerConfig(configurationWrapper);
        kafkaMQProducer.init(kafkaProducerConfig);
    }

    @Test
    public void send() throws ExecutionException, InterruptedException {
        RecordMetadata recordMetadata = new RecordMetadata(new TopicPartition("topic", 1), 1,
                1, System.currentTimeMillis(), 1L, 1, 1);

        Future<RecordMetadata> mockFuture = PowerMockito.mock(Future.class);
        Mockito.when(mockFuture.get()).thenReturn(recordMetadata);
        PowerMockito.when(kafkaProducer.send(Mockito.any())).thenReturn(mockFuture);

        Message message = new Message("test.topic", "tag", "data".getBytes(StandardCharsets.UTF_8));
        SendResult sendResult = kafkaMQProducer.send(message);
        Assert.assertNotNull(sendResult);
    }

    @Test
    public void sendOneway() {
    }

    @Test
    public void sendAsync() {
    }

    @Test
    public void setCallbackExecutor() {
    }

    @Test
    public void updateCredential() {
    }

    @Test
    public void isStarted() {
    }

    @Test
    public void isClosed() {
    }

    @Test
    public void start() {
    }

    @Test
    public void shutdown() {
    }

    @Test
    public void messageBuilder() {
    }

    @Test
    public void buildMQClientId() {
    }
}