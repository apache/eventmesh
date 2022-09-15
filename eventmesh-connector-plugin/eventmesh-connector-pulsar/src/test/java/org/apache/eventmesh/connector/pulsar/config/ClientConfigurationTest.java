package org.apache.eventmesh.connector.pulsar.config;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClientConfigurationTest {

  private static ClientConfiguration config;

  @BeforeClass
  public static void init() {
    config = new ClientConfiguration();
    config.init();
  }

  @Test
  public void getServiceUrl() {
    assertEquals(config.serviceAddr, "pulsar://127.0.0.1:6650");
  }

}
