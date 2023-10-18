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

package org.apache.eventmesh.admin.controller;

import org.apache.eventmesh.admin.dto.Result;
import org.apache.eventmesh.admin.model.SubscriptionInfo;
import org.apache.eventmesh.admin.service.SubscriptionService;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class SubscriptionController {

    @Autowired
    public SubscriptionService subscriptionService;

    // the subscription dataId naming pattern of EventMesh clients: ip-protocol
    private static final String CLIENT_DATA_ID_PATTERN = "*.*.*.*-*";

    /**
     * Retrieve the config content of a specified config.
     *
     * @param dataId nacos config data id (Exact Matching)
     * @param group  config group (Exact Matching)
     * @return config content
     */
    @GetMapping("/subscription")
    public Result<String> retrieveSubscription(@RequestParam("dataId") String dataId, @RequestParam("group") String group) {
        return Result.success(subscriptionService.retrieveConfig(dataId, group));
    }

    /**
     * Retrieve a list of configs.
     *
     * @param page page number
     * @param size page size
     * @param dataId nacos config data id (Fuzzy Matching)
     * @param group config group (Fuzzy Matching)
     * @return config properties and base64 encoded config content
     */
    @GetMapping("/subscriptions")
    public Result<List<SubscriptionInfo>> listSubscriptions(
        @RequestParam(name = "page", defaultValue = "1") Integer page,
        @RequestParam(name = "size", defaultValue = "10") Integer size,
        @RequestParam(name = "dataId", defaultValue = CLIENT_DATA_ID_PATTERN) String dataId,
        @RequestParam(name = "group", defaultValue = "") String group) {
        return Result.success(subscriptionService.retrieveConfigs(page, size, dataId, group));
    }

}
