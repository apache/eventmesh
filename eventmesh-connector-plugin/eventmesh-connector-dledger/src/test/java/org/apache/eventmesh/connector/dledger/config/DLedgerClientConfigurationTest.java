package org.apache.eventmesh.connector.dledger.config;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;

public class DLedgerClientConfigurationTest {

    private static final DLedgerClientConfiguration config = new DLedgerClientConfiguration();

    @BeforeClass
    public static void init() {
        config.init();
    }

    @Test
    public void getGroup() {
        assertEquals("default", config.getGroup());
    }

    @Test
    public void getPeers() {
        assertEquals("n0-localhost:20911", config.getPeers());
    }

    @Test
    public void getClientPoolSize() {
        assertEquals(8, config.getClientPoolSize());
    }
}