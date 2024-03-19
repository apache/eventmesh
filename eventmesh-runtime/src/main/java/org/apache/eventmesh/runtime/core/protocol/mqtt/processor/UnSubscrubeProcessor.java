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

package org.apache.eventmesh.runtime.core.protocol.mqtt.processor;

import org.apache.eventmesh.runtime.boot.EventMeshMQTTServer;
import org.apache.eventmesh.runtime.core.protocol.mqtt.client.ClientManager.ClientInfo;
import org.apache.eventmesh.runtime.core.protocol.mqtt.exception.MqttException;

import java.util.List;
import java.util.concurrent.Executor;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttUnsubAckMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UnSubscrubeProcessor extends AbstractMqttProcessor {

    public UnSubscrubeProcessor(EventMeshMQTTServer eventMeshMQTTServer) {
        super(eventMeshMQTTServer);
    }

    public UnSubscrubeProcessor(EventMeshMQTTServer eventMeshMQTTServer, Executor executor) {
        super(eventMeshMQTTServer, executor);
    }


    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage mqttMessage) throws MqttException {
        MqttUnsubscribeMessage mqttUnsubscribeMessage = (MqttUnsubscribeMessage) mqttMessage;
        ClientInfo clientInfo = clientManager.getOrRegisterClient(ctx, mqttMessage);
        List<String> topics = mqttUnsubscribeMessage.payload().topics();
        clientInfo.unsubscribes(topics);
        MqttMessageIdVariableHeader messageIdVariableHeader = (MqttMessageIdVariableHeader) mqttMessage.variableHeader();
        MqttMessageIdVariableHeader variableHeaderBack = MqttMessageIdVariableHeader.from(messageIdVariableHeader.messageId());
        MqttFixedHeader mqttFixedHeaderBack = new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 2);
        MqttUnsubAckMessage unSubAck = new MqttUnsubAckMessage(mqttFixedHeaderBack, variableHeaderBack);
        log.info("unsubscribe send back {}", unSubAck);
        ctx.writeAndFlush(unSubAck);
    }
}
