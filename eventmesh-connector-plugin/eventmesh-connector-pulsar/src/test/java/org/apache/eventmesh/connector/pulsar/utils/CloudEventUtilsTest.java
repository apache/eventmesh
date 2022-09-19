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

package org.apache.eventmesh.connector.pulsar.utils;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.apache.eventmesh.api.SendResult;
import org.junit.Assert;
import org.junit.Test;

import java.net.URI;
import java.time.OffsetDateTime;

public class CloudEventUtilsTest {

    @Test
    public void testConvertSendResult() {
        CloudEvent cloudEvent = CloudEventBuilder.v1()
            .withId("001")
            .withTime(OffsetDateTime.now())
            .withSource(URI.create("testsource"))
            .withSubject("testTopic")
            .withType(String.class.getCanonicalName())
            .withDataContentType("text/plain")
            .withData("data".getBytes())
            .build();

       SendResult result = CloudEventUtils.convertSendResult(cloudEvent);
       Assert.assertEquals("testTopic",result.getTopic());
       Assert.assertEquals("001",result.getMessageId());
    }

}
