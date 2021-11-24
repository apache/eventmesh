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

package org.apache.eventmesh.connector.standalone.consumer;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.consumer.Consumer;

import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

public class StandaloneConsumerAdaptor implements Consumer {

    private final Logger logger = LoggerFactory.getLogger(StandaloneConsumerAdaptor.class);

    private StandaloneConsumer consumer;

    public StandaloneConsumerAdaptor() {
    }

    @Override
    public boolean isStarted() {
        return false;
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public void start() {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public void init(Properties keyValue) throws Exception {

    }

    @Override
    public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {

    }

    @Override
    public void subscribe(String topic, EventListener listener) throws Exception {

    }

    @Override
    public void unsubscribe(String topic) {

    }

//    @Override
//    public void init(Properties keyValue) throws Exception {
//        String producerGroup = keyValue.getProperty("producerGroup");
//
//        MessagingAccessPointImpl messagingAccessPoint = new MessagingAccessPointImpl(keyValue);
//        consumer = (StandaloneConsumer) messagingAccessPoint.createConsumer(keyValue);
//
//    }
//
//    @Override
//    public void updateOffset(List<Message> msgs, AbstractContext context) {
//        for(Message message : msgs) {
//            consumer.updateOffset(message);
//        }
//    }
//
//    @Override
//    public void subscribe(String topic, AsyncMessageListener listener) throws Exception {
//        // todo: support subExpression
//        consumer.subscribe(topic, "*", listener);
//    }
//
//    @Override
//    public void unsubscribe(String topic) {
//        consumer.unsubscribe(topic);
//    }
//
//    @Override
//    public void subscribe(String topic, String subExpression, MessageListener listener) {
//        throw new UnsupportedOperationException("not supported yet");
//    }
//
//    @Override
//    public void subscribe(String topic, MessageSelector selector, MessageListener listener) {
//        throw new UnsupportedOperationException("not supported yet");
//    }
//
//    @Override
//    public <T> void subscribe(String topic, String subExpression, GenericMessageListener<T> listener) {
//        throw new UnsupportedOperationException("not supported yet");
//    }
//
//    @Override
//    public <T> void subscribe(String topic, MessageSelector selector, GenericMessageListener<T> listener) {
//        throw new UnsupportedOperationException("not supported yet");
//    }
//
//    @Override
//    public void subscribe(String topic, String subExpression, AsyncMessageListener listener) {
//        throw new UnsupportedOperationException("not supported yet");
//    }
//
//    @Override
//    public void subscribe(String topic, MessageSelector selector, AsyncMessageListener listener) {
//        throw new UnsupportedOperationException("not supported yet");
//    }
//
//    @Override
//    public <T> void subscribe(String topic, String subExpression, AsyncGenericMessageListener<T> listener) {
//        throw new UnsupportedOperationException("not supported yet");
//    }
//
//    @Override
//    public <T> void subscribe(String topic, MessageSelector selector, AsyncGenericMessageListener<T> listener) {
//        throw new UnsupportedOperationException("not supported yet");
//    }
//
//    @Override
//    public void updateCredential(Properties credentialProperties) {
//
//    }
//
//    @Override
//    public boolean isStarted() {
//        return consumer.isStarted();
//    }
//
//    @Override
//    public boolean isClosed() {
//        return consumer.isClosed();
//    }
//
//    @Override
//    public void start() {
//        consumer.start();
//    }
//
//    @Override
//    public void shutdown() {
//        consumer.shutdown();
//    }
}
