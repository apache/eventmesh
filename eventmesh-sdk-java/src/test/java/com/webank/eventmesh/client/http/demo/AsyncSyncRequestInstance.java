package com.webank.eventmesh.client.http.demo;
import com.webank.eventmesh.client.http.conf.LiteClientConfig;
import com.webank.eventmesh.client.http.producer.LiteProducer;
import com.webank.eventmesh.client.http.producer.RRCallback;
import com.webank.eventmesh.common.IPUtil;
import com.webank.eventmesh.common.LiteMessage;
import com.webank.eventmesh.common.ThreadUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncSyncRequestInstance {

    public static Logger logger = LoggerFactory.getLogger(AsyncSyncRequestInstance.class);

    public static void main(String[] args) throws Exception {

        LiteProducer liteProducer = null;
        try {
//            String proxyIPPort = args[0];
            String proxyIPPort = "";
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

            final long startTime = System.currentTimeMillis();
            final LiteMessage liteMessage = new LiteMessage();
            liteMessage.setBizSeqNo(RandomStringUtils.randomNumeric(30))
                    .setContent("testAsyncMessage")
                    .setTopic(topic)
                    .setUniqueId(RandomStringUtils.randomNumeric(30));

            liteProducer.request(liteMessage, new RRCallback() {
                @Override
                public void onSuccess(LiteMessage o) {
                    logger.debug("sendmsg : {}, return : {}, cost:{}ms", liteMessage.getContent(), o.getContent(), System.currentTimeMillis() - startTime);
                }

                @Override
                public void onException(Throwable e) {
                    logger.debug("sendmsg failed", e);
                }
            }, 3000);

            Thread.sleep(2000);
        } catch (Exception e) {
            logger.warn("async send msg failed", e);
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
