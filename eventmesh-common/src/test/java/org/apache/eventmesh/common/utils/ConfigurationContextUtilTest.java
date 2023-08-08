package org.apache.eventmesh.common.utils;

import org.apache.eventmesh.common.config.CommonConfiguration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ConfigurationContextUtilTest {


    private CommonConfiguration grpcConfig;

    @Before
    public void setUp() {
        grpcConfig = new CommonConfiguration();
        grpcConfig.setEventMeshName("grpc");
        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.GRPC,grpcConfig);
    }

    @Test
    public void testPutIfAbsent(){
        CommonConfiguration tcpConfig = new CommonConfiguration();
        tcpConfig.setEventMeshName("tpc");
        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.TCP,new CommonConfiguration());
        CommonConfiguration get = ConfigurationContextUtil.get(ConfigurationContextUtil.TCP);
        Assert.assertNotNull(get);
        Assert.assertEquals(tcpConfig,get);
        CommonConfiguration newGrpc = new CommonConfiguration();
        newGrpc.setEventMeshName("newGrpc");
        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.GRPC,newGrpc);
        CommonConfiguration getGrpc = ConfigurationContextUtil.get(ConfigurationContextUtil.GRPC);
        Assert.assertNotNull(getGrpc);
        Assert.assertEquals(grpcConfig,getGrpc);
        Assert.assertNotEquals(newGrpc,getGrpc);
    }

    @Test
    public void testGet() throws Exception {
        CommonConfiguration result = ConfigurationContextUtil.get("key");
        Assert.assertEquals(new CommonConfiguration(), result);
    }

    @Test
    public void testClear() throws Exception {
        ConfigurationContextUtil.clear();
    }
}
