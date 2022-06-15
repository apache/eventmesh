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

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WatchFileTask extends Thread {

    private static final Logger LOGGER = LoggerFactory.getLogger(WatchFileTask.class);

    private static final FileSystem FILE_SYSTEM = FileSystems.getDefault();

    private final WatchService watchService;

    private final List<FileChangeListener> fileChangeListeners = new ArrayList<>();

    private volatile boolean watch = true;

    private final String directoryPath;

    public WatchFileTask(String directoryPath) {
        this.directoryPath = directoryPath;
        final Path path = Paths.get(directoryPath);
        if (!path.toFile().exists()) {
            throw new IllegalArgumentException("file directory not exist: " + directoryPath);
        }
        if (!path.toFile().isDirectory()) {
            throw new IllegalArgumentException("must be a file directory : " + directoryPath);
        }

        try {
            WatchService service = FILE_SYSTEM.newWatchService();
            path.register(service, StandardWatchEventKinds.OVERFLOW, StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
            this.watchService = service;
        } catch (Throwable ex) {
            throw new UnsupportedOperationException("WatchService registry fail", ex);
        }
    }

    public void addFileChangeListener(FileChangeListener fileChangeListener) {
        if (fileChangeListener != null) {
            fileChangeListeners.add(fileChangeListener);
        }
    }

    public void shutdown() {
        watch = false;
    }

    @Override
    public void run() {
        while (watch) {
            try {
                WatchKey watchKey = watchService.take();
                List<WatchEvent<?>> events = watchKey.pollEvents();
                watchKey.reset();

                if (events.isEmpty()) {
                    continue;
                }

                for (WatchEvent<?> event : events) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind.equals(StandardWatchEventKinds.OVERFLOW)) {
                        LOGGER.warn("[WatchFileTask] file overflow: " + event.context());
                        continue;
                    }
                    precessWatchEvent(event);
                }
            } catch (InterruptedException ex) {
                boolean interrupted = Thread.interrupted();
                if (interrupted) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[WatchFileTask] file watch is interrupted");
                    }
                }
            } catch (Throwable ex) {
                LOGGER.error("[WatchFileTask] an exception occurred during file listening : ", ex);
            }
        }
    }

    private void precessWatchEvent(WatchEvent<?> event) {
        try {
            for (FileChangeListener fileChangeListener : fileChangeListeners) {
                FileChangeContext context = new FileChangeContext();
                context.setDirectoryPath(directoryPath);
                context.setFileName(event.context().toString());
                context.setWatchEvent(event);
                if (fileChangeListener.support(context)) {
                    fileChangeListener.onChanged(context);
                }
            }
        } catch (Throwable ex) {
            LOGGER.error("[WatchFileTask] file change event callback error : ", ex);
        }
    }
}
