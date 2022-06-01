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

import com.alibaba.fastjson.JSONObject;

import org.apache.eventmesh.http.demo.sub.service.SubService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/sub")
public class SubController {

    public static Logger logger = LoggerFactory.getLogger(SubController.class);

    @Autowired
    private SubService subService;

    @RequestMapping(value = "/test", method = RequestMethod.POST)
    public String subTest(@RequestBody String message) {
        logger.info("=======receive message======= {}", JSONObject.toJSONString(message));
        subService.consumeMessage(message);

        JSONObject result = new JSONObject();
        result.put("retCode", 1);
        return result.toJSONString();
    }

}
