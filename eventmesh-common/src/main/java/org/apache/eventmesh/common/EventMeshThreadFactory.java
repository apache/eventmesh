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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import lombok.Getter;

public class EventMeshThreadFactory implements ThreadFactory {

    @Getter
    private final String threadNamePrefix;
    private final AtomicInteger threadIndex;
    private final boolean daemon;
    private final Integer priority;

    public EventMeshThreadFactory(final String threadNamePrefix, final AtomicInteger threadIndex, final boolean daemon,
        final Integer priority) {
        this.threadNamePrefix = threadNamePrefix;
        this.threadIndex = threadIndex;
        this.daemon = daemon;
        this.priority = priority;
    }

    public EventMeshThreadFactory(final String threadNamePrefix, final AtomicInteger threadIndex,
        final boolean daemon) {
        this(threadNamePrefix, threadIndex, daemon, null);
    }

    public EventMeshThreadFactory(final String threadNamePrefix, final boolean daemon, final Integer priority) {
        this(threadNamePrefix, new AtomicInteger(0), daemon, priority);
    }

    public EventMeshThreadFactory(final String threadNamePrefix, final boolean daemon) {
        this(threadNamePrefix, new AtomicInteger(0), daemon);
    }

    public EventMeshThreadFactory(final String threadNamePrefix) {
        this(threadNamePrefix, new AtomicInteger(0), false);
    }

    /**
     * Constructs a new {@code Thread}.  Implementations may also initialize priority, name, daemon status, {@code ThreadGroup}, etc.
     *
     * @param runnable a runnable to be executed by new thread instance
     * @return constructed thread, or {@code null} if the request to create a thread is rejected
     */
    @Override
    public Thread newThread(@Nonnull final Runnable runnable) {

        StringBuilder threadName = new StringBuilder(threadNamePrefix);
        if (threadIndex != null) {
            threadName.append("-").append(threadIndex.incrementAndGet());
        }
        Thread thread = new Thread(runnable, threadName.toString());
        thread.setDaemon(daemon);
        if (priority != null) {
            thread.setPriority(priority);
        }

        return thread;
    }
}
