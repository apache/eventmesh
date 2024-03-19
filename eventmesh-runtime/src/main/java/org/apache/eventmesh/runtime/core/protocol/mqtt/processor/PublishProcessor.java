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

import java.util.Set;
import java.util.concurrent.Executor;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageFactory;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PublishProcessor extends AbstractMqttProcessor {

    public PublishProcessor(EventMeshMQTTServer eventMeshMQTTServer) {
        super(eventMeshMQTTServer);
    }

    public PublishProcessor(EventMeshMQTTServer eventMeshMQTTServer, Executor executor) {
        super(eventMeshMQTTServer, executor);
    }


    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage mqttMessage) throws MqttException {
        //
        clientManager.getOrRegisterClient(ctx, mqttMessage);
        MqttPublishMessage mqttPublishMessage = (MqttPublishMessage) mqttMessage;
        MqttFixedHeader mqttFixedHeaderInfo = mqttPublishMessage.fixedHeader();
        MqttPublishVariableHeader mqttPublishVariableHeader = mqttPublishMessage.variableHeader();
        String topic = mqttPublishVariableHeader.topicName();
        MqttQoS qos = (MqttQoS) mqttFixedHeaderInfo.qosLevel();
        byte[] headBytes = new byte[mqttPublishMessage.payload().readableBytes()];
        mqttPublishMessage.payload().readBytes(headBytes);
        switch (qos) {
            case AT_MOST_ONCE:
                this.sendPublishMessage(topic, qos, headBytes, false, false);
                break;
            case AT_LEAST_ONCE:
                //to do
                break;
            case EXACTLY_ONCE:
                //to do
                break;
            default:
                break;

        }
    }

    private void sendPublishMessage(String topic, MqttQoS respQoS, byte[] messageBytes, boolean retain, boolean dup) {
        MqttPublishMessage publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
            new MqttFixedHeader(MqttMessageType.PUBLISH, dup, respQoS, retain, 0),
            new MqttPublishVariableHeader(topic, 0), Unpooled.buffer().writeBytes(messageBytes));
        Set<ClientInfo> clientInfoSet = clientManager.search(topic);
        for (ClientInfo clientInfo : clientInfoSet) {
            Channel channel = clientInfo.getChannel();
            if (channel != null) {
                channel.writeAndFlush(publishMessage);
            }
        }
    }
}
