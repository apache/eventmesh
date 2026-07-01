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

package org.apache.eventmesh.connector.http.source;


import org.apache.eventmesh.common.config.connector.http.HttpSourceConfig;
import org.apache.eventmesh.common.config.connector.http.SourceConnectorConfig;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


class HttpSourceConnectorTest {

    private static HttpSourceConnector connector;
    private static String url;
    private static final String expectedMessage = "testHttpMessage";
    private static final int batchSize = 10;


    @BeforeAll
    static void setUpAll() throws Exception {
        connector = new HttpSourceConnector();
        final HttpSourceConfig sourceConfig = (HttpSourceConfig) ConfigUtil.parse(connector.configClass());
        final SourceConnectorConfig config = sourceConfig.getConnectorConfig();
        // initialize and start the connector
        connector.init(sourceConfig);
        connector.start();

        // wait for the connector to start
        long timeout = 5000; // 5 seconds
        long start = System.currentTimeMillis();
        while (!connector.isStarted()) {
            if (System.currentTimeMillis() - start > timeout) {
                // timeout
                Assertions.fail("Failed to start the connector");
            } else {
                Thread.sleep(100);
            }
        }

        url = new URL("http", "127.0.0.1", config.getPort(), config.getPath()).toString();
    }

    @AfterAll
    static void tearDownAll() throws IOException {
        connector.stop();
    }


    @Test
    void testPollForBinaryRequest() {
        for (int i = 0; i < batchSize; i++) {
            try {
                // Set the request body
                StringEntity entity = new StringEntity(expectedMessage, ContentType.TEXT_PLAIN);

                Request.post(url)
                    .addHeader("Content-Type", "text/plain")
                    .addHeader("ce-id", String.valueOf(UUID.randomUUID()))
                    .addHeader("ce-specversion", "1.0")
                    .addHeader("ce-type", "com.example.someevent")
                    .addHeader("ce-source", "/mycontext")
                    .addHeader("ce-subject", "test")
                    .body(entity)
                    .execute()
                    .handleResponse(res -> {
                        Assertions.assertEquals(HttpStatus.SC_OK, res.getCode());
                        return null;
                    });
            } catch (IOException e) {
                Assertions.fail("Failed to send request", e);
            }
        }
        List<ConnectRecord> res = connector.poll();
        Assertions.assertEquals(batchSize, res.size());
        for (ConnectRecord r : res) {
            Assertions.assertEquals(expectedMessage, new String((byte[]) r.getData()));
        }
    }

    @Test
    void testPollForStructuredRequest() {
        for (int i = 0; i < batchSize; i++) {
            try {
                // Create a CloudEvent
                TestEvent event = new TestEvent();
                event.id = String.valueOf(UUID.randomUUID());
                event.specversion = "1.0";
                event.type = "com.example.someevent";
                event.source = "/mycontext";
                event.subject = "test";
                event.datacontenttype = "text/plain";
                event.data = expectedMessage;

                // Set the request body
                StringEntity entity = new StringEntity(Objects.requireNonNull(JsonUtils.toJSONString(event)), ContentType.APPLICATION_JSON);

                // Send the request and return the response
                Request.post(url)
                    .addHeader("Content-Type", "application/cloudevents+json")
                    .body(entity)
                    .execute()
                    .handleResponse(res -> {
                        Assertions.assertEquals(HttpStatus.SC_OK, res.getCode());
                        return null;
                    });
            } catch (IOException e) {
                Assertions.fail("Failed to send request", e);
            }
        }
        List<ConnectRecord> res = connector.poll();
        Assertions.assertEquals(batchSize, res.size());
        for (ConnectRecord r : res) {
            Assertions.assertEquals(expectedMessage, new String((byte[]) r.getData()));
        }
    }


    @Test
    void testPollForInvalidRequest() {
        // Send a bad request.
        try {
            Request.post(url)
                .addHeader("Content-Type", "text/plain")
                .execute()
                .handleResponse(res -> {
                    // Check the response code
                    Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, res.getCode());
                    return null;
                });
        } catch (IOException e) {
            Assertions.fail("Failed to send request", e);
        }
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