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

package org.apache.eventmesh.runtime.core.protocol.a2a.pubsub;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import lombok.extern.slf4j.Slf4j;

/**
 * Metrics collector for A2A publish/subscribe operations
 */
@Slf4j
public class TaskMetricsCollector {
    
    // Global metrics
    private final AtomicLong totalTasksPublished = new AtomicLong(0);
    private final AtomicLong totalTasksCompleted = new AtomicLong(0);
    private final AtomicLong totalTasksFailed = new AtomicLong(0);
    private final AtomicLong totalTasksTimeout = new AtomicLong(0);
    
    // Per task type metrics
    private final Map<String, TaskTypeMetrics> taskTypeMetrics = new ConcurrentHashMap<>();
    
    // Processing time statistics
    private final Map<String, ProcessingTimeStats> processingTimeStats = new ConcurrentHashMap<>();
    
    /**
     * Record task published
     */
    public void recordTaskPublished(String taskType) {
        totalTasksPublished.incrementAndGet();
        getTaskTypeMetrics(taskType).published.increment();
    }
    
    /**
     * Record task publish failed
     */
    public void recordTaskPublishFailed(String taskType) {
        getTaskTypeMetrics(taskType).publishFailed.increment();
    }
    
    /**
     * Record task started processing
     */
    public void recordTaskStarted(String taskType) {
        getTaskTypeMetrics(taskType).started.increment();
    }
    
    /**
     * Record task completed
     */
    public void recordTaskCompleted(String taskType, long processingTimeMs) {
        totalTasksCompleted.incrementAndGet();
        TaskTypeMetrics metrics = getTaskTypeMetrics(taskType);
        metrics.completed.increment();
        
        // Update processing time stats
        updateProcessingTimeStats(taskType, processingTimeMs);
    }
    
    /**
     * Record task failed
     */
    public void recordTaskFailed(String taskType) {
        totalTasksFailed.incrementAndGet();
        getTaskTypeMetrics(taskType).failed.increment();
    }
    
    /**
     * Record task timeout
     */
    public void recordTaskTimeout(String taskType) {
        totalTasksTimeout.incrementAndGet();
        getTaskTypeMetrics(taskType).timeout.increment();
    }
    
    /**
     * Record task result received
     */
    public void recordTaskResult(A2ATaskResultMessage resultMessage) {
        String taskType = resultMessage.getTaskType();
        
        switch (resultMessage.getStatus()) {
            case COMPLETED:
                // Already recorded in recordTaskCompleted
                break;
            case FAILED:
                // Already recorded in recordTaskFailed  
                break;
            case TIMEOUT:
                recordTaskTimeout(taskType);
                break;
            default:
                log.warn("Unknown task status: {}", resultMessage.getStatus());
        }
        
        // Update processing time stats from result message
        if (resultMessage.getProcessingTime() > 0) {
            updateProcessingTimeStats(taskType, resultMessage.getProcessingTime());
        }
    }
    
    /**
     * Get metrics for a specific task type
     */
    private TaskTypeMetrics getTaskTypeMetrics(String taskType) {
        return taskTypeMetrics.computeIfAbsent(taskType, k -> new TaskTypeMetrics());
    }
    
    /**
     * Update processing time statistics
     */
    private void updateProcessingTimeStats(String taskType, long processingTimeMs) {
        ProcessingTimeStats stats = processingTimeStats.computeIfAbsent(
            taskType, k -> new ProcessingTimeStats());
        
        stats.totalTime.add(processingTimeMs);
        stats.count.increment();
        
        // Update min/max
        stats.minTime.updateAndGet(current -> current == 0 ? processingTimeMs : Math.min(current, processingTimeMs));
        stats.maxTime.updateAndGet(current -> Math.max(current, processingTimeMs));
    }
    
    /**
     * Get overall metrics
     */
    public A2AMetrics getOverallMetrics() {
        return A2AMetrics.builder()
            .totalTasksPublished(totalTasksPublished.get())
            .totalTasksCompleted(totalTasksCompleted.get())
            .totalTasksFailed(totalTasksFailed.get())
            .totalTasksTimeout(totalTasksTimeout.get())
            .successRate(calculateSuccessRate())
            .build();
    }
    
    /**
     * Get metrics for specific task type
     */
    public TaskTypeMetricsSnapshot getTaskTypeMetrics(String taskType) {
        TaskTypeMetrics metrics = taskTypeMetrics.get(taskType);
        ProcessingTimeStats timeStats = processingTimeStats.get(taskType);
        
        if (metrics == null) {
            return null;
        }
        
        TaskTypeMetricsSnapshot.TaskTypeMetricsSnapshotBuilder builder = TaskTypeMetricsSnapshot.builder()
            .taskType(taskType)
            .published(metrics.published.sum())
            .publishFailed(metrics.publishFailed.sum())
            .started(metrics.started.sum())
            .completed(metrics.completed.sum())
            .failed(metrics.failed.sum())
            .timeout(metrics.timeout.sum());
        
        if (timeStats != null && timeStats.count.sum() > 0) {
            builder
                .averageProcessingTime(timeStats.totalTime.sum() / timeStats.count.sum())
                .minProcessingTime(timeStats.minTime.get())
                .maxProcessingTime(timeStats.maxTime.get());
        }
        
        return builder.build();
    }
    
    /**
     * Calculate overall success rate
     */
    private double calculateSuccessRate() {
        long completed = totalTasksCompleted.get();
        long total = completed + totalTasksFailed.get() + totalTasksTimeout.get();
        
        return total > 0 ? (double) completed / total : 0.0;
    }
    
    /**
     * Reset all metrics
     */
    public void reset() {
        totalTasksPublished.set(0);
        totalTasksCompleted.set(0);
        totalTasksFailed.set(0);
        totalTasksTimeout.set(0);
        taskTypeMetrics.clear();
        processingTimeStats.clear();
    }
    
    /**
     * Task type specific metrics
     */
    private static class TaskTypeMetrics {
        final LongAdder published = new LongAdder();
        final LongAdder publishFailed = new LongAdder();
        final LongAdder started = new LongAdder();
        final LongAdder completed = new LongAdder();
        final LongAdder failed = new LongAdder();
        final LongAdder timeout = new LongAdder();
    }
    
    /**
     * Processing time statistics
     */
    private static class ProcessingTimeStats {
        final LongAdder totalTime = new LongAdder();
        final LongAdder count = new LongAdder();
        final AtomicLong minTime = new AtomicLong(0);
        final AtomicLong maxTime = new AtomicLong(0);
    }
    
    /**
     * Overall A2A metrics snapshot
     */
    @lombok.Data
    @lombok.Builder
    public static class A2AMetrics {
        private long totalTasksPublished;
        private long totalTasksCompleted;
        private long totalTasksFailed;
        private long totalTasksTimeout;
        private double successRate;
    }
    
    /**
     * Task type metrics snapshot
     */
    @lombok.Data
    @lombok.Builder
    public static class TaskTypeMetricsSnapshot {
        private String taskType;
        private long published;
        private long publishFailed;
        private long started;
        private long completed;
        private long failed;
        private long timeout;
        private long averageProcessingTime;
        private long minProcessingTime;
        private long maxProcessingTime;
        
        public double getSuccessRate() {
            long total = completed + failed + timeout;
            return total > 0 ? (double) completed / total : 0.0;
        }
    }
}