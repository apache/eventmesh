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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class BatchProcessResultTest {

    @Test
    public void testInitialState() {
        // Given
        BatchProcessResult result = new BatchProcessResult(10);

        // Then
        assertEquals(10, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFilteredCount());
        assertEquals(0, result.getFailedCount());
        assertTrue(result.getFailedMessageIds().isEmpty());
        assertEquals("total=10, success=0, filtered=0, failed=0", result.toSummary());
    }

    @Test
    public void testIncrementSuccess() {
        // Given
        BatchProcessResult result = new BatchProcessResult(5);

        // When
        result.incrementSuccess();
        result.incrementSuccess();
        result.incrementSuccess();

        // Then
        assertEquals(3, result.getSuccessCount());
        assertEquals(0, result.getFilteredCount());
        assertEquals(0, result.getFailedCount());
    }

    @Test
    public void testIncrementFiltered() {
        // Given
        BatchProcessResult result = new BatchProcessResult(5);

        // When
        result.incrementFiltered();
        result.incrementFiltered();

        // Then
        assertEquals(0, result.getSuccessCount());
        assertEquals(2, result.getFilteredCount());
        assertEquals(0, result.getFailedCount());
    }

    @Test
    public void testIncrementFailed() {
        // Given
        BatchProcessResult result = new BatchProcessResult(5);

        // When
        result.incrementFailed("msg-1");
        result.incrementFailed("msg-2");

        // Then
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFilteredCount());
        assertEquals(2, result.getFailedCount());

        List<String> failedIds = result.getFailedMessageIds();
        assertEquals(2, failedIds.size());
        assertTrue(failedIds.contains("msg-1"));
        assertTrue(failedIds.contains("msg-2"));
    }

    @Test
    public void testIncrementFailedWithNullId() {
        // Given
        BatchProcessResult result = new BatchProcessResult(5);

        // When
        result.incrementFailed(null);
        result.incrementFailed("msg-1");

        // Then
        assertEquals(2, result.getFailedCount());
        List<String> failedIds = result.getFailedMessageIds();
        assertEquals(1, failedIds.size()); // null ID not added
        assertEquals("msg-1", failedIds.get(0));
    }

    @Test
    public void testMixedOperations() {
        // Given
        BatchProcessResult result = new BatchProcessResult(10);

        // When
        result.incrementSuccess();
        result.incrementSuccess();
        result.incrementSuccess();
        result.incrementFiltered();
        result.incrementFiltered();
        result.incrementFailed("msg-1");
        result.incrementFailed("msg-2");

        // Then
        assertEquals(10, result.getTotalCount());
        assertEquals(3, result.getSuccessCount());
        assertEquals(2, result.getFilteredCount());
        assertEquals(2, result.getFailedCount());
        assertEquals(2, result.getFailedMessageIds().size());
    }

    @Test
    public void testToSummary() {
        // Given
        BatchProcessResult result = new BatchProcessResult(20);
        result.incrementSuccess();
        result.incrementSuccess();
        result.incrementSuccess();
        result.incrementSuccess();
        result.incrementSuccess(); // 5 success
        result.incrementFiltered();
        result.incrementFiltered(); // 2 filtered
        result.incrementFailed("msg-1"); // 1 failed

        // When
        String summary = result.toSummary();

        // Then
        assertEquals("total=20, success=5, filtered=2, failed=1", summary);
    }

    @Test
    public void testToDetailedSummary_NoFailures() {
        // Given
        BatchProcessResult result = new BatchProcessResult(10);
        result.incrementSuccess();
        result.incrementSuccess();
        result.incrementFiltered();

        // When
        String detailedSummary = result.toDetailedSummary();

        // Then: Should be same as regular summary when no failed IDs
        assertEquals("total=10, success=2, filtered=1, failed=0", detailedSummary);
    }

    @Test
    public void testToDetailedSummary_WithFailures() {
        // Given
        BatchProcessResult result = new BatchProcessResult(10);
        result.incrementSuccess();
        result.incrementFailed("msg-1");
        result.incrementFailed("msg-2");

        // When
        String detailedSummary = result.toDetailedSummary();

        // Then
        assertTrue(detailedSummary.contains("total=10"));
        assertTrue(detailedSummary.contains("success=1"));
        assertTrue(detailedSummary.contains("failed=2"));
        assertTrue(detailedSummary.contains("failedIds=[msg-1, msg-2]"));
    }

    @Test
    public void testIsAllSuccess_AllSucceed() {
        // Given
        BatchProcessResult result = new BatchProcessResult(5);
        result.incrementSuccess();
        result.incrementSuccess();
        result.incrementSuccess();
        result.incrementSuccess();
        result.incrementSuccess();

        // When & Then
        assertTrue(result.isAllSuccess());
    }

    @Test
    public void testIsAllSuccess_WithFiltered() {
        // Given
        BatchProcessResult result = new BatchProcessResult(5);
        result.incrementSuccess();
        result.incrementSuccess();
        result.incrementSuccess();
        result.incrementSuccess();
        result.incrementFiltered(); // 1 filtered

        // When & Then
        assertFalse(result.isAllSuccess());
    }

    @Test
    public void testIsAllSuccess_WithFailed() {
        // Given
        BatchProcessResult result = new BatchProcessResult(5);
        result.incrementSuccess();
        result.incrementSuccess();
        result.incrementSuccess();
        result.incrementSuccess();
        result.incrementFailed("msg-1");

        // When & Then
        assertFalse(result.isAllSuccess());
    }

    @Test
    public void testIsAllSuccess_Partial() {
        // Given
        BatchProcessResult result = new BatchProcessResult(5);
        result.incrementSuccess();
        result.incrementSuccess(); // Only 2 out of 5

        // When & Then
        assertFalse(result.isAllSuccess());
    }

    @Test
    public void testHasFailed() {
        // Given
        BatchProcessResult result = new BatchProcessResult(5);

        // When & Then
        assertFalse(result.hasFailed()); // Initially no failures

        result.incrementFailed("msg-1");
        assertTrue(result.hasFailed()); // After failure
    }

    @Test
    public void testHasFiltered() {
        // Given
        BatchProcessResult result = new BatchProcessResult(5);

        // When & Then
        assertFalse(result.hasFiltered()); // Initially no filtered

        result.incrementFiltered();
        assertTrue(result.hasFiltered()); // After filtering
    }

    @Test
    public void testFailedMessageIdsImmutable() {
        // Given
        BatchProcessResult result = new BatchProcessResult(5);
        result.incrementFailed("msg-1");
        result.incrementFailed("msg-2");

        // When
        List<String> failedIds = result.getFailedMessageIds();

        // Then: Should be unmodifiable
        try {
            failedIds.add("msg-3");
            assertTrue(false, "Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected - list is unmodifiable
            assertTrue(true);
        }
    }

    @Test
    public void testZeroTotalCount() {
        // Given
        BatchProcessResult result = new BatchProcessResult(0);

        // Then
        assertEquals(0, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertTrue(result.isAllSuccess()); // No messages to process = all success
    }

    @Test
    public void testLargeNumbers() {
        // Given
        BatchProcessResult result = new BatchProcessResult(10000);

        // When: Simulate large batch processing
        for (int i = 0; i < 5000; i++) {
            result.incrementSuccess();
        }
        for (int i = 0; i < 3000; i++) {
            result.incrementFiltered();
        }
        for (int i = 0; i < 2000; i++) {
            result.incrementFailed("msg-" + i);
        }

        // Then
        assertEquals(10000, result.getTotalCount());
        assertEquals(5000, result.getSuccessCount());
        assertEquals(3000, result.getFilteredCount());
        assertEquals(2000, result.getFailedCount());
        assertEquals(2000, result.getFailedMessageIds().size());
        assertFalse(result.isAllSuccess());
        assertTrue(result.hasFailed());
        assertTrue(result.hasFiltered());
    }
}
