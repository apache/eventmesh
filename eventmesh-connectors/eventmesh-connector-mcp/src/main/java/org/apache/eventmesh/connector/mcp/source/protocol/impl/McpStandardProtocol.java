package org.apache.eventmesh.connector.mcp.source.protocol.impl;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.config.connector.http.SourceConnectorConfig;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.mcp.source.data.McpRequest;
import org.apache.eventmesh.connector.mcp.source.data.McpResponse;
import org.apache.eventmesh.connector.mcp.source.protocol.Protocol;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

// 解析标准 2025-03-26 spec 请求
/**
 * Mcp Protocol. This class represents the mcp protocol. The processing method of this class does not perform any other operations
 * except storing the request and returning a general response.
 */
@Slf4j
public class McpStandardProtocol implements Protocol {
    public static final String PROTOCOL_NAME = "Mcp";

    private SourceConnectorConfig sourceConnectorConfig;

    /**
     * Initialize the protocol
     *
     * @param sourceConnectorConfig source connector config
     */
    @Override
    public void initialize(SourceConnectorConfig sourceConnectorConfig) {
        this.sourceConnectorConfig = sourceConnectorConfig;
    }

    /**
     * Set the handler for the route
     *
     * @param route route
     * @param queue queue info
     */
    @Override
    public void setHandler(Route route, BlockingQueue<Object> queue) {
        route.method(HttpMethod.POST)
                .handler(BodyHandler.create())
                .handler(ctx -> {
                    // Get the payload
                    Object payload = ctx.body().asString(Constants.DEFAULT_CHARSET.toString());
                    payload = JsonUtils.parseObject(payload.toString(), String.class);

                    // Create and store the mcp request
                    Map<String, String> headerMap = ctx.request().headers().entries().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    McpRequest webhookRequest = new McpRequest(PROTOCOL_NAME, ctx.request().absoluteURI(), headerMap, true, payload, ctx);
                    if (!queue.offer(webhookRequest)) {
                        throw new IllegalStateException("Failed to store the request.");
                    }

                    if (!sourceConnectorConfig.isDataConsistencyEnabled()) {
                        // Return 200 OK
                        ctx.response()
                                .setStatusCode(HttpResponseStatus.OK.code())
                                .end(McpResponse.success(null, "0").toJsonStr());
                    }

                })
                .failureHandler(ctx -> {
                    log.error("Failed to handle the request. ", ctx.failure());

                    // Return Bad Response
                    ctx.response()
                            .setStatusCode(ctx.statusCode())
                            .end(McpResponse.base(null, "0", ctx.failure().getMessage()).toJsonStr()); // todo
                });

    }

    /**
     * Convert the message to a connect record
     *
     * @param message message
     * @return connect record
     */
    @Override
    public ConnectRecord convertToConnectRecord(Object message) {
        McpRequest request = (McpRequest) message;
        ConnectRecord connectRecord = new ConnectRecord(null, null, System.currentTimeMillis(), request.getInputs());
        connectRecord.addExtension("source", request.getProtocolName());
        request.getMetadata().forEach((k, v) -> {
            if (k.equalsIgnoreCase("extension")) {
                JsonObject extension = new JsonObject(v);
                extension.forEach(e -> connectRecord.addExtension(e.getKey(), e.getValue()));
            }
        });
        // check recordUniqueId
        if (!connectRecord.getExtensions().containsKey("recordUniqueId")) {
            connectRecord.addExtension("recordUniqueId", connectRecord.getRecordId());
        }

        // check data
        if (connectRecord.getExtensionObj("isBase64") != null) {
            if (Boolean.parseBoolean(connectRecord.getExtensionObj("isBase64").toString())) {
                byte[] data = Base64.getDecoder().decode(connectRecord.getData().toString());
                connectRecord.setData(data);
            }
        }
        if (request.getRoutingContext() != null) {
            connectRecord.addExtension("routingContext", request.getRoutingContext());
        }
        return connectRecord;
    }
}
