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
import org.apache.eventmesh.runtime.core.protocol.mqtt.client.ClientManager.TopicAndQos;
import org.apache.eventmesh.runtime.core.protocol.mqtt.exception.MqttException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubAckPayload;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SubscrubeProcessor extends AbstractMqttProcessor {

    public SubscrubeProcessor(EventMeshMQTTServer eventMeshMQTTServer) {
        super(eventMeshMQTTServer);
    }

    public SubscrubeProcessor(EventMeshMQTTServer eventMeshMQTTServer, Executor executor) {
        super(eventMeshMQTTServer, executor);
    }


    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage mqttMessage) throws MqttException {
        ClientInfo clientInfo = clientManager.getOrRegisterClient(ctx, mqttMessage);
        MqttSubscribeMessage mqttSubscribeMessage = (MqttSubscribeMessage) mqttMessage;
        MqttMessageIdVariableHeader messageIdVariableHeader = mqttSubscribeMessage.variableHeader();
        List<MqttTopicSubscription> mqttTopicSubscriptions = mqttSubscribeMessage.payload().topicSubscriptions();
        if (validTopicFilter(mqttTopicSubscriptions)) {
            Set<TopicAndQos>
                topics =
                mqttTopicSubscriptions.stream().map(
                        mqttTopicSubscription -> new TopicAndQos(mqttTopicSubscription.topicName(), mqttTopicSubscription.qualityOfService().value()))
                    .collect(
                        Collectors.toSet());
            log.info("client subscribe {}", topics.toString());
            List<Integer> grantedQoSLevels = new ArrayList<>(topics.size());
            clientInfo.subscribes(topics);
            for (int i = 0; i < topics.size(); i++) {
                grantedQoSLevels.add(mqttSubscribeMessage.payload().topicSubscriptions().get(i).qualityOfService().value());
            }
            MqttSubAckPayload payloadBack = new MqttSubAckPayload(grantedQoSLevels);
            MqttFixedHeader mqttFixedHeaderBack = new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 2 + topics.size());
            MqttMessageIdVariableHeader variableHeaderBack = MqttMessageIdVariableHeader.from(messageIdVariableHeader.messageId());
            MqttSubAckMessage subAck = new MqttSubAckMessage(mqttFixedHeaderBack, variableHeaderBack, payloadBack);
            log.info("subscribe send back {}", subAck);
            ctx.writeAndFlush(subAck);
        } else {
            log.info("topic valid faild {}", mqttTopicSubscriptions);
            ctx.close();
        }


    }

    private boolean validTopicFilter(List<MqttTopicSubscription> topicSubscriptions) {
        for (MqttTopicSubscription topicSubscription : topicSubscriptions) {
            String topicName = topicSubscription.topicName();
            if (topicName.startsWith("+") || topicName.endsWith("/")) {
                return false;
            }
            if (topicName.contains("#")) {
                if (count(topicName, "#") > 1) {
                    return false;
                }
            }
            if (topicName.contains("+")) {
                if (count(topicName, "+") != count(topicName, "/+")) {
                    return false;
                }
            }
        }
        return true;
    }

    private int count(String srcText, String findText) {
        int count = 0;
        Pattern p = Pattern.compile(findText);
        Matcher m = p.matcher(srcText);
        while (m.find()) {
            count++;
        }
        return count;
    }


}
