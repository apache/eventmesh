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

import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.connector.chatgpt.source.config.ChatGPTSourceConfig;
import org.apache.eventmesh.connector.chatgpt.source.dto.ChatGPTRequestDTO;
import org.apache.eventmesh.connector.chatgpt.source.enums.ChatGPTRequestType;
import org.apache.eventmesh.connector.chatgpt.source.handlers.ChatHandler;
import org.apache.eventmesh.connector.chatgpt.source.handlers.ParseHandler;
import org.apache.eventmesh.connector.chatgpt.source.managers.OpenaiManager;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.CloudEventUtil;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatGPTSourceConnector implements Source {

    private static final int DEFAULT_BATCH_SIZE = 10;

    private ChatGPTSourceConfig sourceConfig;
    private BlockingQueue<CloudEvent> queue;
    private HttpServer server;
    private final ExecutorService chatgptSourceExecutorService =
        ThreadPoolFactory.createThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 2, Runtime.getRuntime().availableProcessors() * 2,
            "ChatGPTSourceThread");

    private OpenaiManager openaiManager;
    private String parsePromptTemplateStr;
    private ChatHandler chatHandler;
    private ParseHandler parseHandler;
    private static final int DEFAULT_TIMEOUT = 0;

    private static final String APPLICATION_JSON = "application/json";
    private static final String TEXT_PLAIN = "text/plain";


    @Override
    public Class<? extends Config> configClass() {
        return ChatGPTSourceConfig.class;
    }

    @Override
    public void init(Config config) {
        this.sourceConfig = (ChatGPTSourceConfig) config;
        doInit();
    }

    @Override
    public void init(ConnectorContext connectorContext) {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.sourceConfig = (ChatGPTSourceConfig) sourceConnectorContext.getSourceConfig();
        doInit();
    }

    public void initParsePrompt() {
        String parsePromptFileName = sourceConfig.getConnectorConfig().getParsePromptFileName();
        URL resource = Thread.currentThread().getContextClassLoader().getResource(parsePromptFileName);
        if (resource == null) {
            log.warn("cannot find prompt file {} in resources", parsePromptFileName);
            return;
        }
        String filePath = resource.getPath();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("#") && StringUtils.isNotBlank(line)) {
                    builder.append(line).append("\n");
                }
            }
            this.parsePromptTemplateStr = builder.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read file", e);
        }
    }


    @SuppressWarnings("checkstyle:WhitespaceAround")
    private void doInit() {
        initParsePrompt();
        this.openaiManager = new OpenaiManager(sourceConfig);
        this.chatHandler = new ChatHandler(this.openaiManager);
        if (StringUtils.isNotEmpty(parsePromptTemplateStr)) {
            this.parseHandler = new ParseHandler(openaiManager, parsePromptTemplateStr);
        }
        this.queue = new LinkedBlockingQueue<>(1024);
        final Vertx vertx = Vertx.vertx();
        final Router router = Router.router(vertx);
        router.route().path(this.sourceConfig.connectorConfig.getPath()).method(HttpMethod.POST).handler(BodyHandler.create()).handler(ctx -> {
            try {
                RequestBody body = ctx.body();
                ChatGPTRequestDTO bodyObject = body.asPojo(ChatGPTRequestDTO.class);
                validateRequestDTO(bodyObject);
                handleRequest(bodyObject, ctx);
            } catch (Exception e) {
                handleError(e, ctx);
            }
        });
        if (sourceConfig.connectorConfig.getIdleTimeout() < 0) {
            log.warn("idleTimeout must be >= 0, your config value is {}, idleTimeout will be reset {}", sourceConfig.connectorConfig.getIdleTimeout(),
                DEFAULT_TIMEOUT);
            sourceConfig.connectorConfig.setIdleTimeout(DEFAULT_TIMEOUT);
        }
        this.server = vertx.createHttpServer(new HttpServerOptions().setPort(this.sourceConfig.connectorConfig.getPort())
            .setIdleTimeout(this.sourceConfig.connectorConfig.getIdleTimeout())).requestHandler(router);
    }


    private void validateRequestDTO(ChatGPTRequestDTO bodyObject) {
        if (StringUtils.isBlank(bodyObject.getText())) {
            throw new IllegalArgumentException("Attributes 'text' cannot be null");
        }
    }

    private void handleRequest(ChatGPTRequestDTO bodyObject, RoutingContext ctx) {
        chatgptSourceExecutorService.execute(() -> {
            try {
                ChatGPTRequestType chatgptRequestType = ChatGPTRequestType.valueOf(bodyObject.getRequestType());
                CloudEvent cloudEvent = invokeHandler(chatgptRequestType, bodyObject);
                queue.add(cloudEvent);
                log.info("[ChatGPTSourceConnector] Succeed to convert payload into CloudEvent.");
                ctx.response().setStatusCode(HttpResponseStatus.OK.code()).end();
            } catch (IllegalArgumentException e) {
                log.error("[ChatGPTSourceConnector] the request type is illegal: {}", e.getMessage(), e);
                ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                    .setStatusMessage(String.format("request type '%s' is not supported", bodyObject.getRequestType())).end();
            } catch (Exception e) {
                log.error("[ChatGPTSourceConnector] Error processing request: {}", e.getMessage(), e);
                ctx.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
            }
        });
    }

    private CloudEvent invokeHandler(ChatGPTRequestType chatgptRequestType, ChatGPTRequestDTO bodyObject) {
        switch (chatgptRequestType) {
            case CHAT:
                if (StringUtils.isBlank(bodyObject.getDataContentType())) {
                    bodyObject.setDataContentType(TEXT_PLAIN);
                }
                return chatHandler.invoke(bodyObject);
            case PARSE:
                if (StringUtils.isBlank(parsePromptTemplateStr)) {
                    throw new IllegalStateException(
                        "the request type of PARSE must be configured with the correct parsePromptFileName in source-config.yml");
                }
                if (StringUtils.isBlank(bodyObject.getFields())) {
                    throw new IllegalStateException("Attributes 'fields' cannot be null in PARSE");
                }
                if (StringUtils.isBlank(bodyObject.getDataContentType())) {
                    bodyObject.setDataContentType(APPLICATION_JSON);
                }
                return parseHandler.invoke(bodyObject);
            default:
                throw new IllegalStateException("the request type is illegal");
        }
    }

    private void handleError(Exception e, RoutingContext ctx) {
        log.error("[ChatGPTSourceConnector] Malformed request.", e);
        ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
    }

    @Override
    public void start() {
        Throwable t = this.server.listen().cause();
        if (t != null) {
            throw new EventMeshException("failed to start Vertx server", t);
        }
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sourceConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
        Throwable t = this.server.close().cause();
        if (t != null) {
            throw new EventMeshException("failed to stop Vertx server", t);
        }
    }

    @Override
    public List<ConnectRecord> poll() {
        List<ConnectRecord> connectRecords = new ArrayList<>(DEFAULT_BATCH_SIZE);
        for (int i = 0; i < DEFAULT_BATCH_SIZE; i++) {
            try {
                CloudEvent event = queue.poll(3, TimeUnit.SECONDS);
                if (event == null) {
                    break;
                }
                connectRecords.add(CloudEventUtil.convertEventToRecord(event));
            } catch (InterruptedException e) {
                break;
            }
        }
        return connectRecords;
    }

}
