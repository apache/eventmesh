package com.webank.eventmesh.http.demo;
import com.webank.eventmesh.client.http.conf.LiteClientConfig;
import com.webank.eventmesh.client.http.producer.LiteProducer;
import com.webank.eventmesh.common.Constants;
import com.webank.eventmesh.common.IPUtil;
import com.webank.eventmesh.common.LiteMessage;
import com.webank.eventmesh.common.ThreadUtil;
import com.webank.eventmesh.util.Utils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class AsyncPublishInstance {

    public static Logger logger = LoggerFactory.getLogger(AsyncPublishInstance.class);

    public static void main(String[] args) throws Exception {
        Properties properties = Utils.readPropertiesFile("application.properties");
        final String eventMeshIp = properties.getProperty("eventmesh.ip");
        final String eventMeshHttpPort = properties.getProperty("eventmesh.http.port");

        LiteProducer liteProducer = null;
        try{
//            String proxyIPPort = args[0];
            String proxyIPPort = eventMeshIp + ":" + eventMeshHttpPort;
//            final String topic = args[1];
            final String topic = "FT0-e-80010000-01-1";
            if (StringUtils.isBlank(proxyIPPort)) {
                // if has multi value, can config as: 127.0.0.1:10105;127.0.0.2:10105
                proxyIPPort = "127.0.0.1:10105";
            }

            LiteClientConfig weMQProxyClientConfig = new LiteClientConfig();
            weMQProxyClientConfig.setLiteProxyAddr(proxyIPPort)
                    .setEnv("env")
                    .setIdc("idc")
                    .setDcn("dcn")
                    .setIp(IPUtil.getLocalAddress())
                    .setSys("1234")
                    .setPid(String.valueOf(ThreadUtil.getPID()));

            liteProducer = new LiteProducer(weMQProxyClientConfig);
            liteProducer.start();
            for(int i = 0; i < 5; i++) {
                LiteMessage liteMessage = new LiteMessage();
                liteMessage.setBizSeqNo(RandomStringUtils.randomNumeric(30))
//                    .setContent("contentStr with special protocal")
                        .setContent("testPublishMessage")
                        .setTopic(topic)
                        .setUniqueId(RandomStringUtils.randomNumeric(30))
                        .addProp(Constants.PROXY_MESSAGE_CONST_TTL, String.valueOf(4 * 1000));

                boolean flag = liteProducer.publish(liteMessage);
                Thread.sleep(1000);
                logger.info("publish result , {}", flag);
            }
        }catch (Exception e){
            logger.warn("publish msg failed", e);
        }

        try{
            Thread.sleep(30000);
            if(liteProducer != null){
                liteProducer.shutdown();
            }
        }catch (Exception e1){
            logger.warn("producer shutdown exception", e1);
        }
    }
}
