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

package org.apache.eventmesh.connector.mcp.source;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.LoggerHandler;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.var;

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.mcp.McpSourceConfig;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.connector.mcp.source.data.McpResponse;
import org.apache.eventmesh.connector.mcp.source.protocol.Protocol;
import org.apache.eventmesh.connector.mcp.source.protocol.ProtocolFactory;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class McpSourceConnector implements Source, ConnectorCreateService<Source> {

    private McpSourceConfig sourceConfig;

    private BlockingQueue<Object> queue;

    private int batchSize;

    private Route route;

    private Protocol protocol;

    private HttpServer server;

    @Getter
    private volatile boolean started = false;

    @Getter
    private volatile boolean destroyed = false;

    private final Map<String, HttpServerResponse> sseSessions = new ConcurrentHashMap<>();

    private WebClient webClient;


    @Override
    public Class<? extends Config> configClass() {
        return McpSourceConfig.class;
    }

    @Override
    public Source create() {
        return new McpSourceConnector();
    }

    @Override
    public void init(Config config) {
        this.sourceConfig = (McpSourceConfig) config;
        doInit();
    }

    @Override
    public void init(ConnectorContext connectorContext) {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.sourceConfig = (McpSourceConfig) sourceConnectorContext.getSourceConfig();
        doInit();
    }

    private void doInit() {
        // init queue
        int maxQueueSize = this.sourceConfig.getConnectorConfig().getMaxStorageSize();
        this.queue = new LinkedBlockingQueue<>(maxQueueSize);

        // init batch size
        this.batchSize = this.sourceConfig.getConnectorConfig().getBatchSize();

        // init protocol
        String protocolName = this.sourceConfig.getConnectorConfig().getProtocol();
        this.protocol = ProtocolFactory.getInstance(this.sourceConfig.connectorConfig, protocolName);

        final Vertx vertx = Vertx.vertx();
        final Router router = Router.router(vertx);

        final String path = this.sourceConfig.connectorConfig.getPath();
        final int idleMs = this.sourceConfig.connectorConfig.getIdleTimeout();
        final long heartbeatMs = (idleMs > 0) ? Math.max(1000L, idleMs / 2L) : 15000L;
        final String rpcPath = path.endsWith("/") ? (path + "rpc") : (path + "/rpc");

        this.webClient = WebClient.create(vertx);

        router.options(path).handler(ctx -> {
            addCors(ctx.response());
            ctx.response().setStatusCode(204).end();
        });

        router.get(path)
                .order(-1)
                .handler(LoggerHandler.create())
                .handler(ctx -> {
                    HttpServerResponse res = ctx.response();
                    addCors(res);
                    res.putHeader("Content-Type", "text/event-stream");
                    res.putHeader("Cache-Control", "no-cache");
                    res.putHeader("Connection", "keep-alive");
                    res.setChunked(true);

                    String sid = ctx.request().getHeader("Mcp-Session-Id");
                    if (sid == null || sid.isEmpty()) sid = "default";
                    sseSessions.put(sid, res);

                    writeSseComment(res, "mcp-sse ready");

                    long timerId = vertx.setPeriodic(heartbeatMs, t ->
                            writeSseComment(res, "keepalive " + System.currentTimeMillis())
                    );

                    final String sid0 = sid;
                    ctx.request().connection()
                            .closeHandler(v -> { vertx.cancelTimer(timerId); sseSessions.remove(sid0); })
                            .exceptionHandler(ex -> { vertx.cancelTimer(timerId); sseSessions.remove(sid0); });
                });

        router.post(rpcPath).handler(LoggerHandler.create()).handler(ctx -> {
            addCors(ctx.response());
            String sid = ctx.request().getHeader("Mcp-Session-Id");
            if (sid == null || sid.isEmpty()) sid = "default";
            HttpServerResponse sse = sseSessions.get(sid);
            if (sse == null) {
                ctx.response().setStatusCode(409).putHeader("Content-Type","application/json")
                        .end(new JsonObject().put("error", "no sse session for sid " + sid).encode());
                return;
            }

            ctx.request().body().onSuccess(buf -> {
                JsonObject root;
                try {
                    root = buf.toJsonObject();
                } catch (Exception e) {
                    ctx.response().setStatusCode(400).end("{\"error\":\"bad json\"}");
                    return;
                }

                String jsonrpc = root.getString("jsonrpc", "");
                String method  = root.getString("method", "");
                Object idVal   = root.getValue("id");
                String idRaw   = toRawJson(idVal);

                if (!"2.0".equals(jsonrpc)) {
                    writeSseMessage(sse, jsonRpcError(idRaw, -32600, "Invalid Request"));
                    ctx.response().setStatusCode(200).end("{\"ok\":true}");
                    return;
                }

                if ("initialize".equals(method)) {
                    String result = new JsonObject()
                            .put("protocolVersion", "2025-03-26")
                            .put("capabilities", new JsonObject())
                            .encode();
                    writeSseMessage(sse, jsonRpcResult(idRaw, result));
                    ctx.response().setStatusCode(200).end("{\"ok\":true}");
                    return;
                }

                if ("tools/list".equals(method)) {
                    JsonObject inputSchema = new JsonObject()
                            .put("type", "object")
                            .put("properties", new JsonObject()
                                    .put("body", new JsonObject().put("type", "object"))
                                    .put("headers", new JsonObject().put("type", "object")))
                            .put("required", new JsonArray().add("body"));

                    JsonObject tool = new JsonObject()
                            .put("name", "callConnector")
                            .put("description", "POST body to " + path)
                            .put("inputSchema", inputSchema);

                    String toolsJson = new JsonObject().put("tools", new JsonArray().add(tool)).encode();
                    writeSseMessage(sse, jsonRpcResult(idRaw, toolsJson));
                    ctx.response().setStatusCode(200).end("{\"ok\":true}");
                    return;
                }

                if ("tools/call".equals(method)) {
                    JsonObject params     = root.getJsonObject("params", new JsonObject());
                    String toolName       = params.getString("name", "");
                    JsonObject arguments  = params.getJsonObject("arguments", new JsonObject());
                    JsonObject bodyObj    = arguments.getJsonObject("body", new JsonObject());
                    JsonObject headersObj = arguments.getJsonObject("headers", new JsonObject());

                    if (!"callConnector".equals(toolName)) {
                        writeSseMessage(sse, jsonRpcError(idRaw, -32601, "Unknown tool"));
                        ctx.response().setStatusCode(200).end("{\"ok\":true}");
                        return;
                    }

                    int port = this.sourceConfig.connectorConfig.getPort();

                    var req = webClient.post(port, "127.0.0.1", path);

                    for (String key : headersObj.fieldNames()) {
                        req.putHeader(key, String.valueOf(headersObj.getValue(key)));
                    }
                    req.putHeader("Content-Type", "application/json");

                    req.sendBuffer(Buffer.buffer(bodyObj.encode()), ar -> {
                        int code;
                        String respText;
                        boolean isError;
                        if (ar.succeeded()) {
                            HttpResponse<Buffer> resp = ar.result();
                            code = resp.statusCode();
                            respText = resp.bodyAsString();
                            isError = code >= 400;
                        } else {
                            code = 500;
                            respText = String.valueOf(ar.cause());
                            isError = true;
                        }

                        JsonObject result = new JsonObject()
                                .put("content", new JsonArray().add(
                                        new JsonObject().put("type","text")
                                                .put("text", "HTTP " + code + "\n" + respText)))
                                .put("isError", isError);

                        writeSseMessage(sse, jsonRpcResult(idRaw, result.encode()));
                    });

                    ctx.response().setStatusCode(200).end("{\"ok\":true}");
                    return;
                }

                writeSseMessage(sse, jsonRpcError(idRaw, -32601, "Method not found"));
                ctx.response().setStatusCode(200).end("{\"ok\":true}");
            }).onFailure(err -> ctx.response().setStatusCode(400).end());
        });

        route = router.route()
                .path(this.sourceConfig.connectorConfig.getPath())
                .handler(LoggerHandler.create());

        // set protocol handler
        this.protocol.setHandler(route, queue);

        // create server
        this.server = vertx.createHttpServer(new HttpServerOptions()
                .setPort(this.sourceConfig.connectorConfig.getPort())
                .setMaxFormAttributeSize(this.sourceConfig.connectorConfig.getMaxFormAttributeSize())
                .setIdleTimeout(this.sourceConfig.connectorConfig.getIdleTimeout())
                .setIdleTimeoutUnit(TimeUnit.MILLISECONDS)).requestHandler(router);
    }

    @Override
    public void start() {
        this.server.listen(res -> {
            if (res.succeeded()) {
                this.started = true;
                log.info("McpSourceConnector started on port: {}", this.sourceConfig.getConnectorConfig().getPort());
            } else {
                log.error("McpSourceConnector failed to start on port: {}", this.sourceConfig.getConnectorConfig().getPort());
                throw new EventMeshException("failed to start Vertx server", res.cause());
            }
        });
    }

    @Override
    public void commit(ConnectRecord record) {
        if (sourceConfig.getConnectorConfig().isDataConsistencyEnabled()) {
            log.debug("McpSourceConnector commit record: {}", record.getRecordId());
            RoutingContext routingContext = (RoutingContext) record.getExtensionObj("routingContext");
            if (routingContext != null) {
                routingContext.response()
                        .putHeader("content-type", "application/json")
                        .setStatusCode(HttpResponseStatus.OK.code())
                        .end(McpResponse.success().toJsonStr());
            } else {
                log.error("Failed to commit the record, routingContext is null, recordId: {}", record.getRecordId());
            }
        }
    }

    @Override
    public String name() {
        return this.sourceConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void onException(ConnectRecord record) {
        if (this.route != null) {
            this.route.failureHandler(ctx -> {
                log.error("Failed to handle the request, recordId {}. ", record.getRecordId(), ctx.failure());
                // Return Bad Response
                ctx.response()
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                        .end("{\"status\":\"failed\",\"recordId\":\"" + record.getRecordId() + "\"}");
            });
        }
    }

    @Override
    public void stop() {
        if (this.server != null) {
            this.server.close(res -> {
                        if (res.succeeded()) {
                            this.destroyed = true;
                            log.info("McpSourceConnector stopped on port: {}", this.sourceConfig.getConnectorConfig().getPort());
                        } else {
                            log.error("McpSourceConnector failed to stop on port: {}", this.sourceConfig.getConnectorConfig().getPort());
                            throw new EventMeshException("failed to stop Vertx server", res.cause());
                        }
                    }
            );
        } else {
            log.warn("McpSourceConnector server is null, ignore.");
        }
    }

    @Override
    public List<ConnectRecord> poll() {
        long startTime = System.currentTimeMillis();
        long maxPollWaitTime = 5000;
        long remainingTime = maxPollWaitTime;

        // poll from queue
        List<ConnectRecord> connectRecords = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            try {
                Object obj = queue.poll(remainingTime, TimeUnit.MILLISECONDS);
                if (obj == null) {
                    break;
                }
                // convert to ConnectRecord
                ConnectRecord connectRecord = protocol.convertToConnectRecord(obj);
                connectRecords.add(connectRecord);

                // calculate elapsed time and update remaining time for next poll
                long elapsedTime = System.currentTimeMillis() - startTime;
                remainingTime = maxPollWaitTime > elapsedTime ? maxPollWaitTime - elapsedTime : 0;
            } catch (Exception e) {
                log.error("Failed to poll from queue.", e);
                throw new RuntimeException(e);
            }

        }
        return connectRecords;
    }

    private static void addCors(HttpServerResponse res) {
        res.putHeader("Access-Control-Allow-Origin", "*");
        res.putHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        res.putHeader("Access-Control-Allow-Headers",
                "Authorization,Content-Type,Accept,Mcp-Protocol-Version,Mcp-Session-Id");
    }

    private static void writeSseComment(HttpServerResponse res, String text) {
        res.write(": " + text + "\n\n");
    }

    private static void writeSseMessage(HttpServerResponse res, String jsonLine) {
        res.write("event: message\n");
        res.write("data: " + jsonLine + "\n\n");
    }

    private static String toRawJson(Object idVal) {
        if (idVal == null) return "null";
        if (idVal instanceof Number || idVal instanceof Boolean) return idVal.toString();
        return "\"" + String.valueOf(idVal).replace("\\","\\\\").replace("\"","\\\"") + "\"";
    }

    private static String jsonRpcResult(String idRaw, String resultJson) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + idRaw + ",\"result\":" + resultJson + "}";
    }

    private static String jsonRpcError(String idRaw, int code, String message) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + idRaw + ",\"error\":{\"code\":" + code + ",\"message\":\"" + message + "\"}}";
    }


}
