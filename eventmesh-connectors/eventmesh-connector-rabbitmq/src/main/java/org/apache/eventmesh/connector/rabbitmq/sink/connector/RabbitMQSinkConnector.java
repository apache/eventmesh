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

package org.apache.eventmesh.connector.rabbitmq.sink.connector;

import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.connector.rabbitmq.sink.config.RabbitMQSinkConfig;
import org.apache.eventmesh.connector.rabbitmq.source.config.RabbitMQSourceConfig;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import java.util.List;

@Slf4j
public class RabbitMQSinkConnector implements Sink {

    private RabbitMQSinkConfig sinkConfig;

    RabbitmqMockConnectionFactory rabbitmqMockConnectionFactory = new RabbitmqMockConnectionFactory();

    @Override
    public Class<? extends Config> configClass() {
        return RabbitMQSinkConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        this.sinkConfig = (RabbitMQSinkConfig) config;
    }

    @Override
    public void start() throws Exception {
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sinkConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        for (ConnectRecord connectRecord : sinkRecords) {
//            Message message = convertRecordToMessage(connectRecord);
            try {
//                SendResult sendResult = producer.send(message);
//            } catch (InterruptedException e) {
                Thread currentThread = Thread.currentThread();
//                log.warn("[RabbitMQSinkConnector] Interrupting thread {} due to exception {}",
//                    currentThread.getName(), e.getMessage());
                currentThread.interrupt();
            } catch (Exception e) {
                log.error("[RabbitMQSinkConnector] sendResult has error : ", e);
            }
        }
    }

//    public Message convertRecordToMessage(ConnectRecord connectRecord) {
//        Message message = new Message();
//        message.setTopic(this.sinkConfig.getConnectorConfig().getTopic());
//        message.setBody((byte[]) connectRecord.getData());
//        for (String key : connectRecord.getExtensions().keySet()) {
//            MessageAccessor.putProperty(message, key, connectRecord.getExtension(key));
//        }
//        return message;
//    }
}
