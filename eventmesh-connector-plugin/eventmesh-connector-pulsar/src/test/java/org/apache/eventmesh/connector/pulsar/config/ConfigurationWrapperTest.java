package org.apache.eventmesh.connector.pulsar.config;

import org.junit.Assert;
import org.junit.Test;

public class ConfigurationWrapperTest {

    @Test
    public void getProp() {
      String namesrcAddr = ConfigurationWrapper.getProp("eventMesh.server.pulsar.service");
      Assert.assertNotNull(namesrcAddr);
    }

}
