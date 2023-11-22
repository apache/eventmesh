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

package org.apache.eventmesh.admin.service;

import org.apache.eventmesh.admin.dto.Result;
import org.apache.eventmesh.admin.model.SubscriptionInfo;

import java.util.List;

/**
 * "Subscription" refers to the traditional MQ producer-consumer topic subscription relationship,
 * emphasizing the subscription relationship between EventMesh clients (including SDK and connectors) and topics,
 * reported by the EventMesh runtime.
 */
public interface SubscriptionService {

    String retrieveConfig(String dataId, String group);

    Result<List<SubscriptionInfo>> retrieveConfigs(Integer page, Integer size, String dataId, String group);
}
