package org.apache.eventmesh.common.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SubscriptionModeConverterTest {

    private final SubscriptionModeConverter converter =
            new SubscriptionModeConverter();

    @Test
    public void testNullValue() {
        assertNull(converter.convert(null));
    }

    @Test
    public void testEmptyValue() {
        assertNull(converter.convert(""));
    }

    @Test
    public void testInvalidValue() {
        assertNull(converter.convert("invalid_value"));
    }

    @Test
    public void testLowerCaseValue() {
        assertEquals(
                SubscriptionMode.CLUSTERING,
                converter.convert("clustering")
        );
    }

    @Test
    public void testUpperCaseValue() {
        assertEquals(
                SubscriptionMode.BROADCASTING,
                converter.convert("BROADCASTING")
        );
    }
}