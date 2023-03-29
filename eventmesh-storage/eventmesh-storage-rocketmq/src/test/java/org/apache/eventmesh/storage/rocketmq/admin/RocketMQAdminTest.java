package org.apache.eventmesh.storage.rocketmq.admin;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RocketMQAdminTest {

    private RocketMQAdmin rocketMQAdmin;

    @Before
    public void setUp() {
        rocketMQAdmin = new RocketMQAdmin();
    }

    @Test
    public void testIsStarted() {
        Assert.assertFalse(rocketMQAdmin.isStarted());
    }

    @Test
    public void testIsClosed() {
        Assert.assertTrue(rocketMQAdmin.isClosed());
    }

    @Test
    public void testStart() {
        rocketMQAdmin.start();
        Assert.assertTrue(rocketMQAdmin.isStarted());
    }

    @Test
    public void testShutdown() {
        rocketMQAdmin.shutdown();
        Assert.assertFalse(rocketMQAdmin.isClosed());
    }
}