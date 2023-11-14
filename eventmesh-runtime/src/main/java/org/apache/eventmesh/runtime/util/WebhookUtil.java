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
import org.apache.eventmesh.common.utils.LogUtils;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import lombok.experimental.UtilityClass;

/**
 * Utility class for implementing CloudEvents Http Webhook spec
 *
 * @see <a href="https://github.com/cloudevents/spec/blob/v1.0.2/cloudevents/http-webhook.md">CloudEvents Http Webhook</a>
 */
@Slf4j
@UtilityClass
public class WebhookUtil {

    private final String CONTENT_TYPE_HEADER = "Content-Type";
    private final String REQUEST_ORIGIN_HEADER = "WebHook-Request-Origin";
    private final String ALLOWED_ORIGIN_HEADER = "WebHook-Allowed-Origin";

    private final Map<String, AuthService> AUTH_SERVICES_MAP = new ConcurrentHashMap<>();

    public boolean obtainDeliveryAgreement(final CloseableHttpClient httpClient,
        final String targetUrl,
        final String requestOrigin) {

        LogUtils.info(log, "obtain webhook delivery agreement for url: {}", targetUrl);

        final HttpOptions builder = new HttpOptions(targetUrl);
        builder.addHeader(REQUEST_ORIGIN_HEADER, requestOrigin);

        try (CloseableHttpResponse response = httpClient.execute(builder)) {
            String allowedOrigin = null;

            if (response.getLastHeader(ALLOWED_ORIGIN_HEADER) != null) {
                allowedOrigin = response.getLastHeader(ALLOWED_ORIGIN_HEADER).getValue();
            }
            return StringUtils.isEmpty(allowedOrigin)
                || "*".equals(allowedOrigin) || allowedOrigin.equalsIgnoreCase(requestOrigin);
        } catch (Exception e) {
            LogUtils.error(log, "HTTP Options Method is not supported at the Delivery Target: {}, "
                + "unable to obtain the webhook delivery agreement.", targetUrl);
        }
        return true;
    }

    public void setWebhookHeaders(final HttpPost builder,
        final String contentType,
        final String requestOrigin,
        final String urlAuthType) {
        builder.setHeader(CONTENT_TYPE_HEADER, contentType);
        builder.setHeader(REQUEST_ORIGIN_HEADER, requestOrigin);

        final Map<String, String> authParam = getHttpAuthParam(urlAuthType);
        if (authParam != null) {
            authParam.forEach((k, v) -> builder.addHeader(new BasicHeader(k, v)));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getHttpAuthParam(final String authType) {
        if (StringUtils.isEmpty(authType)) {
            return new HashMap<String, String>();
        }

        final AuthService authService = getHttpAuthPlugin(authType);
        return authService != null ? authService.getAuthParams() : null;
    }

    private AuthService getHttpAuthPlugin(final String pluginType) {
        if (AUTH_SERVICES_MAP.containsKey(pluginType)) {
            return AUTH_SERVICES_MAP.get(pluginType);
        }

        final AuthService authService = EventMeshExtensionFactory.getExtension(AuthService.class, pluginType);
        Objects.requireNonNull(authService, "authService can not be null");
        authService.init();
        AUTH_SERVICES_MAP.put(pluginType, authService);
        return authService;
    }
}
