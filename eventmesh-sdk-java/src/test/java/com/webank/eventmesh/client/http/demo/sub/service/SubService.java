package com.webank.eventmesh.client.http.demo.sub.service;

import com.webank.eventmesh.client.http.conf.LiteClientConfig;
import com.webank.eventmesh.client.http.consumer.LiteConsumer;
import com.webank.eventmesh.common.IPUtil;
import com.webank.eventmesh.common.ThreadUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class SubService implements InitializingBean {

    public static Logger logger = LoggerFactory.getLogger(SubService.class);

    private LiteConsumer liteConsumer;

    private String proxyIPPort = "";

    final String topic = "FT0-e-80010000-01-1";
    final String url = "http://127.0.0.1:8088/sub/test";
    final String env = "P";
    final String idc = "FT";
    final String dcn = "FT0";
    final String subsys = "1234";

    @Override
    public void afterPropertiesSet() throws Exception {

        if (StringUtils.isBlank(proxyIPPort)) {
            // if has multi value, can config as: 127.0.0.1:10105;127.0.0.2:10105
            proxyIPPort = "127.0.0.1:10105";
        }
        LiteClientConfig weMQProxyClientConfig = new LiteClientConfig();
        weMQProxyClientConfig.setLiteProxyAddr(proxyIPPort)
                .setEnv(env)
                .setIdc(idc)
                .setDcn(dcn)
                .setIp(IPUtil.getLocalAddress())
                .setSys(subsys)
                .setPid(String.valueOf(ThreadUtil.getPID()));

        liteConsumer = new LiteConsumer(weMQProxyClientConfig);
        liteConsumer.start();
        liteConsumer.heartBeat(topic, url);
        liteConsumer.subscribe(topic, url);

    }
}
