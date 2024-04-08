package org.apache.eventmesh.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThreadUtilsTest {

    @Test
    public void testRandomPauseDurationWithinBounds() throws InterruptedException {
        long min = 100;
        long max = 200;

        long startTime = System.currentTimeMillis();

        ThreadUtils.randomPause(min, max);

        long endTime = System.currentTimeMillis();

        long duration = endTime - startTime;

        assertTrue(duration >= min && duration <= max + 10, "The pause duration should be within the specified bounds, allowing a small margin for timing inaccuracies.");
    }

    @Test
    public void testGetPIDReturnsPositiveValue() {

        long pid = ThreadUtils.getPID();

        assertTrue(pid > 0, "The PID should be a positive value.");
    }

    @Test
    public void testGetPIDReturnsConsistentValue() {

        long firstCallPID = ThreadUtils.getPID();
        long secondCallPID = ThreadUtils.getPID();

        assertEquals(firstCallPID, secondCallPID, "The PID should remain consistent across calls.");
    }
}