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

package org.apache.eventmesh.connector.http.source.protocol.impl;

import org.apache.eventmesh.common.config.connector.http.SourceConnectorConfig;
import org.apache.eventmesh.connector.http.source.data.WebhookRequest;

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommonProtocolTest {

    private Vertx vertx;
    private HttpServer server;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            CompletableFuture<Void> closeServer = new CompletableFuture<>();
            server.close(ar -> {
                if (ar.succeeded()) {
                    closeServer.complete(null);
                } else {
                    closeServer.completeExceptionally(ar.cause());
                }
            });
            closeServer.get(5, TimeUnit.SECONDS);
        }
        if (vertx != null) {
            CompletableFuture<Void> closeVertx = new CompletableFuture<>();
            vertx.close(ar -> {
                if (ar.succeeded()) {
                    closeVertx.complete(null);
                } else {
                    closeVertx.completeExceptionally(ar.cause());
                }
            });
            closeVertx.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldAcceptJsonObjectPayload() throws Exception {
        BlockingQueue<Object> queue = new ArrayBlockingQueue<>(1);
        SourceConnectorConfig sourceConnectorConfig = new SourceConnectorConfig();
        sourceConnectorConfig.setDataConsistencyEnabled(false);

        CommonProtocol protocol = new CommonProtocol();
        protocol.initialize(sourceConnectorConfig);

        Router router = Router.router(vertx);
        protocol.setHandler(router.route("/"), queue);

        server = vertx.createHttpServer().requestHandler(router);
        CompletableFuture<Integer> listenPort = new CompletableFuture<>();
        server.listen(0, "127.0.0.1", ar -> {
            if (ar.succeeded()) {
                listenPort.complete(ar.result().actualPort());
            } else {
                listenPort.completeExceptionally(ar.cause());
            }
        });

        String payload = "{\"name\":\"Andy\"}";
        Request.post("http://127.0.0.1:" + listenPort.get(5, TimeUnit.SECONDS) + "/")
            .body(new StringEntity(payload, ContentType.APPLICATION_JSON))
            .execute()
            .handleResponse(response -> {
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                return null;
            });

        WebhookRequest request = (WebhookRequest) queue.poll(5, TimeUnit.SECONDS);
        Assertions.assertNotNull(request);
        Assertions.assertEquals(payload, request.getPayload());
    }
}
