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

package cn.webank.emesher.threads;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class SharedExecutorService extends DelegatedExecutorService {
    private final String name;
    boolean _shutdown = false;

    public SharedExecutorService(ExecutorService executor, String name) {
        super(executor);
        this.name = name;
    }

    @Override
    public void shutdown() {
        _shutdown = true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Runnable> shutdownNow() {
        _shutdown = true;
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
