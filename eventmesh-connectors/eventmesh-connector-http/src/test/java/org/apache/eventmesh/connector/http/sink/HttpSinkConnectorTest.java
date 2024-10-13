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


import static org.mockserver.model.HttpRequest.request;

import org.apache.eventmesh.common.config.connector.http.HttpSinkConfig;
import org.apache.eventmesh.common.config.connector.http.HttpWebhookConfig;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;


public class HttpSinkConnectorTest {

    private HttpSinkConnector sinkConnector;

    private HttpSinkConfig sinkConfig;

    private URL url;

    private ClientAndServer mockServer;

    private static final AtomicInteger counter = new AtomicInteger(0);

    @BeforeEach
    void before() throws Exception {
        // init sinkConnector
        sinkConnector = new HttpSinkConnector();
        sinkConfig = (HttpSinkConfig) ConfigUtil.parse(sinkConnector.configClass());
        sinkConnector.init(this.sinkConfig);
        sinkConnector.start();

        url = new URL(sinkConfig.connectorConfig.getUrls()[0]);
        // start mockServer
        mockServer = ClientAndServer.startClientAndServer(url.getPort());
        mockServer.reset()
            .when(
                request()
                    .withMethod("POST")
                    .withPath(url.getPath())
            )
            .respond(
                httpRequest -> {
                    // Increase the number of requests received
                    counter.incrementAndGet();
                    return HttpResponse.response()
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withStatusCode(HttpStatus.SC_OK)
                        .withBody(new JSONObject()
                            .fluentPut("code", 0)
                            .fluentPut("message", "success")
                            .toJSONString()
                        ); // .withDelay(TimeUnit.SECONDS, 10);
                }
            );

    }

    @AfterEach
    void after() throws Exception {
        this.sinkConnector.stop();
        this.mockServer.close();
    }

    @Test
    void testPut() throws Exception {
        // Create a list of ConnectRecord
        final int size = 10;
        List<ConnectRecord> connectRecords = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ConnectRecord record = createConnectRecord();
            connectRecords.add(record);
        }
        // Put ConnectRecord
        sinkConnector.put(connectRecords);

        // wait for receiving request
        final int times = 5000; // 5 seconds
        long start = System.currentTimeMillis();
        while (counter.get() < size) {
            if (System.currentTimeMillis() - start > times) {
                // timeout
                Assertions.fail("The number of requests received=" + counter.get() + " is less than the number of ConnectRecord=" + size);
            } else {
                Thread.sleep(100);
            }
        }

        // verify response
        HttpWebhookConfig webhookConfig = sinkConfig.connectorConfig.getWebhookConfig();

        URI exportUrl = new URIBuilder()
            .setScheme("http")
            .setHost(url.getHost())
            .setPort(webhookConfig.getPort())
            .setPath(webhookConfig.getExportPath())
            .addParameter("pageNum", "1")
            .addParameter("pageSize", "10")
            .addParameter("type", "poll")
            .build();

        Request.get(exportUrl)
            .execute()
            .handleResponse(response -> {
                // check response code
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                // check response body
                JSONObject jsonObject = JSON.parseObject(response.getEntity().getContent());
                JSONArray pageItems = jsonObject.getJSONArray("pageItems");

                Assertions.assertNotNull(pageItems);
                Assertions.assertEquals(size, pageItems.size());
                for (int i = 0; i < size; i++) {
                    JSONObject pageItem = pageItems.getJSONObject(i);
                    Assertions.assertNotNull(pageItem);
                }
                return null;
            });
    }

    private ConnectRecord createConnectRecord() {
        long timestamp = System.currentTimeMillis();
        return new ConnectRecord(null, null, timestamp, UUID.randomUUID().toString());
    }
}
