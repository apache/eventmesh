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

package org.apache.eventmesh.http.demo.sub.controller;

import static org.apache.eventmesh.common.Constants.CLOUD_EVENTS_PROTOCOL_NAME;

import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.http.demo.sub.service.SubService;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/sub")
public class SubController {

    @Autowired
    private transient SubService subService;

    @RequestMapping(value = "/test", method = RequestMethod.POST)
    public String subTest(final HttpServletRequest request) {
        final String content = request.getParameter("content");
        if (log.isInfoEnabled()) {
            log.info("receive message: {}", content);
        }
        @SuppressWarnings("unchecked")
        final Map<String, String> contentMap = JsonUtils.parseObject(content, HashMap.class);
        if (StringUtils.equals(CLOUD_EVENTS_PROTOCOL_NAME, contentMap.get(ProtocolKey.PROTOCOL_TYPE))) {
            final EventFormat eventFormat = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE);
            if (eventFormat != null) {
                final CloudEvent event = eventFormat.deserialize(content.getBytes(StandardCharsets.UTF_8));
                final CloudEventData eventData = event.getData();
                if (eventData != null) {
                    final String data = new String(eventData.toBytes(), StandardCharsets.UTF_8);
                    if (log.isInfoEnabled()) {
                        log.info("receive data: {}", data);
                    }
                }
            }
        }

        subService.consumeMessage(content);

        final Map<String, Object> map = new HashMap<>();
        map.put("retCode", 1);
        return JsonUtils.toJSONString(map);
    }

}
