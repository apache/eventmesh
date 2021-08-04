package org.apache.eventmesh.common.config;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.List;

public class YamlConfigurationReaderTest {

    private YamlConfigurationReader yamlConfigurationReader;

    @Before
    public void prepare() throws IOException {
        URL resource = YamlConfigurationReaderTest.class.getClassLoader().getResource("yamlConfiguration.yml");
        yamlConfigurationReader = new YamlConfigurationReader(resource.getPath());
    }

    @Test
    public void getInt() {
        Assert.assertEquals(3, yamlConfigurationReader.getInt("eventMesh.sysid", 1));
        Assert.assertEquals(1, yamlConfigurationReader.getInt("eventMesh.sysid.xx", 1));
    }

    @Test
    public void getString() {
        Assert.assertEquals("rocketmq", yamlConfigurationReader.getString("eventMesh.connector.plugin.type", "kafka"));
        Assert.assertEquals("kafka", yamlConfigurationReader.getString("eventMesh.connector.plugin.type1", "kafka"));
        Assert.assertEquals("3", yamlConfigurationReader.getString("eventMesh.sysidStr", "kafka"));
    }

    @Test
    public void getList() {
        List<String> ipList = yamlConfigurationReader.getList("eventMesh.server.hostIp", null);
        Assert.assertNotNull(ipList);
    }
}