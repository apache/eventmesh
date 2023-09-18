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

package org.apache.eventmesh.storage.redis.producer;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.api.exception.StorageRuntimeException;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.storage.redis.client.RedissonClient;

import java.util.Properties;

import org.redisson.Redisson;
import org.redisson.api.RTopic;

import io.cloudevents.CloudEvent;

import com.google.common.base.Preconditions;

public class RedisProducer implements Producer {

    private Redisson redisson;

    private volatile boolean started = false;

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public boolean isClosed() {
        return !isStarted();
    }

    @Override
    public synchronized void start() {
        if (!started) {
            started = true;
        }
    }

    @Override
    public synchronized void shutdown() {
        if (started) {
            try {
                redisson = null;
            } finally {
                started = false;
            }
        }
    }

    @Override
    public void init(Properties properties) {
        // Currently, 'properties' does not pass useful configuration information.
        redisson = RedissonClient.INSTANCE;
    }

    @Override
    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) {
        Preconditions.checkNotNull(cloudEvent);
        Preconditions.checkNotNull(sendCallback);

        try {
            RTopic topic = redisson.getTopic(cloudEvent.getSubject());

            topic.publishAsync(cloudEvent).whenCompleteAsync((stage, throwable) -> {
                if (throwable != null) {
                    sendCallback.onException(
                            OnExceptionContext.builder()
                                    .topic(cloudEvent.getSubject())
                                    .messageId(cloudEvent.getId())
                                    .exception(new StorageRuntimeException(throwable))
                                    .build());
                } else {
                    SendResult sendResult = new SendResult();
                    sendResult.setTopic(cloudEvent.getSubject());
                    sendResult.setMessageId(cloudEvent.getId());
                    sendCallback.onSuccess(sendResult);
                }
            });
        } catch (Exception e) {
            sendCallback.onException(
                    OnExceptionContext.builder()
                            .topic(cloudEvent.getSubject())
                            .messageId(cloudEvent.getId())
                            .exception(new StorageRuntimeException(e))
                            .build());
        }
    }

    @Override
    public void sendOneway(CloudEvent cloudEvent) {
        Preconditions.checkNotNull(cloudEvent);

        RTopic topic = redisson.getTopic(cloudEvent.getSubject());
        topic.publish(cloudEvent);
    }

    @Override
    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) {
        throw new StorageRuntimeException("Request is not supported");
    }

    @Override
    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) {
        throw new StorageRuntimeException("Reply is not supported");
    }

    @Override
    public void checkTopicExist(String topic) {
        // Because redis has the feature of creating topics when used, there is no need to check existence.
    }

    @Override
    public void setExtFields() {

    }
}
