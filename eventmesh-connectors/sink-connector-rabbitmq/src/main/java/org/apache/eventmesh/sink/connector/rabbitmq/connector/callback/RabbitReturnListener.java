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

package org.apache.eventmesh.sink.connector.rabbitmq.connector.callback;

import org.apache.eventmesh.openconnect.api.config.Constants;
import org.apache.eventmesh.sink.connector.rabbitmq.connector.connector.RabbitMQSinkConnector;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ReturnListener;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RabbitReturnListener implements ReturnListener {

    private static final int MAX_RETRY = Constants.DEFAULT_ATTEMPT;

    private RabbitMQSinkConnector connector;

    private Channel channel;

    // Record the number of retries for each returned message separately.
    private Map<Long, Integer> retryCountMap = new ConcurrentHashMap<>();

    public RabbitReturnListener(RabbitMQSinkConnector connector, Channel channel) {
        this.connector = connector;
        this.channel = channel;
    }

    @SneakyThrows
    @Override
    public void handleReturn(int replyCode, String replyText, String exchange, String routingKey, BasicProperties props, byte[] body)
        throws IOException {
        // TODO (Currently, when a message failed to route, even if the SinkConnector is immediately closed,
        //  eventmesh-runtime will keep retrieving data and attempt to sink. In this situation, maybe
        //  eventmesh-runtime's subscription to the data should also be cancelled?)
        if (!channel.isOpen()) {
            log.error("Channel is already closed!");
            return;
        }
        Long correlationId = Long.valueOf(props.getCorrelationId());
        log.warn("Failed to publish message(correlationId:{}, body:{}) to RabbitMQ!", correlationId, body.toString());
        Integer retryCount = retryCountMap.computeIfAbsent(correlationId, (key) -> new Integer(0));
        if (retryCount < MAX_RETRY) {
            log.info("Retry publishing message(correlationId:{}), retry count:{}.", correlationId, retryCount);
            TimeUnit.MILLISECONDS.sleep(500);
            retryCountMap.put(correlationId, ++retryCount);
            channel.basicPublish(exchange, routingKey, true, props, body);
        } else {
            log.error("Retries for message(correlationId:{}) has reached max num {}. Then failed.", correlationId, MAX_RETRY);
            try {
                connector.stop();
            } catch (Exception e) {
                log.error("Failed to stop RabbitMQSinkConnector.", e);
            }
        }
    }
}
