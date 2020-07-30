package cn.webank.eventmesh.client.http.demo;
import cn.webank.eventmesh.client.http.conf.LiteClientConfig;
import cn.webank.eventmesh.client.http.producer.LiteProducer;
import cn.webank.eventmesh.client.http.producer.RRCallback;
import cn.webank.eventmesh.common.IPUtil;
import cn.webank.eventmesh.common.LiteMessage;
import cn.webank.eventmesh.common.ThreadPoolFactory;
import cn.webank.eventmesh.common.ThreadUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class AsyncSyncRequestInstance {

    public static Logger logger = LoggerFactory.getLogger(SyncRequestInstance.class);

    private static AtomicLong httpRequestPerSecond = new AtomicLong(0);

    private static LinkedList<Integer> httpRequestTPSSnapshots = new LinkedList<Integer>();

    private static AtomicLong httpResPerSecond = new AtomicLong(0);

    private static LinkedList<Integer> httpResTPSSnapshots = new LinkedList<Integer>();

    public static ScheduledExecutorService serviceRegistryScheduler =
            ThreadPoolFactory.createSingleScheduledExecutor("proxy-sdk-stat-");

    public static void main(String[] args) throws Exception {

        serviceRegistryScheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                snapshotHTTPTPS();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        serviceRegistryScheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                logger.info("TPS, request tps:{}, response tps:{}",
                        avgTps(httpRequestTPSSnapshots), avgTps(httpResTPSSnapshots));
            }
        }, 0, 30000, TimeUnit.MILLISECONDS);

        String confCenterAddr = args[0];

        String proxyIPPort = args[1];

        final String topic = args[2];

        String packetSize = args[3];

        if (StringUtils.isBlank(confCenterAddr)) {
            confCenterAddr = "http://127.0.0.1:8090";
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

        final LiteProducer liteProducer = new LiteProducer(weMQProxyClientConfig);

        while (true) {
            final long startTime = System.currentTimeMillis();
            final LiteMessage liteMessage = new LiteMessage();
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
                    .setUniqueId(RandomStringUtils.randomNumeric(30));

            try {
                liteProducer.request(liteMessage, new RRCallback() {
                    @Override
                    public void onSuccess(LiteMessage o) {
                        httpResPerSecond.incrementAndGet();
                        logger.debug("sendmsg : {}, return : {}, cost:{}ms", liteMessage.getContent(), System.currentTimeMillis() - startTime);
                    }

                    @Override
                    public void onException(Throwable e) {
                        httpResPerSecond.incrementAndGet();
                        logger.debug("", e);
                    }
                }, 3000);
                httpRequestPerSecond.incrementAndGet();

                Thread.sleep(2000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void snapshotHTTPTPS() {
        Integer tps = httpRequestPerSecond.intValue();
        httpRequestTPSSnapshots.add(tps);
        httpRequestPerSecond.set(0);
        if (httpRequestTPSSnapshots.size() > 30) {
            httpRequestTPSSnapshots.removeFirst();
        }

        Integer resTps = httpResPerSecond.intValue();
        httpResTPSSnapshots.add(resTps);
        httpResPerSecond.set(0);
        if (httpResTPSSnapshots.size() > 30) {
            httpResTPSSnapshots.removeFirst();
        }
    }

    private static int avgTps(LinkedList<Integer> list) {
        int sum = 0;
        for (Integer i : list) {
            sum = sum + i.intValue();
        }
        return (int) sum / list.size();
    }

}
