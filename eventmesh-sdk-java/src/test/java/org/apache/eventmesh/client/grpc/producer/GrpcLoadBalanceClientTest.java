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

package org.apache.eventmesh.client.grpc.producer;

import org.apache.eventmesh.client.common.EventMeshAddress;
import org.apache.eventmesh.client.common.constant.ZooKeeperConstant;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.test.TestingServer;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import io.cloudevents.CloudEvent;

@RunWith(MockitoJUnitRunner.class)
public class GrpcLoadBalanceClientTest {

    private EventMeshGrpcClientConfig clientConfig;

    private GrpcLoadBalanceClient grpcLoadBalanceClientUnderTest;

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


        // Setup
        this.clientConfig = EventMeshGrpcClientConfig.builder()
            .registerUri(new URI("zookeeper://127.0.0.1:1500"))
            .nameResolverName("zookeeper")
            .loadBalanceStrategyName("random")
            .build();

        this.grpcLoadBalanceClientUnderTest = new GrpcLoadBalanceClient(this.clientConfig);
        this.grpcLoadBalanceClientUnderTest.init();

    }

    @After
    public void close() throws Exception {
        this.grpcLoadBalanceClientUnderTest.close();
        this.client.close();
        this.testingServer.close();
    }

    @Test
    public void testInit() throws Exception {


        // Run the test
        this.grpcLoadBalanceClientUnderTest.init();

        // Verify the results
    }


    @Test
    public void testPublish1() {

        // Setup
        final EventMeshMessage message = this.defaultEventMeshMessageBuilder().build();

        // Run the test
        final Response result = this.grpcLoadBalanceClientUnderTest.publish(message);

    }

    @Test
    public void testPublish2() {

        // Setup
        final List<CloudEvent> messageList = Arrays.asList(new MockCloudEvent());

        // Run the test
        final Response result = this.grpcLoadBalanceClientUnderTest.publish(messageList);

    }

    @Test
    public void testPublish3() {

        // Run the test
        final Response result = this.grpcLoadBalanceClientUnderTest.publish(new MockCloudEvent());

    }

    @Test
    public void testRequestReply1() {

        // Run the test
        final CloudEvent result = this.grpcLoadBalanceClientUnderTest.requestReply(new MockCloudEvent(), 2000);

    }

    @Test
    public void testRequestReply2() {

        // Run the test
        final EventMeshMessage result = this.grpcLoadBalanceClientUnderTest.requestReply(this.defaultEventMeshMessageBuilder().build(), 2000);

    }

    @Test
    public void testCreateClient() {
        // Setup
        final EventMeshGrpcClientConfig clientConfig = EventMeshGrpcClientConfig.builder().build();
        final EventMeshAddress address = new EventMeshAddress();
        address.setHost("127.0.0.1");
        address.setPort(8090);

        // Run the test
        final EventMeshGrpcProducer result = this.grpcLoadBalanceClientUnderTest.createClient(clientConfig, address);

    }

    private EventMeshMessage.EventMeshMessageBuilder defaultEventMeshMessageBuilder() {
        return EventMeshMessage.builder()
            .bizSeqNo("bizSeqNo")
            .content("mockContent")
            .createTime(System.currentTimeMillis())
            .uniqueId("mockUniqueId")
            .topic("mockTopic")
            .prop(Collections.emptyMap());
    }

}
