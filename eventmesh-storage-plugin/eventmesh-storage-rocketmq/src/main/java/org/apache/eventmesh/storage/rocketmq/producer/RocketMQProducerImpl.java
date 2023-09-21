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

package org.apache.eventmesh.storage.rocketmq.producer;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.storage.rocketmq.common.EventMeshConstants;
import org.apache.eventmesh.storage.rocketmq.config.ClientConfiguration;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.util.Properties;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressWarnings("deprecation")
@Config(field = "clientConfiguration")
public class RocketMQProducerImpl implements Producer {

    private ProducerImpl producer;

    private ClientConfiguration clientConfiguration;

    @Override
    public synchronized void init(Properties keyValue) {
        String producerGroup =
            keyValue.getProperty(Constants.PRODUCER_GROUP) == null ? "RMQ-producerGroup" : keyValue.getProperty(Constants.PRODUCER_GROUP);

        String omsNamesrv = clientConfiguration.getNamesrvAddr();
        Properties properties = new Properties();
        properties.put(Constants.ACCESS_POINTS, omsNamesrv);
        properties.put(Constants.REGION, Constants.NAMESPACE);
        properties.put(Constants.RMQ_PRODUCER_GROUP, producerGroup);
        properties.put(Constants.OPERATION_TIMEOUT, 3000);
        properties.put(Constants.PRODUCER_ID, producerGroup);

        producer = new ProducerImpl(properties);

    }

    @Override
    public boolean isStarted() {
        return producer.isStarted();
    }

    @Override
    public boolean isClosed() {
        return producer.isClosed();
    }

    @Override
    public void start() {
        producer.start();
    }

    @Override
    public synchronized void shutdown() {
        producer.shutdown();
    }

    @Override
    public void publish(CloudEvent message, SendCallback sendCallback) throws Exception {
        producer.sendAsync(message, sendCallback);
    }

    @Override
    public void request(CloudEvent message, RequestReplyCallback rrCallback, long timeout)
        throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        producer.request(message, rrCallback, timeout);
    }

    @Override
    public boolean reply(final CloudEvent message, final SendCallback sendCallback) throws Exception {
        producer.reply(message, sendCallback);
        return true;
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {
        this.producer.getRocketmqProducer()
            .getDefaultMQProducerImpl()
            .getmQClientFactory()
            .getMQClientAPIImpl()
            .getDefaultTopicRouteInfoFromNameServer(topic, EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
    }

    @Override
    public void setExtFields() {
        producer.setExtFields();
    }

    @Override
    public void sendOneway(CloudEvent message) {
        producer.sendOneway(message);
    }

    public ClientConfiguration getClientConfiguration() {
        return clientConfiguration;
    }
}
