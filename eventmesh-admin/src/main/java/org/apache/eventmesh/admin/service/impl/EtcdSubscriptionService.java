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

package org.apache.eventmesh.admin.service.impl;

import org.apache.eventmesh.admin.dto.Result;
import org.apache.eventmesh.admin.model.SubscriptionInfo;
import org.apache.eventmesh.admin.service.SubscriptionService;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EtcdSubscriptionService implements SubscriptionService {

    @Override
    public String retrieveConfig(String dataId, String group) {
        return null;
    }

    @Override
    public Result<List<SubscriptionInfo>> retrieveConfigs(Integer page, Integer size, String dataId, String group) {
        return null;
    }
}
