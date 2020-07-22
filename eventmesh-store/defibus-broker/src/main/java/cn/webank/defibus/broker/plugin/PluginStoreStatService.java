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

package cn.webank.defibus.broker.plugin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.rocketmq.common.ThreadFactoryImpl;
import org.apache.rocketmq.common.constant.LoggerName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginStoreStatService {
    private final static Logger LOG = LoggerFactory.getLogger(LoggerName.ROCKETMQ_STATS_LOGGER_NAME);
    private final String[] MESSAGE_ENTIRE_TIME_DESC = new String[] {
        "[<=1000ns]", "[<=10000ns]", "[10000~100000ns]",
        "[100000~1000000ns]", "[1~10ms]", "[10~100ms]", "[100~1000ms]", "[>=1000ms]"};
    private volatile AtomicLong[] putMessageDistributeTime;
    private volatile AtomicLong[] getMessageDistributeTime;
    private ScheduledExecutorService scheduleService = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryImpl
        ("DeFiBusPluginStore_Stat_"));

    public void start() {
        putMessageDistributeTime = new AtomicLong[MESSAGE_ENTIRE_TIME_DESC.length];
        getMessageDistributeTime = new AtomicLong[MESSAGE_ENTIRE_TIME_DESC.length];
        for (int i = 0; i < MESSAGE_ENTIRE_TIME_DESC.length; i++) {
            putMessageDistributeTime[i] = new AtomicLong(0);
            getMessageDistributeTime[i] = new AtomicLong(0);
        }

        scheduleService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                printStoreStat();
            }
        }, 60 * 1000, 60 * 1000, TimeUnit.MILLISECONDS);

    }

    public void shutdown() {
        scheduleService.shutdownNow();
    }

    public void recordPutTime(long value) {
        final AtomicLong[] times = this.putMessageDistributeTime;

        if (null == times)
            return;

        if (value <= 1000) {
            times[0].incrementAndGet();
        } else if (value < 10000) {
            times[1].incrementAndGet();
        } else if (value < 100000) {
            times[2].incrementAndGet();
        } else if (value < 1000000) {
            times[3].incrementAndGet();
        } else if (value < 10000000) {
            times[4].incrementAndGet();
        } else if (value < 100000000) {
            times[5].incrementAndGet();
        } else if (value < 1000000000) {
            times[6].incrementAndGet();
        } else {
            times[7].incrementAndGet();
        }
    }

    public void recordGetTime(long value) {
        final AtomicLong[] times = this.getMessageDistributeTime;

        if (null == times)
            return;

        //stat
        if (value <= 1000) {
            times[0].incrementAndGet();
        } else if (value < 10000) {
            times[1].incrementAndGet();
        } else if (value < 100000) {
            times[2].incrementAndGet();
        } else if (value < 1000000) {
            times[3].incrementAndGet();
        } else if (value < 10000000) {
            times[4].incrementAndGet();
        } else if (value < 100000000) {
            times[5].incrementAndGet();
        } else if (value < 1000000000) {
            times[6].incrementAndGet();
        } else {
            times[7].incrementAndGet();
        }
    }

    public void printStoreStat() {
        StringBuilder sbPut = new StringBuilder();
        for (int i = 0; i < MESSAGE_ENTIRE_TIME_DESC.length; i++) {
            long value = putMessageDistributeTime[i].get();
            sbPut.append(String.format("%s:%d", MESSAGE_ENTIRE_TIME_DESC[i], value));
            sbPut.append(" ");
        }

        StringBuilder sbGet = new StringBuilder();
        for (int i = 0; i < MESSAGE_ENTIRE_TIME_DESC.length; i++) {
            long value = getMessageDistributeTime[i].get();
            sbGet.append(String.format("%s:%d", MESSAGE_ENTIRE_TIME_DESC[i], value));
            sbGet.append(" ");
        }

        LOG.info("putMessage stat:" + sbPut);
        LOG.info("getMessage stat:" + sbGet);
        for (int i = 0; i < MESSAGE_ENTIRE_TIME_DESC.length; i++) {
            putMessageDistributeTime[i].set(0);
        }

        for (int i = 0; i < MESSAGE_ENTIRE_TIME_DESC.length; i++) {
            getMessageDistributeTime[i].set(0);
        }
    }
}
