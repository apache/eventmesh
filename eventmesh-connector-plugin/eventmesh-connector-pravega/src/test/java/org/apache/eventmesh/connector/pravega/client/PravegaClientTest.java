package org.apache.eventmesh.connector.pravega.client;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.eventmesh.connector.pravega.config.PravegaConnectorConfig;

import java.net.URI;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.pravega.client.admin.StreamManager;
import io.pravega.client.stream.EventWriterConfig;
import io.pravega.client.stream.impl.ByteArraySerializer;

public class PravegaClientTest {
    private PravegaClient pravegaClient;
    private StreamManager streamManager;
    private PravegaConnectorConfig config;

    @Rule
    public GenericContainer pravega = new GenericContainer(DockerImageName.parse("pravega/pravega:0.11.0"))
        .withExposedPorts(9090).withCommand("standalone");

    @Before
    public void setUp() throws Exception {
        String host = pravega.getHost();
        int port = pravega.getFirstMappedPort();
        URI controllerURI = URI.create(String.format("tcp://%s:%d", host, port));
        streamManager = StreamManager.create(controllerURI);
        config = PravegaConnectorConfig.getInstance();
        config.setControllerURI(controllerURI);
        pravegaClient = PravegaClient.setUpAndGetInstance(config);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void start() {
        pravegaClient.start();
        assertTrue(streamManager.checkScopeExists(config.getScope()));
    }

    @Ignore
    public void shutdown() {
        pravegaClient.start();
        pravegaClient.publish("test1", createCloudEvent());
        pravegaClient.publish("test2", createCloudEvent());
        pravegaClient.publish("test3", createCloudEvent());

        pravegaClient.shutdown();
        assertTrue(pravegaClient.getSubscribeTaskMap().isEmpty());
        assertTrue(pravegaClient.getReaderIdMap().isEmpty());
        assertNull(pravegaClient.getReaderGroupManager().getReaderGroup("test1-consumerGroup"));
        assertNull(pravegaClient.getClientFactory().createEventWriter("test2",
                                                                      new ByteArraySerializer(),
                                                                      EventWriterConfig.builder().build()));
        assertNull(pravegaClient.getStreamManager().getStreamInfo(config.getScope(), "test2"));
    }

    @Test
    public void publish() {
    }

    @Test
    public void subscribe() {
    }

    @Test
    public void unsubscribe() {
    }

    @Test
    public void checkTopicExist() {
    }

    @Test
    public void createScope() {
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