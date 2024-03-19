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

import java.util.concurrent.Executor;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnAckVariableHeader;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttConnectVariableHeader;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageFactory;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientConnectProcessor extends AbstractMqttProcessor {

    private boolean passwordMust = false;

    public ClientConnectProcessor(EventMeshMQTTServer eventMeshMQTTServer) {
        super(eventMeshMQTTServer);
    }

    public ClientConnectProcessor(EventMeshMQTTServer eventMeshMQTTServer, Executor executor) {
        super(eventMeshMQTTServer, executor);
        this.passwordMust = configuration.isMqttPasswordMust();
    }


    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage mqttMessage) throws MqttException {
        MqttConnectMessage mqttConnectMessage = (MqttConnectMessage) mqttMessage;
        if (passwordMust) {
            String username = mqttConnectMessage.payload().userName();
            String password = mqttConnectMessage.payload().passwordInBytes() == null ? null
                : new String(mqttConnectMessage.payload().passwordInBytes(), CharsetUtil.UTF_8);
            if (!acl.checkValid(username, password)) {
                MqttConnAckMessage connAckMessage = (MqttConnAckMessage) MqttMessageFactory.newMessage(
                    new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                    new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD, false), null);
                ctx.writeAndFlush(connAckMessage);
                ctx.close();
                return;
            }
        }
        ClientInfo clientInfo = clientManager.getOrRegisterClient(ctx, mqttMessage);
        if (clientInfo.getKeepLiveTime() > 0) {
            ctx.pipeline().addFirst("idle", new IdleStateHandler(0, 0, clientInfo.getKeepLiveTime() * 2));
        }
        MqttFixedHeader mqttFixedHeaderInfo = mqttConnectMessage.fixedHeader();
        MqttConnectVariableHeader mqttConnectVariableHeaderInfo = mqttConnectMessage.variableHeader();
        MqttConnAckVariableHeader
            mqttConnAckVariableHeaderBack =
            new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_ACCEPTED, mqttConnectVariableHeaderInfo.isCleanSession());
        MqttFixedHeader mqttFixedHeaderBack =
            new MqttFixedHeader(MqttMessageType.CONNACK, mqttFixedHeaderInfo.isDup(), MqttQoS.AT_MOST_ONCE, mqttFixedHeaderInfo.isRetain(), 0x02);
        MqttConnAckMessage connAck = new MqttConnAckMessage(mqttFixedHeaderBack, mqttConnAckVariableHeaderBack);
        log.info("client connected {}", connAck);
        ctx.writeAndFlush(connAck);
    }
}
