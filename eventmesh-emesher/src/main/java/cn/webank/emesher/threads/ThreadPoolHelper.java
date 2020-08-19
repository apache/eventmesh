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

package cn.webank.emesher.threads;

import cn.webank.emesher.boot.ProxyTCPServer;
import cn.webank.emesher.metrics.MonitorMetricConstants;
import com.google.common.collect.Lists;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import org.apache.rocketmq.common.ThreadFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolHelper {
    private static final boolean enabled = Boolean.parseBoolean(System.getProperty("access.server.share.threads", "true"));
    private static final int executorServicePoolSize = Integer.parseInt(System.getProperty("client.executor.corePoolSize",
            String.valueOf(Runtime.getRuntime().availableProcessors())));
    private static final int scheduledExecutorServicePoolSize = Integer.parseInt(System.getProperty("client.schedule.corePoolSize",
            String.valueOf(Runtime.getRuntime().availableProcessors()/2)));
    private static final int mqClientInstanceExecutorServicePoolSize = Integer.parseInt(System.getProperty("client.instance.corePoolSize",
            String.valueOf(Runtime.getRuntime().availableProcessors()/2)));
    private static final int producerCheckExecutorPoolSize = Integer.parseInt(System.getProperty("client.checkExecutor.corePoolSize",
            String.valueOf(Runtime.getRuntime().availableProcessors()/2)));
    private static final int rebalanceImplExecutorServicePoolSize = Integer.parseInt(System.getProperty("client.rebalanceImpl.corePoolSize",
            String.valueOf(Runtime.getRuntime().availableProcessors()/2)));
    private static final int rebalanceServiceExecutorServicePoolSize = Integer.parseInt(System.getProperty("client.rebalanceService.corePoolSize",
            String.valueOf(Runtime.getRuntime().availableProcessors()/4)));
    private static final int pullMessageServiceExecutorServicePoolSize = Integer.parseInt(System.getProperty("client.pullMessage.corePoolSize",
            String.valueOf(Runtime.getRuntime().availableProcessors())));

    private static final Logger logger = LoggerFactory.getLogger("tcpMonitor");

    private static final Logger appLogger = LoggerFactory.getLogger("appMonitor");

    public static void printThreadPoolState() {
        printState(executorService);
        printState(scheduledExecutorService);
        printState(mqClientInstanceExecutorService);
        printState(producerCheckExecutorService);
        printState(rebalanceImplExecutorService);
        printState(rebalanceServiceExecutorService);
        printState(pullMessageServiceExecutorService);
        printState(pullMessageRetryServiceExecutorService);
        printState(consumeMessageExecutor);
        printState((ThreadPoolExecutor) ProxyTCPServer.taskHandleExecutorService);
        printState((ThreadPoolExecutor) ProxyTCPServer.scheduler);
    }

    public static void printState(ThreadPoolExecutor scheduledExecutorService) {
        String threadPoolName = ((ProxyThreadFactoryImpl) scheduledExecutorService.getThreadFactory()).getThreadNamePrefix();
        appLogger.info(String.format(MonitorMetricConstants.PROXY_TCP_MONITOR_FORMAT_THREADPOOL, threadPoolName, MonitorMetricConstants.QUEUE_SIZE, scheduledExecutorService.getQueue().size()));
        appLogger.info(String.format(MonitorMetricConstants.PROXY_TCP_MONITOR_FORMAT_THREADPOOL, threadPoolName, MonitorMetricConstants.POOL_SIZE, scheduledExecutorService.getPoolSize()));
        appLogger.info(String.format(MonitorMetricConstants.PROXY_TCP_MONITOR_FORMAT_THREADPOOL, threadPoolName, MonitorMetricConstants.ACTIVE_COUNT, scheduledExecutorService.getActiveCount()));
        appLogger.info(String.format(MonitorMetricConstants.PROXY_TCP_MONITOR_FORMAT_THREADPOOL, threadPoolName, MonitorMetricConstants.COMPLETED_TASK, scheduledExecutorService.getCompletedTaskCount()));
    }

    /**
     * 全局共享的ThreadPoolExecutor
     */
    private static final ThreadPoolExecutor executorService = __initExecutorService("SharedExecutorService", executorServicePoolSize);

    /**
     * 全局共享的ScheduledThreadPoolExecutor
     */
    private static final ScheduledThreadPoolExecutor scheduledExecutorService = __initScheduledExecutorService
            ("SharedScheduledExecutorService", scheduledExecutorServicePoolSize);

    /**
     * 所有MQClientInstance共享的线程池
     */
    private static final ScheduledThreadPoolExecutor mqClientInstanceExecutorService =
            __initScheduledExecutorService("MQClientInstanceExecutorService", mqClientInstanceExecutorServicePoolSize);

    /**
     * 所有Producer共享的checkExecutor线程池
     */
    private static final ScheduledThreadPoolExecutor producerCheckExecutorService =
            __initScheduledExecutorService("ProducerCheckExecutorService", producerCheckExecutorPoolSize);

    /**
     * 对单topic进行负载均衡操作的线程池
     */
    private static final ThreadPoolExecutor rebalanceImplExecutorService = __initExecutorService
            ("RebalanceImplExecutorService", rebalanceImplExecutorServicePoolSize);

    /**
     * 对单个client进行负载均衡操作的线程池
     */
    private static final ScheduledThreadPoolExecutor rebalanceServiceExecutorService = __initScheduledExecutorService
            ("RebalanceServiceExecutorService", rebalanceServiceExecutorServicePoolSize);

    /**
     * client拉消息的线程池
     */
    private static final ScheduledThreadPoolExecutor pullMessageServiceExecutorService = __initScheduledExecutorService
            ("PullMessageServiceExecutorService", pullMessageServiceExecutorServicePoolSize);

    /**
     * client重试拉消息的线程池
     */
    private static final ScheduledThreadPoolExecutor pullMessageRetryServiceExecutorService = __initScheduledExecutorService
            ("PullMessageRetryServiceExecutorService", pullMessageServiceExecutorServicePoolSize);

    /**
     * ACCESS全局共享的调度线程池
     */
    private static final ScheduledThreadPoolExecutor accessScheduler = new ScheduledThreadPoolExecutor(20, new
            ProxyThreadFactoryImpl("ProxyServerScheduler"));

    /**
     * 用于上报rmb跟踪日志的线程池
     */
    private static final ScheduledThreadPoolExecutor traceLogUploadScheduler = new ScheduledThreadPoolExecutor(50, new
            ProxyThreadFactoryImpl("TraceLogUploadScheduler"));

    private static final ThreadPoolHelper.ProxyServerThreadPool consumeMessageExecutor = new
            ThreadPoolHelper.ProxyServerThreadPool(
            80,
            80,
            120,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100_000),
            new ProxyThreadFactoryImpl("SharedConsumeMessageThread")
    );

    private static NioEventLoopGroup nettyClientSelectors = new SharedNioEventLoopGroup(8, new
            ProxyThreadFactoryImpl("AccessNettyClientSelector"));

    private static DefaultEventExecutorGroup nettyClientWorkerThreads = new SharedEventExecutorGroup(16, new
            ProxyThreadFactoryImpl("AccessNettyClientWorkerThread"));

    private static ThreadPoolExecutor __initExecutorService(String namePrefix, int corePoolSize) {
        return new ThreadPoolExecutor(
                corePoolSize,
                300,
                120,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50_000),
                new ProxyThreadFactoryImpl(namePrefix)
        );
    }

    private static ScheduledThreadPoolExecutor __initScheduledExecutorService(String namePrefix, int corePoolSize) {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                corePoolSize,
                new ProxyThreadFactoryImpl(namePrefix)
        );
        executor.setRemoveOnCancelPolicy(true);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    private static ExecutorService createSharedExecutorService(String name) {
        return new SharedExecutorService(executorService, name);
    }

    private static ScheduledExecutorService createSharedScheduledExecutorService(String name) {
        return new SharedScheduledExecutorService(scheduledExecutorService, name);
    }

    private static ScheduledExecutorService createMQClientInstanceExecutorService(String name) {
        return new SharedScheduledExecutorService(mqClientInstanceExecutorService, name);
    }

    private static ScheduledExecutorService createProducerCheckExecutorService(String name) {
        return new SharedScheduledExecutorService(producerCheckExecutorService, name);
    }

    private static ExecutorService createRebalanceImplExecutorService(String name) {
        return new SharedExecutorService(rebalanceImplExecutorService, name);
    }

    private static ScheduledExecutorService createRebalanceServiceExecutorService(String name) {
        return new SharedScheduledExecutorService(rebalanceServiceExecutorService, name);
    }

    private static ScheduledExecutorService createPullMessageServiceExecutorService(String name) {
        return new SharedScheduledExecutorService(pullMessageServiceExecutorService, name);
    }

    private static ScheduledExecutorService createPullMessageRetryServiceExecutorService(String name) {
        return new SharedScheduledExecutorService(pullMessageRetryServiceExecutorService, name);
    }

    private static ScheduledExecutorService createOriginalMQClientFactoryScheduledThread() {
        return Executors.newSingleThreadScheduledExecutor(new ProxyThreadFactoryImpl
                ("MQClientFactoryScheduledThread"));
    }

    private static ScheduledExecutorService createOriginalUpdateCLInfoScheduledThread() {
        return Executors.newSingleThreadScheduledExecutor(new ProxyThreadFactoryImpl("UpdateCLInfo"));
    }

    private static ScheduledExecutorService createOriginalCleanUpRRResponseServiceScheduledThread() {
        return Executors.newSingleThreadScheduledExecutor(new ProxyThreadFactoryImpl
                ("CleanUp_RRResponse_Service_"));
    }

    private static ScheduledExecutorService createOriginalMQClusterManageServiceScheduledThread() {
        return Executors.newSingleThreadScheduledExecutor(new ProxyThreadFactoryImpl("MQClusterManageService_"));
    }

    private static ScheduledExecutorService createOriginalClientHouseKeepingService() {
        return Executors.newSingleThreadScheduledExecutor(new ProxyThreadFactoryImpl("ClientHouseKeepingService"));
    }

    private static ScheduledExecutorService createOriginalCleanExpireMsgScheduledThreads() {
        return Executors.newSingleThreadScheduledExecutor(new ThreadFactoryImpl("CleanExpireMsgScheduledThread_"));
    }

    private static ScheduledExecutorService createOriginalConsumeMessageScheduledThreads() {
        return Executors.newSingleThreadScheduledExecutor(new ThreadFactoryImpl("ConsumeMessageScheduledThread_"));
    }

    private static ScheduledExecutorService createOriginalPullMessageServiceScheduledThreads() {
        return Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "PullMessageServiceScheduledThread");
            }
        });
    }

    private static ExecutorService createOriginalNettyClientPublicExecutor() {
        return Executors.newFixedThreadPool(2, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "NettyClientPublicExecutor_" + this.threadIndex.incrementAndGet());
            }
        });
    }

    private static NioEventLoopGroup createOriginalNettyClientSelectors() {
        return new NioEventLoopGroup(1, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("NettyClientSelector_%d",
                        this.threadIndex.incrementAndGet()));
            }
        });
    }

    private static DefaultEventExecutorGroup createOriginalNettyClientWorkerThreads() {
        return new DefaultEventExecutorGroup(//
                2,// nettyClientConfig.getClientWorkerThreads(), //
                new ThreadFactory() {
                    private AtomicInteger threadIndex = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "NettyClientWorkerThread_" + this.threadIndex.incrementAndGet());
                    }
                });
    }

    private static ThreadPoolExecutor createOriginalConsumeMessageThreads() {
        return new ThreadPoolExecutor(//
                20, //this.defaultMQPushConsumer.getConsumeThreadMin(),//
                60,// this.defaultMQPushConsumer.getConsumeThreadMax(),//
                1000 * 60,//
                TimeUnit.MILLISECONDS,//
                new LinkedBlockingQueue<Runnable>(),// this.consumeRequestQueue,//
                new ThreadFactoryImpl("ConsumeMessageThread_"));
    }

    public static ScheduledExecutorService getMQClientFactoryScheduledThread() {
        if (enabled)
            return createMQClientInstanceExecutorService("MQClientFactoryScheduledThread");
        else
            return createOriginalMQClientFactoryScheduledThread();
    }

    public static ScheduledExecutorService getProducerCheckScheduledThread() {
        return createProducerCheckExecutorService("ProducerCheckScheduledThread");
    }

    public static ScheduledExecutorService getUpdateCLInfoScheduledThread() {
        if (enabled)
            return createSharedScheduledExecutorService("UpdateCLInfo");
        else
            return createOriginalUpdateCLInfoScheduledThread();
    }

    public static ScheduledExecutorService getCleanUpRRResponseServiceScheduledThread() {
        if (enabled)
            return createSharedScheduledExecutorService("CleanUp_RRResponse_Service_");
        else
            return createOriginalCleanUpRRResponseServiceScheduledThread();
    }

    public static ScheduledExecutorService getMQClusterManageServiceScheduledThread() {
        if (enabled)
            return createSharedScheduledExecutorService("MQClusterManageService_");
        else
            return createOriginalMQClusterManageServiceScheduledThread();
    }

    public static ExecutorService getClientExecutorService() {
        if (enabled)
            return createSharedExecutorService("MQClientThread_");
        else
            throw new UnsupportedOperationException();
    }

    public static ScheduledExecutorService getClientScheduledExecutorService() {
        if (enabled)
            return createSharedScheduledExecutorService("MQClientScheduler_");
        else
            throw new UnsupportedOperationException();
    }

    public static ScheduledExecutorService getPullMessageServiceScheduledThread() {
        if (enabled)
            return createPullMessageServiceExecutorService("PullMessageServiceScheduledThread");
        else
            return createOriginalPullMessageServiceScheduledThreads();
    }

    public static ScheduledExecutorService getPullMessageRetryServiceScheduledThread() {
        return createPullMessageRetryServiceExecutorService("PullMessageRetryServiceScheduledThread");
    }

    public static ExecutorService getNettyClientPublicExecutor() {
        if (enabled)
            return createSharedExecutorService("NettyClientPublicExecutor");
        else
            return createOriginalNettyClientPublicExecutor();
    }

    public static NioEventLoopGroup getNettyClientSelectors() {
        if (enabled)
            return nettyClientSelectors;
        else
            return createOriginalNettyClientSelectors();
    }

    public static DefaultEventExecutorGroup getNettyClientWorkerThreads() {
        if (enabled)
            return nettyClientWorkerThreads;
        else
            return createOriginalNettyClientWorkerThreads();
    }

    public static ScheduledExecutorService getClientHouseKeepingService() {
        if (enabled)
            return createSharedScheduledExecutorService("ClientHouseKeepingService");
        else
            return createOriginalClientHouseKeepingService();
    }

    public static ScheduledExecutorService getConsumeMessageScheduledThread() {
        if (enabled)
            return createSharedScheduledExecutorService("ConsumeMessageScheduledThread");
        else
            return createOriginalConsumeMessageScheduledThreads();
    }

    public static ScheduledExecutorService getCleanExpireMsgScheduledThread() {
        if (enabled) {
            return createSharedScheduledExecutorService("CleanExpireMsgScheduledThread");
        } else {
            return createOriginalCleanExpireMsgScheduledThreads();
        }
    }

    public static ExecutorService getConsumeMessageThreads() {
        if (enabled)
            return createSharedExecutorService("ConsumeMessageThread_");
        else
            return createOriginalConsumeMessageThreads();
    }

    public static ScheduledExecutorService getRebalanceServiceExecutorService() {
        return createRebalanceServiceExecutorService("RebalanceServiceThread");
    }

    public static ExecutorService getRebalanceImplExecutorService() {
        return createRebalanceImplExecutorService("RebalanceByTopicThread");
    }

    public static ProxyServerThreadPool getConsumeMessageExecutor() {
        return consumeMessageExecutor;
    }

    public static void shutdownSharedScheduledExecutorService() {
        scheduledExecutorService.shutdown();
    }

    public static void shutdownRebalanceImplExecutorService() {
        rebalanceImplExecutorService.shutdown();
    }

    public static void shutdownRebalanceServiceExecutorService() {
        rebalanceServiceExecutorService.shutdown();
    }

    public static void shutdownMQClientInstanceExecutorService() {
        mqClientInstanceExecutorService.shutdown();
    }

    public static void shutdownProducerCheckExecutorService(){
        producerCheckExecutorService.shutdown();
    }

    public static void shutdownPullMessageServiceExecutorService() {
        pullMessageServiceExecutorService.shutdown();
    }

    public static void shutdownPullMessageRetryServiceExecutorService() {
        pullMessageRetryServiceExecutorService.shutdown();
    }

    public static void shutdownExecutorService() {
        executorService.shutdown();
    }


    public static void shutdownConsumeMessageExecutor() {
        consumeMessageExecutor.shutdownGracefully();
    }

    public static void shutdownNettyClientWorkerThread() {
        if (nettyClientWorkerThreads instanceof SharedEventExecutorGroup) {
            ((SharedEventExecutorGroup) nettyClientWorkerThreads).shutdownInternel();
        } else {
            nettyClientWorkerThreads.shutdownGracefully();
        }
    }

    public static void shutdownNettyClientSelector() {
        if (nettyClientSelectors instanceof SharedNioEventLoopGroup) {
            ((SharedNioEventLoopGroup) nettyClientSelectors).shutdownInternel();
        } else {
            nettyClientSelectors.shutdownGracefully();
        }
    }

    public static class ProxyServerThreadPool extends ThreadPoolExecutor {
        private boolean terminated = false;

        public ProxyServerThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                                     BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        }

        @Override
        public void shutdown() {
            terminated = true;
        }

        @Override
        public boolean isTerminated() {
            return terminated;
        }

        public void shutdownGracefully() {
            super.shutdown();
        }

        public List<Runnable> shutdownNow() {
            return Lists.newArrayList();
        }
    }
}

