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

import static org.apache.eventmesh.common.Constants.CLOUD_EVENTS_PROTOCOL_NAME;

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

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import org.assertj.core.util.Preconditions;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.provider.EventFormatProvider;
import io.openmessaging.api.Message;

public class MessageUtils {

    private static final int SEQ_LENGTH = 10;

    public static Package hello(UserAgent user) {
        final Package msg = getPackage(Command.HELLO_REQUEST);
        msg.setBody(user);
        return msg;
    }

    public static Package heartBeat() {
        return getPackage(Command.HEARTBEAT_REQUEST);
    }

    public static Package goodbye() {
        return getPackage(Command.CLIENT_GOODBYE_REQUEST);
    }

    public static Package listen() {
        return getPackage(Command.LISTEN_REQUEST);
    }

    public static Package subscribe(String topic, SubscriptionMode subscriptionMode,
        SubscriptionType subscriptionType) {
        Package msg = getPackage(Command.SUBSCRIBE_REQUEST);
        msg.setBody(generateSubscription(topic, subscriptionMode, subscriptionType));
        return msg;
    }

    public static Package subscribe(String topic, String subExpression, SubscriptionMode subscriptionMode,
        SubscriptionType subscriptionType) {
        if (StringUtils.isBlank(subExpression)) {
            return subscribe(topic, subscriptionMode, subscriptionType);
        }
        Package msg = new Package();
        Header header = new Header(Command.SUBSCRIBE_REQUEST, 0, null, generateRandomString());
        header.putProperty(Constants.MSG_TAG, subExpression);
        msg.setHeader(header);
        msg.setBody(generateSubscription(topic, subExpression, subscriptionMode, subscriptionType));
        return msg;
    }

    public static Package unsubscribe() {
        return getPackage(Command.UNSUBSCRIBE_REQUEST);
    }

    public static Package asyncMessageAck(Package in) {
        return getPackage(Command.ASYNC_MESSAGE_TO_CLIENT_ACK, in);
    }

    public static Package buildPackage(Object message, Command command) {
        final Package msg = getPackage(command);
        if (message instanceof CloudEvent) {
            final CloudEvent cloudEvent = (CloudEvent) message;
            Preconditions.checkNotNull(cloudEvent.getDataContentType(), "DateContentType cannot be null");
            msg.getHeader().putProperty(Constants.PROTOCOL_TYPE, CLOUD_EVENTS_PROTOCOL_NAME);
            msg.getHeader().putProperty(Constants.PROTOCOL_VERSION, cloudEvent.getSpecVersion().toString());
            msg.getHeader().putProperty(Constants.PROTOCOL_DESC, "tcp");
            Optional.ofNullable(cloudEvent.getExtension(Constants.MSG_TAG))
                .ifPresent((tag) -> msg.getHeader().putProperty(Constants.MSG_TAG, tag));

            final byte[] bodyByte = EventFormatProvider.getInstance().resolveFormat(cloudEvent.getDataContentType())
                .serialize((CloudEvent) message);
            msg.setBody(bodyByte);
        } else if (message instanceof EventMeshMessage) {
            msg.getHeader().putProperty(Constants.PROTOCOL_TYPE, EventMeshCommon.EM_MESSAGE_PROTOCOL_NAME);
            msg.getHeader().putProperty(Constants.PROTOCOL_VERSION, SpecVersion.V1.toString());
            msg.getHeader().putProperty(Constants.PROTOCOL_DESC, "tcp");
            Optional.ofNullable(((EventMeshMessage) message).getProperties().get(Constants.MSG_TAG))
                .ifPresent((tag) -> msg.getHeader().putProperty(Constants.MSG_TAG, tag));
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
        return getPackage(Command.BROADCAST_MESSAGE_TO_CLIENT_ACK, in);
    }

    public static Package requestToClientAck(Package in) {
        return getPackage(Command.REQUEST_TO_CLIENT_ACK, in);
    }

    public static Package responseToClientAck(Package in) {
        return getPackage(Command.RESPONSE_TO_CLIENT_ACK, in);
    }

    public static UserAgent generateSubClient(UserAgent agent) {
        return getUserAgent(agent, EventMeshCommon.USER_AGENT_PURPOSE_SUB);
    }

    public static UserAgent generatePubClient(UserAgent agent) {
        return getUserAgent(agent, EventMeshCommon.USER_AGENT_PURPOSE_PUB);
    }

    private static Subscription generateSubscription(String topic, SubscriptionMode subscriptionMode,
        SubscriptionType subscriptionType) {
        final Subscription subscription = new Subscription();
        final List<SubscriptionItem> subscriptionItems = new ArrayList<>();
        subscriptionItems.add(new SubscriptionItem(topic, subscriptionMode, subscriptionType));
        subscription.setTopicList(subscriptionItems);
        return subscription;
    }

    private static Subscription generateSubscription(String topic, String subExpression, SubscriptionMode subscriptionMode,
        SubscriptionType subscriptionType) {
        final Subscription subscription = new Subscription();
        final List<SubscriptionItem> subscriptionItems = new ArrayList<>();
        subscriptionItems.add(new SubscriptionItem(topic, subscriptionMode, subscriptionType, subExpression));
        subscription.setTopicList(subscriptionItems);
        return subscription;
    }

    private static String generateRandomString() {
        final StringBuilder builder = new StringBuilder(MessageUtils.SEQ_LENGTH);
        IntStream.range(0, MessageUtils.SEQ_LENGTH).forEach(i -> builder.append((char) ThreadLocalRandom.current().nextInt(48, 57)));

        return builder.toString();
    }

    private static Package getPackage(Command command) {
        final Package msg = new Package();
        msg.setHeader(new Header(command, 0, null, generateRandomString()));
        return msg;
    }

    private static Package getPackage(Command command, String seq, Object body) {
        final Package msg = new Package();
        msg.setHeader(new Header(command, 0, null, seq));
        msg.setBody(body);
        return msg;
    }

    private static Package getPackage(Command command, Package in) {
        return getPackage(command, in.getHeader().getSeq(), in.getBody());
    }

    private static UserAgent getUserAgent(UserAgent agent, String purpose) {
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
            .purpose(purpose)
            .build();
    }
}
