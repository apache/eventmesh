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

import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractComponent implements ComponentLifeCycle {
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    @Override
    public void start() throws Exception {
        if (!started.compareAndSet(false, true)) {
            log.info("component [{}] has started", this.getClass());
            return;
        }
        log.info("component [{}] will start", this.getClass());
        run();
        log.info("component [{}] started successfully", this.getClass());
    }

    @Override
    public void stop() throws Exception {
        if (!stopped.compareAndSet(false, true)) {
            log.info("component [{}] has stopped", this.getClass());
            return;
        }
        log.info("component [{}] will stop", this.getClass());
        shutdown();
        log.info("component [{}] stopped successfully", this.getClass());
    }

    protected abstract void run() throws Exception;

    protected abstract void shutdown() throws Exception;
}
