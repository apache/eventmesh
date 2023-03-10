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

package org.apache.eventmesh.storage.standalone.broker.task;

import org.apache.eventmesh.common.utils.ThreadUtils;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HistoryMessageClearTask implements Runnable {

    private HistoryMessageClear historyMessageClear;

    public HistoryMessageClearTask(HistoryMessageClear historyMessageClear) {
        this.historyMessageClear = historyMessageClear;
    }

    @Override
    public void run() {
        while (true) {
            historyMessageClear.clearMessages();
            try {
                ThreadUtils.sleepWithThrowException(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.error("Thread is interrupted, thread name: {}", Thread.currentThread().getName(), e);
                Thread.currentThread().interrupt();
            }
        }
    }
}
