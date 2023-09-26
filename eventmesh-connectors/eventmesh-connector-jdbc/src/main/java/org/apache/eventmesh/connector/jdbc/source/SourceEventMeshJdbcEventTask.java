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

import org.apache.eventmesh.connector.jdbc.event.Event;
import org.apache.eventmesh.connector.jdbc.event.EventHandler;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class SourceEventMeshJdbcEventTask extends AbstractEventMeshJdbcEventTask {


    private final String taskName;

    private EventHandler eventHandler;

    public SourceEventMeshJdbcEventTask(String taskName) {
        this.taskName = taskName;

    }

    @Override
    public String getThreadName() {
        return taskName;
    }

    /**
     * When an object implementing interface <code>Runnable</code> is used to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        while (isRunning) {
            try {
                Event event = eventBlockingQueue.poll(5, TimeUnit.SECONDS);
                if (Objects.isNull(event)) {
                    continue;
                }
                eventHandler.handle(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }


    /**
     * Registers a snapshot event handler to be executed when snapshot events occur.
     *
     * @param handler The SnapshotEventHandler to be registered.
     */
    @Override
    public void registerEventHandler(EventHandler handler) {
        this.eventHandler = handler;
    }
}
