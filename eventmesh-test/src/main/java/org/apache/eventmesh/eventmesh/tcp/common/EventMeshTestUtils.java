package com.webank.eventmesh.tcp.common;

import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.eventmesh.common.protocol.tcp.*;

import java.util.concurrent.ThreadLocalRandom;

import static com.webank.eventmesh.tcp.common.EventMeshTestCaseTopicSet.*;
import static com.webank.eventmesh.common.protocol.tcp.Command.RESPONSE_TO_SERVER;

public class EventMeshTestUtils {
    private static final int seqLength = 10;

    public static UserAgent generateClient1() {
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

    public static UserAgent generateClient2() {
        UserAgent user = new UserAgent();
        user.setDcn("FT0");
        user.setHost("127.0.0.1");
        user.setPassword(generateRandomString(8));
        user.setUsername("PU4283");
        user.setPath("/data/app/umg_proxy");
        user.setPort(9362);
        user.setSubsystem("5017");
        user.setPid(42893);
        user.setVersion("2.0.11");
        user.setIdc("FT");
        return user;
    }

    public static Package syncRR() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.REQUEST_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(generateSyncRRMqMsg());
        return msg;
    }

    public static Package asyncRR() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.REQUEST_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(generateAsyncRRMqMsg());
        return msg;
    }

    public static Package asyncMessage() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.ASYNC_MESSAGE_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(generateAsyncEventMqMsg());
        return msg;
    }

    public static Package broadcastMessage() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.BROADCAST_MESSAGE_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(generateBroadcastMqMsg());
        return msg;
    }

    public static Package rrResponse(Package request) {
        Package msg = new Package();
        msg.setHeader(new Header(RESPONSE_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(request.getBody());
        return msg;
    }

    private static EventMeshMessage generateSyncRRMqMsg() {
        EventMeshMessage mqMsg = new EventMeshMessage();
        mqMsg.setTopic(TOPIC_PRX_SyncSubscribeTest);
        mqMsg.getProperties().put("msgType", "persistent");
        mqMsg.getProperties().put("TTL", "300000");
        mqMsg.getProperties().put("KEYS", generateRandomString(16));
        mqMsg.setBody("testSyncRR");
        return mqMsg;
    }


    private static EventMeshMessage generateAsyncRRMqMsg() {
        EventMeshMessage mqMsg = new EventMeshMessage();
        mqMsg.setTopic(TOPIC_PRX_SyncSubscribeTest);
        mqMsg.getProperties().put("REPLY_TO", "10.36.0.109@ProducerGroup-producerPool-9-access#V1_4_0#CI");
        mqMsg.getProperties().put("TTL", "300000");
        mqMsg.getProperties().put("PROPERTY_MESSAGE_REPLY_TO", "notnull");
        mqMsg.setBody("testAsyncRR");
        return mqMsg;
    }

    private static EventMeshMessage generateAsyncEventMqMsg() {
        EventMeshMessage mqMsg = new EventMeshMessage();
        mqMsg.setTopic(TOPIC_PRX_WQ2ClientUniCast);
        mqMsg.getProperties().put("REPLY_TO", "10.36.0.109@ProducerGroup-producerPool-9-access#V1_4_0#CI");
        mqMsg.getProperties().put("TTL", "30000");
        mqMsg.getProperties().put("PROPERTY_MESSAGE_REPLY_TO", "notnull");
        mqMsg.setBody("testAsyncMessage");
        return mqMsg;
    }

    private static EventMeshMessage generateBroadcastMqMsg() {
        EventMeshMessage mqMsg = new EventMeshMessage();
        mqMsg.setTopic(TOPIC_PRX_WQ2ClientBroadCast);
        mqMsg.getProperties().put("REPLY_TO", "10.36.0.109@ProducerGroup-producerPool-9-access#V1_4_0#CI");
        mqMsg.getProperties().put("TTL", "30000");
        mqMsg.getProperties().put("PROPERTY_MESSAGE_REPLY_TO", "notnull");
        mqMsg.setBody("testAsyncMessage");
        return mqMsg;
    }

    private static String generateRandomString(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) ThreadLocalRandom.current().nextInt(48, 57));
        }
        return builder.toString();
    }
}
