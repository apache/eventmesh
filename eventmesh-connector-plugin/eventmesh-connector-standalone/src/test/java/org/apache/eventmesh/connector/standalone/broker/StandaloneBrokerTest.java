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

package org.apache.eventmesh.connector.standalone.broker;

import org.apache.eventmesh.connector.standalone.broker.model.MessageEntity;

import java.net.URI;

import org.junit.Assert;
import org.junit.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class StandaloneBrokerTest {

    @Test
    public void getInstance() {
        Assert.assertNotNull(StandaloneBroker.getInstance());
    }

    @Test
    public void putMessage() throws InterruptedException {
        StandaloneBroker instance = StandaloneBroker.getInstance();
        CloudEvent cloudEvent = CloudEventBuilder.v1()
                .withId("test")
                .withSource(URI.create("testsource"))
                .withType("testType")
                .build();
        MessageEntity messageEntity = instance.putMessage("test-topic", cloudEvent);
        Assert.assertNotNull(messageEntity);
    }

    @Test
    public void takeMessage() throws InterruptedException {
        StandaloneBroker instance = StandaloneBroker.getInstance();
        CloudEvent cloudEvent = CloudEventBuilder.v1()
                .withId("test")
                .withSource(URI.create("testsource"))
                .withType("testType")
                .build();
        instance.putMessage("test-topic", cloudEvent);
        CloudEvent message = instance.takeMessage("test-topic");
        Assert.assertNotNull(message);
    }

    @Test
    public void getMessage() {
    }

    @Test
    public void testGetMessage() {
    }

    @Test
    public void checkTopicExist() {
    }
}