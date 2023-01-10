/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;


public class EventMeshServerTest {
    public static Logger logger = LoggerFactory.getLogger(EventMeshServerTest.class);

    @Test
    public void testGetConfigForEventMeshHTTPConfiguration() throws Exception {

        ConfigService configService = ConfigService.getInstance();
        configService.setRootConfig("classPath://configuration.properties");

        EventMeshHTTPConfiguration config = configService.getConfig(EventMeshHTTPConfiguration.class);

        assertCommonConfig(config);

        assertHTTPConfig(config);
    }

    private void assertHTTPConfig(EventMeshHTTPConfiguration config) throws AddressStringException {
        Assert.assertEquals(config.httpServerPort, 1816);
        Assert.assertEquals(config.eventMeshServerBatchMsgBatchEnabled, Boolean.FALSE);
        Assert.assertEquals(config.eventMeshServerBatchMsgThreadNum, 2816);
        Assert.assertEquals(config.eventMeshServerSendMsgThreadNum, 3816);
        Assert.assertEquals(config.eventMeshServerPushMsgThreadNum, 4816);
        Assert.assertEquals(config.eventMeshServerReplyMsgThreadNum, 5816);
        Assert.assertEquals(config.eventMeshServerClientManageThreadNum, 6816);
        Assert.assertEquals(config.eventMeshServerRegistryThreadNum, 7816);
        Assert.assertEquals(config.eventMeshServerAdminThreadNum, 8816);

        Assert.assertEquals(config.eventMeshServerRetryThreadNum, 9816);
        Assert.assertEquals(config.eventMeshServerPullRegistryInterval, 11816);
        Assert.assertEquals(config.eventMeshServerAsyncAccumulationThreshold, 12816);
        Assert.assertEquals(config.eventMeshServerRetryBlockQSize, 13816);
        Assert.assertEquals(config.eventMeshServerBatchBlockQSize, 14816);
        Assert.assertEquals(config.eventMeshServerSendMsgBlockQSize, 15816);
        Assert.assertEquals(config.eventMeshServerPushMsgBlockQSize, 16816);
        Assert.assertEquals(config.eventMeshServerClientManageBlockQSize, 17816);
        Assert.assertEquals(config.eventMeshServerBusyCheckInterval, 18816);
        Assert.assertEquals(config.eventMeshServerConsumerEnabled, Boolean.TRUE);
        Assert.assertEquals(config.eventMeshServerUseTls, Boolean.TRUE);
        Assert.assertEquals(config.eventMeshHttpMsgReqNumPerSecond, 19816);
        Assert.assertEquals(config.eventMeshBatchMsgRequestNumPerSecond, 21816);
        Assert.assertEquals(config.eventMeshEventSize, 22816);
        Assert.assertEquals(config.eventMeshEventBatchSize, 23816);

        List<IPAddress> list4 = new ArrayList<>();
        list4.add(new IPAddressString("127.0.0.1").toAddress());
        list4.add(new IPAddressString("127.0.0.2").toAddress());
        Assert.assertEquals(config.eventMeshIpv4BlackList, list4);
        List<IPAddress> list6 = new ArrayList<>();
        list6.add(new IPAddressString("0:0:0:0:0:0:7f00:01").toAddress());
        list6.add(new IPAddressString("0:0:0:0:0:0:7f00:02").toAddress());
        Assert.assertEquals(config.eventMeshIpv6BlackList, list6);
    }

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

    @Test
    public void testGetConfigForEventMeshTCPConfiguration() throws Exception {

        ConfigService configService = ConfigService.getInstance();
        configService.setRootConfig("classPath://configuration.properties");

        EventMeshTCPConfiguration config = configService.getConfig(EventMeshTCPConfiguration.class);

        assertCommonConfig(config);

        assertTCPConfig(config);
    }

