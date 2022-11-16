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

package org.apache.eventmesh.protocol.amqp.resolver;

import org.apache.eventmesh.common.protocol.amqp.AmqpMessage;
import org.apache.eventmesh.common.protocol.amqp.common.ProtocolKey;
import org.apache.eventmesh.protocol.amqp.AMQPProtocolConstant;

import org.apache.commons.collections4.MapUtils;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.v1.CloudEventBuilder;

import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.impl.AMQContentHeader;

/**
 * Resolve AmqpMessage or CloudEvent
 */
public class AmqpProtocolResolver {
    public static CloudEvent buildEvent(AmqpMessage amqpMessage) {
        CloudEventBuilder cloudEventBuilder;
        cloudEventBuilder = new CloudEventBuilder();
        String id = UUID.randomUUID().toString();
        URI source = URI.create("/");
//        String routingKey = ProtocolKey.DEFAULT_ROUTING_KEY;
//        String exchange = ProtocolKey.DEFAULT_EXCHANGE;
        String queueName = "";
        Map<String, Object> extendInfo = amqpMessage.getExtendInfo();
        if (MapUtils.isNotEmpty(extendInfo)) {
            // removed routingKey, exchange, routing should be finished before
//            routingKey = extendInfo.get(ProtocolKey.ROUTING_KEY).toString();
//            exchange = extendInfo.get(ProtocolKey.EXCHANGE).toString();
            queueName = extendInfo.get(ProtocolKey.QUEUE_NAME).toString();
        }
        AMQContentHeader amqContentHeader = amqpMessage.getContentHeader();
        byte[] contentBody = amqpMessage.getContentBody();
        cloudEventBuilder
                .withId(id)
                .withSource(source)
                .withType(AMQPProtocolConstant.PROTOCOL_NAME)
                .withSubject(queueName)
                .withExtension(ProtocolKey.AMQ_CONTEND_HEADER, JSON.toJSONString(amqContentHeader))
                .withData(contentBody);
        return cloudEventBuilder.build();
    }

    public static AmqpMessage buildAmqpMessage(CloudEvent cloudEvent) {
        Map<String, Object> extendInfo = new HashMap<>();
        String routingKey = cloudEvent.getSubject();
        String exchange = Objects.requireNonNull(cloudEvent.getExtension(ProtocolKey.EXCHANGE)).toString();
        extendInfo.put(ProtocolKey.ROUTING_KEY, routingKey);
        extendInfo.put(ProtocolKey.EXCHANGE, exchange);
        byte[] contentBody = Objects.requireNonNull(cloudEvent.getData()).toBytes();
        AMQContentHeader amqContentHeader = JSON.parseObject(Objects.requireNonNull(cloudEvent.getExtension(ProtocolKey.AMQ_CONTEND_HEADER)).toString(), AMQContentHeader.class);
        return new AmqpMessage(amqContentHeader, contentBody, extendInfo);
    }

}