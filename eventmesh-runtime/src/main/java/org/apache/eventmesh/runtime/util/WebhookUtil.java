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

import org.apache.eventmesh.api.auth.AuthService;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for implementing CloudEvents Http Webhook spec
 *
 * @see <a href="https://github.com/cloudevents/spec/blob/v1.0.2/cloudevents/http-webhook.md">CloudEvents Http Webhook</a>
 */
public class WebhookUtil {

    private static final Logger logger = LoggerFactory.getLogger(WebhookUtil.class.getName());

    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String REQUEST_ORIGIN_HEADER = "WebHook-Request-Origin";
    private static final String ALLOWED_ORIGIN_HEADER = "WebHook-Allowed-Origin";

    private static final Map<String, AuthService> authServices = new ConcurrentHashMap<>();

    public static boolean obtainDeliveryAgreement(CloseableHttpClient httpClient, String targetUrl, String requestOrigin) {
        logger.info("obtain webhook delivery agreement for url: {}", targetUrl);
        HttpOptions builder = new HttpOptions(targetUrl);
        builder.addHeader(REQUEST_ORIGIN_HEADER, requestOrigin);

        try (CloseableHttpResponse response = httpClient.execute(builder)) {
            String allowedOrigin = response.getLastHeader(ALLOWED_ORIGIN_HEADER).getValue();
            return StringUtils.isEmpty(allowedOrigin) ||
                allowedOrigin.equals("*") || allowedOrigin.equalsIgnoreCase(requestOrigin);
        } catch (Exception e) {
            logger.warn("HTTP Options Method is not supported at the Delivery Target: {},"
                + " unable to obtain the webhook delivery agreement.", targetUrl);
        }
        return true;
    }

    public static void setWebhookHeaders(HttpPost builder, String contentType, String requestOrigin, String urlAuthType) {
        builder.setHeader(CONTENT_TYPE_HEADER, contentType);
        builder.setHeader(REQUEST_ORIGIN_HEADER, requestOrigin);

        Map<String, String> authParam = getHttpAuthParam(urlAuthType);
        if (authParam != null) {
            authParam.forEach((k, v) -> builder.addHeader(new BasicHeader(k, v)));
        }
    }

    private static Map<String, String> getHttpAuthParam(String authType) {
        if (StringUtils.isEmpty(authType)) {
            return null;
        }
        AuthService authService = getHttpAuthPlugin(authType);
        if (authService != null) {
            return authService.getAuthParams();
        } else {
            return null;
        }
    }

    private static AuthService getHttpAuthPlugin(String pluginType) {
        if (authServices.containsKey(pluginType)) {
            return authServices.get(pluginType);
        }

        AuthService authService = EventMeshExtensionFactory.getExtension(AuthService.class, pluginType);

        if (authService == null) {
            logger.error("can't load the authService plugin, please check.");
            throw new RuntimeException("doesn't load the authService plugin, please check.");
        }
        try {
            authService.init();
            authServices.put(pluginType, authService);
            return authService;
        } catch (Exception e) {
            logger.error("Error in initializing authService", e);
        }
        return null;
    }
}
