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

package org.apache.eventmesh.webhook.receive.storage;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookOperationConstant;
import org.apache.eventmesh.webhook.api.common.SharedLatchHolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebhookFileListener {

    private final transient Set<String> pathSet = new LinkedHashSet<>(); // monitored subdirectory
    private final transient Map<WatchKey, String> watchKeyPathMap = new ConcurrentHashMap<>(); // WatchKey's path
    private transient String filePath;
    private final transient Map<String, WebHookConfig> cacheWebHookConfig;

    public WebhookFileListener(final String filePath, final Map<String, WebHookConfig> cacheWebHookConfig) {
        this.filePath = WebHookOperationConstant.getFilePath(filePath);
        this.cacheWebHookConfig = cacheWebHookConfig;
        filePatternInit();
    }

    /**
     * Read the directory and register the listener
     */
    private void filePatternInit() {
        final File webHookFileDir = new File(filePath);
        if (!webHookFileDir.exists()) {
            webHookFileDir.mkdirs();
        } else {
            readFiles(webHookFileDir);
        }

        fileWatchRegister();
    }

    /**
     * Recursively traverse the folder
     *
     * @param file file
     */
    private void readFiles(final File file) {
        final File[] fs = file.listFiles();
        for (final File f : Objects.requireNonNull(fs)) {
            if (f.isDirectory()) {
                readFiles(f);
            } else if (f.isFile()) {
                cacheInit(f);
            }
        }
    }

    /**
     * Read the file and cache it in local map for manufacturers webhook payload delivery
     * <p>
     * A CountDownLatch is used to ensure that this method should be invoked after the {@code webhookConfigFile} is written completely
     * by {@code org.apache.eventmesh.webhook.admin.FileWebHookConfigOperation#writeToFile} when multiple modify events are triggered.
     *
     * @param webhookConfigFile webhookConfigFile
     */
    private void cacheInit(final File webhookConfigFile) {
        final StringBuilder fileContent = new StringBuilder();
        try (BufferedReader br = Files.newBufferedReader(Paths.get(webhookConfigFile.getAbsolutePath()), StandardCharsets.UTF_8)) {
            while (br.ready()) {
                fileContent.append(br.readLine());
            }
        } catch (IOException e) {
            log.error("cacheInit buffer read failed", e);
        }
        final WebHookConfig webHookConfig = JsonUtils.parseObject(fileContent.toString(), WebHookConfig.class);
        cacheWebHookConfig.put(webhookConfigFile.getName(), webHookConfig);
    }

    public void deleteConfig(final File webhookConfigFile) {
        cacheWebHookConfig.remove(webhookConfigFile.getName());
    }

    /**
     * Register listeners with folders
     */
    private void fileWatchRegister() {
        final ExecutorService cachedThreadPool = Executors.newFixedThreadPool(1);
        cachedThreadPool.execute(() -> {
            final File root = new File(filePath);
            loopDirInsertToSet(root, pathSet);

            WatchService service = null;
            try {
                service = FileSystems.getDefault().newWatchService();
            } catch (Exception e) {
                log.error("getWatchService failed.", e);
            }

            for (final String path : pathSet) {
                WatchKey key = null;
                try {
                    key = Paths.get(path).register(service, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                } catch (IOException e) {
                    log.error("registerWatchKey failed", e);
                }
                watchKeyPathMap.put(key, path);
            }

            while (true) {
                WatchKey key = null;
                try {
                    assert service != null;
                    // The code will block here until a file system event occurs
                    key = service.take();
                } catch (InterruptedException e) {
                    log.error("Interrupted", e);
                }

                assert key != null;
                // A newly created config file will be captured for two events, ENTRY_CREATE and ENTRY_MODIFY
                for (final WatchEvent<?> event : key.pollEvents()) {
                    final String flashPath = watchKeyPathMap.get(key);
                    // manufacturer change
                    final String path = flashPath.concat(WebHookOperationConstant.FILE_SEPARATOR).concat(event.context().toString());
                    final File file = new File(path);
                    if (file.isDirectory() && (ENTRY_CREATE == event.kind() || ENTRY_MODIFY == event.kind())) {
                        // If it is a folder, re-register the listener
                        try {
                            key = Paths.get(path).register(service, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                            watchKeyPathMap.put(key, path);
                        } catch (IOException e) {
                            log.error("registerWatchKey failed", e);
                        }
                    } else if (file.isFile() && ENTRY_MODIFY == event.kind()) {
                        // If it is a file, cache it only when it is modified to wait for complete file writes
                        try {
                            // Wait for the notification of file write completion before initializing the cache
                            SharedLatchHolder.latch.await();
                            synchronized (SharedLatchHolder.lock) {
                                cacheInit(file);
                            }
                        } catch (Exception e) {
                            log.error("cacheInit failed", e);
                        }
                    } else if (ENTRY_DELETE == event.kind()) {
                        if (file.isDirectory()) {
                            watchKeyPathMap.remove(key);
                        } else {
                            deleteConfig(file);
                        }
                    }
                }
                // Reset the WatchKey to receive subsequent file system events
                if (!key.reset()) {
                    break;
                }
            }
        });
    }

    /**
     * Recursive folder, adding folder's path to set
     *
     * @param parent  parent folder
     * @param pathSet folder's path set
     */
    private void loopDirInsertToSet(final File parent, final Set<String> pathSet) {
        if (!parent.isDirectory()) {
            return;
        }
        pathSet.add(parent.getPath());
        for (final File child : Objects.requireNonNull(parent.listFiles())) {
            loopDirInsertToSet(child, pathSet);
        }
    }
}
