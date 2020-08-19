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

package com.webank.emesher.threads;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SharedScheduledExecutorService extends DelegatedScheduledExecutorService {
    private final String name;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private CopyOnWriteArrayList<ScheduledFuture<?>> scheduledFutures = new CopyOnWriteArrayList();
    private boolean _shutdown = false;
    public SharedScheduledExecutorService(ScheduledExecutorService executor, String name) {
        super(executor);
        this.name = name;
        this.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                int size = scheduledFutures.size();
                for (ScheduledFuture<?> future : scheduledFutures) {
                    if (future.isDone() || future.isCancelled()) scheduledFutures.remove(future);
                }
                logger.debug("clean up SharedScheduledExecutorService.scheduledFutures. this={} [{}->{}]", this, size, scheduledFutures.size());
            }
        }, 60 * 30, 60 * 30, TimeUnit.SECONDS);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        ScheduledFuture<?> sf = super.schedule(command, delay, unit);
        scheduledFutures.add(sf);
        return sf;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        ScheduledFuture<V> sf = super.schedule(callable, delay, unit);
        scheduledFutures.add(sf);
        return sf;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        ScheduledFuture<?> sf = super.scheduleAtFixedRate(command, initialDelay, period, unit);
        scheduledFutures.add(sf);
        return sf;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        ScheduledFuture<?> sf = super.scheduleWithFixedDelay(command, initialDelay, delay, unit);
        scheduledFutures.add(sf);
        return sf;
    }

    @Override
    public void shutdown() {
        for (ScheduledFuture sf : scheduledFutures) {
            sf.cancel(false);
        }
        _shutdown = true;
        scheduledFutures.clear();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Runnable> shutdownNow() {
        this.shutdown();
        return Collections.EMPTY_LIST;
    }

    @Override
    public boolean isShutdown() {
        return _shutdown;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public boolean isTerminated() {
        if (isShutdown()) {
            Thread.yield();
            Thread.yield();
            Thread.yield();
            Thread.yield();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "SharedExecutorService{" +
                "name='" + name + '\'' +
                "} " + super.toString();
    }
}
