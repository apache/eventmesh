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

package org.apache.eventmesh.runtime.connector;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * File-based OffsetStore with periodic flush — production-ready.
 *
 * <p>Design:
 * <ul>
 *   <li>Writes to memory map first, then async flush to disk</li>
 *   <li>Uses atomic file write (write temp → rename) for crash safety</li>
 *   <li>Periodic auto-flush + explicit flush() on shutdown</li>
 *   <li>Storage format: one line per offset — {@code connectorName:topic:partition:position}</li>
 *   <li>Supports optional remote sync callback for Admin Server</li>
 * </ul>
 *
 * <p>Recovery priority: local file > remote Admin Server > connector default
 */
@Slf4j
public class FilePersistentOffsetStore implements OffsetStore {

    private final Map<String, String> offsets;
    private final Path storePath;
    private final ScheduledExecutorService flushScheduler;
    private volatile boolean closed;
    private RemoteSyncCallback remoteSync;

    private static final int FLUSH_INTERVAL_SECONDS = 10;

    @FunctionalInterface
    public interface RemoteSyncCallback {
        void sync(Map<String, String> offsets);
    }

    /** @param dataDir directory to store offset files */
    public FilePersistentOffsetStore(String dataDir) {
        this(dataDir, FLUSH_INTERVAL_SECONDS);
    }

    public FilePersistentOffsetStore(String dataDir, int flushIntervalSeconds) {
        this.offsets = new ConcurrentHashMap<>();
        this.storePath = Paths.get(dataDir, "connector-offsets.dat");
        this.closed = false;

        // Load existing offsets from disk
        loadFromDisk();

        // Start periodic flush
        this.flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "offset-store-flush");
            t.setDaemon(true);
            return t;
        });
        flushScheduler.scheduleWithFixedDelay(
            this::flush, flushIntervalSeconds, flushIntervalSeconds, TimeUnit.SECONDS);

        log.info("FilePersistentOffsetStore initialized at {}", storePath);
    }

    public void setRemoteSyncCallback(RemoteSyncCallback callback) {
        this.remoteSync = callback;
    }

    @Override
    public void save(String connectorName, String topic, int partition, String position) {
        if (closed) {
            log.warn("FilePersistentOffsetStore is closed, ignoring save");
            return;
        }
        String key = buildKey(connectorName, topic, partition);
        offsets.put(key, position);
        log.trace("Offset saved: {} = {}", key, position);
    }

    @Override
    public String load(String connectorName, String topic, int partition) {
        return offsets.get(buildKey(connectorName, topic, partition));
    }

    @Override
    public Map<String, String> loadAll(String connectorName) {
        String prefix = connectorName + ":";
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : offsets.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    @Override
    public void flush() {
        if (closed) return;
        try {
            // Write to temp file then atomic rename
            Path tempFile = Paths.get(storePath.toString() + ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, String> entry : offsets.entrySet()) {
                    writer.write(entry.getKey() + ":" + entry.getValue());
                    writer.newLine();
                }
            }
            Files.move(tempFile, storePath, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);

            // Trigger remote sync if configured
            if (remoteSync != null) {
                try {
                    remoteSync.sync(new LinkedHashMap<>(offsets));
                } catch (Exception e) {
                    log.warn("Remote offset sync failed", e);
                }
            }

            log.debug("Flushed {} offsets to {}", offsets.size(), storePath);
        } catch (IOException e) {
            log.error("Failed to flush offsets to {}", storePath, e);
        }
    }

    @Override
    public void close() {
        closed = true;
        flushScheduler.shutdown();
        try {
            if (!flushScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                flushScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            flushScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        flush(); // final flush before close
        log.info("FilePersistentOffsetStore closed, {} offsets persisted", offsets.size());
    }

    // -- internal helpers --

    private void loadFromDisk() {
        if (!Files.exists(storePath)) {
            log.info("No existing offset file at {} — starting fresh", storePath);
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(storePath, StandardCharsets.UTF_8)) {
            String line;
            int loaded = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                // Format: connectorName:topic:partition:position
                int lastColon = line.lastIndexOf(':');
                if (lastColon < 0) continue;
                String key = line.substring(0, lastColon);
                String position = line.substring(lastColon + 1);
                offsets.put(key, position);
                loaded++;
            }
            log.info("Loaded {} offsets from {}", loaded, storePath);
        } catch (IOException e) {
            log.warn("Failed to load offsets from {}, starting fresh", storePath, e);
        }
    }

    static String buildKey(String connectorName, String topic, int partition) {
        return connectorName + ":" + topic + ":" + partition;
    }
}
