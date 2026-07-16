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

package org.apache.eventmesh.connector.pulsar.sink;

import org.apache.eventmesh.connector.SinkConnector;

import org.apache.pulsar.client.api.*;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PulsarSinkConnector implements SinkConnector {

    private Producer<byte[]> producer;
    @Override
    public void init(Properties props) {
        try {
            PulsarClient client = PulsarClient.builder().serviceUrl(props.getProperty("connector.serviceUrl", "pulsar://localhost:6650")).build();
            producer = client.newProducer(Schema.BYTES).topic(props.getProperty("connector.topic", "persistent://public/default/sink")).create();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public void put(List<CloudEvent> events) {
        for (CloudEvent event : events) {
            byte[] data = event.getData() != null ? event.getData().toBytes() : new byte[0];
            try {
                producer.send(data);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    @Override
    public void commit(List<CloudEvent> written) {

    }
}
