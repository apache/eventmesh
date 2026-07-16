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

package org.apache.eventmesh.connector.rabbitmq.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.rabbitmq.client.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RabbitmqSourceConnector implements SourceConnector {

    private LinkedBlockingQueue<byte[]> buffer;
    @Override
    public void init(Properties props) {
        try {
            ConnectionFactory f = new ConnectionFactory();
            f.setHost(props.getProperty("connector.host", "localhost"));
            f.setPort(Integer.parseInt(props.getProperty("connector.port", "5672")));
            Connection conn = f.newConnection();
            Channel ch = conn.createChannel();
            buffer = new LinkedBlockingQueue<>();
            ch.basicConsume(props.getProperty("connector.queue", "source"), true, (tag, msg) -> buffer.offer(msg.getBody()), tag -> {
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public List<CloudEvent> poll() {
        if (buffer == null)
            return Collections.emptyList();
        List<CloudEvent> out = new ArrayList<>();
        byte[] body;
        while ((body = buffer.poll()) != null)
            out.add(CloudEventBuilder.v1().withId("rabbit-" + System.nanoTime()).withSource(URI.create("rabbitmq")).withType("rabbitmq.message")
                .withDataContentType("application/octet-stream").withData(body).build());
        return out;
    }
    @Override
    public void commit(CloudEvent lastPublished) {

    }
}
