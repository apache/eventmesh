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

package org.apache.eventmesh.connector.redis.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;

import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.config.Config;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RedisSourceConnector implements SourceConnector {

    private LinkedBlockingQueue<byte[]> buffer;
    @Override
    public void init(Properties props) {
        Config cfg = new Config();
        cfg.useSingleServer().setAddress(props.getProperty("connector.redisUrl", "redis://localhost:6379"));
        RedissonClient rc = Redisson.create(cfg);
        buffer = new LinkedBlockingQueue<>();
        rc.getTopic(props.getProperty("connector.topic", "source")).addListener(String.class,
            (ch, msg) -> buffer.offer(msg.getBytes(StandardCharsets.UTF_8)));
    }
    @Override
    public List<CloudEvent> poll() {
        if (buffer == null)
            return Collections.emptyList();
        List<CloudEvent> out = new ArrayList<>();
        byte[] body;
        while ((body = buffer.poll()) != null)
            out.add(CloudEventBuilder.v1().withId("redis-" + System.nanoTime()).withSource(URI.create("redis")).withType("redis.message")
                .withDataContentType("application/octet-stream").withData(body).build());
        return out;
    }
    @Override
    public void commit(CloudEvent lastPublished) {

    }
}
