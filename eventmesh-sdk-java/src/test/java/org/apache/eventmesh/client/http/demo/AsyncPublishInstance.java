package org.apache.eventmesh.client.http.demo;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.client.http.conf.LiteClientConfig;
import org.apache.eventmesh.client.http.producer.LiteProducer;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.IPUtil;
import org.apache.eventmesh.common.LiteMessage;
import org.apache.eventmesh.common.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncPublishInstance {

    public static Logger logger = LoggerFactory.getLogger(AsyncPublishInstance.class);

    public static void main(String[] args) throws Exception {

        LiteProducer liteProducer = null;
        try {
//            String eventMeshIPPort = args[0];
            String eventMeshIPPort = "";
//            final String topic = args[1];
            final String topic = "FT0-e-80010000-01-1";
            if (StringUtils.isBlank(eventMeshIPPort)) {
                // if has multi value, can config as: 127.0.0.1:10105;127.0.0.2:10105
                eventMeshIPPort = "127.0.0.1:10105";
            }

            LiteClientConfig eventMeshClientConfig = new LiteClientConfig();
            eventMeshClientConfig.setLiteEventMeshAddr(eventMeshIPPort)
                    .setEnv("env")
                    .setIdc("idc")
                    .setDcn("dcn")
                    .setIp(IPUtil.getLocalAddress())
                    .setSys("1234")
                    .setPid(String.valueOf(ThreadUtil.getPID()));

            liteProducer = new LiteProducer(eventMeshClientConfig);
            liteProducer.start();
            for (int i = 0; i < 1; i++) {
                LiteMessage liteMessage = new LiteMessage();
                liteMessage.setBizSeqNo(RandomStringUtils.randomNumeric(30))
//                    .setContent("contentStr with special protocal")
                        .setContent("testPublishMessage")
                        .setTopic(topic)
                        .setUniqueId(RandomStringUtils.randomNumeric(30))
                        .addProp(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4 * 1000));

                boolean flag = liteProducer.publish(liteMessage);
                Thread.sleep(1000);
                logger.info("publish result , {}", flag);
            }
        } catch (Exception e) {
            logger.warn("publish msg failed", e);
        }

        try {
            Thread.sleep(30000);
            if (liteProducer != null) {
                liteProducer.shutdown();
            }
        } catch (Exception e1) {
            logger.warn("producer shutdown exception", e1);
        }
    }
}
