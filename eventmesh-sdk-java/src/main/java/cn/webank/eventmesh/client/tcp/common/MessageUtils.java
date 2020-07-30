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

package cn.webank.eventmesh.client.tcp.common;

import cn.webank.eventmesh.common.protocol.tcp.Command;
import cn.webank.eventmesh.common.protocol.tcp.Header;
import cn.webank.eventmesh.common.protocol.tcp.Subscription;
import cn.webank.eventmesh.common.protocol.tcp.UserAgent;
import cn.webank.eventmesh.common.protocol.tcp.Package;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MessageUtil
 *
 */
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

    public static Package subscribe(String topic) {
        Package msg = new Package();
        msg.setHeader(new Header(Command.SUBSCRIBE_REQUEST, 0, null, generateRandomString(seqLength)));
        msg.setBody(generateSubscription(topic));
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
        UserAgent user = new UserAgent();
        user.setDcn(agent.getDcn());
        user.setHost(agent.getHost());
        user.setPassword(agent.getPassword());
        user.setUsername(agent.getUsername());
        user.setPath(agent.getPath());
        user.setPort(agent.getPort());
        user.setSubsystem(agent.getSubsystem());
        user.setPid(agent.getPid());
        user.setVersion(agent.getVersion());
        user.setIdc(agent.getIdc());

        user.setPurpose(WemqAccessCommon.USER_AGENT_PURPOSE_SUB);
        return user;
    }

    public static UserAgent generatePubClient(UserAgent agent) {
        UserAgent user = new UserAgent();
        user.setDcn(agent.getDcn());
        user.setHost(agent.getHost());
        user.setPassword(agent.getPassword());
        user.setUsername(agent.getUsername());
        user.setPath(agent.getPath());
        user.setPort(agent.getPort());
        user.setSubsystem(agent.getSubsystem());
        user.setPid(agent.getPid());
        user.setVersion(agent.getVersion());
        user.setIdc(agent.getIdc());

        user.setPurpose(WemqAccessCommon.USER_AGENT_PURPOSE_PUB);
        return user;
    }

    private static Subscription generateSubscription(String topic) {
        Subscription subscription = new Subscription();
        List<String> topicList = new ArrayList<>();
        topicList.add(topic);
        subscription.setTopicList(topicList);
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

