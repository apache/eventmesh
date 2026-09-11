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

package org.apache.eventmesh.connector.prometheus.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * New-architecture Prometheus sink connector: pushes metric samples (event data formatted as
 * Prometheus text exposition format) to a Pushgateway, the official push channel for
 * batch-style jobs. A non-2xx response throws so the runtime does not ACK (redelivery).
 */
@Slf4j
public class PrometheusSinkConnector implements SinkConnector {

    private String pushgatewayUrl;
    private String job;
    private String instance;
    private int timeoutMs;

    @Override
    public void init(Properties props) {
        this.pushgatewayUrl = props.getProperty("connector.pushgatewayUrl", "http://localhost:9091");
        this.job = props.getProperty("connector.job", "eventmesh");
        this.instance = props.getProperty("connector.instance", "connector-1");
        this.timeoutMs = Integer.parseInt(props.getProperty("connector.timeoutMs", "10000"));
    }

    @Override
    public void put(List<CloudEvent> events) {
        // Merge the batch into one text exposition payload and push it in a single request.
        StringBuilder payload = new StringBuilder();
        for (CloudEvent event : events) {
            byte[] data = event.getData() != null ? event.getData().toBytes() : new byte[0];
            String text = new String(data, StandardCharsets.UTF_8).trim();
            if (!text.isEmpty()) {
                payload.append(text).append('\n');
            }
        }
        if (payload.length() == 0) {
            return;
        }
        try {
            String url = pushgatewayUrl + "/metrics/job/" + urlEncode(job) + "/instance/" + urlEncode(instance);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("Content-Type", "text/plain; version=0.0.4");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            conn.disconnect();
            if (code < 200 || code >= 300) {
                throw new RuntimeException("prometheus sink pushgateway http " + code);
            }
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("prometheus sink failed: " + e.getMessage(), e);
        }
    }

    private static String urlEncode(String raw) {
        return raw.replace("/", "%2F").replace(" ", "%20");
    }

    @Override
    public void commit(List<CloudEvent> written) {
        // The Pushgateway 2xx response is the write ack.
    }
}
