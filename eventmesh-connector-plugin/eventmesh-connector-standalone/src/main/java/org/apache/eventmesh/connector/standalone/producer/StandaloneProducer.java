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

import com.google.common.base.Preconditions;
import io.openmessaging.api.Message;
import io.openmessaging.api.MessageBuilder;
import io.openmessaging.api.OnExceptionContext;
import io.openmessaging.api.Producer;
import io.openmessaging.api.SendCallback;
import io.openmessaging.api.SendResult;
import io.openmessaging.api.exception.OMSRuntimeException;
import org.apache.eventmesh.connector.standalone.broker.StandaloneBroker;
import org.apache.eventmesh.connector.standalone.broker.model.MessageEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class StandaloneProducer implements Producer {

    private Logger logger = LoggerFactory.getLogger(StandaloneProducer.class);

    private StandaloneBroker standaloneBroker;

    private AtomicBoolean isStarted;

    public StandaloneProducer(Properties properties) {
        this.standaloneBroker = StandaloneBroker.getInstance();
        this.isStarted = new AtomicBoolean(false);
    }

    @Override
    public SendResult send(Message message) {
        Preconditions.checkNotNull(message);
        try {
            MessageEntity messageEntity = standaloneBroker.putMessage(message.getTopic(), message);
            SendResult sendResult = new SendResult();
            sendResult.setTopic(message.getTopic());
            sendResult.setMessageId(String.valueOf(messageEntity.getOffset()));
            return sendResult;
        } catch (Exception e) {
            logger.error("send message error, topic: {}", message.getTopic(), e);
            throw new OMSRuntimeException(String.format("Send message error, topic: %s", message.getTopic()));
        }
    }

    @Override
    public void sendOneway(Message message) {
        send(message);
    }

    @Override
    public void sendAsync(Message message, SendCallback sendCallback) {
        Preconditions.checkNotNull(message);
        Preconditions.checkNotNull(sendCallback);

        try {
            SendResult sendResult = send(message);
            sendCallback.onSuccess(sendResult);
        } catch (Exception ex) {
            OnExceptionContext exceptionContext = new OnExceptionContext();
            exceptionContext.setTopic(message.getTopic());
            exceptionContext.setException(new OMSRuntimeException(ex));
            exceptionContext.setMessageId(message.getMsgID());
            sendCallback.onException(exceptionContext);
        }

    }

    @Override
    public void setCallbackExecutor(ExecutorService callbackExecutor) {

    }

    @Override
    public void updateCredential(Properties credentialProperties) {

    }

    @Override
    public boolean isStarted() {
        return isStarted.get();
    }

    @Override
    public boolean isClosed() {
        return !isStarted.get();
    }

    @Override
    public void start() {
        isStarted.compareAndSet(false, true);
    }

    @Override
    public void shutdown() {
        isStarted.compareAndSet(true, false);

    }

    @Override
    public <T> MessageBuilder<T> messageBuilder() {
        return null;
    }

    public boolean checkTopicExist(String topicName) {
        return standaloneBroker.checkTopicExist(topicName);
    }
}
