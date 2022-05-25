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

import org.apache.eventmesh.common.exception.EventMeshException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.util.List;

public class WatchDirectoryTask extends Thread {

    private static final Logger LOGGER = LoggerFactory.getLogger(WatchDirectoryTask.class);

    private static final FileSystem FILE_SYSTEM = FileSystems.getDefault();

    private final WatchService watchService;

    private volatile boolean watch = true;

    public WatchDirectoryTask(String directoryPath) throws EventMeshException {
        final Path path = Paths.get(directoryPath);
        if (!path.toFile().isDirectory()) {
            throw new IllegalArgumentException("must be a file directory : " + directoryPath);
        }

        try {
            WatchService service = FILE_SYSTEM.newWatchService();
            path.register(service, StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
            this.watchService = service;
        } catch (Throwable ex) {
            throw new EventMeshException("WatchService registry fail", ex);
        }
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
                    precessWatchEvent(event.kind());
                }
            } catch (InterruptedException ex) {
                boolean interrupted = Thread.interrupted();
                if (interrupted) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("file watch is interrupted");
                    }
                }
            } catch (Throwable ex) {
                LOGGER.error("an exception occurred during file listening : ", ex);
            }
        }
    }

    private void precessWatchEvent(Object context) {
        try {

        } catch (Throwable ex) {
            LOGGER.error("file change event callback error : ", ex);
        }
    }

    private void shutdown() {
        watch = false;
    }
}
