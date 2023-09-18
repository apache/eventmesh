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

package org.apache.eventmesh.storage.rabbitmq.cloudevent;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class RabbitmqCloudEventTest {

    private CloudEvent cloudEvent;

    @Before
    public void before() {
        cloudEvent = CloudEventBuilder.v1()
                .withId("1")
                .withTime(OffsetDateTime.now())
                .withSource(URI.create("testsource"))
                .withSubject("topic")
                .withType(String.class.getCanonicalName())
                .withDataContentType("text/plain")
                .withData("data".getBytes(StandardCharsets.UTF_8))
                .build();
    }

    @Test
    public void toByteArray() throws Exception {
        RabbitmqCloudEventWriter writer = new RabbitmqCloudEventWriter();
        RabbitmqCloudEvent rabbitmqCloudEvent = writer.writeBinary(cloudEvent);
        Assert.assertEquals("topic", cloudEvent.getSubject());

        byte[] data = RabbitmqCloudEvent.toByteArray(rabbitmqCloudEvent);
        Assert.assertNotNull(data);
    }

    @Test
    public void getFromByteArray() throws Exception {
        RabbitmqCloudEventWriter writer = new RabbitmqCloudEventWriter();
        RabbitmqCloudEvent rabbitmqCloudEvent = writer.writeBinary(cloudEvent);
        Assert.assertEquals("topic", cloudEvent.getSubject());

        byte[] data = RabbitmqCloudEvent.toByteArray(rabbitmqCloudEvent);
        Assert.assertNotNull(data);

        RabbitmqCloudEvent event = RabbitmqCloudEvent.getFromByteArray(data);
        Assert.assertEquals("topic", event.getExtensions().get("subject"));
    }
}
