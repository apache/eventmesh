package com.webank.eventmesh.client.http.demo;
import com.webank.eventmesh.client.http.conf.LiteClientConfig;
import com.webank.eventmesh.client.http.producer.LiteProducer;
import com.webank.eventmesh.client.http.producer.RRCallback;
import com.webank.eventmesh.common.IPUtil;
import com.webank.eventmesh.common.LiteMessage;
import com.webank.eventmesh.common.ThreadPoolFactory;
import com.webank.eventmesh.common.ThreadUtil;
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
                    .setContent("contentStr with special protocal")
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
