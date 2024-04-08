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

package org.apache.eventmesh.connector.chatgpt.source.handlers;


import org.apache.eventmesh.connector.chatgpt.source.dto.ChatGPTRequestDTO;
import org.apache.eventmesh.connector.chatgpt.source.managers.OpenaiManager;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatHandler  {

    private final OpenaiManager openaiManager;

    public ChatHandler(OpenaiManager openaiManager) {
        this.openaiManager = openaiManager;
    }

    public CloudEvent invoke(ChatGPTRequestDTO event) {
        return genGptConnectRecord(event);
    }

    private CloudEvent genGptConnectRecord(ChatGPTRequestDTO event) {
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new ChatMessage(ChatMessageRole.USER.value(), event.getText()));
        ChatCompletionRequest req = openaiManager.newChatCompletionRequest(chatMessages);
        StringBuilder gptData = new StringBuilder();

        try {
            openaiManager.getOpenAiService().createChatCompletion(req).getChoices()
                .forEach(chatCompletionChoice -> gptData.append(chatCompletionChoice.getMessage().getContent()));
        } catch (Exception e) {
            log.error("Failed to generate GPT connection record: {}", e.getMessage());
        }

        return CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSource(URI.create(event.getSource()))
            .withType(event.getType())
            .withTime(ZonedDateTime.now().toOffsetDateTime())
            .withData(gptData.toString().getBytes())
            .withSubject(event.getSubject())
            .withDataContentType(event.getDataContentType())
            .build();
    }

}
