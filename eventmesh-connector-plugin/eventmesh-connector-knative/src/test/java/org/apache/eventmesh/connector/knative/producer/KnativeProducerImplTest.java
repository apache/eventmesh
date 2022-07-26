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

package org.apache.eventmesh.connector.knative.producer;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.connector.knative.cloudevent.KnativeMessageFactory;
import org.apache.eventmesh.connector.knative.cloudevent.impl.KnativeHeaders;

import java.util.Properties;

import org.junit.jupiter.api.Test;

public class KnativeProducerImplTest {

    @Test
    public void testPublish() throws Exception {
        Properties properties = new Properties();

        // Set CloudEvent header:
        properties.put(KnativeHeaders.CONTENT_TYPE, "application/json");
        properties.put(KnativeHeaders.CE_ID, "1234");
        properties.put(KnativeHeaders.CE_SPECVERSION, "1.0");
        properties.put(KnativeHeaders.CE_TYPE, "some-type");
        properties.put(KnativeHeaders.CE_SOURCE, "java-client");

        // Set CloudEvent message data:
        properties.put("data", "Hello Knative from EventMesh!");

        // Initialize a Knative producer:
        KnativeProducerImpl producer = new KnativeProducerImpl();
        producer.init(properties);

        // Publish a CloudEvent message to cloudevents-player:
        producer.publish(KnativeMessageFactory.createWriter(properties), new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                System.out.println("Send cloudevent message successfully.");
            }

            @Override
            public void onException(OnExceptionContext context) {
                System.out.println("Send cloudevent message exception.");
            }
        });
    }
}
