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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class WebhookProtocolTransportObjectTest {

    private WebhookProtocolTransportObject webhookProtocolTransportObject;

    @Before
    public void setUp() {
        webhookProtocolTransportObject = WebhookProtocolTransportObject.builder().build();
    }

    @Test
    public void testSetCloudEventId() {
        webhookProtocolTransportObject.setCloudEventId("d0b29520-2bba-11ee-877b-2b18ac132e64");
    }

    @Test
    public void testSetEventType() {
        webhookProtocolTransportObject.setEventType("github.all");
    }

    @Test
    public void testSetCloudEventName() {
        webhookProtocolTransportObject.setCloudEventName("github-eventmesh");
    }

    @Test
    public void testSetCloudEventSource() {
        webhookProtocolTransportObject.setCloudEventSource("www.github.com");
    }

    @Test
    public void testSetDataContentType() {
        webhookProtocolTransportObject.setDataContentType("application/json");
    }

    @Test
    public void testSetBody() {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("user", "tom");
        bodyMap.put("password", "123456");
        webhookProtocolTransportObject.setBody(Objects.requireNonNull(JsonUtils.toJSONString(bodyMap)).getBytes(Constants.DEFAULT_CHARSET));
    }

    @Test
    public void testBuilder() {
        WebhookProtocolTransportObject.WebhookProtocolTransportObjectBuilder result = WebhookProtocolTransportObject.builder();
        Assert.assertNotNull(result);
    }
}
