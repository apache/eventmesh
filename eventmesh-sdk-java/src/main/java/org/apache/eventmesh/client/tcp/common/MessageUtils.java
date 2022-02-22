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

package org.apache.eventmesh.client.tcp.common;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.Subscription;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.assertj.core.util.Preconditions;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.provider.EventFormatProvider;
import io.openmessaging.api.Message;

public class MessageUtils {
    private static final int seqLength = 10;

    public static Package hello(UserAgent user) {
        Package msg = new Package();
        msg.setHeader(new Header(Command.HELLO_REQUEST, 0, null, generateRandomString(seqLength)));
        msg.setBody(user);
        return msg;
    }

    public static Package heartBeat() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.HEARTBEAT_REQUEST, 0, null, generateRandomString(seqLength)));
        return msg;
    }

    public static Package goodbye() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.CLIENT_GOODBYE_REQUEST, 0, null, generateRandomString(seqLength)));
        return msg;
    }

    public static Package listen() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.LISTEN_REQUEST, 0, null, generateRandomString(seqLength)));
        return msg;
    }

    public static Package subscribe(String topic, SubscriptionMode subscriptionMode,
                                    SubscriptionType subscriptionType) {
        Package msg = new Package();
        msg.setHeader(new Header(Command.SUBSCRIBE_REQUEST, 0, null, generateRandomString(seqLength)));
        msg.setBody(generateSubscription(topic, subscriptionMode, subscriptionType));
        return msg;
    }

    public static Package unsubscribe() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.UNSUBSCRIBE_REQUEST, 0, null, generateRandomString(seqLength)));
        return msg;
    }

    public static Package asyncMessageAck(Package in) {
        Package msg = new Package();
        msg.setHeader(new Header(Command.ASYNC_MESSAGE_TO_CLIENT_ACK, 0, null, in.getHeader().getSeq()));
        msg.setBody(in.getBody());
        return msg;
    }

    public static Package buildPackage(Object message, Command command) {
        Package msg = new Package();
        msg.setHeader(new Header(command, 0, null, generateRandomString(seqLength)));
        if (message instanceof CloudEvent) {
            CloudEvent cloudEvent = (CloudEvent) message;
            Preconditions.checkNotNull(cloudEvent.getDataContentType(), "DateContentType cannot be null");
            msg.getHeader().putProperty(Constants.PROTOCOL_TYPE, EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME);
            msg.getHeader().putProperty(Constants.PROTOCOL_VERSION, cloudEvent.getSpecVersion().toString());
            msg.getHeader().putProperty(Constants.PROTOCOL_DESC, "tcp");
            byte[] bodyByte = EventFormatProvider.getInstance().resolveFormat(cloudEvent.getDataContentType())
                    .serialize((CloudEvent) message);
            msg.setBody(bodyByte);
        } else if (message instanceof EventMeshMessage) {
            msg.getHeader().putProperty(Constants.PROTOCOL_TYPE, EventMeshCommon.EM_MESSAGE_PROTOCOL_NAME);
            msg.getHeader().putProperty(Constants.PROTOCOL_VERSION, SpecVersion.V1.toString());
            msg.getHeader().putProperty(Constants.PROTOCOL_DESC, "tcp");
            msg.setBody(message);
        } else if (message instanceof Message) {
            msg.getHeader().putProperty(Constants.PROTOCOL_TYPE, EventMeshCommon.OPEN_MESSAGE_PROTOCOL_NAME);
            // todo: this version need to be confirmed.
            msg.getHeader().putProperty(Constants.PROTOCOL_VERSION, SpecVersion.V1.toString());
        } else {
            // unsupported protocol for server
            throw new IllegalArgumentException("Unsupported message protocol");
        }

        return msg;
    }

    public static Package broadcastMessageAck(Package in) {
        Package msg = new Package();
        msg.setHeader(new Header(Command.BROADCAST_MESSAGE_TO_CLIENT_ACK, 0, null, in.getHeader().getSeq()));
        msg.setBody(in.getBody());
        return msg;
    }

    public static Package requestToClientAck(Package in) {
        Package msg = new Package();
        msg.setHeader(new Header(Command.REQUEST_TO_CLIENT_ACK, 0, null, in.getHeader().getSeq()));
        msg.setBody(in.getBody());
        return msg;
    }

    public static Package responseToClientAck(Package in) {
        Package msg = new Package();
        msg.setHeader(new Header(Command.RESPONSE_TO_CLIENT_ACK, 0, null, in.getHeader().getSeq()));
        msg.setBody(in.getBody());
        return msg;
    }

    public static UserAgent generateSubClient(UserAgent agent) {
        return UserAgent.builder()
                .env(agent.getEnv())
                .host(agent.getHost())
                .password(agent.getPassword())
                .username(agent.getUsername())
                .path(agent.getPath())
                .port(agent.getPort())
                .subsystem(agent.getSubsystem())
                .pid(agent.getPid())
                .version(agent.getVersion())
                .idc(agent.getIdc())
                .group(agent.getGroup())
                .purpose(EventMeshCommon.USER_AGENT_PURPOSE_SUB)
                .build();
    }

    public static UserAgent generatePubClient(UserAgent agent) {
        return UserAgent.builder()
                .env(agent.getEnv())
                .host(agent.getHost())
                .password(agent.getPassword())
                .username(agent.getUsername())
                .path(agent.getPath())
                .port(agent.getPort())
                .subsystem(agent.getSubsystem())
                .pid(agent.getPid())
                .version(agent.getVersion())
                .idc(agent.getIdc())
                .group(agent.getGroup())
                .purpose(EventMeshCommon.USER_AGENT_PURPOSE_PUB)
                .build();
    }

    private static Subscription generateSubscription(String topic, SubscriptionMode subscriptionMode,
                                                     SubscriptionType subscriptionType) {
        Subscription subscription = new Subscription();
        List<SubscriptionItem> subscriptionItems = new ArrayList<>();
        subscriptionItems.add(new SubscriptionItem(topic, subscriptionMode, subscriptionType));
        subscription.setTopicList(subscriptionItems);
        return subscription;
    }

    private static String generateRandomString(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) ThreadLocalRandom.current().nextInt(48, 57));
        }
        return builder.toString();
    }
}
