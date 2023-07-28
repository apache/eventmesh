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

package org.apache.eventmesh.admin.rocketmq.service;

import static org.apache.eventmesh.admin.rocketmq.Constants.PLUGIN_NAME;

import org.apache.eventmesh.admin.rocketmq.response.TopicResponse;
import org.apache.eventmesh.api.admin.Admin;
import org.apache.eventmesh.api.factory.StoragePluginFactory;
import org.apache.eventmesh.common.Constants;

import org.apache.commons.lang3.time.DateFormatUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RocketMQService {

    private final Admin redisAdmin;

    public RocketMQService() {
        this.redisAdmin = StoragePluginFactory.getMeshMQAdmin(PLUGIN_NAME);
    }

    public TopicResponse createTopic(String topic) {
        TopicResponse topicResponse = null;
        try {
            redisAdmin.createTopic(topic);
            topicResponse = new TopicResponse(topic,
                    DateFormatUtils.format(System.currentTimeMillis(), Constants.DATE_FORMAT_INCLUDE_MILLISECONDS));
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return topicResponse;
    }

}
