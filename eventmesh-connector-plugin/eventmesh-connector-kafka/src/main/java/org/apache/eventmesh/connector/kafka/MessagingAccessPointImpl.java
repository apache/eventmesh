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

package org.apache.eventmesh.connector.kafka;

import io.openmessaging.api.Consumer;
import io.openmessaging.api.MessagingAccessPoint;
import io.openmessaging.api.OMS;
import io.openmessaging.api.Producer;
import io.openmessaging.api.PullConsumer;
import io.openmessaging.api.batch.BatchConsumer;
import io.openmessaging.api.order.OrderConsumer;
import io.openmessaging.api.order.OrderProducer;
import io.openmessaging.api.transaction.LocalTransactionChecker;
import io.openmessaging.api.transaction.TransactionProducer;
import org.apache.eventmesh.connector.kafka.consumer.KafkaMQConsumerImpl;
import org.apache.eventmesh.connector.kafka.producer.KafkaMQProducerImpl;

import java.util.Properties;

/**
 * The implementation of open message{@link MessagingAccessPoint}
 */
public class MessagingAccessPointImpl implements MessagingAccessPoint {


    private final Properties attributesProperties;

    public MessagingAccessPointImpl(final Properties attributesProperties) {
        this.attributesProperties = attributesProperties;
    }


    @Override
    public String version() {
        return OMS.specVersion;
    }

    @Override
    public Properties attributes() {
        return attributesProperties;
    }

    @Override
    public Producer createProducer(Properties properties) {
        return new KafkaMQProducerImpl(properties);
    }

    @Override
    public OrderProducer createOrderProducer(Properties properties) {
        return null;
    }

    @Override
    public TransactionProducer createTransactionProducer(Properties properties, LocalTransactionChecker checker) {
        return null;
    }

    @Override
    public TransactionProducer createTransactionProducer(Properties properties) {
        return null;
    }

    @Override
    public Consumer createConsumer(Properties properties) {
        return new KafkaMQConsumerImpl(properties);
    }

    @Override
    public PullConsumer createPullConsumer(Properties properties) {
        return null;
    }

    @Override
    public BatchConsumer createBatchConsumer(Properties properties) {
        return null;
    }

    @Override
    public OrderConsumer createOrderedConsumer(Properties properties) {
        return null;
    }
}
