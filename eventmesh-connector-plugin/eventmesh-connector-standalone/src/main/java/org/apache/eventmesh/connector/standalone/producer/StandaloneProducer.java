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

package org.apache.eventmesh.connector.standalone.producer;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.connector.standalone.broker.StandaloneBroker;
import org.apache.eventmesh.connector.standalone.broker.model.MessageEntity;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import io.cloudevents.CloudEvent;

public class StandaloneProducer {

    private Logger logger = LoggerFactory.getLogger(StandaloneProducer.class);

    private StandaloneBroker standaloneBroker;

    private AtomicBoolean isStarted;

    public StandaloneProducer(Properties properties) {
        this.standaloneBroker = StandaloneBroker.getInstance();
        this.isStarted = new AtomicBoolean(false);
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

    public StandaloneProducer init(Properties properties) throws Exception {
        return new StandaloneProducer(properties);
    }

    public SendResult publish(CloudEvent cloudEvent) {
        Preconditions.checkNotNull(cloudEvent);
        try {
            MessageEntity messageEntity = standaloneBroker.putMessage(cloudEvent.getSubject(), cloudEvent);
            SendResult sendResult = new SendResult();
            sendResult.setTopic(cloudEvent.getSubject());
            sendResult.setMessageId(String.valueOf(messageEntity.getOffset()));
            return sendResult;
        } catch (Exception e) {
            logger.error("send message error, topic: {}", cloudEvent.getSubject(), e);
            throw new ConnectorRuntimeException(
                String.format("Send message error, topic: %s", cloudEvent.getSubject()));
        }
    }

    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        Preconditions.checkNotNull(cloudEvent);
        Preconditions.checkNotNull(sendCallback);

        try {
            SendResult sendResult = publish(cloudEvent);
            sendCallback.onSuccess(sendResult);
        } catch (Exception ex) {
            OnExceptionContext onExceptionContext = new OnExceptionContext();
            onExceptionContext.setMessageId(cloudEvent.getId());
            onExceptionContext.setTopic(cloudEvent.getSubject());
            onExceptionContext.setException(new ConnectorRuntimeException(ex));
            sendCallback.onException(onExceptionContext);
        }
    }

    public void sendOneway(CloudEvent cloudEvent) {
        publish(cloudEvent);
    }

    public void sendAsync(CloudEvent cloudEvent, SendCallback sendCallback) {
        Preconditions.checkNotNull(cloudEvent);
        Preconditions.checkNotNull(sendCallback);
        // todo: current is not async
        try {
            SendResult sendResult = publish(cloudEvent);
            sendCallback.onSuccess(sendResult);
        } catch (Exception ex) {
            OnExceptionContext onExceptionContext = new OnExceptionContext();
            onExceptionContext.setMessageId(cloudEvent.getId());
            onExceptionContext.setTopic(cloudEvent.getSubject());
            onExceptionContext.setException(new ConnectorRuntimeException(ex));
            sendCallback.onException(onExceptionContext);
        }
    }

//    @Override
//    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {
//        throw new ConnectorRuntimeException("Request is not supported");
//    }

    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {
        throw new ConnectorRuntimeException("Request is not supported");
    }

    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        throw new ConnectorRuntimeException("Reply is not supported");
    }

    public void checkTopicExist(String topic) throws Exception {
        boolean exist = standaloneBroker.checkTopicExist(topic);
        if (!exist) {
            throw new ConnectorRuntimeException(String.format("topic:%s is not exist", topic));
        }
    }

    public void setExtFields() {

    }
}
