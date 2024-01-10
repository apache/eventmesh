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

package org.apache.eventmesh.spring.pub;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.spring.source.connector.SpringSourceConnector;
import org.apache.eventmesh.openconnect.api.callback.SendExcepionContext;
import org.apache.eventmesh.openconnect.api.callback.SendMessageCallback;
import org.apache.eventmesh.openconnect.api.callback.SendResult;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/spring")
public class SpringPubController {

    @Autowired
    private SpringSourceConnector springSourceConnector;

    @RequestMapping("/pub")
    public String publish() {
        final Map<String, String> content = new HashMap<>();
        content.put("content", "testSpringPublishMessage");
        springSourceConnector.send(JsonUtils.toJSONString(content), new SendMessageCallback() {

            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("Spring source worker send message to EventMesh success! msgId={}, topic={}",
                    sendResult.getMessageId(), sendResult.getTopic());
            }

            @Override
            public void onException(SendExcepionContext sendExcepionContext) {
                log.info("Spring source worker send message to EventMesh failed!", sendExcepionContext.getCause());
            }
        });
        return "success!";
    }
}
