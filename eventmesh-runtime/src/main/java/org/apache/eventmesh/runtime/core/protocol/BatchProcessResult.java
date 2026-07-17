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

package org.apache.eventmesh.runtime.core.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result tracker for batch message processing.
 * Tracks success, filtered, and failed message counts for batch operations.
 */
public class BatchProcessResult {

    private final int totalCount;
    private int successCount;
    private int filteredCount;
    private int failedCount;
    private final List<String> failedMessageIds;

    public BatchProcessResult(int totalCount) {
        this.totalCount = totalCount;
        this.successCount = 0;
        this.filteredCount = 0;
        this.failedCount = 0;
        this.failedMessageIds = new ArrayList<>();
    }

    /**
     * Increment the success count by one.
     */
    public void incrementSuccess() {
        successCount++;
    }

    /**
     * Increment the filtered count by one.
     */
    public void incrementFiltered() {
        filteredCount++;
    }

    /**
     * Increment the failed count by one and record the failed message ID.
     *
     * @param messageId the ID of the failed message
     */
    public void incrementFailed(String messageId) {
        failedCount++;
        if (messageId != null) {
            failedMessageIds.add(messageId);
        }
    }

    /**
     * Get the total number of messages in the batch.
     *
     * @return total count
     */
    public int getTotalCount() {
        return totalCount;
    }

    /**
     * Get the number of successfully processed messages.
     *
     * @return success count
     */
    public int getSuccessCount() {
        return successCount;
    }

    /**
     * Get the number of filtered messages.
     *
     * @return filtered count
     */
    public int getFilteredCount() {
        return filteredCount;
    }

    /**
     * Get the number of failed messages.
     *
     * @return failed count
     */
    public int getFailedCount() {
        return failedCount;
    }

    /**
     * Get the list of failed message IDs.
     *
     * @return unmodifiable list of failed message IDs
     */
    public List<String> getFailedMessageIds() {
        return Collections.unmodifiableList(failedMessageIds);
    }

    /**
     * Get a formatted summary string of the batch processing result.
     *
     * @return summary string
     */
    public String toSummary() {
        return String.format("total=%d, success=%d, filtered=%d, failed=%d",
            totalCount, successCount, filteredCount, failedCount);
    }

    /**
     * Get a detailed summary string including failed message IDs.
     *
     * @return detailed summary string
     */
    public String toDetailedSummary() {
        if (failedMessageIds.isEmpty()) {
            return toSummary();
        }
        return String.format("total=%d, success=%d, filtered=%d, failed=%d, failedIds=%s",
            totalCount, successCount, filteredCount, failedCount, failedMessageIds);
    }

    /**
     * Check if all messages were processed successfully (no filtered or failed).
     *
     * @return true if all messages succeeded
     */
    public boolean isAllSuccess() {
        return successCount == totalCount && filteredCount == 0 && failedCount == 0;
    }

    /**
     * Check if any messages failed.
     *
     * @return true if there are failed messages
     */
    public boolean hasFailed() {
        return failedCount > 0;
    }

    /**
     * Check if any messages were filtered.
     *
     * @return true if there are filtered messages
     */
    public boolean hasFiltered() {
        return filteredCount > 0;
    }
}
