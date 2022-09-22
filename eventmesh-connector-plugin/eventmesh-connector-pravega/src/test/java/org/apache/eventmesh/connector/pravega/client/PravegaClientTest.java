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

package org.apache.eventmesh.connector.pravega.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.eventmesh.api.AsyncConsumeContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.connector.pravega.config.PravegaConnectorConfig;

import java.net.URI;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.pravega.client.admin.StreamManager;
import io.pravega.client.stream.ReaderGroupNotFoundException;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.PortBinding;

@Ignore
public class PravegaClientTest {
    private StreamManager streamManager;
    private PravegaConnectorConfig config;
    private URI controllerURI;

    @Rule
    public GenericContainer pravega =
        new GenericContainer(DockerImageName.parse("pravega/pravega:0.11.0"))
            .withExposedPorts(9090, 12345).withEnv("HOST_IP", "127.0.0.1").withCommand("standalone")
            .withCreateContainerCmdModifier((Consumer<CreateContainerCmd>) createContainerCmd ->
                createContainerCmd.getHostConfig()
                    .withPortBindings(PortBinding.parse("9090:9090"), PortBinding.parse("12345:12345")));

    @Before
    public void setUp() {
        String host = pravega.getHost();
        int port = pravega.getFirstMappedPort();
        controllerURI = URI.create(String.format("tcp://%s:%d", host, port));
        streamManager = StreamManager.create(controllerURI);
    }

    @Test
    public void startTest() {
        PravegaClient pravegaClient = getNewPravegaClient();
        pravegaClient.start();
        assertTrue(streamManager.checkScopeExists(config.getScope()));
    }

    @Test
    public void shutdownTest() {
        PravegaClient pravegaClient = getNewPravegaClient();
        pravegaClient.start();
        pravegaClient.publish("test1", createCloudEvent());
        pravegaClient.publish("test2", createCloudEvent());
        pravegaClient.publish("test3", createCloudEvent());

        pravegaClient.shutdown();
        assertTrue(pravegaClient.getSubscribeTaskMap().isEmpty());
        assertTrue(pravegaClient.getWriterMap().isEmpty());
    }

    @Test
    public void publishTest() {
        PravegaClient pravegaClient = getNewPravegaClient();
        pravegaClient.start();
        pravegaClient.publish("test1", createCloudEvent());
        pravegaClient.publish("test2", createCloudEvent());
        assertTrue(streamManager.checkStreamExists(config.getScope(), "test1"));
        assertTrue(streamManager.checkStreamExists(config.getScope(), "test2"));
    }

    @Test
    public void subscribeTest() {
        PravegaClient pravegaClient = getNewPravegaClient();
        pravegaClient.start();
        pravegaClient.subscribe("test1", "consumerGroup", new EventListener() {
            @Override
            public void consume(CloudEvent cloudEvent, AsyncConsumeContext context) {
                // do nothing
            }
        });
        assertNotNull(pravegaClient.getReaderGroupManager().getReaderGroup("test1-consumerGroup"));
        assertTrue(pravegaClient.getSubscribeTaskMap().containsKey("test1"));
    }

    @Test
    public void unsubscribeTest() {
        PravegaClient pravegaClient = getNewPravegaClient();
        pravegaClient.unsubscribe("test1", "consumerGroup");

        pravegaClient.start();
        pravegaClient.subscribe("test1", "consumerGroup", new EventListener() {
            @Override
            public void consume(CloudEvent cloudEvent, AsyncConsumeContext context) {
                // do nothing
            }
        });
        pravegaClient.unsubscribe("test1", "consumerGroup");

        assertFalse(pravegaClient.getSubscribeTaskMap().containsKey("test1"));
        try {
            pravegaClient.getReaderGroupManager().getReaderGroup("test1-consumerGroup");
        } catch (Exception e) {
            assertTrue(e instanceof ReaderGroupNotFoundException);
            return;
        }
        fail();
    }

    @Test
    public void checkTopicExistTest() {
        PravegaClient pravegaClient = getNewPravegaClient();
        pravegaClient.start();
        assertFalse(pravegaClient.checkTopicExist("test1"));

        pravegaClient.publish("test1", createCloudEvent());
        assertTrue(pravegaClient.checkTopicExist("test1"));
    }

    private PravegaClient getNewPravegaClient() {
        config = PravegaConnectorConfig.getInstance();
        config.setControllerURI(controllerURI);
        return PravegaClient.getNewInstance(config);
    }

    private CloudEvent createCloudEvent() {
        String data = "{\"headers\":{\"content-length\":\"36\",\"Accept\":\"*/*\",\"ip\":\"127.0.0.1:51226\",\"User-Agent\":\"curl/7.83.1\","
            + "\"Host\":\"127.0.0.1:10105\",\"source\":\"127.0.0.1:51226\",\"Content-Type\":\"application/json\"},"
            + "\"path\":\"/eventmesh/publish/TEST-TOPIC-HTTP-ASYNC\",\"method\":\"POST\",\"body\":{\"pass\":\"12345678\",\"name\":\"admin\"}}";
        return CloudEventBuilder.v1()
            .withId("c61039e1-7884-4d7f-b72f-6c61160a64fc")
            .withSource(URI.create("source:127.0.0.1:51226"))
            .withType("http_request")
            .withDataContentType("application/json")
            .withSubject("TEST-TOPIC-HTTP-ASYNC")
            .withData(data.getBytes())
            .withExtension("reqeventmesh2mqtimestamp", "1659342713460")
            .withExtension("ip", "127.0.0.1:51226")
            .withExtension("idc", "idc")
            .withExtension("protocoldesc", "http")
            .withExtension("pid", 2376)
            .withExtension("env", "env")
            .withExtension("sys", 1234)
            .withExtension("ttl", 4000)
            .withExtension("producergroup", "em-http-producer")
            .withExtension("consumergroup", "em-http-consumer")
            .withExtension("passwd", "pass")
            .withExtension("bizseqno", "249695004068274968410952665702")
            .withExtension("protocoltype", "http")
            .withExtension("msgtype", "persistent")
            .withExtension("uniqueid", "866012286651006371403062105469")
            .withExtension("username", "eventmesh")
            .withExtension("reqc2eventmeshtimestamp", "1659342713460")
            .build();
    }
}