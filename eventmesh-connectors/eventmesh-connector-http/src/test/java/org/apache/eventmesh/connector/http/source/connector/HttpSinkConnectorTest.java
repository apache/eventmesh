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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

import com.alibaba.fastjson2.JSONObject;

public class HttpSinkConnectorTest {

    private HttpSinkConnector sinkConnector;

    private HttpSinkConfig sinkConfig;

    private ClientAndServer mockServer;

    @BeforeEach
    void before() throws Exception {
        // init sinkConnector
        this.sinkConnector = new HttpSinkConnector();
        this.sinkConfig = (HttpSinkConfig) ConfigUtil.parse(sinkConnector.configClass());
        this.sinkConnector.init(this.sinkConfig);
        this.sinkConnector.start();

        // start mockServer
        mockServer = ClientAndServer.startClientAndServer(this.sinkConfig.connectorConfig.getPort());
        // mockServer response
        new MockServerClient(this.sinkConfig.connectorConfig.getHost(), this.sinkConfig.connectorConfig.getPort())
            .when(
                request()
                    .withMethod("POST")
                    .withPath(this.sinkConfig.connectorConfig.getPath())
            )
            .respond(
                HttpResponse.response()
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withStatusCode(200)
                    .withBody(new JSONObject()
                        .fluentPut("code", 0)
                        .fluentPut("message", "success")
                        .toJSONString()
                    )
                    .withDelay(TimeUnit.SECONDS, 10)
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

        // sleep 15s
        Thread.sleep(15000);

        // verify request
        new MockServerClient(this.sinkConfig.connectorConfig.getHost(), this.sinkConfig.connectorConfig.getPort())
            .verify(
                HttpRequest.request()
                    .withMethod(HttpMethod.POST.name())
                    .withPath(this.sinkConfig.connectorConfig.getPath()),
                VerificationTimes.exactly(times));

        // verify data
        CommonHttpSinkHandler sinkHandler = (CommonHttpSinkHandler) sinkConnector.getSinkHandler();
        Object[] receivedDataArr = sinkHandler.getAllReceivedData();
        assertEquals(times, receivedDataArr.length);
        for (Object receivedData : receivedDataArr) {
            JSONObject receivedDataJson = (JSONObject) receivedData;
            assertEquals(0, receivedDataJson.getInteger("code"));
            assertEquals("success", receivedDataJson.getString("message"));
        }

    }

    private ConnectRecord createConnectRecord() {
        RecordPartition partition = new RecordPartition();
        RecordOffset offset = new RecordOffset();
        long timestamp = System.currentTimeMillis();
        return new ConnectRecord(partition, offset, timestamp, UUID.randomUUID().toString());
    }
}
