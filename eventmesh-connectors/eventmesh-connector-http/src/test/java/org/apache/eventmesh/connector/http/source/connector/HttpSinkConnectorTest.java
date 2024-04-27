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

import static org.mockserver.model.HttpRequest.request;

import org.apache.eventmesh.connector.http.sink.HttpSinkConnector;
import org.apache.eventmesh.connector.http.sink.config.HttpSinkConfig;
import org.apache.eventmesh.connector.http.sink.config.HttpWebhookConfig;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordPartition;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.mockserver.verify.VerificationTimes;

import io.vertx.core.http.HttpMethod;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

public class HttpSinkConnectorTest {

    private HttpSinkConnector sinkConnector;

    private HttpSinkConfig sinkConfig;

    private URI severUri;

    private ClientAndServer mockServer;


    @BeforeEach
    void before() throws Exception {
        // init sinkConnector
        this.sinkConnector = new HttpSinkConnector();
        this.sinkConfig = (HttpSinkConfig) ConfigUtil.parse(sinkConnector.configClass());
        this.sinkConnector.init(this.sinkConfig);
        this.sinkConnector.start();

        this.severUri = URI.create(sinkConfig.connectorConfig.getUrls()[0]);
        // start mockServer
        mockServer = ClientAndServer.startClientAndServer(severUri.getPort());
        // mockServer response
        new MockServerClient(severUri.getHost(), severUri.getPort())
            .when(
                request()
                    .withMethod("POST")
                    .withPath(severUri.getPath())
            )
            .respond(
                httpRequest -> {
                    JSONObject requestBody = JSON.parseObject(httpRequest.getBodyAsString());
                    return HttpResponse.response()
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withStatusCode(200)
                        .withBody(new JSONObject()
                            .fluentPut("code", 0)
                            .fluentPut("message", "success")
                            .fluentPut("data", requestBody.getJSONObject("data").get("data"))
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
        final int times = 10;
        List<ConnectRecord> connectRecords = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            ConnectRecord record = createConnectRecord();
            connectRecords.add(record);
        }
        // Put ConnectRecord
        sinkConnector.put(connectRecords);

        // sleep 5s
        Thread.sleep(5000);

        // verify request
        new MockServerClient(severUri.getHost(), severUri.getPort())
            .verify(
                HttpRequest.request()
                    .withMethod(HttpMethod.POST.name())
                    .withPath(severUri.getPath()),
                VerificationTimes.exactly(times));

        // verify response
        HttpWebhookConfig webhookConfig = sinkConfig.connectorConfig.getWebhookConfig();
        String url = "http://" + severUri.getHost() + ":" + webhookConfig.getPort() + webhookConfig.getExportPath();
        CloseableHttpClient httpClient = HttpClients.createDefault();
        for (int i = 0; i < times; i++) {
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader("Content-Type", "application/json");
            CloseableHttpResponse response = httpClient.execute(httpGet);
            assert response.getEntity() != null;
            String responseBody = EntityUtils.toString(response.getEntity());
            JSONObject jsonObject = JSON.parseObject(responseBody);
            assert jsonObject.get("data") != null;
        }

    }

    private ConnectRecord createConnectRecord() {
        RecordPartition partition = new RecordPartition();
        RecordOffset offset = new RecordOffset();
        long timestamp = System.currentTimeMillis();
        return new ConnectRecord(partition, offset, timestamp, UUID.randomUUID().toString());
    }
}
