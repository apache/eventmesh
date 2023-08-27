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

package org.apache.eventmesh.storage.redis.admin;

import org.apache.eventmesh.api.admin.AbstractAdmin;
import org.apache.eventmesh.api.admin.TopicProperties;
import org.apache.eventmesh.storage.redis.client.RedissonClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.redisson.Redisson;
import org.redisson.api.RPatternTopic;
import org.redisson.api.RTopic;

import io.cloudevents.CloudEvent;

public class RedisAdmin extends AbstractAdmin {

    private final Redisson redisson;

    public RedisAdmin() {
        super(new AtomicBoolean(false));
        redisson = RedissonClient.INSTANCE;
    }

    @Override
    public List<TopicProperties> getTopic() throws Exception {
        RPatternTopic patternTopic = redisson.getPatternTopic("*");
        return patternTopic.getPatternNames()
            .stream()
            .map(s -> new TopicProperties(s, 0))
            .collect(Collectors.toList());
    }

    @Override
    public void createTopic(String topicName) throws Exception {
        // Just subscribe it directly, no need to create it first.
    }

    @Override
    public void deleteTopic(String topicName) throws Exception {
        RTopic topic = redisson.getTopic(topicName);
        topic.removeAllListeners();
    }

    @Override
    public void publish(CloudEvent cloudEvent) throws Exception {
        RTopic topic = redisson.getTopic(cloudEvent.getSubject());
        topic.publish(cloudEvent);
    }
}
