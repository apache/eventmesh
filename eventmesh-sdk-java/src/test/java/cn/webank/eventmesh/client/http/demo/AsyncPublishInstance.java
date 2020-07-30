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
            confCenterAddr = "http://172.22.1.82:8090";
        }

        if (StringUtils.isBlank(topic)) {
            topic = "FT0-e-10010000-01-1";
        }

        if (StringUtils.isBlank(proxyIPPort)) {
            proxyIPPort = "127.0.0.1:10105";
        }

        if (StringUtils.isBlank(packetSize)) {
            packetSize = "1000";
        }

        LiteClientConfig weMQProxyClientConfig = new LiteClientConfig();
        weMQProxyClientConfig.setRegistryEnabled(false)
                .setRegistryAddr("http://172.22.1.82:8090")
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
                    .setContent("{\"appHeaderContent\":\"{\\\"extFields\\\":{}}\"," +
                            "\"appHeaderName\":\"cn.webank.rmb.message.AppHeader\",\"body\":\"{\\\"key\\\":\\\"nanoxiong\\\"}\"," +
                            "\"correlationId\":\"#JAVA\",\"createTime\":1566378251260,\"deliveryTimes\":1," +
                            "\"destinationContent\":\"{\\\"anyDCN\\\":false,\\\"dcnNo\\\":\\\"FT0\\\",\\\"name\\\":\\\"FT0/e/10010000/01/1\\\",\\\"organizationId\\\":\\\"99996\\\"," +
                            "\\\"organizationIdInputFlag\\\":1,\\\"scenario\\\":\\\"01\\\",\\\"serviceOrEventId\\\":\\\"10010000\\\",\\\"type\\\":\\\"se\\\"}\"," +
                            "\"duplicated\":false,\"errorCode\":\"\",\"replyToContent\":\"null\",\"resent\":false,\"syn\":false," +
                            "\"sysHeaderContent\":\"{\\\"apiType\\\":1,\\\"bizSeqNo\\\":\\\"13263386761525211049779105006348\\\"," +
                            "\\\"consumerDCN\\\":\\\"FT0\\\",\\\"consumerId\\\":\\\"1001\\\",\\\"consumerSeqNo\\\":\\\"74601315176162031765354323451864\\\"," +
                            "\\\"consumerSvrId\\\":\\\"127.0.0.1\\\",\\\"contentLength\\\":19,\\\"extFields\\\":{\\\"req_ip\\\":\\\"127.0.0.1\\\",\\\"req_sys\\\":\\\"1001\\\"," +
                            "\\\"req_dcn\\\":\\\"FT0\\\",\\\"MSG_FROM\\\":\\\"WeMQ\\\"},\\\"messageType\\\":3,\\\"orgSvrId\\\":\\\"127.0.0.1\\\",\\\"orgSysId\\\":\\\"1001\\\"," +
                            "\\\"organizationId\\\":\\\"99996\\\",\\\"receiveMode\\\":1,\\\"rmbVersion\\\":\\\"2.1.3\\\",\\\"sendTimestamp\\\":1566378255141," +
                            "\\\"solCorrelationId\\\":\\\"#JAVA\\\",\\\"tranTimestamp\\\":1566378251088," +
                            "\\\"uniqueId\\\":\\\"w/f06e3842-c926-4ad3-b5a6-55bef5e5c708\\\",\\\"version\\\":\\\"1.0.0\\\"}\",\"timeToLive\":4000}")
                    .setTopic(topic)
                    .setUniqueId(RandomStringUtils.randomNumeric(30))
                    .addProp(Constants.PROXY_MESSAGE_CONST_TTL, String.valueOf(4 * 3600 * 1000));

            boolean flag = liteProducer.publish(liteMessage);
            Thread.sleep(1000);
            logger.info("publish result , {}", flag);
        }
    }

}
