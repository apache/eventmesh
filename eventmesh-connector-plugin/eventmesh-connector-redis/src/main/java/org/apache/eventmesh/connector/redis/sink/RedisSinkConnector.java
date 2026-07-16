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

package org.apache.eventmesh.connector.redis.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.config.Config;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RedisSinkConnector implements SinkConnector {

    private RTopic topic;
    @Override
    public void init(Properties props) {
        Config cfg = new Config();
        cfg.useSingleServer().setAddress(props.getProperty("connector.redisUrl", "redis://localhost:6379"));
        topic = Redisson.create(cfg).getTopic(props.getProperty("connector.topic", "sink"));
    }
    @Override
    public void put(List<CloudEvent> events) {
        for (CloudEvent event : events) {
            byte[] data = event.getData() != null ? event.getData().toBytes() : new byte[0];
            topic.publish(new String(data, StandardCharsets.UTF_8));
        }
    }
    @Override
    public void commit(List<CloudEvent> written) {

    }
}
