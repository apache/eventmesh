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

package cn.webank.defibus.common;

import org.apache.rocketmq.common.annotation.ImportantField;

public class DeFiBusBrokerConfig {
    private int sendReplyMessageThreadPoolNums = 64;

    private int pushReplyMessageThreadPoolNums = 64;

    private int sendReplyThreadPoolQueueCapacity = 10000;

    private int pushReplyThreadPoolQueueCapacity = 10000;

    @ImportantField
    private String rmqAddressServerSubGroup = "namesrvAddr";

    @ImportantField
    private String rmqAddressServerDomain = "http://127.0.0.1:8090";

    private int scaleQueueThreadPoolQueueCapacity = 100000;

    private int scaleQueueSizeDelayTimeMinute = 5;

    private int scaleQueueRetryTimesMax = 10000;

    private int minQueueNum = 1;

    private boolean redirectMessageEnable = true;

    // whether reject sending when the depth exceeds threshold
    @ImportantField
    private boolean rejectSendWhenMaxDepth = true;

    //warning threshold of fuse
    private double queueDepthHighWatermark = 0.7;

    //whether enable auto-clean when there are multiple consumer groups
    @ImportantField
    private boolean autoUpdateDepth = true;

    @ImportantField
    private int depthCheckInterval = 30 * 1000;

    @ImportantField
    private int checkQueueListeningPeriod = 5;

    @ImportantField
    private boolean checkQueueListening = true;

    public int getSendReplyMessageThreadPoolNums() {
        return sendReplyMessageThreadPoolNums;
    }

    public void setSendReplyMessageThreadPoolNums(int sendReplyMessageThreadPoolNums) {
        this.sendReplyMessageThreadPoolNums = sendReplyMessageThreadPoolNums;
    }

    public int getPushReplyMessageThreadPoolNums() {
        return pushReplyMessageThreadPoolNums;
    }

    public void setPushReplyMessageThreadPoolNums(int pushReplyMessageThreadPoolNums) {
        this.pushReplyMessageThreadPoolNums = pushReplyMessageThreadPoolNums;
    }

    public int getSendReplyThreadPoolQueueCapacity() {
        return sendReplyThreadPoolQueueCapacity;
    }

    public void setSendReplyThreadPoolQueueCapacity(int sendReplyThreadPoolQueueCapacity) {
        this.sendReplyThreadPoolQueueCapacity = sendReplyThreadPoolQueueCapacity;
    }

    public int getPushReplyThreadPoolQueueCapacity() {
        return pushReplyThreadPoolQueueCapacity;
    }

    public void setPushReplyThreadPoolQueueCapacity(int pushReplyThreadPoolQueueCapacity) {
        this.pushReplyThreadPoolQueueCapacity = pushReplyThreadPoolQueueCapacity;
    }

    public String getRmqAddressServerSubGroup() {
        return rmqAddressServerSubGroup;
    }

    public void setRmqAddressServerSubGroup(String rmqAddressServerSubGroup) {
        this.rmqAddressServerSubGroup = rmqAddressServerSubGroup;
    }

    public String getRmqAddressServerDomain() {
        return rmqAddressServerDomain;
    }

    public void setRmqAddressServerDomain(String rmqAddressServerDomain) {
        this.rmqAddressServerDomain = rmqAddressServerDomain;
    }

    public int getScaleQueueThreadPoolQueueCapacity() {
        return scaleQueueThreadPoolQueueCapacity;
    }

    public void setScaleQueueThreadPoolQueueCapacity(int scaleQueueThreadPoolQueueCapacity) {
        this.scaleQueueThreadPoolQueueCapacity = scaleQueueThreadPoolQueueCapacity;
    }

    public int getScaleQueueSizeDelayTimeMinute() {
        return scaleQueueSizeDelayTimeMinute;
    }

    public void setScaleQueueSizeDelayTimeMinute(int scaleQueueSizeDelayTimeMinute) {
        this.scaleQueueSizeDelayTimeMinute = scaleQueueSizeDelayTimeMinute;
    }

    public int getScaleQueueRetryTimesMax() {
        return scaleQueueRetryTimesMax;
    }

    public void setScaleQueueRetryTimesMax(int scaleQueueRetryTimesMax) {
        this.scaleQueueRetryTimesMax = scaleQueueRetryTimesMax;
    }

    public int getMinQueueNum() {
        return minQueueNum;
    }

    public void setMinQueueNum(int minQueueNum) {
        this.minQueueNum = minQueueNum;
    }

    public boolean isRedirectMessageEnable() {
        return redirectMessageEnable;
    }

    public void setRedirectMessageEnable(boolean redirectMessageEnable) {
        this.redirectMessageEnable = redirectMessageEnable;
    }

    public boolean isAutoUpdateDepth() {
        return autoUpdateDepth;
    }

    public boolean isRejectSendWhenMaxDepth() {
        return rejectSendWhenMaxDepth;
    }

    public double getQueueDepthHighWatermark() {
        return queueDepthHighWatermark;
    }

    public int getDepthCheckInterval() {
        return depthCheckInterval;
    }

    public boolean isCheckQueueListening() {
        return checkQueueListening;
    }

    public int getCheckQueueListeningPeriod() {
        return checkQueueListeningPeriod;
    }
}
