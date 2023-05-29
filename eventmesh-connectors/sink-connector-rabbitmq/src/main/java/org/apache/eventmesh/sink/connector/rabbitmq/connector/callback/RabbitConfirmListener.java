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
import org.apache.eventmesh.sink.connector.rabbitmq.connector.domain.MessageInfo;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmListener;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RabbitConfirmListener  implements ConfirmListener {

    private static final int MAX_RETRY = Constants.DEFAULT_ATTEMPT;

    private RabbitMQSinkConnector connector;

    private Channel channel;

    // Message cache for retrying.
    private Map<Long, MessageInfo> messages;

    // Record the number of retries for each unconfirmed message separately.
    private Map<Long, Integer> retryCountMap = new ConcurrentHashMap<>();

    public RabbitConfirmListener(RabbitMQSinkConnector connector, Channel channel, Map<Long, MessageInfo> messages) {
        this.connector = connector;
        this.channel = channel;
        this.messages = messages;
    }

    @Override
    public void handleAck(long deliveryTag, boolean multiple) throws IOException {
        // Remove the relevant information of this message that was previously saved for retry.
        if (messages.containsKey(deliveryTag)) {
            messages.remove(deliveryTag);
        }
        if (retryCountMap.containsKey(deliveryTag)) {
            log.info("Message(deliveryTag:{}) was published successfully after {} retries.", deliveryTag, retryCountMap.get(deliveryTag));
            retryCountMap.remove(deliveryTag);
        }
    }

    @SneakyThrows
    @Override
    public void handleNack(long deliveryTag, boolean multiple) throws IOException {
        if (!channel.isOpen()) {
            log.error("Channel is already closed!");
            return;
        }
        log.warn("Message(deliveryTag:{}) get nack from RabbitMQ!", deliveryTag);
        MessageInfo msg = messages.get(deliveryTag);
        if (msg != null) {
            Integer retryCount = retryCountMap.computeIfAbsent(deliveryTag, (key) -> new Integer(0));
            if (retryCount < MAX_RETRY) {
                log.info("Retry publishing message(deliveryTag:{}).", deliveryTag);
                TimeUnit.MILLISECONDS.sleep(500);
                retryCountMap.put(deliveryTag, ++retryCount);
                channel.basicPublish(msg.getExchange(), msg.getRoutingKey(), msg.getProperties(), msg.getBody());
            } else {
                log.error("Retries for message(deliveryTag:{}) has reached max num {}. Then failed.", deliveryTag, MAX_RETRY);
                try {
                    this.connector.stop();
                } catch (Exception e) {
                    log.error("Failed to stop RabbitMQSinkConnector.", e);
                }
            }
        }
    }
}
