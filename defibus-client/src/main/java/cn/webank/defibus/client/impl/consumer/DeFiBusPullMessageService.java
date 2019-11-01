/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.webank.defibus.client.impl.consumer;

import cn.webank.defibus.client.impl.factory.DeFiBusClientInstance;
import cn.webank.defibus.common.util.ReflectUtil;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.client.impl.consumer.DefaultMQPushConsumerImpl;
import org.apache.rocketmq.client.impl.consumer.MQConsumerInner;
import org.apache.rocketmq.client.impl.consumer.PullMessageService;
import org.apache.rocketmq.client.impl.consumer.PullRequest;
import org.apache.rocketmq.client.log.ClientLogger;
import org.apache.rocketmq.common.utils.ThreadUtils;
import org.apache.rocketmq.logging.InternalLogger;

public class DeFiBusPullMessageService extends PullMessageService {
    private final InternalLogger log = ClientLogger.getLog();
    private final DeFiBusClientInstance mQClientFactory;
    private final LinkedBlockingQueue<PullRequest> pullRequestQueue;
    private final BrokerHealthyManager brokerHealthyManager;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor(
        new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "DeFiBusPullMessageRetryThread");
            }
        });

    public DeFiBusPullMessageService(DeFiBusClientInstance deFiBusClientInstance) {
        super(deFiBusClientInstance);
        this.mQClientFactory = deFiBusClientInstance;
        this.brokerHealthyManager = new BrokerHealthyManager();

        pullRequestQueue = (LinkedBlockingQueue<PullRequest>) ReflectUtil.getSimpleProperty(PullMessageService.class, this, "pullRequestQueue");
    }

    private void pullMessage(final PullRequest pullRequest) {
        final MQConsumerInner consumer = this.mQClientFactory.selectConsumer(pullRequest.getConsumerGroup());
        if (consumer != null) {
            long beginPullRequestTime = System.currentTimeMillis();

            DefaultMQPushConsumerImpl impl = (DefaultMQPushConsumerImpl) consumer;
            log.debug("begin Pull Message, {}", pullRequest);
            impl.pullMessage(pullRequest);

            long rt = System.currentTimeMillis() - beginPullRequestTime;
            if (rt >= brokerHealthyManager.getIsolateThreshold()) {
                brokerHealthyManager.isolateBroker(pullRequest.getMessageQueue().getBrokerName());
            }
        } else {
            log.warn("No matched consumer for the PullRequest {}, drop it", pullRequest);
        }
    }

    private void pullMessageWithHealthyManage(final PullRequest pullRequest) {
        boolean brokerAvailable = brokerHealthyManager.isBrokerAvailable(pullRequest.getMessageQueue().getBrokerName());
        if (brokerAvailable) {
            pullMessage(pullRequest);
        } else {
            runInRetryThread(pullRequest);
        }
    }

    @Override
    public void run() {
        log.info(this.getServiceName() + " service started");

        while (!this.isStopped()) {
            try {
                PullRequest pullRequest = this.pullRequestQueue.take();
                this.pullMessageWithHealthyManage(pullRequest);
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                log.error("Pull Message Service Run Method exception", e);
            }
        }

        log.info(this.getServiceName() + " service end");
    }

    private void runInRetryThread(PullRequest pullRequest) {
        try {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    pullMessage(pullRequest);
                }
            });
        } catch (Exception ex) {
            log.info("execute pull message in retry thread fail.", ex);
            super.executePullRequestLater(pullRequest, 100);
        }
    }

    @Override
    public void shutdown(boolean interrupt) {
        super.shutdown(interrupt);
        ThreadUtils.shutdownGracefully(this.executorService, 1000, TimeUnit.MILLISECONDS);
    }

    @Override
    public String getServiceName() {
        return DeFiBusPullMessageService.class.getSimpleName();
    }

    class BrokerHealthyManager {
        private final ConcurrentHashMap<String/*brokerName*/, Long/*isolateTime*/> isolatedBroker = new ConcurrentHashMap<>();
        private long isolateThreshold = 500;
        private long ISOLATE_TIMEOUT = 5 * 60 * 1000;

        public boolean isBrokerAvailable(String brokerName) {
            boolean brokerIsolated = isolatedBroker.containsKey(brokerName);
            if (brokerIsolated) {
                boolean isolatedTimeout = System.currentTimeMillis() - isolatedBroker.get(brokerName) > ISOLATE_TIMEOUT;
                if (isolatedTimeout) {
                    removeIsolateBroker(brokerName);
                    return true;
                } else {
                    return false;
                }
            } else {
                return true;
            }
        }

        public void removeIsolateBroker(String brokerName) {
            Long val = isolatedBroker.remove(brokerName);
            if (!isolatedBroker.containsKey(brokerName)) {
                log.info("remove isolated broker success, brokerName: {} isolate time: {}", brokerName, val);
            }
        }

        public void isolateBroker(String brokerName) {
            isolatedBroker.put(brokerName, System.currentTimeMillis());
            if (isolatedBroker.containsKey(brokerName)) {
                log.info("isolate broker for slow pull message success, {}", brokerName);
            }
        }

        public long getIsolateThreshold() {
            return isolateThreshold;
        }
    }
}
