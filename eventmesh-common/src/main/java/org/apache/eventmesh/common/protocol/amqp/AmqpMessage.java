/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.eventmesh.common.protocol.amqp;

import com.rabbitmq.client.impl.AMQContentHeader;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.amqp.common.ProtocolKey;

import java.util.Map;

import com.rabbitmq.client.ContentHeader;

import com.rabbitmq.client.AMQP.BasicProperties;

import lombok.Data;

/**
 * message body of Amqp, including content header and content body
 */
@Data
public class AmqpMessage implements ProtocolTransportObject {
    private AMQContentHeader contentHeader;

    private byte[] contentBody;

    private Map<String, Object> extendInfo;

    public AmqpMessage() {
        this(null, null, null);
    }

    public AmqpMessage(AMQContentHeader contentHeader, byte[] contentBody, Map<String, Object> extendInfo) {
        this.contentHeader = contentHeader;
        this.contentBody = contentBody;
        this.extendInfo = extendInfo;
    }

    public String getExchange() {
        return extendInfo.getOrDefault(ProtocolKey.EXCHANGE, "").toString();
    }

    public String getRoutingKey() {
        return extendInfo.getOrDefault(ProtocolKey.ROUTING_KEY, "").toString();
    }
}