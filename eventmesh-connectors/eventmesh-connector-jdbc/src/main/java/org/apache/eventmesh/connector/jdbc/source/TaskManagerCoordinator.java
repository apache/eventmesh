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

package org.apache.eventmesh.connector.jdbc.source;

import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * The TaskManagerCoordinator is responsible for coordinating multiple JDBC task managers and managing the processing of ConnectRecords. It provides
 * methods for registering task managers, initializing them, and starting their processing.
 */
@Slf4j
public class TaskManagerCoordinator {

    private static final int BATCH_MAX = 10;
    private static final int DEFAULT_QUEUE_SIZE = 1 << 13;

    private BlockingQueue<ConnectRecord> recordBlockingQueue = new LinkedBlockingQueue<>(DEFAULT_QUEUE_SIZE);
    private Map<String, JdbcTaskManager> taskManagerCache = new HashMap<>(8);

    /**
     * Constructs a new TaskManagerCoordinator.
     */
    public TaskManagerCoordinator() {
    }

    /**
     * Registers a JDBC task manager with the given name.
     *
     * @param name        The name of the task manager.
     * @param taskManager The JDBC task manager to register.
     */
    public void registerTaskManager(String name, JdbcTaskManager taskManager) {
        taskManagerCache.put(name, taskManager);
    }

    /**
     * Initializes all registered JDBC task managers.
     */
    public void init() {
        taskManagerCache.values().forEach(JdbcTaskManager::init);

        // Register a listener on each task manager to process incoming records and add them to the blocking queue.
        taskManagerCache.values().forEach(taskManager -> taskManager.registerListener(records -> {
            if (CollectionUtils.isEmpty(records)) {
                return;
            }
            records.forEach(record -> {
                try {
                    recordBlockingQueue.put(record);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }));
    }

    /**
     * Starts the processing of all registered JDBC task managers.
     */
    public void start() {
        taskManagerCache.values().forEach(JdbcTaskManager::start);
    }

    /**
     * Polls for a batch of ConnectRecords from the blocking queue.
     *
     * @return A list of ConnectRecords, up to the maximum batch size defined by BATCH_MAX.
     */
    public List<ConnectRecord> poll() {
        List<ConnectRecord> records = new ArrayList<>(BATCH_MAX);
        for (int index = 0; index < BATCH_MAX; ++index) {
            try {
                ConnectRecord record = recordBlockingQueue.poll(3, TimeUnit.SECONDS);
                if (Objects.isNull(record)) {
                    break;
                }
                records.add(record);
            } catch (InterruptedException e) {
                break;
            }
        }
        return records;
    }
}
