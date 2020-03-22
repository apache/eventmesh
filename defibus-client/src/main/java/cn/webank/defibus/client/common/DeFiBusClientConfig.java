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

package cn.webank.defibus.client.common;

import cn.webank.defibus.common.DeFiBusVersion;
import org.apache.rocketmq.remoting.RPCHook;

public class DeFiBusClientConfig {
    //default mq producer
    private String producerGroup = "DefaultProducerGroup";
    private int retryTimesWhenSendFailed = 2;
    private int retryTimesWhenSendAsyncFailed = 2;

    private int pubWindowSize = 65535;      //clientAsyncSemaphoreValue

    //default mq push consumer
    private String consumerGroup = "DefaultConsumerGroup";
    private int consumeConcurrentlyMaxSpan = 2000;
    private long pullInterval = 0;
    private int consumeMessageBatchMaxSize = 1;
    private int pullBatchSize = 32;
    private int maxReconsumeTimes = 3;
    private long consumeTimeout = 15;

    private int ackWindowSize = 1000;       //pullThresholdForQueue
    private int threadPoolCoreSize = 24;   //consumeThreadMin
    private int threadPoolMaxSize = 72;    //consumeThreadMax
    private int ackTime = 5000;             //persistConsumerOffsetInterval

    //rmq client config
    private String namesrvAddr = null;
    private int pollNameServerInterval = 1000 * 5;
    private int heartbeatBrokerInterval = 1000 * 10;

    private int consumeRequestQueueCapacity = 10000;
    private String wsAddr = null;
    private String clusterPrefix = null;
    private final int version = DeFiBusVersion.CURRENT_VERSION;
    private final RPCHook rpcHook = null;
    private long queueIsolateTimeMillis = 60 * 1000L;

    private long pullTimeDelayMillsWhenExcept = 0;
    private long pullTimeDelayMillsWhenFlowControl = 50;
    private long pullTimeDelayMillsWhenSuspend = 500;

    private int minMqNumWhenSendLocal = 1;

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public int getRetryTimesWhenSendFailed() {
        return retryTimesWhenSendFailed;
    }

    public void setRetryTimesWhenSendFailed(int retryTimesWhenSendFailed) {
        this.retryTimesWhenSendFailed = retryTimesWhenSendFailed;
    }

    public int getRetryTimesWhenSendAsyncFailed() {
        return retryTimesWhenSendAsyncFailed;
    }

    public void setRetryTimesWhenSendAsyncFailed(int retryTimesWhenSendAsyncFailed) {
        this.retryTimesWhenSendAsyncFailed = retryTimesWhenSendAsyncFailed;
    }

    public int getPubWindowSize() {
        return pubWindowSize;
    }

