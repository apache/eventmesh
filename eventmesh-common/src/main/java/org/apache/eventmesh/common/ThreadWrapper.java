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

package org.apache.eventmesh.common;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ThreadWrapper implements Runnable {

    private final AtomicBoolean started = new AtomicBoolean(false);
    protected Thread thread;
    protected final ResetCountDownLatch waiter = new ResetCountDownLatch(1);
    protected volatile AtomicBoolean hasWakeup = new AtomicBoolean(false);
    protected boolean isDaemon = false;
    protected volatile boolean isRunning = false;

    public ThreadWrapper() {

    }

    public abstract String getThreadName();

    public void start() {

        if (!started.compareAndSet(false, true)) {
            log.warn("Start thread:{} fail", getThreadName());
            return;
        }
        this.thread = new Thread(this, getThreadName());
        this.thread.setDaemon(isDaemon);
        this.thread.start();
        this.isRunning = true;
        log.info("Start thread:{} success", getThreadName());
    }

    public void await() {
        if (hasWakeup.compareAndSet(true, false)) {
            return;
        }
        // reset count
        waiter.reset();
        try {
            waiter.await();
        } catch (InterruptedException e) {
            log.error("Thread[{}] Interrupted", getThreadName(), e);
        } finally {
            hasWakeup.set(false);
        }
    }

    public void await(long timeout) {
        await(timeout, TimeUnit.MILLISECONDS);
    }

    public void await(long timeout, TimeUnit timeUnit) {
        if (hasWakeup.compareAndSet(true, false)) {
            return;
        }
        // reset count
        waiter.reset();
        try {
            waiter.await(timeout, timeUnit == null ? TimeUnit.MILLISECONDS : timeUnit);
        } catch (InterruptedException e) {
            log.error("Thread[{}] Interrupted", getThreadName(), e);
        } finally {
            hasWakeup.set(false);
        }
    }

    public void wakeup() {
        if (hasWakeup.compareAndSet(false, true)) {
            waiter.countDown();
        }
    }

    public void shutdownImmediately() {
        shutdown(true);
    }

    public void shutdown() {
        shutdown(false);
    }

    private void shutdown(final boolean interruptThread) {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        this.isRunning = false;
        // wakeup the thread to run
        wakeup();

        try {
            if (interruptThread) {
                this.thread.interrupt();
            }
            if (!this.isDaemon) {
                // wait main thread to wait this thread finish
                this.thread.join(TimeUnit.SECONDS.toMillis(60));
            }
        } catch (InterruptedException e) {
            log.error("Thread[{}] Interrupted", getThreadName(), e);
        }
    }

    public void setDaemon(boolean daemon) {
        isDaemon = daemon;
    }

    public boolean isStated() {
        return this.started.get();
    }
}
