package com.webank.eventmesh.http.demo;
import com.webank.eventmesh.client.http.conf.LiteClientConfig;
import com.webank.eventmesh.client.http.producer.LiteProducer;
import com.webank.eventmesh.common.IPUtil;
import com.webank.eventmesh.common.LiteMessage;
import com.webank.eventmesh.common.ThreadUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncRequestInstance {

    public static Logger logger = LoggerFactory.getLogger(SyncRequestInstance.class);

    public static void main(String[] args) throws Exception {

        LiteProducer liteProducer = null;
        try {
            String proxyIPPort = args[0];

            final String topic = args[1];

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

            long startTime = System.currentTimeMillis();
            LiteMessage liteMessage = new LiteMessage();
            liteMessage.setBizSeqNo(RandomStringUtils.randomNumeric(30))
                    .setContent("contentStr with special protocal")
                    .setTopic(topic)
                    .setUniqueId(RandomStringUtils.randomNumeric(30));

            LiteMessage rsp = liteProducer.request(liteMessage, 10000);
            if (logger.isDebugEnabled()) {
                logger.debug("sendmsg : {}, return : {}, cost:{}ms", liteMessage.getContent(), rsp.getContent(), System.currentTimeMillis() - startTime);
            }
        } catch (Exception e) {
            logger.warn("send msg failed", e);
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
