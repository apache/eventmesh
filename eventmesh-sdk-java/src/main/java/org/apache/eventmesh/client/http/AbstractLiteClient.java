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

import org.apache.eventmesh.client.http.conf.LiteClientConfig;
import org.apache.eventmesh.client.http.ssl.MyX509TrustManager;
import org.apache.eventmesh.client.http.util.HttpLoadBalanceUtils;
import org.apache.eventmesh.common.loadbalance.LoadBalanceSelector;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.security.SecureRandom;

public abstract class AbstractLiteClient {

    public Logger logger = LoggerFactory.getLogger(AbstractLiteClient.class);

    protected LiteClientConfig liteClientConfig;

    protected LoadBalanceSelector<String> eventMeshServerSelector;

    public AbstractLiteClient(LiteClientConfig liteClientConfig) {
        this.liteClientConfig = liteClientConfig;
    }

    public void start() throws Exception {
        eventMeshServerSelector = HttpLoadBalanceUtils.createEventMeshServerLoadBalanceSelector(liteClientConfig);
    }

    public LiteClientConfig getLiteClientConfig() {
        return liteClientConfig;
    }

    public void shutdown() throws Exception {
        logger.info("AbstractLiteClient shutdown");
    }

    public CloseableHttpClient setHttpClient() throws Exception {
        if (!liteClientConfig.isUseTls()) {
            return HttpClients.createDefault();
        }
        SSLContext sslContext = null;
        try {
            String protocol = System.getProperty("ssl.client.protocol", "TLSv1.1");
            TrustManager[] tm = new TrustManager[]{new MyX509TrustManager()};
            sslContext = SSLContext.getInstance(protocol);
            sslContext.init(null, tm, new SecureRandom());
            return HttpClients.custom().setSSLContext(sslContext)
                    .setSSLHostnameVerifier(new DefaultHostnameVerifier()).build();
        } catch (Exception e) {
            logger.error("Error in creating HttpClient.", e);
            throw e;
        }
    }
}
