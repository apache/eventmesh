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

package org.apache.eventmesh.runtime.protocol.processor;

import static org.mockito.ArgumentMatchers.any;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.core.protocol.http.processor.WebHookProcessor;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.receive.WebHookController;
import org.apache.eventmesh.webhook.receive.WebHookMQProducer;
import org.apache.eventmesh.webhook.receive.storage.HookConfigOperationManager;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.data.BytesCloudEventData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;

@RunWith(MockitoJUnitRunner.class)
public class WebHookProcessorTest {

    @Mock
    private transient HookConfigOperationManager hookConfigOperationManager;
    @Mock
    private transient WebHookMQProducer webHookMQProducer;
    @Spy
    private transient ProtocolAdaptor<ProtocolTransportObject> protocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor("webhook");

    private transient ArgumentCaptor<CloudEvent> captor = ArgumentCaptor.forClass(CloudEvent.class);

    @InjectMocks
    private transient WebHookController controller = new WebHookController();

    @Before
    public void init() throws Exception {
        Mockito.when(hookConfigOperationManager.queryWebHookConfigById(any())).thenReturn(buildMockWebhookConfig());
        Mockito.doNothing().when(webHookMQProducer).send(captor.capture(), any());
    }

    @Test
    public void testHandler() {
        try {
            WebHookProcessor processor = new WebHookProcessor();
            processor.setWebHookController(controller);
            processor.handler(buildMockWebhookRequest());

            CloudEvent msgSendToMq = captor.getValue();
            Assert.assertNotNull(msgSendToMq);
            Assert.assertTrue(StringUtils.isNoneBlank(msgSendToMq.getId()));
            Assert.assertEquals("www.github.com", msgSendToMq.getSource().getPath());
            Assert.assertEquals("github.ForkEvent", msgSendToMq.getType());
            Assert.assertEquals(BytesCloudEventData.wrap("\"mock_data\":0".getBytes(StandardCharsets.UTF_8)), msgSendToMq.getData());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    private HttpRequest buildMockWebhookRequest() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeBytes("\"mock_data\":0".getBytes(StandardCharsets.UTF_8));

        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/webhook/github/eventmesh/all", buffer);
        request.headers().set("content-type", "application/json");
        // encrypt method see: GithubProtocol
        request.headers().set("x-hub-signature-256", "sha256=ddb62e1182e2e6d364c0b5d03f2413fd5d1f68d99d1a4b3873e0d6850650d4b3");
        return request;
    }

    private WebHookConfig buildMockWebhookConfig() {
        WebHookConfig config = new WebHookConfig();
        config.setCallbackPath("/webhook/github/eventmesh/all");
        config.setManufacturerName("github");
        config.setManufacturerDomain("www.github.com");
        config.setManufacturerEventName("ForkEvent");
        config.setContentType("application/json");
        config.setSecret("secret");
        config.setCloudEventName("github-eventmesh");
        config.setCloudEventIdGenerateMode("uuid");
        return config;
    }
}
