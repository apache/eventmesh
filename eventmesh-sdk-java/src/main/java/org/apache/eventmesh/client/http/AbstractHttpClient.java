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

package org.apache.eventmesh.client.http;

import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.ssl.MyX509TrustManager;
import org.apache.eventmesh.client.http.util.HttpLoadBalanceUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.loadbalance.LoadBalanceSelector;

import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractHttpClient implements AutoCloseable {

    protected EventMeshHttpClientConfig eventMeshHttpClientConfig;

    protected LoadBalanceSelector<String> eventMeshServerSelector;

    protected final CloseableHttpClient httpClient;

    public AbstractHttpClient(EventMeshHttpClientConfig eventMeshHttpClientConfig) throws EventMeshException {
        Preconditions.checkNotNull(eventMeshHttpClientConfig, "liteClientConfig can't be null");
        Preconditions.checkNotNull(eventMeshHttpClientConfig.getLiteEventMeshAddr(), "liteServerAddr can't be null");

        this.eventMeshHttpClientConfig = eventMeshHttpClientConfig;
        this.eventMeshServerSelector = HttpLoadBalanceUtils.createEventMeshServerLoadBalanceSelector(
                eventMeshHttpClientConfig);
        this.httpClient = setHttpClient();
    }

    @Override
    public void close() throws EventMeshException {
        try (final CloseableHttpClient ignore = this.httpClient) {
            // ignore
        } catch (IOException e) {
            throw new EventMeshException("Close http client error", e);
        }
    }

    private CloseableHttpClient setHttpClient() throws EventMeshException {
        if (!eventMeshHttpClientConfig.isUseTls()) {
            return HttpClients.createDefault();
        }
        SSLContext sslContext;
        try {
            // todo: config in properties file?
            String protocol = System.getProperty("ssl.client.protocol", "TLSv1.2");
            TrustManager[] tm = new TrustManager[]{new MyX509TrustManager()};
            sslContext = SSLContext.getInstance(protocol);
            sslContext.init(null, tm, new SecureRandom());
            // todo: custom client pool
            return HttpClients.custom()
                    .setSSLContext(sslContext)
                    .setSSLHostnameVerifier(new DefaultHostnameVerifier())
                    .build();
        } catch (Exception e) {
            log.error("Error in creating HttpClient.", e);
            throw new EventMeshException(e);
        }
    }

    protected String selectEventMesh() {
        // todo: target endpoint maybe destroy, should remove the bad endpoint
        if (eventMeshHttpClientConfig.isUseTls()) {
            return Constants.HTTPS_PROTOCOL_PREFIX + eventMeshServerSelector.select();
        } else {
            return Constants.HTTP_PROTOCOL_PREFIX + eventMeshServerSelector.select();
        }
    }
}
