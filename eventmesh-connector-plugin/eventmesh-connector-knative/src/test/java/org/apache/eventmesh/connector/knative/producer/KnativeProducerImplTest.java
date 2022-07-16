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

import org.apache.eventmesh.connector.knative.cloudevent.KnativeMessageFactory;
import org.apache.eventmesh.connector.knative.cloudevent.impl.KnativeHeaders;
import org.junit.jupiter.api.Test;

import java.util.Properties;

public class KnativeProducerImplTest {

    @Test
    public void testSendOneway() throws Exception {
        Properties properties = new Properties();

        // Set URL according to cloudevents-player:
        // Please follow the steps in https://knative.dev/docs/getting-started/first-source/#sending-an-event to set up a Knative service (cloudevents-player) as source.
        properties.put("url", "http://cloudevents-player.default.127.0.0.1.sslip.io");

        // Set CloudEvent header:
        properties.put(KnativeHeaders.CONTENT_TYPE, "application/json");
        properties.put(KnativeHeaders.CE_ID, "1234");
        properties.put(KnativeHeaders.CE_SPECVERSION, "1.0");
        properties.put(KnativeHeaders.CE_TYPE, "some-type");
        properties.put(KnativeHeaders.CE_SOURCE, "java-client");

        // Initialize a Knative producer:
        KnativeProducerImpl producer = new KnativeProducerImpl();
        producer.init(properties);

        // Set CloudEvent message data:
        String s = "Hello Knative from EventMesh!";

        // Send CloudEvent message to cloudevents-player:
        producer.sendOneway(KnativeMessageFactory.createWriter(s));
    }
}
