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

package org.apache.eventmesh.connector.rocketmq.sink;

import org.apache.eventmesh.connector.SinkConnector;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RocketmqSinkConnector implements SinkConnector {

    private DefaultMQProducer producer;
    private Properties props;

    @Override
    public void init(Properties props) {
        this.props = props;
        try {
            producer = new DefaultMQProducer(props.getProperty("connector.group", "connector-sink"));
            producer.setNamesrvAddr(props.getProperty("connector.namesrvAddr", "localhost:9876"));
            producer.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void put(List<CloudEvent> events) {
        for (CloudEvent event : events) {
            String topic = event.getSubject() != null ? event.getSubject() : "sink-topic";
            byte[] data = event.getData() != null ? event.getData().toBytes() : new byte[0];
            try {
                producer.send(new Message(topic, data));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void commit(List<CloudEvent> written) {

    }
}
