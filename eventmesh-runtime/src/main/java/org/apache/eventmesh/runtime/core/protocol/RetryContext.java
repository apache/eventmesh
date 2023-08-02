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

package org.apache.eventmesh.runtime.core.protocol;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import io.cloudevents.CloudEvent;

public abstract class RetryContext implements DelayRetryable {

    private CloudEvent event;

    private String seq;

    private int retryTimes;

    private long executeTime = System.currentTimeMillis();

    public RetryContext() {}

    public RetryContext(CloudEvent event) {
        this.event = event;
    }

    public RetryContext delay(long delay) {
        this.executeTime = System.currentTimeMillis() + (retryTimes + 1) * delay;
        return this;
    }

    @Override
    public int compareTo(@Nonnull Delayed delayed) {
        RetryContext obj = (RetryContext) delayed;
        return Long.compare(this.executeTime, obj.executeTime);

    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(this.executeTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    public void increaseRetryTimes() {
        retryTimes++;
    }

    public RetryContext(CloudEvent event, String seq) {
        this.event = event;
        this.seq = seq;
    }

    public CloudEvent getEvent() {
        return event;
    }

    public void setEvent(CloudEvent event) {
        this.event = event;
    }

    public String getSeq() {
        return seq;
    }

    public void setSeq(String seq) {
        this.seq = seq;
    }

    public int getRetryTimes() {
        return retryTimes;
    }

    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    public long getExecuteTime() {
        return executeTime;
    }

    public void setExecuteTime(long executeTime) {
        this.executeTime = executeTime;
    }
}
