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

package org.apache.eventmesh.common.file;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WatchFileManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(WatchFileManager.class);

    private static final AtomicBoolean CLOSED = new AtomicBoolean(false);

    private static final Map<String, WatchFileTask> WATCH_FILE_TASK_MAP = new HashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.warn("[WatchFileManager] WatchFileManager closed");
            shutdown();
        }));
    }

    public static void registerFileChangeListener(String directoryPath,
                                                  FileChangeListener listener) {
        WatchFileTask task = WATCH_FILE_TASK_MAP.get(directoryPath);
        if (task == null) {
            task = new WatchFileTask(directoryPath);
            task.start();
            WATCH_FILE_TASK_MAP.put(directoryPath, task);
        }
        task.addFileChangeListener(listener);
    }

    public static void deregisterFileChangeListener(String directoryPath) {
        WatchFileTask task = WATCH_FILE_TASK_MAP.get(directoryPath);
        if (task != null) {
            task.shutdown();
            WATCH_FILE_TASK_MAP.remove(directoryPath);
        }
    }

    private static void shutdown() {
        if (!CLOSED.compareAndSet(false, true)) {
            return;
        }
        LOGGER.warn("[WatchFileManager] start close");
        for (Map.Entry<String, WatchFileTask> entry : WATCH_FILE_TASK_MAP.entrySet()) {
            LOGGER.warn("[WatchFileManager] start to shutdown : " + entry.getKey());
            try {
                entry.getValue().shutdown();
            } catch (Throwable ex) {
                LOGGER.error("[WatchFileManager] shutdown has error : ", ex);
            }
        }
        WATCH_FILE_TASK_MAP.clear();
        LOGGER.warn("[WatchFileManager] already closed");
    }
}
