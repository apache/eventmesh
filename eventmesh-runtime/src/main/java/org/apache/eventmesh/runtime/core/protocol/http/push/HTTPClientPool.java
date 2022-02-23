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

package org.apache.eventmesh.runtime.core.protocol.http.push;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HTTPClientPool {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private List<CloseableHttpClient> clients = Collections.synchronizedList(new ArrayList<>());

    private int core = 1;

    public HTTPClientPool(int core) {
        this.core = core;
    }

    public CloseableHttpClient getClient() {
        if (CollectionUtils.size(clients) < core) {
            CloseableHttpClient client = getHttpClient(200, 30, null);
            clients.add(client);
            return client;
        }
        return clients.get(RandomUtils.nextInt(core, 2 * core) % core);
    }

    public void shutdown() throws Exception {
        Iterator<CloseableHttpClient> itr = clients.iterator();
        while (itr.hasNext()) {
            CloseableHttpClient client = itr.next();
            client.close();
            itr.remove();
        }
    }

    public CloseableHttpClient getHttpClient(int maxTotal, int idleTimeInSeconds, SSLContext sslContext) {
        try {
            if (sslContext == null) {
                sslContext = SSLContexts.custom().loadTrustMaterial(new TheTrustStrategy()).build();
            }
        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
            logger.error("Get sslContext error: {}", e.getMessage());
            return HttpClients.createDefault();
        }

        HostnameVerifier hostnameVerifier = SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;

        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
        Registry<ConnectionSocketFactory> socketFactoryRegistry
            = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", PlainConnectionSocketFactory.getSocketFactory())
            .register("https", sslsf).build();
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);

        connectionManager.setDefaultMaxPerRoute(maxTotal);
        connectionManager.setMaxTotal(maxTotal);
        return HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())
            .evictIdleConnections(idleTimeInSeconds, TimeUnit.SECONDS)
            .setConnectionReuseStrategy(new DefaultConnectionReuseStrategy())
            .setRetryHandler(new DefaultHttpRequestRetryHandler())
            .build();
    }

    public static class TheTrustStrategy implements TrustStrategy {
        @Override
        public boolean isTrusted(X509Certificate[] arg0, String arg1) {
            return true;
        }
    }
}
