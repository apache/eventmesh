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

package client.common;

import com.webank.eventmesh.common.protocol.tcp.EventMeshMessage;
import com.webank.eventmesh.common.protocol.tcp.Command;
import com.webank.eventmesh.common.protocol.tcp.Header;
import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.eventmesh.common.protocol.tcp.Subscription;
import com.webank.eventmesh.common.protocol.tcp.UserAgent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MessageUtils {
    public static int seqLength = 10;

    public static Package hello(UserAgent user) {
        Package msg = new Package();
        msg.setHeader(new Header(Command.HELLO_REQUEST, 0, "sucess", generateRandomString(seqLength)));
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

    public static Package subscribe() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.SUBSCRIBE_REQUEST, 0, null, generateRandomString(seqLength)));
        msg.setBody(generateSubscription());
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

    public static Package unsubscribe(String topic) {
        Package msg = new Package();
        msg.setHeader(new Header(Command.UNSUBSCRIBE_REQUEST, 0, null, generateRandomString(seqLength)));
        msg.setBody(generateSubscription(topic));
        return msg;
    }

    public static Package rrMesssage(String topic, int i) {
        Package msg = new Package();
        msg.setHeader(new Header(Command.REQUEST_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(generateRRMsg(topic, i));
        return msg;
    }

    public static Package asyncMessage(String topic, int i) {
        Package msg = new Package();
        msg.setHeader(new Header(Command.ASYNC_MESSAGE_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(generateAsyncEventMsg(topic, i));
        return msg;
    }

    public static Package broadcastMessage(String topic, int i) {
        Package msg = new Package();
        msg.setHeader(new Header(Command.BROADCAST_MESSAGE_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(generateBroadcastMsg(topic, i));
        return msg;
    }

    public static Package rrResponse(Package request) {
        Package msg = new Package();
        msg.setHeader(new Header(Command.RESPONSE_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(request.getBody());
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

    public static UserAgent generatePubClient() {
        UserAgent user = new UserAgent();
        user.setDcn("AC0");
        user.setHost("127.0.0.1");
        user.setPassword(generateRandomString(8));
        user.setUsername("PU4283");
        user.setPath("/data/app/umg_proxy");
        user.setPort(8362);
        user.setSubsystem("5023");
        user.setPid(32893);
        user.setVersion("2.0.11");
        user.setIdc("FT");
        return user;
    }

    public static UserAgent generateSubServer() {
        UserAgent user = new UserAgent();
        user.setDcn("FT0");
        user.setHost("127.0.0.1");
        user.setPassword(generateRandomString(8));
        user.setUsername("PU4283");
        user.setPath("/data/app/umg_proxy");
        user.setPort(9437);
        user.setSubsystem("5023");
        user.setPid(23948);
        user.setVersion("2.0.11");
        return user;
    }

    public static Subscription generateSubscription() {
        Subscription subscription = new Subscription();
        List<String> topicList = new ArrayList<>();
        topicList.add("FT0-s-80000000-01-0");
        topicList.add("FT0-s-80000000-02-0");
        topicList.add("FT0-s-80000000-03-0");
        topicList.add("FT0-s-80000000-04-0");
        subscription.setTopicList(topicList);
        return subscription;
    }

    public static Subscription generateSubscription(String topic) {
        Subscription subscription = new Subscription();
        List<String> topicList = new ArrayList<>();
        topicList.add(topic);
        subscription.setTopicList(topicList);
        return subscription;
    }

    public static EventMeshMessage generateRRMsg(String topic, int i) {
        EventMeshMessage msg = new EventMeshMessage();
        msg.setTopic(topic);
        msg.getProperties().put("msgType", "persistent");
        msg.getProperties().put("TTL", "300000");
        msg.getProperties().put("KEYS", generateRandomString(16));
        msg.setBody("testRR" + i);
        return msg;
    }

    public static EventMeshMessage generateAsyncEventMsg(String topic, int i) {
        EventMeshMessage msg = new EventMeshMessage();
        msg.setTopic(topic);
        msg.getProperties().put("REPLY_TO", "10.36.0.109@ProducerGroup-producerPool-9-access#V1_4_0#CI");
        msg.getProperties().put("TTL", "30000");
        msg.getProperties().put("PROPERTY_MESSAGE_REPLY_TO", "notnull");
        msg.setBody("testAsyncMessage" + i);
        return msg;
    }

    public static EventMeshMessage generateBroadcastMsg(String topic, int i) {
        EventMeshMessage msg = new EventMeshMessage();
        msg.setTopic(topic);
        msg.getProperties().put("REPLY_TO", "");
        msg.getProperties().put("TTL", "30000");
        msg.getProperties().put("PROPERTY_MESSAGE_REPLY_TO", "notnull");
        msg.setBody("testBroadCastMessage" + i);
        return msg;
    }

    public static String generateRandomString(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) ThreadLocalRandom.current().nextInt(48, 57));
        }
        return builder.toString();
    }

    public static Package askRecommend(UserAgent user) {
        Package msg = new Package();
        msg.setHeader(new Header(Command.RECOMMEND_REQUEST, 0, "sucess", generateRandomString(seqLength)));
        msg.setBody(user);
        return msg;
    }
}

