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

package org.apache.eventmesh.connector.http.source.connector;


import org.apache.eventmesh.common.config.connector.http.HttpSourceConfig;
import org.apache.eventmesh.common.config.connector.http.SourceConnectorConfig;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

class HttpSourceConnectorTest {

    private HttpSourceConnector connector;
    private SourceConnectorConfig config;
    private OkHttpClient httpClient;
    private String url;
    private final String expectedMessage = "testHttpMessage";

    @BeforeEach
    void setUp() throws Exception {
        connector = new HttpSourceConnector();
        HttpSourceConfig sourceConfig = (HttpSourceConfig) ConfigUtil.parse(connector.configClass());
        config = sourceConfig.getConnectorConfig();
        connector.init(sourceConfig);
        connector.start();

        url = new URL("http", "127.0.0.1", config.getPort(), config.getPath()).toString();
        httpClient = new OkHttpClient();
    }

    @Test
    void testPoll() throws Exception {
        final int batchSize = 10;
        // test binary content mode
        for (int i = 0; i < batchSize; i++) {
            try (Response resp = mockBinaryRequest()) {
                Assertions.assertEquals(200, resp.code());
            }
        }
        List<ConnectRecord> res = connector.poll();
        Assertions.assertEquals(batchSize, res.size());
        for (ConnectRecord r : res) {
            Assertions.assertEquals(expectedMessage, new String((byte[]) r.getData()));
        }

        // test structured content mode
        for (int i = 0; i < batchSize; i++) {
            try (Response resp = mockStructuredRequest()) {
                Assertions.assertEquals(200, resp.code());
            }
        }
        res = connector.poll();
        Assertions.assertEquals(batchSize, res.size());
        for (ConnectRecord r : res) {
            Assertions.assertEquals(expectedMessage, new String((byte[]) r.getData()));
        }

        // test invalid requests
        Request request = new Request.Builder()
            .url(url)
            .addHeader("Content-Type", "text/plain")
            .addHeader("ce-id", String.valueOf(UUID.randomUUID()))
            .build();

        try (Response resp = httpClient.newCall(request).execute()) {
            // verify the response code
            Assertions.assertEquals(405, resp.code());
        }

    }

    Response mockBinaryRequest() throws Exception {

        RequestBody body = RequestBody.create(expectedMessage, MediaType.parse("text/plain"));

        Request request = new Request.Builder()
            .url(url)
            .addHeader("Content-Type", "text/plain")
            .addHeader("ce-id", String.valueOf(UUID.randomUUID()))
            .addHeader("ce-specversion", "1.0")
            .addHeader("ce-type", "com.example.someevent")
            .addHeader("ce-source", "/mycontext")
            .addHeader("ce-subject", "test")
            .post(body)
            .build();

        return httpClient.newCall(request).execute();
    }

    Response mockStructuredRequest() throws Exception {
        // create a CloudEvent
        TestEvent event = new TestEvent();
        event.id = String.valueOf(UUID.randomUUID());
        event.specversion = "1.0";
        event.type = "com.example.someevent";
        event.source = "/mycontext";
        event.subject = "test";
        event.datacontenttype = "text/plain";
        event.data = expectedMessage;

        RequestBody body = RequestBody.create(Objects.requireNonNull(JsonUtils.toJSONString(event)), MediaType.parse("application/cloudevents+json"));

        Request request = new Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/cloudevents+json")
            .post(body)
            .build();

        return httpClient.newCall(request).execute();

    }

    @AfterEach
    void tearDown() {
        connector.stop();
        httpClient.dispatcher().executorService().shutdown();
    }

    class TestEvent {

        public String specversion;
        public String type;
        public String source;
        public String subject;
        public String datacontenttype;
        public String id;

        public String data;
    }
}