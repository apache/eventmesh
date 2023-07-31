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

package org.apache.eventmesh.runtime.core.protocol.http.consumer;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HTTPClientPool {

    private final transient List<CloseableHttpClient> clients = Collections.synchronizedList(new ArrayList<>());

    private final int core;

    private static final int DEFAULT_MAX_TOTAL = 200;
    private static final int DEFAULT_IDLETIME_SECONDS = 30;

    private static final int CONNECT_TIMEOUT = 5000;
    private static final int SOCKET_TIMEOUT = 5000;

    private transient PoolingHttpClientConnectionManager connectionManager;

    public HTTPClientPool(final int core) {
        this.core = core <= 0 ? 1 : core;
    }

    public CloseableHttpClient getClient() {
        if (CollectionUtils.size(clients) < core) {
            final CloseableHttpClient client = getHttpClient(DEFAULT_MAX_TOTAL, DEFAULT_IDLETIME_SECONDS, null);
            clients.add(client);
            return client;
        }

        return clients.get(ThreadLocalRandom.current().nextInt(core, 2 * core) % core);
    }

    public void shutdown() throws IOException {
        synchronized (clients) {
            final Iterator<CloseableHttpClient> itr = clients.iterator();
            while (itr.hasNext()) {
                try (CloseableHttpClient client = itr.next()) {
                    itr.remove();
                }
            }
        }

        if (this.connectionManager == null) {
            this.connectionManager.close();
        }
    }

    //@SuppressWarnings("deprecation")
    public CloseableHttpClient getHttpClient(final int maxTotal, final int idleTimeInSeconds, final SSLContext sslContext) {

        SSLContext innerSSLContext = sslContext;
        try {
            innerSSLContext = innerSSLContext == null ? SSLContexts.custom().loadTrustMaterial(new TheTrustStrategy()).build() : innerSSLContext;

        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
            log.error("Get sslContext error", e);
            return HttpClients.createDefault();
        }

        if (connectionManager == null) {
            final SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(innerSSLContext, NoopHostnameVerifier.INSTANCE);
            final Registry<ConnectionSocketFactory> socketFactoryRegistry
                = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslsf)
                .build();
            connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            connectionManager.setDefaultMaxPerRoute(maxTotal);
            connectionManager.setMaxTotal(maxTotal);
        }

        RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT)
            .setConnectionRequestTimeout(CONNECT_TIMEOUT)
            .setSocketTimeout(SOCKET_TIMEOUT).build();

        return HttpClients.custom()
            .setDefaultRequestConfig(config)
            .setConnectionManager(connectionManager)
            .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())
            .evictIdleConnections(idleTimeInSeconds, TimeUnit.SECONDS)
            .setConnectionReuseStrategy(new DefaultConnectionReuseStrategy())
            .setRetryHandler(new DefaultHttpRequestRetryHandler())
            .build();
    }

    private static class TheTrustStrategy implements TrustStrategy {

        @Override
        public boolean isTrusted(final X509Certificate[] chain, final String authType) {
            return true;
        }
    }
}
