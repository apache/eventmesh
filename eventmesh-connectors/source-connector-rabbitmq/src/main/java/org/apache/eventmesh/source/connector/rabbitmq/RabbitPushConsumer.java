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

package org.apache.eventmesh.source.connector.rabbitmq;

import static org.apache.eventmesh.openconnect.api.config.Constants.QUEUE_OFFSET;
import static org.apache.eventmesh.source.connector.rabbitmq.utils.RabbitSourceConstants.BASIC_PROPS;
import static org.apache.eventmesh.source.connector.rabbitmq.utils.RabbitSourceConstants.DELIVERY_TAG;
import static org.apache.eventmesh.source.connector.rabbitmq.utils.RabbitSourceConstants.EXCHANGE;
import static org.apache.eventmesh.source.connector.rabbitmq.utils.RabbitSourceConstants.ROUTING_KEY;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.openconnect.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.api.data.RecordPartition;
import org.apache.eventmesh.source.connector.rabbitmq.connector.RabbitMQSourceConnector;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import org.assertj.core.util.Maps;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RabbitPushConsumer extends DefaultConsumer {

    // Store the messages consumed.
    private BlockingQueue<ConnectRecord> messageQueue;

    private RabbitMQSourceConnector connector;

    /**
     * Constructs a new instance and records its association to the passed-in channel.
     *
     * @param channel the channel to which this consumer is attached
     */
    public RabbitPushConsumer(Channel channel, BlockingQueue<ConnectRecord> messageQueue, RabbitMQSourceConnector connector) {
        super(channel);
        this.messageQueue = messageQueue;
        this.connector = connector;
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
        Map<String, Object> map = new HashMap<String, Object>() {
            {
                put(EXCHANGE, envelope.getExchange());
                put(ROUTING_KEY, envelope.getRoutingKey());
                put(DELIVERY_TAG, envelope.getDeliveryTag());
                put(BASIC_PROPS, properties);
            }
        };
        RecordPartition recordPartition = new RecordPartition(map);
        // No offset in push mode.
        RecordOffset recordOffset = new RecordOffset(Maps.newHashMap(QUEUE_OFFSET, -1));
        ConnectRecord connectRecord = new ConnectRecord(recordPartition, recordOffset,
            System.currentTimeMillis(), new String(body, Constants.DEFAULT_CHARSET));
        try {
            messageQueue.put(connectRecord);
            if (!connector.getConnectorConfig().isAutoAck()) {
                getChannel().basicAck(envelope.getDeliveryTag(), false);
            }
        } catch (InterruptedException e) {
            log.error("InterruptedException occurred during message delivery.", e);
            Thread.currentThread().interrupt();
            stopConnector(consumerTag);
        } catch (IOException e) {
            log.error("Exception occurred during ack manually.", e);
            stopConnector(consumerTag);
        }
    }

    private void stopConnector(String consumerTag) {
        try {
            getChannel().basicCancel(consumerTag);
            connector.stop();
        } catch (Exception ex) {
            log.error("Failed to stop RabbitMQSourceConnector.", ex);
        }
    }
}
