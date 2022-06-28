package org.apache.eventmesh.registry.consul.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.agent.model.NewService;

/**
 * @author huyuanxin
 */
public class HeatBeatScheduler {

    private final ConsulClient consulClient;

    private final ConcurrentHashMap<String, NewService> heartBeatMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatServiceExecutor = new ScheduledThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors(),
        r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("ConsulHeartbeatService");
            return thread;
        }
    );

    public HeatBeatScheduler(ConsulClient consulClient) {
        this.consulClient = consulClient;
    }

    /**
     * 开始服务的心跳
     *
     * @param newService 服务
     * @param aclToken   token
     */
    protected void startHeartBeat(NewService newService, String aclToken) {
        heartbeatServiceExecutor.execute(new HeartBeat(newService, aclToken));
        heartBeatMap.put(newService.getName(), newService);
    }

    /**
     * 停止服务的心跳
     *
     * @param newService 服务
     */
    private void stopHeartBeat(NewService newService) {
        heartBeatMap.remove(newService.getName());
    }

    class HeartBeat implements Runnable {

        private static final String CHECK_ID_PREFIX = "service:";

        private String checkId;

        private final String aclToken;

        private final NewService instance;

        public HeartBeat(NewService instance, String aclToken) {
            this.instance = instance;
            this.checkId = instance.getId();
            this.aclToken = aclToken;
            if (!checkId.startsWith(CHECK_ID_PREFIX)) {
                checkId = CHECK_ID_PREFIX + checkId;
            }
        }

        @Override
        public void run() {
            try {
                if (aclToken != null) {
                    consulClient.agentCheckPass(checkId, aclToken);
                    return;
                }
                if (heartBeatMap.contains(instance)) {
                    consulClient.agentCheckPass(checkId);
                }
            } finally {
                heartbeatServiceExecutor.schedule(this, 3000, TimeUnit.SECONDS);
            }
        }

    }
}
