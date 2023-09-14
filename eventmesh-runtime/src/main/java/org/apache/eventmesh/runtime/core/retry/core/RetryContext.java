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

package org.apache.eventmesh.runtime.core.retry.core;

import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.retry.Attempt;
import org.apache.eventmesh.runtime.core.retry.Retryable;
import org.apache.eventmesh.runtime.core.retry.strategies.StopStrategies;
import org.apache.eventmesh.runtime.core.retry.strategies.StopStrategy;
import org.apache.eventmesh.runtime.core.retry.strategies.StorageStrategy;
import org.apache.eventmesh.runtime.core.retry.strategies.WaitStrategies;
import org.apache.eventmesh.runtime.core.retry.strategies.WaitStrategy;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import io.cloudevents.CloudEvent;

public abstract class RetryContext implements Retryable {

    public CloudEvent event;

    public String seq;

    public int retryTimes;

    private final AtomicBoolean complete = new AtomicBoolean(Boolean.FALSE);

    private Attempt<Void> attempt;

    private StopStrategy stopStrategy = StopStrategies.stopAfterAttempt(EventMeshConstants.DEFAULT_PUSH_RETRY_TIMES);

    private WaitStrategy waitStrategy = WaitStrategies.fixedWait(10000, TimeUnit.MILLISECONDS);

    private StorageStrategy storageStrategy;

    public long executeTime = System.currentTimeMillis();

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

    public RetryContext withStopStrategy(StopStrategy stopStrategy) {
        this.stopStrategy = stopStrategy;
        return this;
    }

    public RetryContext withWaitStrategy(WaitStrategy waitStrategy) {
        this.waitStrategy = waitStrategy;
        return this;
    }

    @Override
    public StopStrategy getStopStrategy() {
        return stopStrategy;
    }

    @Override
    public StorageStrategy getStorageStrategy() {
        return storageStrategy;
    }

    @Override
    public WaitStrategy getWaitStrategy() {
        return waitStrategy;
    }

    @Override
    public Attempt<Void> getAttempt() {
        return attempt;
    }

    public void setAttempt(Attempt<Void> attempt) {
        this.attempt = attempt;
    }

    public void setExecuteTime(long timeMillis) {
        this.executeTime = timeMillis;
    }

    protected void complete() {
        complete.compareAndSet(Boolean.FALSE, Boolean.TRUE);
    }

}
