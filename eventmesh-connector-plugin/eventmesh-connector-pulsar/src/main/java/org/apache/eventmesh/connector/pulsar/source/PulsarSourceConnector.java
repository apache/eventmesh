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

package org.apache.eventmesh.connector.pulsar.source;

import org.apache.eventmesh.connector.SourceConnector;

import org.apache.pulsar.client.api.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PulsarSourceConnector implements SourceConnector {

    private Consumer<byte[]> consumer;
    @Override
    public void init(Properties props) {
        try {
            PulsarClient client = PulsarClient.builder().serviceUrl(props.getProperty("connector.serviceUrl", "pulsar://localhost:6650")).build();
            consumer = client.newConsumer(Schema.BYTES).topic(props.getProperty("connector.topic", "persistent://public/default/source"))
                .subscriptionName("connector-source").subscribe();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public List<CloudEvent> poll() {
        if (consumer == null)
            return Collections.emptyList();
        List<CloudEvent> out = new ArrayList<>();
        try {
            Messages<byte[]> msgs = consumer.batchReceive();
            for (Message<byte[]> msg : msgs)
                out.add(CloudEventBuilder.v1().withId(msg.getMessageId().toString()).withSource(URI.create("pulsar")).withType("pulsar.message")
                    .withSubject(msg.getTopicName()).withDataContentType("application/octet-stream")
                    .withData(msg.getData() != null ? msg.getData() : new byte[0]).build());
            consumer.acknowledge(msgs);
        } catch (Exception e) {
            log.warn("pulsar poll: {}", e.toString());
        }
        return out;
    }
    @Override
    public void commit(CloudEvent lastPublished) {

    }
}
