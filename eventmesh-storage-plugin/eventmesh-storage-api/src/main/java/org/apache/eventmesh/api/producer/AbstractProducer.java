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

package org.apache.eventmesh.api.producer;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.exception.StorageRuntimeException;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;

public abstract class AbstractProducer implements Producer {

    protected final transient AtomicBoolean started = new AtomicBoolean(false);

    protected final transient Properties properties;

    protected AbstractProducer(final Properties properties) {
        this.properties = properties;
    }

    protected StorageRuntimeException checkProducerException(CloudEvent cloudEvent, Throwable e) {
        if (cloudEvent.getData() == null) {
            return new StorageRuntimeException(String.format("CloudEvent message data does not exist, %s", e.getMessage()));
        }
        return new StorageRuntimeException(String.format("Unknown connector runtime exception, %s", e.getMessage()));
    }

    public Properties properties() {
        return this.properties;
    }

    @Override
    public boolean isStarted() {
        return started.get();
    }

    @Override
    public boolean isClosed() {
        return !this.isStarted();
    }

    @Override
    public void start() {
        started.set(true);
    }

    @Override
    public void shutdown() {
        started.set(false);
    }

    @Override
    public void init(Properties properties) throws Exception {
    }

    @Override
    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        throw new StorageRuntimeException("Publish is not supported");
    }

    @Override
    public void sendOneway(CloudEvent cloudEvent) {
        throw new StorageRuntimeException("SendOneWay is not supported");
    }

    @Override
    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {
        throw new StorageRuntimeException("Request is not supported");
    }

    @Override
    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        throw new StorageRuntimeException("Reply is not supported");
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {
        throw new StorageRuntimeException("CheckTopicExist is not supported");
    }

    @Override
    public void setExtFields() {
        throw new StorageRuntimeException("SetExtFields is not supported");
    }
}
