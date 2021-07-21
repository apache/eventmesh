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

package org.apache.eventmesh.connector.kafka.consumer;

import org.apache.eventmesh.common.IPUtil;
import org.apache.eventmesh.connector.kafka.common.Constants;
import org.apache.eventmesh.connector.kafka.config.ConfigurationWrapper;
import org.apache.eventmesh.connector.kafka.producer.KafkaMQProducerImplExample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class KafkaMQConsumerImplExample {

    private static KafkaMQConsumerImpl kafkaMQConsumer;

    private static final Logger logger = LoggerFactory.getLogger(KafkaMQConsumerImplExample.class);

    static {
        kafkaMQConsumer = new KafkaMQConsumerImpl(new Properties());
        String filePath = KafkaMQProducerImplExample.class.getClassLoader().getResource(Constants.KAFKA_CONF_FILE).getPath();
        ConfigurationWrapper configurationWrapper = new ConfigurationWrapper(filePath, false);
        KafkaConsumerConfig kafkaConsumerConfig = new KafkaConsumerConfig(configurationWrapper);
        kafkaConsumerConfig.setGroupId(IPUtil.getLocalAddress());
        kafkaMQConsumer.init(kafkaConsumerConfig);
    }

    public static void main(String[] args) {
        kafkaMQConsumer.subscribe("eventmesh-test-topic",
                (message, context) -> logger.info(new String(message.getBody(), StandardCharsets.UTF_8)));
        kafkaMQConsumer.start();
    }
}
