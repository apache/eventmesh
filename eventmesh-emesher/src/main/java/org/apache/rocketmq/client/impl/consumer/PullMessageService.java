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
package org.apache.rocketmq.client.impl.consumer;

import cn.webank.emesher.threads.ThreadPoolHelper;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.client.log.ClientLogger;
import org.apache.rocketmq.common.ServiceThread;
import org.apache.rocketmq.logging.InternalLogger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PullMessageService extends ServiceThread {
    private final InternalLogger log = ClientLogger.getLog();
    private final MQClientInstance mQClientFactory;
    private final ScheduledExecutorService scheduledExecutorService = ThreadPoolHelper
            .getPullMessageServiceScheduledThread();
    private final ScheduledExecutorService retryScheduledExecutorService = ThreadPoolHelper
            .getPullMessageRetryServiceScheduledThread();
    private final ConcurrentHashMap<String/*brokerName*/, Long/*isolateTime*/> isolatedBroker = new ConcurrentHashMap<>();
    private long ISOLATE_THRESHOLD = 500;
    private long ISOLATE_TIMEOUT = 5 * 60 * 1000;

    public PullMessageService(MQClientInstance mQClientFactory) {
        super(true);
        this.mQClientFactory = mQClientFactory;
    }

    public void executePullRequestLater(final PullRequest pullRequest, final long timeDelay) {
        if (!isStopped()) {
            this.scheduledExecutorService.schedule(new Runnable() {
                //use this to identify anonymous inner class
                private static final String tag = "ExecutePullRequestLater";

                @Override
                public void run() {
                    PullMessageService.this.executePullRequestImmediately(pullRequest);
                }
            }, timeDelay, TimeUnit.MILLISECONDS);
        } else {
            log.warn("PullMessageServiceScheduledThread has shutdown");
        }
    }

    public void executePullRequestImmediately(final PullRequest pullRequest) {
        if (this.isStopped() || pullRequest == null) return;
        this.scheduledExecutorService.execute(new Runnable() {
            //use this to identify anonymous inner class
            private static final String tag = "ExecutePullRequestImmediately";

            @Override
            public void run() {
                if (!PullMessageService.this.isStopped()) {
                    PullMessageService.this.pullMessage(pullRequest);
                }
            }
        });
    }

    public void executeTaskLater(final Runnable r, final long timeDelay) {
        if (!isStopped()) {
            this.scheduledExecutorService.schedule(r, timeDelay, TimeUnit.MILLISECONDS);
        } else {
            log.warn("PullMessageServiceScheduledThread has shutdown");
        }
    }

    public ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }

    public void isolateBroker(String brokerName){
        log.info("isolate broker for slow pull message, {}", brokerName);
        isolatedBroker.put(brokerName, System.currentTimeMillis());
    }

    public void removeIsolateBroker(String brokerName){
        Long val = isolatedBroker.remove(brokerName);
        log.info("remove isolated broker, brokerName: {} isolate time: {}", brokerName, val);
    }

    public boolean isBrokerAvailable(String brokerName){
        boolean brokerIsolated = isolatedBroker.containsKey(brokerName);
        if (brokerIsolated){
            boolean isolatedTimeout = System.currentTimeMillis() - isolatedBroker.get(brokerName) > ISOLATE_TIMEOUT;
            if (isolatedTimeout) {
                removeIsolateBroker(brokerName);
                return true;
            }
            else {
                return false;
            }
        }
        else {
            return true;
        }
    }

    private void pullMessage(final PullRequest pullRequest) {
        boolean brokerAvailable = isBrokerAvailable(pullRequest.getMessageQueue().getBrokerName());
        if (brokerAvailable) {
            final MQConsumerInner consumer = this.mQClientFactory.selectConsumer(pullRequest.getConsumerGroup());
            if (consumer != null) {
                DefaultMQPushConsumerImpl impl = (DefaultMQPushConsumerImpl) consumer;
                long beginPullRequestTime = System.currentTimeMillis();
                log.debug("begin Pull Message, {}", pullRequest);
                impl.pullMessage(pullRequest);
                long rt = System.currentTimeMillis() - beginPullRequestTime;
                if (rt >= ISOLATE_THRESHOLD) {
                    this.isolateBroker(pullRequest.getMessageQueue().getBrokerName());
                }
            } else {
                log.warn("No matched consumer for the PullRequest {}, drop it", pullRequest);
            }
        } else {
            pullMessageByRetryExecutor(pullRequest);
        }
    }

    private void pullMessageByRetryExecutor(final PullRequest pullRequest) {
        if (this.isStopped() || pullRequest == null) return;
        this.retryScheduledExecutorService.execute(new Runnable() {
            private static final String tag = "RetryExecutePullRequestImmediately";

            @Override
            public void run() {
                if (!PullMessageService.this.isStopped()) {
                    final MQConsumerInner consumer = mQClientFactory.selectConsumer(pullRequest.getConsumerGroup());
                    if (consumer != null) {
                        final DefaultMQPushConsumerImpl impl = (DefaultMQPushConsumerImpl) consumer;
                        log.debug("begin Pull Message, {}", pullRequest);
                        impl.pullMessage(pullRequest);
                    } else {
                        log.warn("No matched consumer for the PullRequest {}, drop it", pullRequest);
                    }
                }
            }
        });
    }

    @Override
    public void start() {
        log.debug("PullMessageServiceThread has been replaced with a thread pool.");
    }

    @Override
    public void run() {
        /** expected never be called */
        throw new UnsupportedOperationException("proxy internal bug.");
    }

    @Override
    public void shutdown(boolean interrupt) {
        scheduledExecutorService.shutdown();
        retryScheduledExecutorService.shutdown();
    }

    @Override
    public String getServiceName() {
        return PullMessageService.class.getSimpleName();
    }

    public void makeStop() {
        super.makeStop();
        scheduledExecutorService.shutdown();
        retryScheduledExecutorService.shutdown();
    }

    public void stop(final boolean interrupt) {
        shutdown(interrupt);
    }
}
