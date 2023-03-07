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
import org.apache.eventmesh.api.producer.Producer;

import java.util.Properties;

import io.cloudevents.CloudEvent;

public class StandaloneProducerAdaptor implements Producer {

    private StandaloneProducer standaloneProducer;

    public StandaloneProducerAdaptor() {
    }

    @Override
    public boolean isStarted() {
        return standaloneProducer.isStarted();
    }

    @Override
    public boolean isClosed() {
        return standaloneProducer.isClosed();
    }

    @Override
    public void start() {
        standaloneProducer.start();
    }

    @Override
    public void shutdown() {
        standaloneProducer.shutdown();
    }

    @Override
    public void init(Properties properties) throws Exception {
        standaloneProducer = new StandaloneProducer(properties);
    }

    @Override
    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        standaloneProducer.publish(cloudEvent, sendCallback);
    }

    @Override
    public void sendOneway(CloudEvent cloudEvent) {
        standaloneProducer.sendOneway(cloudEvent);
    }

    @Override
    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {
        standaloneProducer.request(cloudEvent, rrCallback, timeout);
    }

    @Override
    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        return standaloneProducer.reply(cloudEvent, sendCallback);
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {
        standaloneProducer.checkTopicExist(topic);
    }

    @Override
    public void setExtFields() {
        standaloneProducer.setExtFields();
    }

}
