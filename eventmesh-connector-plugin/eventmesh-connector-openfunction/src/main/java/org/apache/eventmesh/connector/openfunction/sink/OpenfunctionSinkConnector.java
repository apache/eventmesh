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

package org.apache.eventmesh.connector.openfunction.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * New-architecture OpenFunction sink connector: invokes the function's HTTP trigger (Knative
 * serving style) with each CloudEvent. A non-2xx response throws so the runtime does not ACK.
 */
@Slf4j
public class OpenfunctionSinkConnector implements SinkConnector {

    private String functionUrl;
    private int timeoutMs;

    @Override
    public void init(Properties props) {
        this.functionUrl = props.getProperty("connector.functionUrl", "http://localhost:8081/function");
        this.timeoutMs = Integer.parseInt(props.getProperty("connector.timeoutMs", "30000"));
    }

    @Override
    public void put(List<CloudEvent> events) {
        for (CloudEvent event : events) {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(functionUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);
                conn.setRequestProperty("Content-Type",
                    event.getDataContentType() != null ? event.getDataContentType() : "application/octet-stream");
                conn.setRequestProperty("Ce-Id", event.getId());
                conn.setRequestProperty("Ce-Type", event.getType());
                conn.setRequestProperty("Ce-Source", event.getSource().toString());
                conn.getOutputStream().write(
                    event.getData() != null ? event.getData().toBytes() : new byte[0]);
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code < 200 || code >= 300) {
                    throw new RuntimeException("openfunction sink http " + code + " for event " + event.getId());
                }
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException("openfunction sink failed for event " + event.getId() + ": " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void commit(List<CloudEvent> written) {
        // Stateless invoke: the 2xx response is the write ack.
    }
}
