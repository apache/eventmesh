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


import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.connector.chatgpt.source.dto.ChatGPTRequestDTO;
import org.apache.eventmesh.connector.chatgpt.source.managers.OpenaiManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.cloudevents.CloudEvent;
import io.cloudevents.jackson.JsonFormat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ParseHandler {

    private final OpenaiManager openaiManager;

    private final String promptTemplate;

    private static final JsonFormat jsonFormat = new JsonFormat(false, true);


    public ParseHandler(OpenaiManager openaiManager, String promptTemplate) {
        this.openaiManager = openaiManager;
        this.promptTemplate = promptTemplate;
    }

    @SuppressWarnings("checkstyle:WhitespaceAfter")
    public CloudEvent invoke(ChatGPTRequestDTO event) {
        Map<String, String> map = convertToMap(event);

        StringSubstitutor substitute = new StringSubstitutor(map);
        String finalPrompt = substitute.replace(promptTemplate);
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new ChatMessage(ChatMessageRole.USER.value(), finalPrompt));
        ChatCompletionRequest req = openaiManager.newChatCompletionRequest(chatMessages);
        String chatResult = openaiManager.getResult(req);
        chatResult = StringUtils.removeFirst(chatResult, "```json");
        chatResult = StringUtils.removeEnd(chatResult, "```");
        CloudEvent cloudEvent;
        try {
            cloudEvent = jsonFormat.deserialize(chatResult.getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            throw new IllegalStateException("cloudEvent parse fail, please check your parse prompt file content", e);
        }
        return cloudEvent;
    }

    public Map<String, String> convertToMap(Object obj) {
        Map<String, String> map = new HashMap<>();
        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (field.isSynthetic()) {
                continue;
            }
            if (Map.class.isAssignableFrom(field.getType()) || List.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                String key = field.getName();
                if (field.isAnnotationPresent(JsonProperty.class)) {
                    JsonProperty annotation = field.getAnnotation(JsonProperty.class);
                    key = annotation.value();
                }
                Method getter = getGetter(field, clazz);
                map.put(key, String.valueOf(getter.invoke(obj)));
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                throw new IllegalStateException("convert to Map is fail", e);
            }
        }

        return map;
    }

    public Method getGetter(Field field, Class<?> clazz) throws NoSuchMethodException {
        boolean isBooleanField = false;
        if (boolean.class.isAssignableFrom(field.getType()) || Boolean.class.isAssignableFrom(field.getType())) {
            isBooleanField = true;
        }
        String handledFieldName = upperFirst(field.getName());
        String methodName;
        if (isBooleanField) {
            methodName = "is" + handledFieldName;
        } else {
            methodName = "get" + handledFieldName;
        }
        return clazz.getDeclaredMethod(methodName);
    }

    public String upperFirst(String str) {
        if (null == str) {
            return null;
        }
        if (!str.isEmpty()) {
            char firstChar = str.charAt(0);
            if (Character.isLowerCase(firstChar)) {
                return Character.toUpperCase(firstChar) + StringUtils.substring(str, 1);
            }
        }
        return str;
    }

}