    public void setPubWindowSize(int pubWindowSize) {
        this.pubWindowSize = pubWindowSize;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public int getConsumeConcurrentlyMaxSpan() {
        return consumeConcurrentlyMaxSpan;
    }

    public void setConsumeConcurrentlyMaxSpan(int consumeConcurrentlyMaxSpan) {
        this.consumeConcurrentlyMaxSpan = consumeConcurrentlyMaxSpan;
    }

    public long getPullInterval() {
        return pullInterval;
    }

    public void setPullInterval(long pullInterval) {
        this.pullInterval = pullInterval;
    }

    public int getConsumeMessageBatchMaxSize() {
        return consumeMessageBatchMaxSize;
    }

    public void setConsumeMessageBatchMaxSize(int consumeMessageBatchMaxSize) {
        this.consumeMessageBatchMaxSize = consumeMessageBatchMaxSize;
    }

    public int getPullBatchSize() {
        return pullBatchSize;
    }

    public void setPullBatchSize(int pullBatchSize) {
        this.pullBatchSize = pullBatchSize;
    }

    public int getMaxReconsumeTimes() {
        return maxReconsumeTimes;
    }

    public void setMaxReconsumeTimes(int maxReconsumeTimes) {
        this.maxReconsumeTimes = maxReconsumeTimes;
    }

    public long getConsumeTimeout() {
        return consumeTimeout;
    }

    public void setConsumeTimeout(long consumeTimeout) {
        this.consumeTimeout = consumeTimeout;
    }

    public int getAckWindowSize() {
        return ackWindowSize;
    }

    public void setAckWindowSize(int ackWindowSize) {
        this.ackWindowSize = ackWindowSize;
    }

    public int getThreadPoolCoreSize() {
        return threadPoolCoreSize;
    }

    public void setThreadPoolCoreSize(int threadPoolCoreSize) {
        this.threadPoolCoreSize = threadPoolCoreSize;
    }

    public int getThreadPoolMaxSize() {
        return threadPoolMaxSize;
    }

    public void setThreadPoolMaxSize(int threadPoolMaxSize) {
        this.threadPoolMaxSize = threadPoolMaxSize;
    }

    public int getAckTime() {
        return ackTime;
    }

    public void setAckTime(int ackTime) {
        this.ackTime = ackTime;
    }

    public String getNamesrvAddr() {
        return namesrvAddr;
    }

    public void setNamesrvAddr(String namesrvAddr) {
        this.namesrvAddr = namesrvAddr;
    }

    public int getPollNameServerInterval() {
        return pollNameServerInterval;
    }

    public void setPollNameServerInterval(int pollNameServerInterval) {
        this.pollNameServerInterval = pollNameServerInterval;
    }

    public int getHeartbeatBrokerInterval() {
        return heartbeatBrokerInterval;
    }

    public void setHeartbeatBrokerInterval(int heartbeatBrokerInterval) {
        this.heartbeatBrokerInterval = heartbeatBrokerInterval;
    }

    public int getConsumeRequestQueueCapacity() {
        return consumeRequestQueueCapacity;
    }

    public void setConsumeRequestQueueCapacity(int consumeRequestQueueCapacity) {
        this.consumeRequestQueueCapacity = consumeRequestQueueCapacity;
    }

    public String getWsAddr() {
        return wsAddr;
    }

    public void setWsAddr(String wsAddr) {
        this.wsAddr = wsAddr;
    }

    public int getVersion() {
        return version;
    }

    public RPCHook getRpcHook() {
        return rpcHook;
    }

    public long getQueueIsolateTimeMillis() {
        return queueIsolateTimeMillis;
    }

    public void setQueueIsolateTimeMillis(long queueIsolateTimeMillis) {
        this.queueIsolateTimeMillis = queueIsolateTimeMillis;
    }

    public String getClusterPrefix() {
        return clusterPrefix;
    }

    public void setClusterPrefix(String clusterPrefix) {
        if (clusterPrefix != null) {
            String tmp = clusterPrefix.toUpperCase().trim().replace(" ", "");
            if (tmp.length() > 0)
                this.clusterPrefix = tmp;
        }
    }

    public long getPullTimeDelayMillsWhenExcept() {
        return pullTimeDelayMillsWhenExcept;
    }

    public void setPullTimeDelayMillsWhenExcept(long pullTimeDelayMillsWhenExcept) {
        this.pullTimeDelayMillsWhenExcept = pullTimeDelayMillsWhenExcept;
    }

    public long getPullTimeDelayMillsWhenFlowControl() {
        return pullTimeDelayMillsWhenFlowControl;
    }

    public void setPullTimeDelayMillsWhenFlowControl(long pullTimeDelayMillsWhenFlowControl) {
        this.pullTimeDelayMillsWhenFlowControl = pullTimeDelayMillsWhenFlowControl;
    }

    public long getPullTimeDelayMillsWhenSuspend() {
        return pullTimeDelayMillsWhenSuspend;
    }

    public void setPullTimeDelayMillsWhenSuspend(long pullTimeDelayMillsWhenSuspend) {
        this.pullTimeDelayMillsWhenSuspend = pullTimeDelayMillsWhenSuspend;
    }

    public int getMinMqNumWhenSendLocal() {
        return minMqNumWhenSendLocal;
    }

    public void setMinMqNumWhenSendLocal(int minMqNumWhenSendLocal) {
        this.minMqNumWhenSendLocal = minMqNumWhenSendLocal;
    }

    @Override public String toString() {
        return "DeFiBusClientConfig{" +
            "producerGroup='" + producerGroup + '\'' +
            ", retryTimesWhenSendFailed=" + retryTimesWhenSendFailed +
            ", retryTimesWhenSendAsyncFailed=" + retryTimesWhenSendAsyncFailed +
            ", pubWindowSize=" + pubWindowSize +
            ", consumerGroup='" + consumerGroup + '\'' +
            ", consumeConcurrentlyMaxSpan=" + consumeConcurrentlyMaxSpan +
            ", pullInterval=" + pullInterval +
            ", consumeMessageBatchMaxSize=" + consumeMessageBatchMaxSize +
            ", pullBatchSize=" + pullBatchSize +
            ", maxReconsumeTimes=" + maxReconsumeTimes +
            ", consumeTimeout=" + consumeTimeout +
            ", ackWindowSize=" + ackWindowSize +
            ", threadPoolCoreSize=" + threadPoolCoreSize +
            ", threadPoolMaxSize=" + threadPoolMaxSize +
            ", ackTime=" + ackTime +
            ", namesrvAddr='" + namesrvAddr + '\'' +
            ", pollNameServerInterval=" + pollNameServerInterval +
            ", heartbeatBrokerInterval=" + heartbeatBrokerInterval +
            ", consumeRequestQueueCapacity=" + consumeRequestQueueCapacity +
            ", wsAddr='" + wsAddr + '\'' +
            ", clusterPrefix='" + clusterPrefix + '\'' +
            ", version=" + version +
            ", rpcHook=" + rpcHook +
            ", queueIsolateTimeMillis=" + queueIsolateTimeMillis +
            ", pullTimeDelayMillsWhenExcept=" + pullTimeDelayMillsWhenExcept +
            ", pullTimeDelayMillsWhenFlowControl=" + pullTimeDelayMillsWhenFlowControl +
            ", pullTimeDelayMillsWhenSuspend=" + pullTimeDelayMillsWhenSuspend +
            ", minMqNumWhenSendLocal=" + minMqNumWhenSendLocal +
            '}';
    }
}
