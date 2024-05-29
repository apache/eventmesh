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

package org.apache.eventmesh.openconnect.offsetmgmt.api.storage;

import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OffsetStorageWriterImpl implements OffsetStorageWriter, Closeable {

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final OffsetManagementService offsetManagementService;
    /**
     * Offset data in Connect format
     */
    private Map<RecordPartition, RecordOffset> data = new HashMap<>();
    private Map<RecordPartition, RecordOffset> toFlush = null;

    // Unique ID for each flush request to handle callbacks after timeouts
    private long currentFlushId = 0;

    public OffsetStorageWriterImpl(OffsetManagementService offsetManagementService) {
        this.offsetManagementService = offsetManagementService;
    }

    @Override
    public void writeOffset(RecordPartition partition, RecordOffset offset) {
//        RecordPartition extendRecordPartition;
        if (partition != null) {
//            extendRecordPartition = new ConnectorRecordPartition(connectorName, partition.getPartitionMap());
            data.put(partition, offset);
        }
    }

    /**
     * write offsets
     *
     * @param positions positions
     */
    @Override
    public void writeOffset(Map<RecordPartition, RecordOffset> positions) {
        for (Map.Entry<RecordPartition, RecordOffset> offset : positions.entrySet()) {
            writeOffset(offset.getKey(), offset.getValue());
        }
    }

    private boolean isFlushing() {
        return toFlush != null;
    }

    /**
     * begin flush offset
     *
     * @return
     */
    public synchronized boolean beginFlush() {
        if (isFlushing()) {
            log.warn("OffsetStorageWriter is already flushing");
            return false;
        }
        if (data.isEmpty()) {
            return false;
        }
        this.toFlush = this.data;
        this.data = new HashMap<>();
        return true;
    }

    /**
     * do flush offset
     */
    public Future<Void> doFlush() {
        final long flushId = currentFlushId;
        return sendOffsetFuture(flushId);
    }

    /**
     * Cancel a flush that has been initiated by {@link #beginFlush}.
     */
    public synchronized void cancelFlush() {
        if (isFlushing()) {
            // rollback to inited
            toFlush.putAll(data);
            data = toFlush;
            currentFlushId++;
            toFlush = null;
        }
    }

    private Future<Void> sendOffsetFuture(long flushId) {
        FutureTask<Void> futureTask = new FutureTask<>(new SendOffsetCallback(flushId));
        executorService.submit(futureTask);
        return futureTask;
    }

    /**
     * Closes this stream and releases any system resources associated
     * with it. If the stream is already closed then invoking this
     * method has no effect.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        executorService.shutdown();
    }

    /**
     * send offset callback
     */
    private class SendOffsetCallback implements Callable<Void> {

        long flushId;

        public SendOffsetCallback(long flushId) {
            this.flushId = flushId;
        }

        /**
         * Computes a result, or throws an exception if unable to do so.
         *
         * @return computed result
         * @throws Exception if unable to compute a result
         */
        @Override
        public Void call() {
            try {
                // has been canceled
                if (flushId != currentFlushId) {
                    return null;
                }
                offsetManagementService.putPosition(toFlush);
                log.debug("Submitting {} entries to backing store. The offsets are: {}", toFlush.size(), toFlush);
                offsetManagementService.persist();
                offsetManagementService.synchronize();
                // persist finished
                toFlush = null;
                currentFlushId++;
            } catch (Throwable throwable) {
                // rollback
                cancelFlush();
            }
            return null;
        }
    }
}
