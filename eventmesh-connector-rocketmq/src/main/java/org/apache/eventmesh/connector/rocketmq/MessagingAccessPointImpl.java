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

package org.apache.eventmesh.connector.rocketmq;

import java.util.Properties;

import io.openmessaging.api.Consumer;
import io.openmessaging.api.MessagingAccessPoint;
import io.openmessaging.api.Producer;
import io.openmessaging.api.PullConsumer;
import io.openmessaging.api.batch.BatchConsumer;
import io.openmessaging.api.order.OrderConsumer;
import io.openmessaging.api.order.OrderProducer;
import io.openmessaging.api.transaction.LocalTransactionChecker;
import io.openmessaging.api.transaction.TransactionProducer;

import org.apache.eventmesh.connector.rocketmq.consumer.PushConsumerImpl;
import org.apache.eventmesh.connector.rocketmq.producer.ProducerImpl;

public class MessagingAccessPointImpl implements MessagingAccessPoint {

    private Properties accessPointProperties;

    public MessagingAccessPointImpl(final Properties accessPointProperties) {
        this.accessPointProperties = accessPointProperties;
    }

    @Override
    public String version() {
        return null;
    }

    @Override
    public Properties attributes() {
        return accessPointProperties;
    }

    @Override
    public Producer createProducer(Properties properties) {
        return new ProducerImpl(this.accessPointProperties);
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
        return new PushConsumerImpl(properties);
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
