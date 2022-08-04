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

package org.apache.eventmesh.connector.knative.producer;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.connector.knative.config.ClientConfiguration;

import java.util.Properties;

import io.cloudevents.CloudEvent;

public class KnativeProducerImpl implements Producer {

    private ProducerImpl producer;

    @Override
    public synchronized void init(Properties properties) throws Exception {
        // Load parameters from properties file:
        final ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.init();

        properties.put("url", clientConfiguration.serviceAddr);
        producer = new ProducerImpl(properties);
    }

    @Override
    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        producer.sendAsync(cloudEvent, sendCallback);
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
        throw new ConnectorRuntimeException("Start is not supported");
    }

    @Override
    public void shutdown() {
        throw new ConnectorRuntimeException("Shutdown is not supported");
    }

    @Override
    public void sendOneway(CloudEvent cloudEvent) {
        throw new ConnectorRuntimeException("SendOneWay is not supported");
    }

    @Override
    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {
        throw new ConnectorRuntimeException("Request is not supported");
    }

    @Override
    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        throw new ConnectorRuntimeException("Reply is not supported");
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {
        throw new ConnectorRuntimeException("CheckTopicExist is not supported");
    }

    @Override
    public void setExtFields() {
        throw new ConnectorRuntimeException("SetExtFields is not supported");
    }
}
