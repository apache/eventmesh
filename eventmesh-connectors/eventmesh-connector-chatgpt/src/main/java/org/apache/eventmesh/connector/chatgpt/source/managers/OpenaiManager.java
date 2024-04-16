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

package org.apache.eventmesh.connector.chatgpt.source.managers;

import static com.theokanning.openai.service.OpenAiService.defaultClient;
import static com.theokanning.openai.service.OpenAiService.defaultObjectMapper;
import static com.theokanning.openai.service.OpenAiService.defaultRetrofit;

import org.apache.eventmesh.common.utils.AssertUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.chatgpt.source.config.ChatGPTSourceConfig;
import org.apache.eventmesh.connector.chatgpt.source.config.OpenaiConfig;
import org.apache.eventmesh.connector.chatgpt.source.config.OpenaiProxyConfig;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.client.OpenAiApi;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionRequest.ChatCompletionRequestBuilder;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;


@Slf4j
public class OpenaiManager {

    @Getter
    private OpenAiService openAiService;

    private String chatCompletionRequestTemplateStr;

    private static final int DEFAULT_TIMEOUT = 30;

    public OpenaiManager(ChatGPTSourceConfig sourceConfig) {
        initOpenAi(sourceConfig);
    }

    public String getResult(ChatCompletionRequest req) {
        StringBuilder gptData = new StringBuilder();
        try {
            openAiService.createChatCompletion(req).getChoices()
                .forEach(chatCompletionChoice -> gptData.append(chatCompletionChoice.getMessage().getContent()));
        } catch (Exception e) {
            log.error("Failed to generate GPT connection record: {}", e.getMessage());
        }
        return gptData.toString();
    }

    public ChatCompletionRequest newChatCompletionRequest(List<ChatMessage> chatMessages) {
        ChatCompletionRequest request = JsonUtils.parseObject(chatCompletionRequestTemplateStr, ChatCompletionRequest.class);
        request.setMessages(chatMessages);
        return request;
    }

    private void initOpenAi(ChatGPTSourceConfig sourceConfig) {
        OpenaiConfig openaiConfig = sourceConfig.getOpenaiConfig();
        if (openaiConfig.getTimeout() <= 0) {
            log.warn("openaiTimeout must be > 0, your config value is {}, openaiTimeout will be reset {}", openaiConfig.getTimeout(),
                DEFAULT_TIMEOUT);
            openaiConfig.setTimeout(DEFAULT_TIMEOUT);
        }
        boolean proxyEnable = sourceConfig.connectorConfig.isProxyEnable();
        if (proxyEnable) {
            OpenaiProxyConfig chatgptProxyConfig = sourceConfig.openaiProxyConfig;
            if (chatgptProxyConfig.getHost() == null) {
                throw new IllegalStateException("chatgpt proxy config 'host' cannot be null");
            }
            ObjectMapper mapper = defaultObjectMapper();
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(chatgptProxyConfig.getHost(), chatgptProxyConfig.getPort()));
            OkHttpClient client =
                defaultClient(openaiConfig.getToken(), Duration.ofSeconds(openaiConfig.getTimeout())).newBuilder().proxy(proxy).build();
            Retrofit retrofit = defaultRetrofit(client, mapper);
            OpenAiApi api = retrofit.create(OpenAiApi.class);
            this.openAiService = new OpenAiService(api);
        } else {
            this.openAiService = new OpenAiService(openaiConfig.getToken(), Duration.ofSeconds(openaiConfig.getTimeout()));
        }
        ChatCompletionRequestBuilder builder = ChatCompletionRequest.builder().model(openaiConfig.getModel());
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


}
