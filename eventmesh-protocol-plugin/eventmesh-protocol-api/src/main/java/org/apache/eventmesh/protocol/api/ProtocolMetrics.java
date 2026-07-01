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

package org.apache.eventmesh.protocol.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

/**
 * Protocol performance metrics collector.
 *
 * @since 1.3.0
 */
@Slf4j
public class ProtocolMetrics {

    private static final ProtocolMetrics INSTANCE = new ProtocolMetrics();
    
    private final Map<String, ProtocolStats> protocolStats = new ConcurrentHashMap<>();
    
    private ProtocolMetrics() {}
    
    public static ProtocolMetrics getInstance() {
        return INSTANCE;
    }

    /**
     * Record successful protocol operation.
     *
     * @param protocolType protocol type
     * @param operationType operation type (toCloudEvent, fromCloudEvent, batch)
     * @param duration operation duration in milliseconds
     */
    public void recordSuccess(String protocolType, String operationType, long duration) {
        getOrCreateStats(protocolType).recordSuccess(operationType, duration);
    }

    /**
     * Record failed protocol operation.
     *
     * @param protocolType protocol type
     * @param operationType operation type
     * @param error error message
     */
    public void recordFailure(String protocolType, String operationType, String error) {
        getOrCreateStats(protocolType).recordFailure(operationType, error);
    }

    /**
     * Get protocol statistics.
     *
     * @param protocolType protocol type
     * @return protocol stats or null if not found
     */
    public ProtocolStats getStats(String protocolType) {
        return protocolStats.get(protocolType);
    }

    /**
     * Get all protocol statistics.
     *
     * @return map of protocol stats
     */
    public Map<String, ProtocolStats> getAllStats() {
        return new ConcurrentHashMap<>(protocolStats);
    }

    /**
     * Reset statistics for a protocol.
     *
     * @param protocolType protocol type
     */
    public void resetStats(String protocolType) {
        ProtocolStats stats = protocolStats.get(protocolType);
        if (stats != null) {
            stats.reset();
        }
    }

    /**
     * Reset all statistics.
     */
    public void resetAllStats() {
        protocolStats.values().forEach(ProtocolStats::reset);
    }

    private ProtocolStats getOrCreateStats(String protocolType) {
        return protocolStats.computeIfAbsent(protocolType, k -> new ProtocolStats());
    }

    /**
     * Protocol statistics holder.
     */
    public static class ProtocolStats {
        
        private final Map<String, OperationStats> operationStats = new ConcurrentHashMap<>();
        private final AtomicLong totalOperations = new AtomicLong(0);
        private final AtomicLong totalErrors = new AtomicLong(0);
        private volatile long lastOperationTime = System.currentTimeMillis();

        /**
         * Record successful operation.
         */
        void recordSuccess(String operationType, long duration) {
            getOrCreateOperationStats(operationType).recordSuccess(duration);
            totalOperations.incrementAndGet();
            lastOperationTime = System.currentTimeMillis();
        }

        /**
         * Record failed operation.
         */
        void recordFailure(String operationType, String error) {
            getOrCreateOperationStats(operationType).recordFailure(error);
            totalOperations.incrementAndGet();
            totalErrors.incrementAndGet();
            lastOperationTime = System.currentTimeMillis();
        }

        /**
         * Get operation statistics.
         */
        public OperationStats getOperationStats(String operationType) {
            return operationStats.get(operationType);
        }

        /**
         * Get all operation statistics.
         */
        public Map<String, OperationStats> getAllOperationStats() {
            return new ConcurrentHashMap<>(operationStats);
        }

        /**
         * Get total operations count.
         */
        public long getTotalOperations() {
            return totalOperations.get();
        }

        /**
         * Get total errors count.
         */
        public long getTotalErrors() {
            return totalErrors.get();
        }

        /**
         * Get success rate as percentage.
         */
        public double getSuccessRate() {
            long total = totalOperations.get();
            if (total == 0) {
                return 0.0;
            }
            return (double) (total - totalErrors.get()) / total * 100.0;
        }

        /**
         * Get last operation timestamp.
         */
        public long getLastOperationTime() {
            return lastOperationTime;
        }

        /**
         * Reset all statistics.
         */
        void reset() {
            operationStats.clear();
            totalOperations.set(0);
            totalErrors.set(0);
            lastOperationTime = System.currentTimeMillis();
        }

        private OperationStats getOrCreateOperationStats(String operationType) {
            return operationStats.computeIfAbsent(operationType, k -> new OperationStats());
        }

        @Override
        public String toString() {
            return String.format("ProtocolStats{operations=%d, errors=%d, successRate=%.2f%%, lastOp=%d}",
                getTotalOperations(), getTotalErrors(), getSuccessRate(), getLastOperationTime());
        }
    }

    /**
     * Operation statistics holder.
     */
    public static class OperationStats {
        
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong failureCount = new AtomicLong(0);
        private final AtomicLong totalDuration = new AtomicLong(0);
        private volatile long minDuration = Long.MAX_VALUE;
        private volatile long maxDuration = 0;
        private volatile String lastError;

        /**
         * Record successful operation.
         */
        void recordSuccess(long duration) {
            successCount.incrementAndGet();
            totalDuration.addAndGet(duration);
            
            // Update min/max duration
            if (duration < minDuration) {
                minDuration = duration;
            }
            if (duration > maxDuration) {
                maxDuration = duration;
            }
        }

        /**
         * Record failed operation.
         */
        void recordFailure(String error) {
            failureCount.incrementAndGet();
            lastError = error;
        }

        /**
         * Get success count.
         */
        public long getSuccessCount() {
            return successCount.get();
        }

        /**
         * Get failure count.
         */
        public long getFailureCount() {
            return failureCount.get();
        }

        /**
         * Get total operations count.
         */
        public long getTotalCount() {
            return successCount.get() + failureCount.get();
        }

        /**
         * Get average duration in milliseconds.
         */
        public double getAverageDuration() {
            long count = successCount.get();
            if (count == 0) {
                return 0.0;
            }
            return (double) totalDuration.get() / count;
        }

        /**
         * Get minimum duration.
         */
        public long getMinDuration() {
            return minDuration == Long.MAX_VALUE ? 0 : minDuration;
        }

        /**
         * Get maximum duration.
         */
        public long getMaxDuration() {
            return maxDuration;
        }

        /**
         * Get success rate as percentage.
         */
        public double getSuccessRate() {
            long total = getTotalCount();
            if (total == 0) {
                return 0.0;
            }
            return (double) successCount.get() / total * 100.0;
        }

        /**
         * Get last error message.
         */
        public String getLastError() {
            return lastError;
        }

        @Override
        public String toString() {
            return String.format("OperationStats{success=%d, failure=%d, avgDuration=%.2fms, successRate=%.2f%%}",
                getSuccessCount(), getFailureCount(), getAverageDuration(), getSuccessRate());
        }
    }
}