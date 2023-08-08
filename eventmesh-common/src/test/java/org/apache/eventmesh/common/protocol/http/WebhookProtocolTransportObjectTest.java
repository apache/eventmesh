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

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class WebhookProtocolTransportObjectTest {

    private WebhookProtocolTransportObject webhookProtocolTransportObject;

    private WebhookProtocolTransportObject.WebhookProtocolTransportObjectBuilder webhookProtocolTransportObjectBuilder;

    @Before
    public void setUp() {
        webhookProtocolTransportObject = new WebhookProtocolTransportObject("cloudEventId", "eventType",
            "cloudEventName", "cloudEventSource", "dataContentType", new byte[]{(byte) 0});
        webhookProtocolTransportObjectBuilder = WebhookProtocolTransportObject.builder();
    }

    @Test
    public void testSetCloudEventId() {
        webhookProtocolTransportObject.setCloudEventId("d0b29520-2bba-11ee-877b-2b18ac132e64");
        Assert.assertEquals("d0b29520-2bba-11ee-877b-2b18ac132e64", webhookProtocolTransportObject.getCloudEventId());
    }

    @Test
    public void testSetEventType() {
        webhookProtocolTransportObject.setEventType("github.all");
        Assert.assertEquals("github.all", webhookProtocolTransportObject.getEventType());
    }

    @Test
    public void testSetCloudEventName() {
        webhookProtocolTransportObject.setCloudEventName("github-eventmesh");
        Assert.assertEquals("github-eventmesh", webhookProtocolTransportObject.getCloudEventName());
    }

    @Test
    public void testSetCloudEventSource() {
        webhookProtocolTransportObject.setCloudEventSource("www.github.com");
        Assert.assertEquals("www.github.com", webhookProtocolTransportObject.getCloudEventSource());
    }

    @Test
    public void testSetDataContentType() {
        webhookProtocolTransportObject.setDataContentType("application/json");
        Assert.assertEquals("application/json", webhookProtocolTransportObject.getDataContentType());
    }

    @Test
    public void testSetBody() {
        Map<String, Object> hookMap = new HashMap<>();
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("insecureSsl", "0");
        configMap.put("url", "http://eventmesh.example.com:10106/webhook/github/eventmesh/all");
        hookMap.put("type", "Repository");
        hookMap.put("name", "web");
        hookMap.put("events", Collections.singletonList("*"));
        hookMap.put("config", configMap);
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("zen", "Design for failure.");
        bodyMap.put("hook", hookMap);
        bodyMap.put("hookId", 425906842);
        byte[] originalBody = webhookProtocolTransportObject.getBody();
        byte[] body = Objects.requireNonNull(JsonUtils.toJSONString(bodyMap)).getBytes(Constants.DEFAULT_CHARSET);
        Assert.assertNotEquals(body, originalBody);
        webhookProtocolTransportObject.setBody(body);
        byte[] responseBody = webhookProtocolTransportObject.getBody();
        Assert.assertNotNull(responseBody);
        Assert.assertEquals(body, responseBody);
    }

    @Test
    public void testBuilder() {
        WebhookProtocolTransportObject.WebhookProtocolTransportObjectBuilder result = WebhookProtocolTransportObject.builder();
        Assert.assertNotNull(result);
        Assert.assertNotEquals(webhookProtocolTransportObjectBuilder, result);
    }
}
