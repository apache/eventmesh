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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WatchFileManager {

    private static final AtomicBoolean CLOSED = new AtomicBoolean(false);

    private static final Map<String, WatchFileTask> WATCH_FILE_TASK_MAP = new ConcurrentHashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.warn("[WatchFileManager] WatchFileManager closed");
            shutdown();
        }));
    }

    public static void registerFileChangeListener(String directoryPath, FileChangeListener listener) {
        WATCH_FILE_TASK_MAP.compute(directoryPath, (path, task) -> {
            if (task != null) {
                synchronized (task) {
                    if (task.isWatching()) {
                        task.addFileChangeListener(listener);
                        return task;
                    }
                }
            }

            WatchFileTask watchFileTask = new WatchFileTask(path);
            watchFileTask.addFileChangeListener(listener);
            watchFileTask.start();
            return watchFileTask;
        });
    }

    public static void deregisterFileChangeListener(String directoryPath, FileChangeListener listener) {
        WATCH_FILE_TASK_MAP.computeIfPresent(directoryPath, (path, task) -> {
            task.removeFileChangeListener(listener);
            if (task.hasFileChangeListener()) {
                return task;
            }
            task.shutdown();
            return null;
        });
    }

    public static void deregisterFileChangeListener(String directoryPath) {
        WATCH_FILE_TASK_MAP.computeIfPresent(directoryPath, (path, task) -> {
            task.shutdown();
            return null;
        });
    }

    static void removeWatchFileTask(String directoryPath, WatchFileTask task) {
        WATCH_FILE_TASK_MAP.remove(directoryPath, task);
    }

    private static void shutdown() {
        if (!CLOSED.compareAndSet(false, true)) {
            return;
        }

        log.info("[WatchFileManager] start close");

        for (Map.Entry<String, WatchFileTask> entry : WATCH_FILE_TASK_MAP.entrySet()) {
            log.info("[WatchFileManager] start to shutdown : {}", entry.getKey());

            try {
                entry.getValue().shutdown();
            } catch (Exception ex) {
                log.error("[WatchFileManager] shutdown has error : ", ex);
            }
        }
        WATCH_FILE_TASK_MAP.clear();
        log.warn("[WatchFileManager] already closed");
    }
}
