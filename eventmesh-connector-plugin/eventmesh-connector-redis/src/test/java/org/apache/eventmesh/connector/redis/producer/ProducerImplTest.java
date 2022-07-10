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

package org.apache.eventmesh.connector.redis.producer;

import java.net.URI;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

@RunWith(MockitoJUnitRunner.class)
public class ProducerImplTest {

    @Mock
    private RedisProducerImpl producer;

    @Before
    public void before() {
        producer = new RedisProducerImpl();
        producer.start();
    }

    @After
    public void after() {
        producer.shutdown();
    }

    @Test
    public void testSend_OK() {
        CloudEvent cloudEvent = CloudEventBuilder.v1()
                .withId("id1")
                .withSource(URI.create("https://github.com/cloudevents/*****"))
                .withType("producer.example")
                .withSubject("HELLO_TOPIC")
                .withData("hello world".getBytes())
                .build();
        producer.sendOneway(cloudEvent);
    }

}