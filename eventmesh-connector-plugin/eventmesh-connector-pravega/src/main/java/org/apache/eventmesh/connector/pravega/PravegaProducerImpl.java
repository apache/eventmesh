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

package org.apache.eventmesh.connector.pravega;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.connector.pravega.client.PravegaClient;
import org.apache.eventmesh.connector.pravega.exception.PravegaConnectorException;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PravegaProducerImpl implements Producer {
    private final AtomicBoolean started = new AtomicBoolean(false);
    private PravegaClient client;

    @Override
    public void init(Properties properties) throws Exception {
        client = PravegaClient.getInstance();
    }

    @Override
    public void start() {
        started.compareAndSet(false, true);
    }

    @Override
    public void shutdown() {
        started.compareAndSet(true, false);
    }

    @Override
    public boolean isStarted() {
        return started.get();
    }

    @Override
    public boolean isClosed() {
        return !started.get();
    }

    @Override
    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        try {
            SendResult sendResult = client.publish(cloudEvent.getSubject(), cloudEvent);
            sendCallback.onSuccess(sendResult);
        } catch (Exception e) {
            log.error("send message error, topic: {}", cloudEvent.getSubject());
            OnExceptionContext onExceptionContext = OnExceptionContext.builder()
                .messageId("-1")
                .topic(cloudEvent.getSubject())
                .exception(new ConnectorRuntimeException(e))
                .build();
            sendCallback.onException(onExceptionContext);
        }
    }

    @Override
    public void sendOneway(CloudEvent cloudEvent) {
        client.publish(cloudEvent.getSubject(), cloudEvent);
    }

    @Override
    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {
        if (!client.checkTopicExist(topic)) {
            throw new PravegaConnectorException(String.format("topic:%s is not exist", topic));
        }
    }

    @Override
    public void setExtFields() {

    }
}
