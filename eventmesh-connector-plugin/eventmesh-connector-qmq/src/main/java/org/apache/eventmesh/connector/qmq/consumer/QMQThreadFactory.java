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

package org.apache.eventmesh.connector.qmq.consumer;

import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class QMQThreadFactory implements ThreadFactory {
    private static final AtomicInteger THREAD_POOL_NO = new AtomicInteger(1);

    private final AtomicInteger threadNo = new AtomicInteger(1);

    private String prefix;

    private boolean daemoThread;

    private ThreadGroup threadGroup;

    public QMQThreadFactory() {
        this("qmq-threadpool-" + THREAD_POOL_NO.getAndIncrement(), false);
    }

    public QMQThreadFactory(String prefix) {
        this(prefix, false);
    }


    public QMQThreadFactory(String prefix, boolean daemo) {
        this.prefix = StringUtils.isNotEmpty(prefix) ? prefix + "-thread-" : "";
        this.daemoThread = daemo;
        SecurityManager s = System.getSecurityManager();
        this.threadGroup = (s == null) ? Thread.currentThread().getThreadGroup() : s.getThreadGroup();
    }

    @Override
    public Thread newThread(Runnable runnable) {
        String name = this.prefix + this.threadNo.getAndIncrement();
        Thread ret = new Thread(this.threadGroup, runnable, name, 0);
        ret.setDaemon(this.daemoThread);
        return ret;
    }
}
