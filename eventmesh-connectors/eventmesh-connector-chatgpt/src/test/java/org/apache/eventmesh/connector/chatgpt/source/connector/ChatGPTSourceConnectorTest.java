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

package org.apache.eventmesh.connector.chatgpt.source.connector;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.chatgpt.source.config.ChatGPTSourceConfig;
import org.apache.eventmesh.connector.chatgpt.source.config.ChatGPTSourceConnectorConfig;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatGPTSourceConnectorTest {

    private ChatGPTSourceConnector connector;
    private ChatGPTSourceConnectorConfig config;
    private CloseableHttpClient httpClient;
    private String uri;
    private final String expectedMessage = "Hello, can you tell me a story.";

    @BeforeEach
    void setUp() throws Exception {
        connector = new ChatGPTSourceConnector();
        ChatGPTSourceConfig sourceConfig = (ChatGPTSourceConfig) ConfigUtil.parse(connector.configClass());
        config = sourceConfig.getConnectorConfig();
        connector.init(sourceConfig);
        connector.start();

        uri = new URIBuilder().setScheme("http").setHost("127.0.0.1").setPort(config.getPort()).setPath(config.getPath()).build().toString();

        httpClient = HttpClients.createDefault();
    }

    @Test
    void testPoll() throws Exception {
        final int batchSize = 10;

        for (int i = 0; i < batchSize; i++) {
            HttpResponse resp = mockStructuredRequest();
            Assertions.assertEquals(resp.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
        }

        List<ConnectRecord> res = connector.poll();
        Assertions.assertEquals(batchSize, res.size());


        // test invalid requests
        HttpPost invalidPost = new HttpPost(uri);
        TestEvent event = new TestEvent();
        event.type = "com.example.someevent";
        event.source = "/mycontext";
        event.datacontenttype = "text/plain";
        event.prompt = expectedMessage;
        invalidPost.setEntity(new StringEntity(JsonUtils.toJSONString(event)));
        HttpResponse resp = httpClient.execute(invalidPost);
        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, resp.getStatusLine().getStatusCode());
    }


    HttpResponse mockStructuredRequest() throws Exception {
        HttpPost httpPost = new HttpPost(uri);
        TestEvent event = new TestEvent();
        event.type = "com.example.someevent";
        event.source = "/mycontext";
        event.subject = "test";
        event.datacontenttype = "text/plain";
        event.prompt = expectedMessage;
        httpPost.setEntity(new StringEntity(JsonUtils.toJSONString(event)));

        return httpClient.execute(httpPost);
    }

    @AfterEach
    void tearDown() throws Exception {
        connector.stop();
        httpClient.close();
    }

    class TestEvent {

        public String type;
        public String source;
        public String subject;
        public String datacontenttype;
        public String prompt;
    }
}