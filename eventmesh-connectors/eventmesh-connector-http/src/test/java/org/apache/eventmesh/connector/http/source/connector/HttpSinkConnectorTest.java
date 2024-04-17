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


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.model.HttpRequest.request;

import org.apache.eventmesh.connector.http.sink.HttpSinkConnector;
import org.apache.eventmesh.connector.http.sink.config.HttpSinkConfig;
import org.apache.eventmesh.connector.http.sink.handle.CommonHttpSinkHandler;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordPartition;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import io.vertx.core.http.HttpMethod;

import com.alibaba.fastjson2.JSONObject;

public class HttpSinkConnectorTest {

    private HttpSinkConnector sinkConnector;

    private HttpSinkConfig sinkConfig;


    private ClientAndServer mockServer;

    @BeforeEach
    void before() throws Exception {
        this.sinkConnector = new HttpSinkConnector();
        this.sinkConfig = (HttpSinkConfig) ConfigUtil.parse(sinkConnector.configClass());

        // start mockServer
        mockServer = ClientAndServer.startClientAndServer(this.sinkConfig.connectorConfig.getPort());
    }

    @AfterEach
    void after() throws Exception {
        this.sinkConnector.stop();
    }

    @Test
    void testPut() throws Exception {
        this.sinkConnector.init(this.sinkConfig);
        this.sinkConnector.start();

        // Mock the response
        JSONObject responseBody = new JSONObject();
        responseBody.put("code", 0);
        responseBody.put("message", "success");
        responseBody.put("data", new JSONObject());
        new MockServerClient(this.sinkConfig.connectorConfig.getHost(), this.sinkConfig.connectorConfig.getPort())
            .when(
                request()
                    .withMethod("POST")
                    .withPath(this.sinkConfig.connectorConfig.getPath())
            )
            .respond(
                HttpResponse.response()
                    .withStatusCode(200)
                    .withBody(responseBody.toJSONString())
            );

        // Create a list of ConnectRecord
        final int times = 10;
        List<ConnectRecord> connectRecords = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            RecordPartition partition = new RecordPartition();
            RecordOffset offset = new RecordOffset();
            long timestamp = System.currentTimeMillis();
            ConnectRecord connectRecord = new ConnectRecord(partition, offset,
                timestamp, "test-http " + i);
            connectRecords.add(connectRecord);
        }

        sinkConnector.put(connectRecords);
        // Sleeps for 3 seconds, waiting for the webClient to finish sending all requests
        Thread.sleep(3000);

        HttpRequest[] allRequests = mockServer.retrieveRecordedRequests(null);
        // Determine the total number of requests
        assertEquals(times, allRequests.length);

        for (int i = 0; i < times; i++) {
            HttpRequest actualRequest = allRequests[i];
            // Determine the request method
            assertEquals(HttpMethod.POST.name(), actualRequest.getMethod().getValue());
        }

        CommonHttpSinkHandler sinkHandler = (CommonHttpSinkHandler) sinkConnector.getSinkHandler();
        Object[] allReceivedData = sinkHandler.getAllReceivedData();
        for (int i = 0; i < times; i++) {
            JSONObject o = (JSONObject) allReceivedData[i];
            // Determine the response body
            assertEquals(responseBody, o);
        }

        mockServer.close();
    }
}
