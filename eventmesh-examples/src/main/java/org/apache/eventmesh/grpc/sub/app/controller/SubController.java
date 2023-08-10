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

package org.apache.eventmesh.grpc.sub.app.controller;

import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.grpc.sub.app.service.SubService;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

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
            log.info("=======receive message======= {}", content);
        }

        subService.consumeMessage(content);

        final Map<String, Object> map = new HashMap<>();
        map.put("retCode", 1);
        return JsonUtils.toJSONString(map);
    }

}
