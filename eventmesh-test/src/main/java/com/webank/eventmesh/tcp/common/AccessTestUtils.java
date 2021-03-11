package com.webank.eventmesh.tcp.common;

import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.eventmesh.common.protocol.tcp.*;

import java.util.concurrent.ThreadLocalRandom;

import static com.webank.eventmesh.tcp.common.AccessTestCaseTopicSet.*;
import static com.webank.eventmesh.common.protocol.tcp.Command.RESPONSE_TO_SERVER;

public class AccessTestUtils {
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
        msg.setBody(generateSyncRRWemqMsg());
        return msg;
    }

    public static Package asyncRR() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.REQUEST_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(generateAsyncRRWemqMsg());
        return msg;
    }

    public static Package asyncMessage() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.ASYNC_MESSAGE_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(generateAsyncEventWemqMsg());
        return msg;
    }

    public static Package broadcastMessage() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.BROADCAST_MESSAGE_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(generateBroadcastWemqMsg());
        return msg;
    }

    public static Package rrResponse(Package request) {
        Package msg = new Package();
        msg.setHeader(new Header(RESPONSE_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(request.getBody());
        return msg;
    }

    private static AccessMessage generateSyncRRWemqMsg() {
        AccessMessage wemqMsg = new AccessMessage();
        wemqMsg.setTopic(TOPIC_PRX_SyncSubscribeTest);
        wemqMsg.getProperties().put("msgType", "persistent");
        wemqMsg.getProperties().put("TTL", "300000");
        wemqMsg.getProperties().put("KEYS", generateRandomString(16));
        wemqMsg.setBody("testSyncRR");
        return wemqMsg;
    }


    private static AccessMessage generateAsyncRRWemqMsg() {
        AccessMessage wemqMsg = new AccessMessage();
        wemqMsg.setTopic(TOPIC_PRX_SyncSubscribeTest);
        wemqMsg.getProperties().put("REPLY_TO", "127.0.0.1@ProducerGroup-producerPool-9-access#V1_4_0#CI");
        wemqMsg.getProperties().put("TTL", "300000");
        wemqMsg.getProperties().put("PROPERTY_MESSAGE_REPLY_TO", "notnull");
        wemqMsg.setBody("testAsyncRR");
        return wemqMsg;
    }

    private static AccessMessage generateAsyncEventWemqMsg() {
        AccessMessage wemqMsg = new AccessMessage();
        wemqMsg.setTopic(TOPIC_PRX_WQ2ClientUniCast);
        wemqMsg.getProperties().put("REPLY_TO", "127.0.0.1@ProducerGroup-producerPool-9-access#V1_4_0#CI");
        wemqMsg.getProperties().put("TTL", "30000");
        wemqMsg.getProperties().put("PROPERTY_MESSAGE_REPLY_TO", "notnull");
        wemqMsg.setBody("testAsyncMessage");
        return wemqMsg;
    }

    private static AccessMessage generateBroadcastWemqMsg() {
        AccessMessage wemqMsg = new AccessMessage();
        wemqMsg.setTopic(TOPIC_PRX_WQ2ClientBroadCast);
        wemqMsg.getProperties().put("REPLY_TO", "127.0.0.1@ProducerGroup-producerPool-9-access#V1_4_0#CI");
        wemqMsg.getProperties().put("TTL", "30000");
        wemqMsg.getProperties().put("PROPERTY_MESSAGE_REPLY_TO", "notnull");
        wemqMsg.setBody("testAsyncMessage");
        return wemqMsg;
    }

    private static String generateRandomString(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) ThreadLocalRandom.current().nextInt(48, 57));
        }
        return builder.toString();
    }
}
