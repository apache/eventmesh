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

package org.apache.eventmesh.connector.http.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpSinkConnector implements SinkConnector {

    private String url;

    @Override
    public void init(Properties props) {
        url = props.getProperty("connector.url", "http://localhost:9090/sink");
    }

    @Override
    public void put(List<CloudEvent> events) {
        for (CloudEvent event : events) {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/octet-stream");
                conn.getOutputStream().write(event.getData() != null ? event.getData().toBytes() : new byte[0]);
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                log.warn("http sink: {}", e.toString());
            }
        }
    }

    @Override
    public void commit(List<CloudEvent> written) {

    }
}
