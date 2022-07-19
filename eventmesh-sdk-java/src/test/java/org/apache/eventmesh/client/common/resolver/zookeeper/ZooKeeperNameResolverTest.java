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

package org.apache.eventmesh.client.common.resolver.zookeeper;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.eventmesh.client.common.EventMeshAddress;
import org.apache.eventmesh.client.common.constant.ZooKeeperConstant;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.test.TestingServer;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.class)
public class ZooKeeperNameResolverTest {

    private Logger logger = LoggerFactory.getLogger(ZooKeeperNameResolverTest.class);

    private ZooKeeperNameResolver zooKeeperNameResolverUnderTest;

    private TestingServer testingServer;

    private CuratorFramework client;

    @Before
    public void setUp() throws Exception {

        this.testingServer = new TestingServer(1500, true);
        this.testingServer.start();

        this.client = CuratorFrameworkFactory.builder()
            .namespace(ZooKeeperConstant.NAME_SPACE)
            .connectString("127.0.0.1:1500")
            .retryPolicy(new RetryNTimes(Integer.MAX_VALUE, 1000))
            .build();
        this.client.start();

        this.client.create()
            .creatingParentsIfNeeded()
            .forPath("/testCluster/testServerName/127.0.0.1:8080");

        this.client.create()
            .creatingParentsIfNeeded()
            .forPath("/testCluster/testServerName/127.0.0.1:9090");

        this.zooKeeperNameResolverUnderTest = new ZooKeeperNameResolver();
        this.zooKeeperNameResolverUnderTest.init(URI.create("zookeeper://127.0.0.1:1500"));
    }

    @After
    public void close() throws IOException {
        this.client.close();
        this.testingServer.close();
    }

    @Test
    public void testResolver() throws Exception {
        // Setup
        final EventMeshAddress eventMeshAddress = new EventMeshAddress();
        eventMeshAddress.setHost("127.0.0.1");
        eventMeshAddress.setPort(8080);
        final EventMeshAddress eventMeshAddress2 = new EventMeshAddress();
        eventMeshAddress2.setHost("127.0.0.1");
        eventMeshAddress2.setPort(9090);
        final Set<EventMeshAddress> expectedResult = new HashSet<>(Arrays.asList(eventMeshAddress, eventMeshAddress2));

        final Set<EventMeshAddress> result = new HashSet<>();
        // Run the test
        this.zooKeeperNameResolverUnderTest.start(serverList -> {
            result.clear();
            result.addAll(serverList);
            ZooKeeperNameResolverTest.this.logServerInstance(serverList);
        });

        Thread.sleep(1000);

        this.client.create()
            .creatingParentsIfNeeded()
            .forPath("/testCluster/testServerName/192.168.0.1:10000");

        Thread.sleep(1000);

        this.client.create()
            .creatingParentsIfNeeded()
            .forPath("/testCluster/testServerName/256.0.0.100:2568");

        Thread.sleep(1000);

        this.client.delete().forPath("/testCluster/testServerName/192.168.0.1:10000");

        Thread.sleep(1000);

        this.client.delete().forPath("/testCluster/testServerName/256.0.0.100:2568");

        Thread.sleep(1000);

        // Verify the results
        assertThat(result).isEqualTo(expectedResult);
    }

    private void logServerInstance(Set<EventMeshAddress> addressSet) {
        addressSet.stream().forEach(p -> this.logger.info(" current serverInstance is  {} ", p));
    }
}
