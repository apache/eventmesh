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

import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.Future;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class SharedEventExecutorGroup extends DefaultEventExecutorGroup {
    private boolean _stutdown = false;

    /**
     * Create a new instance.
     *
     * @param nThreads      the number of threads that will be used by this instance.
     * @param threadFactory the ThreadFactory to use, or {@code null} if the default should be
     *                      used.
     */
    public SharedEventExecutorGroup(int nThreads, ThreadFactory threadFactory) {
        super(nThreads, threadFactory);
    }

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        _stutdown = true;
        return terminationFuture();
    }

    @Override
    public Future<?> terminationFuture() {
        return null;
    }

    @Deprecated
    @Override
    public void shutdown() {
        _stutdown = true;
    }

    @Override
    public boolean isShuttingDown() {
        return _stutdown;

    }

    @Override
    public boolean isShutdown() {
        return _stutdown;
    }

    @Override
    public boolean isTerminated() {
        return _stutdown;
    }

    public void shutdownInternel() {
        super.shutdownGracefully(3,  10, TimeUnit.SECONDS);
    }

    @Override
    public Future<?> shutdownGracefully() {
        _stutdown = true;
        return terminationFuture();
    }

    /**
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} or {@link
     * #shutdownGracefully()} instead.
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<Runnable> shutdownNow() {
        _stutdown = true;
        return Collections.EMPTY_LIST;
    }
}
