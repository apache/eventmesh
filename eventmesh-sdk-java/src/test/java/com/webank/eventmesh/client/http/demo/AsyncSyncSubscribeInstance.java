package com.webank.eventmesh.client.http.demo;

import com.alibaba.fastjson.JSONObject;
import com.webank.eventmesh.client.http.conf.LiteClientConfig;
import com.webank.eventmesh.client.http.consumer.HandleResult;
import com.webank.eventmesh.client.http.consumer.LiteConsumer;
import com.webank.eventmesh.client.http.consumer.context.LiteConsumeContext;
import com.webank.eventmesh.client.http.consumer.listener.LiteMessageListener;
import com.webank.eventmesh.client.http.producer.LiteProducer;
import com.webank.eventmesh.client.http.producer.RRCallback;
import com.webank.eventmesh.common.IPUtil;
import com.webank.eventmesh.common.LiteMessage;
import com.webank.eventmesh.common.ThreadUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncSyncSubscribeInstance {

    public static Logger logger = LoggerFactory.getLogger(AsyncSyncSubscribeInstance.class);

    public static void main(String[] args) {
        LiteConsumer liteConsumer;
        try {
//            String proxyIPPort = args[0];
            String proxyIPPort = "";
//            final String topic = args[1];
            final String topic = "FT0-e-80010000-01-1";
            final String url = "http://127.0.0.1:8088/sub/test";
            if (StringUtils.isBlank(proxyIPPort)) {
                // if has multi value, can config as: 127.0.0.1:10105;127.0.0.2:10105
                proxyIPPort = "127.0.0.1:10105";
            }

            LiteClientConfig weMQProxyClientConfig = new LiteClientConfig();
            weMQProxyClientConfig.setLiteProxyAddr(proxyIPPort)
                    .setEnv("P")
                    .setIdc("FT")
                    .setDcn("FT0")
                    .setIp(IPUtil.getLocalAddress())
                    .setSys("1234")
                    .setPid(String.valueOf(ThreadUtil.getPID()));

            liteConsumer = new LiteConsumer(weMQProxyClientConfig);
//            liteConsumer.registerMessageListener(handler);
            liteConsumer.start();

            liteConsumer.subscribe(topic, url);

//            liteConsumer.unsubscribe(topic, url);
//
//            liteConsumer.shutdown();

            Thread.sleep(2000);
        } catch (Exception e) {
            logger.warn("subscribe msg failed", e);
        }

    }

//    @Override
//    public HandleResult handle(LiteMessage liteMessage, LiteConsumeContext liteConsumeContext) {
//        logger.info("receive message {}", JSONObject.toJSONString(liteMessage));
//        logger.info("context {}", JSONObject.toJSONString(liteConsumeContext));
//        return HandleResult.OK;
//    }
//
//    @Override
//    public boolean reject() {
//        return false;
//    }
}

