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

package org.apache.eventmesh.common.protocol.http;

import org.junit.Assert;
import org.junit.Test;

public class WebhookProtocolTransportObjectTest {

    WebhookProtocolTransportObject webhookProtocolTransportObject = new WebhookProtocolTransportObject("cloudEventId", "eventType",
        "cloudEventName", "cloudEventSource", "dataContentType", new byte[] {(byte) 0});

    @Test
    public void testSetCloudEventId() {
        webhookProtocolTransportObject.setCloudEventId("cloudEventId");
    }

    @Test
    public void testSetEventType() {
        webhookProtocolTransportObject.setEventType("eventType");
    }

    @Test
    public void testSetCloudEventName() {
        webhookProtocolTransportObject.setCloudEventName("cloudEventName");
    }

    @Test
    public void testSetCloudEventSource() {
        webhookProtocolTransportObject.setCloudEventSource("cloudEventSource");
    }

    @Test
    public void testSetDataContentType() {
        webhookProtocolTransportObject.setDataContentType("dataContentType");
    }

    @Test
    public void testSetBody() {
        webhookProtocolTransportObject.setBody(new byte[]{(byte) 0});
    }

    @Test
    public void testBuilder() {
        WebhookProtocolTransportObject.WebhookProtocolTransportObjectBuilder result = WebhookProtocolTransportObject.builder();
        Assert.assertNotNull(result);
    }
}
