package org.apache.eventmesh.runtime.configuration;

import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.ConfigService;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class EventMeshGrpcConfigurationTest {

    @Test
    public void testGetConfigForEventMeshGrpcConfiguration() throws Exception {

        ConfigService configService = ConfigService.getInstance();
        configService.setRootConfig("classPath://configuration.properties");

        EventMeshGrpcConfiguration config = configService.getConfig(EventMeshGrpcConfiguration.class);

        assertCommonConfig(config);
        assertGrpcConfig(config);
    }

    private void assertGrpcConfig(EventMeshGrpcConfiguration config) {
        Assert.assertEquals(config.grpcServerPort, 816);
        Assert.assertEquals(config.eventMeshSessionExpiredInMills, 1816);
        Assert.assertEquals(config.eventMeshServerBatchMsgBatchEnabled, Boolean.FALSE);
        Assert.assertEquals(config.eventMeshServerBatchMsgThreadNum, 2816);
        Assert.assertEquals(config.eventMeshServerSendMsgThreadNum, 3816);
        Assert.assertEquals(config.eventMeshServerPushMsgThreadNum, 4816);
        Assert.assertEquals(config.eventMeshServerReplyMsgThreadNum, 5816);
        Assert.assertEquals(config.eventMeshServerSubscribeMsgThreadNum, 6816);
        Assert.assertEquals(config.eventMeshServerRegistryThreadNum, 7816);
        Assert.assertEquals(config.eventMeshServerAdminThreadNum, 8816);
        Assert.assertEquals(config.eventMeshServerRetryThreadNum, 9816);
        Assert.assertEquals(config.eventMeshServerPullRegistryInterval, 11816);
        Assert.assertEquals(config.eventMeshServerAsyncAccumulationThreshold, 12816);
        Assert.assertEquals(config.eventMeshServerRetryBlockQueueSize, 13816);
        Assert.assertEquals(config.eventMeshServerBatchBlockQueueSize, 14816);
        Assert.assertEquals(config.eventMeshServerSendMsgBlockQueueSize, 15816);
        Assert.assertEquals(config.eventMeshServerPushMsgBlockQueueSize, 16816);
        Assert.assertEquals(config.eventMeshServerSubscribeMsgBlockQueueSize, 17816);
        Assert.assertEquals(config.eventMeshServerBusyCheckInterval, 18816);
        Assert.assertEquals(config.eventMeshServerConsumerEnabled, Boolean.TRUE);
        Assert.assertEquals(config.eventMeshServerUseTls, Boolean.TRUE);
        Assert.assertEquals(config.eventMeshBatchMsgRequestNumPerSecond, 21816);
        Assert.assertEquals(config.eventMeshMsgReqNumPerSecond, 19816);
    }

    private void assertCommonConfig(CommonConfiguration config) {
        Assert.assertEquals("env-succeed!!!", config.getEventMeshEnv());
        Assert.assertEquals("idc-succeed!!!", config.getEventMeshIDC());
        Assert.assertEquals("cluster-succeed!!!", config.getEventMeshCluster());
        Assert.assertEquals("name-succeed!!!", config.getEventMeshName());
        Assert.assertEquals("816", config.getSysID());
        Assert.assertEquals("connector-succeed!!!", config.getEventMeshConnectorPluginType());
        Assert.assertEquals("security-succeed!!!", config.getEventMeshSecurityPluginType());
        Assert.assertEquals("registry-succeed!!!", config.getEventMeshRegistryPluginType());
        Assert.assertEquals("trace-succeed!!!", config.getEventMeshTracePluginType());
        Assert.assertEquals("hostIp-succeed!!!", config.getEventMeshServerIp());

        List<String> list = new ArrayList<>();
        list.add("metrics-succeed1!!!");
        list.add("metrics-succeed2!!!");
        list.add("metrics-succeed3!!!");
        Assert.assertEquals(list, config.getEventMeshMetricsPluginType());

        Assert.assertTrue(config.isEventMeshServerSecurityEnable());
        Assert.assertTrue(config.isEventMeshServerRegistryEnable());
        Assert.assertTrue(config.isEventMeshServerTraceEnable());

        Assert.assertEquals("eventmesh.idc-succeed!!!", config.getEventMeshWebhookOrigin());
    }
}