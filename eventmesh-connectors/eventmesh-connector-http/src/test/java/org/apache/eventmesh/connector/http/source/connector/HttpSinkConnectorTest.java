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
import org.apache.eventmesh.connector.http.sink.config.SinkConnectorConfig;
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

public class HttpSinkConnectorTest {

    private HttpSinkConnector sinkConnector;
    private SinkConnectorConfig sinkConnectorConfig;

    private ClientAndServer mockServer;

    @BeforeEach
    public void setUp() throws Exception {
        sinkConnector = new HttpSinkConnector();
        HttpSinkConfig sinkConfig = (HttpSinkConfig) ConfigUtil.parse(sinkConnector.configClass());
        sinkConnectorConfig = sinkConfig.connectorConfig;
        sinkConnector.init(sinkConfig);
        sinkConnector.start();
        mockServer = ClientAndServer.startClientAndServer(sinkConnectorConfig.getPort());
    }

    @AfterEach
    public void stopMockServer() throws Exception {
        sinkConnector.stop();
        mockServer.close();
    }

    @Test
    void testPut() throws InterruptedException {
        new MockServerClient(sinkConnectorConfig.getHost(), sinkConnectorConfig.getPort())
            .when(
                request()
                    .withMethod("POST")
                    .withPath(sinkConnectorConfig.getPath())
            )
            .respond(
                HttpResponse.response()
                    .withStatusCode(200)
            );
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
    }

}
