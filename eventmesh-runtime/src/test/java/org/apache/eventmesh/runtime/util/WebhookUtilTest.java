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

package org.apache.eventmesh.runtime.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import org.apache.eventmesh.api.auth.AuthService;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class WebhookUtilTest {

    @Test
    public void testObtainDeliveryAgreement() throws Exception {
        // normal case
        try (CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
            CloseableHttpResponse response = mock(CloseableHttpResponse.class);
            CloseableHttpClient httpClient2 = mock(CloseableHttpClient.class)) {

            Mockito.when(response.getLastHeader("WebHook-Allowed-Origin"))
                .thenReturn(new BasicHeader("WebHook-Allowed-Origin", "*"));
            Mockito.when(httpClient.execute(any())).thenReturn(response);
            Assertions.assertTrue(WebhookUtil.obtainDeliveryAgreement(httpClient, "https://eventmesh.apache.org", "*"),
                "match logic must return true");

            // abnormal case
            Mockito.when(httpClient2.execute(any())).thenThrow(new RuntimeException());
            Assertions.assertTrue(WebhookUtil.obtainDeliveryAgreement(httpClient2, "xxx", "*"),
                "when throw exception ,default return true");
        }
    }

    @Test
    public void testSetWebhookHeaders() {
        final String authType = "auth-http-basic";
        final AuthService authService = mock(AuthService.class);
        doNothing().when(authService).init();
        final Map<String, String> authParams = new HashMap<>();
        final String key = "Authorization";
        final String value = "Basic ****";
        authParams.put(key, value);
        Mockito.when(authService.getAuthParams()).thenReturn(authParams);

        try (MockedStatic<EventMeshExtensionFactory> dummyStatic = Mockito.mockStatic(EventMeshExtensionFactory.class)) {
            dummyStatic.when(() -> EventMeshExtensionFactory.getExtension(AuthService.class, authType)).thenReturn(authService);
            final HttpPost post = new HttpPost();
            WebhookUtil.setWebhookHeaders(post, "application/json", "eventmesh.FT", authType);
            Assertions.assertEquals(post.getLastHeader(key).getValue(), value, "match expect value");
        }
    }
}
