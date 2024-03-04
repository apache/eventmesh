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

package org.apache.eventmesh.runtime.lifecircle;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class EventMeshComponent extends AbstractEventMeshComponent {


    public void init() throws Exception {
        if (shouldSkip()) {
            return;
        }
        if (!inited.compareAndSet(false, true)) {
            return;
        }
        componentInit();
        for (EventMeshLifeCycle component : bindLifeCycleComponents) {
            component.init();
        }
        postBindInited();
    }

    protected abstract void componentInit() throws Exception;


    protected void postBindInited() throws Exception {
    }


    public void start() throws Exception {
        if (shouldSkip()) {
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        componentStart();
        for (EventMeshLifeCycle component : bindLifeCycleComponents) {
            component.start();
        }
        postBindStarted();
    }

    protected abstract void componentStart() throws Exception;


    protected void postBindStarted() throws Exception {
    }

    public void shutdown() throws Exception {
        if (shouldSkip()) {
            return;
        }
        inited.compareAndSet(true, false);
        started.compareAndSet(true, false);
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        componentStop();
        for (int index = bindLifeCycleComponents.size() - 1; index >= 0; index--) {
            bindLifeCycleComponents.get(index).shutdown();
        }
        postBindStopped();
    }

    protected abstract void componentStop() throws Exception;

    protected void postBindStopped() throws Exception {
    }

    protected boolean shouldSkip() {
        return false;
    }
}