    private void assertTCPConfig(EventMeshTCPConfiguration config) {
        Assert.assertEquals(config.eventMeshTcpServerPort, 816);
        Assert.assertEquals(config.eventMeshTcpIdleAllSeconds, 1816);
        Assert.assertEquals(config.eventMeshTcpIdleWriteSeconds, 2816);
        Assert.assertEquals(config.eventMeshTcpIdleReadSeconds, 3816);
        Assert.assertEquals(config.eventMeshTcpMsgReqnumPerSecond, Integer.valueOf(4816));
        Assert.assertEquals(config.eventMeshTcpClientMaxNum, 5816);
        Assert.assertEquals(config.eventMeshTcpGlobalScheduler, 6816);
        Assert.assertEquals(config.eventMeshTcpTaskHandleExecutorPoolSize, 7816);
        Assert.assertEquals(config.eventMeshTcpMsgDownStreamExecutorPoolSize, 8816);
        Assert.assertEquals(config.eventMeshTcpSessionExpiredInMills, 1816);
        Assert.assertEquals(config.eventMeshTcpSessionUpstreamBufferSize, 11816);
        Assert.assertEquals(config.eventMeshTcpMsgAsyncRetryTimes, 12816);
        Assert.assertEquals(config.eventMeshTcpMsgSyncRetryTimes, 13816);
        Assert.assertEquals(config.eventMeshTcpMsgRetrySyncDelayInMills, 14816);
        Assert.assertEquals(config.eventMeshTcpMsgRetryAsyncDelayInMills, 15816);
        Assert.assertEquals(config.eventMeshTcpMsgRetryQueueSize, 16816);
        Assert.assertEquals(config.eventMeshTcpRebalanceIntervalInMills, Integer.valueOf(17816));
        Assert.assertEquals(config.eventMeshServerAdminPort, 18816);
        Assert.assertEquals(config.eventMeshTcpSendBackEnabled, Boolean.TRUE);
        Assert.assertEquals(config.eventMeshTcpSendBackMaxTimes, 3);
        Assert.assertEquals(config.eventMeshTcpPushFailIsolateTimeInMills, 21816);
        Assert.assertEquals(config.gracefulShutdownSleepIntervalInMills, 22816);
        Assert.assertEquals(config.sleepIntervalInRebalanceRedirectMills, 23816);
        Assert.assertEquals(config.eventMeshEventSize, 22816);
        Assert.assertEquals(config.eventMeshEventBatchSize, 23816);
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


    /**
     * True Environment variables need to be set during startup
     */
    @Test
    public void testGetConfigWhenStartup() throws Exception {

        testGetConfigWhenStartup(Boolean.FALSE);
    }

    private void testGetConfigWhenStartup(Boolean hasEnv) throws Exception {
        String eventMeshConfFile = "configuration.properties";

        if (hasEnv) {
            ConfigService.getInstance()
                .setConfigPath(EventMeshConstants.EVENTMESH_CONF_HOME + File.separator)
                .setRootConfig(eventMeshConfFile);
        } else {
            eventMeshConfFile = "classPath://" + eventMeshConfFile;
            ConfigService.getInstance().setRootConfig(eventMeshConfFile);
        }

        ConfigService configService = ConfigService.getInstance();
        CommonConfiguration commonConfiguration = configService.getConfig(CommonConfiguration.class);
        EventMeshHTTPConfiguration eventMeshHttpConfiguration = configService.getConfig(EventMeshHTTPConfiguration.class);
        EventMeshTCPConfiguration eventMeshTCPConfiguration = configService.getConfig(EventMeshTCPConfiguration.class);
        EventMeshGrpcConfiguration eventMeshGrpcConfiguration = configService.getConfig(EventMeshGrpcConfiguration.class);

        assertCommonConfig(eventMeshTCPConfiguration);
        assertCommonConfig(eventMeshHttpConfiguration);
        assertCommonConfig(eventMeshGrpcConfiguration);

        assertTCPConfig(eventMeshTCPConfiguration);
        assertHTTPConfig(eventMeshHttpConfiguration);
        assertGrpcConfig(eventMeshGrpcConfiguration);
    }

}