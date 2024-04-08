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


import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

import static com.theokanning.openai.service.OpenAiService.defaultObjectMapper;
import static com.theokanning.openai.service.OpenAiService.defaultRetrofit;

import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.utils.AssertUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.chatgpt.source.config.OpenaiConfig;
import org.apache.eventmesh.connector.chatgpt.source.config.OpenaiProxyConfig;
import org.apache.eventmesh.connector.chatgpt.source.config.ChatGPTSourceConfig;
import org.apache.eventmesh.connector.chatgpt.source.dto.ChatGPTRequestDTO;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.CloudEventUtil;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.client.OpenAiApi;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionRequest.ChatCompletionRequestBuilder;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatGPTSourceConnector implements Source {

    private static final int DEFAULT_BATCH_SIZE = 10;

    private ChatGPTSourceConfig sourceConfig;
    private BlockingQueue<CloudEvent> queue;
    private HttpServer server;
    private OpenAiService openAiService;
    private final ExecutorService chatgptSourceExecutorService =
        ThreadPoolFactory.createThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            Runtime.getRuntime().availableProcessors() * 2,
            "ChatGPTSourceThread");

    private String chatCompletionRequestTemplateStr;

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

    private void initOpenAi() {
        OpenaiConfig openaiConfig = sourceConfig.getOpenaiConfig();
        AssertUtils.isTrue(openaiConfig.getTimeout() > 0, "openaiTimeout must be >= 0");
        boolean proxyEnable = sourceConfig.connectorConfig.isProxyEnable();
        if (proxyEnable) {
            OpenaiProxyConfig chatgptProxyConfig = sourceConfig.openaiProxyConfig;
            if (chatgptProxyConfig.getHost() == null) {
                throw new IllegalStateException("chatgpt proxy config 'host' cannot be null");
            }
            ObjectMapper mapper = defaultObjectMapper();
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(chatgptProxyConfig.getHost(), chatgptProxyConfig.getPort()));
            OkHttpClient client = OpenAiService
                .defaultClient(openaiConfig.getToken(), Duration.ofSeconds(openaiConfig.getTimeout()))
                .newBuilder()
                .proxy(proxy)
                .build();
            Retrofit retrofit = defaultRetrofit(client, mapper);
            OpenAiApi api = retrofit.create(OpenAiApi.class);
            this.openAiService = new OpenAiService(api);
        } else {
            this.openAiService =
                new OpenAiService(openaiConfig.getToken(), Duration.ofSeconds(openaiConfig.getTimeout()));
        }
        ChatCompletionRequestBuilder builder = ChatCompletionRequest
            .builder()
            .model(openaiConfig.getModel());
        AssertUtils.notNull(openaiConfig.getModel(), "model cannot be null");
        builder = builder.model(openaiConfig.getModel());
        if (openaiConfig.getUser() != null) {
            builder = builder.user(openaiConfig.getUser());
        }
        if (openaiConfig.getPresencePenalty() != null) {
            builder = builder.presencePenalty(openaiConfig.getPresencePenalty());
        }
        if (openaiConfig.getFrequencyPenalty() != null) {
            builder = builder.frequencyPenalty(openaiConfig.getFrequencyPenalty());
        }
        if (openaiConfig.getMaxTokens() != null) {
            builder = builder.maxTokens(openaiConfig.getMaxTokens());
        }
        if (openaiConfig.getTemperature() != null) {
            builder = builder.temperature(openaiConfig.getTemperature());
        }
        if (openaiConfig.getLogitBias() != null && !openaiConfig.getLogitBias().isEmpty()) {
            builder = builder.logitBias(openaiConfig.getLogitBias());
        }
        if (openaiConfig.getStop() != null && !openaiConfig.getStop().isEmpty()) {
            builder = builder.stop(openaiConfig.getStop());
        }
        this.chatCompletionRequestTemplateStr = JsonUtils.toJSONString(builder.build());
    }

    public ChatCompletionRequest newChatCompletionRequest(List<ChatMessage> chatMessages) {
        ChatCompletionRequest request = JsonUtils.parseObject(chatCompletionRequestTemplateStr, ChatCompletionRequest.class);
        request.setMessages(chatMessages);
        return request;
    }

    private CloudEvent genGptConnectRecord(ChatGPTRequestDTO event) {
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new ChatMessage(ChatMessageRole.USER.value(), event.getPrompt()));
        ChatCompletionRequest req = newChatCompletionRequest(chatMessages);
        StringBuilder gptData = new StringBuilder();

        try {
            openAiService.createChatCompletion(req).getChoices()
                .forEach(chatCompletionChoice -> gptData.append(chatCompletionChoice.getMessage().getContent()));
        } catch (Exception e) {
            log.error("Failed to generate GPT connection record: {}", e.getMessage());
        }

        return CloudEventBuilder.v1().withId(UUID.randomUUID().toString()).withSource(URI.create(event.getSource())).withType(event.getType())
            .withTime(ZonedDateTime.now().toOffsetDateTime()).withData(gptData.toString().getBytes()).withSubject(event.getSubject())
            .withDataContentType(event.getDataContentType()).build();
    }

    @SuppressWarnings("checkstyle:WhitespaceAround")
    private void doInit() {
        initOpenAi();
        this.queue = new LinkedBlockingQueue<>(1024);
        final Vertx vertx = Vertx.vertx();
        final Router router = Router.router(vertx);
        router.route().path(this.sourceConfig.connectorConfig.getPath()).method(HttpMethod.POST).handler(BodyHandler.create()).handler(ctx -> {
            try {
                RequestBody body = ctx.body();
                ChatGPTRequestDTO bodyObject = body.asPojo(ChatGPTRequestDTO.class);
                if (bodyObject.getSubject() == null || bodyObject.getDataContentType() == null || bodyObject.getPrompt() == null) {
                    throw new IllegalStateException("Attributes 'subject', 'datacontenttype', and 'prompt' cannot be null");
                }
                chatgptSourceExecutorService.execute(() -> {
                    try {
                        CloudEvent cloudEvent = genGptConnectRecord(bodyObject);
                        queue.add(cloudEvent);
                        log.info("[ChatGPTSourceConnector] Succeed to convert payload into CloudEvent.");
                        ctx.response().setStatusCode(HttpResponseStatus.OK.code()).end();
                    } catch (Exception e) {
                        log.error("[ChatGPTSourceConnector] Error processing request: {}", e.getMessage(), e);
                        ctx.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
                    }
                });
            } catch (Exception e) {
                log.error("[ChatGPTSourceConnector] Malformed request. StatusCode={}", HttpResponseStatus.BAD_REQUEST.code(), e);
                ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
            }
        });
        this.server = vertx.createHttpServer(new HttpServerOptions().setPort(this.sourceConfig.connectorConfig.getPort())
            .setIdleTimeout(this.sourceConfig.connectorConfig.getIdleTimeout())).requestHandler(router);
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
