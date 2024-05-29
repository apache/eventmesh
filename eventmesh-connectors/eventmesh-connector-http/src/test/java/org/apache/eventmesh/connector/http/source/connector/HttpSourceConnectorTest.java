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

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpSourceConnectorTest {

    private HttpSourceConnector connector;
    private SourceConnectorConfig config;
    private CloseableHttpClient httpClient;
    private String uri;
    private final String expectedMessage = "testHttpMessage";

    @BeforeEach
    void setUp() throws Exception {
        connector = new HttpSourceConnector();
        HttpSourceConfig sourceConfig = (HttpSourceConfig) ConfigUtil.parse(connector.configClass());
        config = sourceConfig.getConnectorConfig();
        connector.init(sourceConfig);
        connector.start();

        uri = new URIBuilder().setScheme("http").setHost("127.0.0.1").setPort(config.getPort()).setPath(config.getPath()).build().toString();

        httpClient = HttpClients.createDefault();
    }

    @Test
    void testPoll() throws Exception {
        final int batchSize = 10;
        // test binary content mode
        for (int i = 0; i < batchSize; i++) {
            HttpResponse resp = mockBinaryRequest();
            Assertions.assertEquals(resp.getStatusLine().getStatusCode(), HttpStatus.SC_OK);

        }
        List<ConnectRecord> res = connector.poll();
        Assertions.assertEquals(batchSize, res.size());
        for (ConnectRecord r : res) {
            Assertions.assertEquals(expectedMessage, new String((byte[]) r.getData()));
        }

        // test structured content mode
        for (int i = 0; i < batchSize; i++) {
            HttpResponse resp = mockStructuredRequest();
            Assertions.assertEquals(resp.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
        }
        res = connector.poll();
        Assertions.assertEquals(batchSize, res.size());
        for (ConnectRecord r : res) {
            Assertions.assertEquals(expectedMessage, new String((byte[]) r.getData()));
        }

        // test invalid requests
        HttpPost invalidPost = new HttpPost(uri);
        invalidPost.setHeader(HttpHeaders.CONTENT_TYPE, "text/plain");
        invalidPost.setHeader("ce-id", String.valueOf(UUID.randomUUID()));
        HttpResponse resp = httpClient.execute(invalidPost);
        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, resp.getStatusLine().getStatusCode());
    }

    HttpResponse mockBinaryRequest() throws Exception {
        HttpPost httpPost = new HttpPost(uri);
        httpPost.setHeader(HttpHeaders.CONTENT_TYPE, "text/plain");
        httpPost.setHeader("ce-id", String.valueOf(UUID.randomUUID()));
        httpPost.setHeader("ce-specversion", "1.0");
        httpPost.setHeader("ce-type", "com.example.someevent");
        httpPost.setHeader("ce-source", "/mycontext");
        httpPost.setHeader("ce-subject", "test");
        httpPost.setEntity(new StringEntity(expectedMessage));

        return httpClient.execute(httpPost);
    }

    HttpResponse mockStructuredRequest() throws Exception {
        HttpPost httpPost = new HttpPost(uri);
        // according to the CloudEvent specification, a json format event MUST use the media type `application/cloudevents+json`
        httpPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/cloudevents+json");
        TestEvent event = new TestEvent();
        event.id = String.valueOf(UUID.randomUUID());
        event.specversion = "1.0";
        event.type = "com.example.someevent";
        event.source = "/mycontext";
        event.subject = "test";
        event.datacontenttype = "text/plain";
        event.data = expectedMessage;
        httpPost.setEntity(new StringEntity(JsonUtils.toJSONString(event)));

        return httpClient.execute(httpPost);
    }

    @AfterEach
    void tearDown() throws Exception {
        connector.stop();
        httpClient.close();
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