package org.apache.eventmesh.common.utils;

import org.junit.Test;

/**
 * test {@link Assert}
 */
public class AssertTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNotNull() {
        Assert.notNull(null, "error message");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsTrue() {
        Assert.isTrue(false, "error message");
    }
}