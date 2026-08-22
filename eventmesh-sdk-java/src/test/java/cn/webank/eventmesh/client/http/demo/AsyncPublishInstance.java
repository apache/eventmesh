package cn.webank.eventmesh.client.http.demo;
import cn.webank.eventmesh.client.http.conf.LiteClientConfig;
import cn.webank.eventmesh.client.http.producer.LiteProducer;
import cn.webank.eventmesh.common.Constants;
import cn.webank.eventmesh.common.IPUtil;
import cn.webank.eventmesh.common.LiteMessage;
import cn.webank.eventmesh.common.ThreadUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncPublishInstance {

    public static Logger logger = LoggerFactory.getLogger(AsyncPublishInstance.class);

    public static void main(String[] args) throws Exception {
        String confCenterAddr = args[0];
        String proxyIPPort = args[1];
        String topic = args[2];
        String packetSize = args[3];

        if (StringUtils.isBlank(confCenterAddr)) {
            confCenterAddr = "http://127.0.0.1:8090";
        }

        if (StringUtils.isBlank(topic)) {
            topic = "topic-async-test";
        }

        if (StringUtils.isBlank(proxyIPPort)) {
            proxyIPPort = "127.0.0.1:10105";
        }

        if (StringUtils.isBlank(packetSize)) {
            packetSize = "1000";
        }

        LiteClientConfig weMQProxyClientConfig = new LiteClientConfig();
        weMQProxyClientConfig.setRegistryEnabled(false)
                .setRegistryAddr("http://127.0.0.1:8090")
                .setLiteProxyAddr("127.0.0.1:10105")
                .setEnv("A")
                .setRegion("SZ")
                .setIdc("FT")
                .setDcn("AA0")
                .setIp(IPUtil.getLocalAddress())
                .setSys("5147")
                .setPid(String.valueOf(ThreadUtil.getPID()))
                .setLiteProxyAddr(proxyIPPort);
        LiteProducer liteProducer = new LiteProducer(weMQProxyClientConfig);
        liteProducer.start();
        for (int i = 1; i < 10; i++) {
            LiteMessage liteMessage = new LiteMessage();
            liteMessage.setBizSeqNo(RandomStringUtils.randomNumeric(30))
                    .setContent("contentStr with special protocal")
                    .setTopic(topic)
                    .setUniqueId(RandomStringUtils.randomNumeric(30))
                    .addProp(Constants.PROXY_MESSAGE_CONST_TTL, String.valueOf(4 * 3600 * 1000));

            boolean flag = liteProducer.publish(liteMessage);
            Thread.sleep(1000);
            logger.info("publish result , {}", flag);
        }
    }

}
