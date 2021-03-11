package com.webank.eventmesh.client.http.demo.sub.service;

import com.webank.eventmesh.client.http.RemotingServer;
import com.webank.eventmesh.client.http.conf.LiteClientConfig;
import com.webank.eventmesh.client.http.consumer.LiteConsumer;
import com.webank.eventmesh.common.IPUtil;
import com.webank.eventmesh.common.ProxyException;
import com.webank.eventmesh.common.ThreadUtil;
import com.webank.eventmesh.util.Utils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.remoting.support.RemotingSupport;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Component
public class SubService implements InitializingBean {

    public static Logger logger = LoggerFactory.getLogger(SubService.class);

    private LiteConsumer liteConsumer;

    private String proxyIPPort = "";

    final Properties properties = Utils.readPropertiesFile("application.properties");

    final List<String> topicList = Arrays.asList("FT0-e-80010000-01-1");
    final String localIp = IPUtil.getLocalAddress();
    final String localPort = properties.getProperty("server.port");
    final String eventMeshIp = properties.getProperty("eventmesh.ip");
    final String eventMeshHttpPort = properties.getProperty("eventmesh.http.port");
    final String url = "http://" + localIp + ":" + localPort + "/sub/test";
    final String env = "P";
    final String idc = "FT";
    final String dcn = "FT0";
    final String subsys = "1234";

    @Override
    public void afterPropertiesSet() throws Exception {

        if (StringUtils.isBlank(proxyIPPort)) {
            // if has multi value, can config as: 127.0.0.1:10105;127.0.0.2:10105
            proxyIPPort = eventMeshIp + ":" + eventMeshHttpPort;
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
        liteConsumer.heartBeat(topicList, url);
        liteConsumer.subscribe(topicList, url);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("start destory ....");
            try {
                liteConsumer.unsubscribe(topicList, url);
            } catch (ProxyException e) {
                e.printStackTrace();
            }
            try {
                liteConsumer.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
            }
            logger.info("end destory.");
        }));

        Thread stopThread = new Thread(() -> {
            logger.info("stopThread start....");
            System.exit(0);
        });

        Thread.sleep(5 * 60 * 1000);

//        stopThread.start();
    }
}
