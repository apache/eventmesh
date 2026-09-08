package org.apache.eventmesh.common.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SubscriptionModeConverterTest {

    private final SubscriptionModeConverter converter = new SubscriptionModeConverter();

    @Test
    public void testValidBroadcasting() {
        SubscriptionMode result = converter.convert("BROADCASTING");
        assertEquals(SubscriptionMode.BROADCASTING, result);
    }

    @Test
    public void testValidClusteringLowerCase() {
        SubscriptionMode result = converter.convert("clustering");
        assertEquals(SubscriptionMode.CLUSTERING, result);
    }

    @Test
    public void testValidWithSpaces() {
        SubscriptionMode result = converter.convert("  broadcasting  ");
        assertEquals(SubscriptionMode.BROADCASTING, result);
    }

    @Test
    public void testNullValue() {
        SubscriptionMode result = converter.convert(null);
        assertEquals(SubscriptionMode.UNRECOGNIZED, result);
    }

    @Test
    public void testEmptyValue() {
        SubscriptionMode result = converter.convert("");
        assertEquals(SubscriptionMode.UNRECOGNIZED, result);
    }

    @Test
    public void testInvalidValue() {
        SubscriptionMode result = converter.convert("invalid_value");
        assertEquals(SubscriptionMode.UNRECOGNIZED, result);
    }
}