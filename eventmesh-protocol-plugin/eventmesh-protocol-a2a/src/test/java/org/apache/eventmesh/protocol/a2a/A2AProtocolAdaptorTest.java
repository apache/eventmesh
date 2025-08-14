package org.apache.eventmesh.protocol.a2a;

import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.http.message.RequestMessage;
import org.apache.eventmesh.common.protocol.http.message.ResponseMessage;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageResponseHeader;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("A2A Protocol Adaptor Tests")
public class A2AProtocolAdaptorTest {

    private A2AProtocolAdaptor adaptor;

    @BeforeEach
    void setUp() {
        adaptor = new A2AProtocolAdaptor();
    }

    @Test
    @DisplayName("Test A2A message creation")
    void testA2AMessageCreation() {
        A2AProtocolAdaptor.A2AMessage message = new A2AProtocolAdaptor.A2AMessage();
        message.setMessageType("TASK_REQUEST");
        
        // Create source agent
        A2AProtocolAdaptor.AgentInfo sourceAgent = new A2AProtocolAdaptor.AgentInfo();
        sourceAgent.setAgentId("agent-001");
        sourceAgent.setAgentType("task-executor");
        sourceAgent.setCapabilities(new String[]{"data-processing"});
        message.setSourceAgent(sourceAgent);
        
        // Create target agent
        A2AProtocolAdaptor.AgentInfo targetAgent = new A2AProtocolAdaptor.AgentInfo();
        targetAgent.setAgentId("agent-002");
        targetAgent.setAgentType("data-provider");
        message.setTargetAgent(targetAgent);
        
        // Set payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", "task-001");
        payload.put("taskType", "data-processing");
        message.setPayload(payload);
        
        // Verify message structure
        assertEquals("A2A", message.getProtocol());
        assertEquals("1.0", message.getVersion());
        assertEquals("TASK_REQUEST", message.getMessageType());
        assertEquals("agent-001", message.getSourceAgent().getAgentId());
        assertEquals("agent-002", message.getTargetAgent().getAgentId());
        assertNotNull(message.getMessageId());
        assertNotNull(message.getTimestamp());
    }

    @Test
    @DisplayName("Test agent info creation")
    void testAgentInfoCreation() {
        A2AProtocolAdaptor.AgentInfo agentInfo = new A2AProtocolAdaptor.AgentInfo();
        agentInfo.setAgentId("test-agent");
        agentInfo.setAgentType("test-type");
        agentInfo.setCapabilities(new String[]{"capability1", "capability2"});
        
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("grpc", "localhost:9090");
        endpoints.put("http", "http://localhost:8080");
        agentInfo.setEndpoints(endpoints);
        
        Map<String, String> resources = new HashMap<>();
        resources.put("cpu", "4 cores");
        resources.put("memory", "8GB");
        agentInfo.setResources(resources);
        
        assertEquals("test-agent", agentInfo.getAgentId());
        assertEquals("test-type", agentInfo.getAgentType());
        assertArrayEquals(new String[]{"capability1", "capability2"}, agentInfo.getCapabilities());
        assertEquals("localhost:9090", agentInfo.getEndpoints().get("grpc"));
        assertEquals("4 cores", agentInfo.getResources().get("cpu"));
    }

    @Test
    @DisplayName("Test message metadata creation")
    void testMessageMetadataCreation() {
        A2AProtocolAdaptor.MessageMetadata metadata = new A2AProtocolAdaptor.MessageMetadata();
        metadata.setPriority("HIGH");
        metadata.setTtl(600);
        metadata.setCorrelationId("correlation-123");
        
        assertEquals("HIGH", metadata.getPriority());
        assertEquals(600, metadata.getTtl());
        assertEquals("correlation-123", metadata.getCorrelationId());
    }

    @Test
    @DisplayName("Test HTTP message to CloudEvent conversion")
    void testHttpMessageToCloudEventConversion() {
        // Create HTTP request message
        SendMessageRequestHeader header = new SendMessageRequestHeader();
        header.putHeader(ProtocolKey.REQUEST_CODE, "200");
        header.putHeader(ProtocolKey.LANGUAGE, "JAVA");
        header.putHeader(ProtocolKey.PROTOCOL_TYPE, "A2A");
        header.putHeader(ProtocolKey.PROTOCOL_VERSION, "1.0");
        
        SendMessageRequestBody body = new SendMessageRequestBody();
        
        // Create A2A message
        A2AProtocolAdaptor.A2AMessage a2aMessage = new A2AProtocolAdaptor.A2AMessage();
        a2aMessage.setMessageType("TASK_REQUEST");
        
        A2AProtocolAdaptor.AgentInfo sourceAgent = new A2AProtocolAdaptor.AgentInfo();
        sourceAgent.setAgentId("agent-001");
        a2aMessage.setSourceAgent(sourceAgent);
        
        A2AProtocolAdaptor.AgentInfo targetAgent = new A2AProtocolAdaptor.AgentInfo();
        targetAgent.setAgentId("agent-002");
        a2aMessage.setTargetAgent(targetAgent);
        
        body.setContent(JsonUtils.toJSONString(a2aMessage));
        body.setTtl(300);
        body.setUniqueId("test-unique-id");
        
        RequestMessage requestMessage = new RequestMessage(header, body);
        
        // Convert to CloudEvent
        CloudEvent cloudEvent = adaptor.toCloudEvent(requestMessage);
        
        // Verify CloudEvent attributes
        assertEquals("A2A", cloudEvent.getAttributesMap().get("protocol").getCeString());
        assertEquals("1.0", cloudEvent.getAttributesMap().get("version").getCeString());
        assertEquals("TASK_REQUEST", cloudEvent.getAttributesMap().get("messageType").getCeString());
        assertEquals("agent-001", cloudEvent.getAttributesMap().get("sourceAgent").getCeString());
        assertEquals("agent-002", cloudEvent.getAttributesMap().get("targetAgent").getCeString());
    }

    @Test
    @DisplayName("Test CloudEvent to HTTP message conversion")
    void testCloudEventToHttpMessageConversion() {
        // Create CloudEvent
        CloudEvent.Builder cloudEventBuilder = CloudEvent.newBuilder()
            .setId("test-event-id")
            .setSource("eventmesh")
            .setSpecVersion("1.0")
            .setType("org.apache.eventmesh.protocol.a2a")
            .putAttributes("protocol", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("A2A").build())
            .putAttributes("version", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("1.0").build())
            .putAttributes("messageType", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("TASK_REQUEST").build())
            .putAttributes("sourceAgent", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("agent-001").build())
            .putAttributes("targetAgent", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("agent-002").build());
        
        // Create A2A message data
        A2AProtocolAdaptor.A2AMessage a2aMessage = new A2AProtocolAdaptor.A2AMessage();
        a2aMessage.setMessageType("TASK_REQUEST");
        a2aMessage.setPayload(Map.of("taskId", "task-001"));
        
        String messageData = JsonUtils.toJSONString(a2aMessage);
        cloudEventBuilder.setData(com.google.protobuf.ByteString.copyFrom(
            messageData.getBytes(StandardCharsets.UTF_8)));
        
        CloudEvent cloudEvent = cloudEventBuilder.build();
        
        // Convert to HTTP message
        Object result = adaptor.fromCloudEvent(cloudEvent);
        
        // Verify result is a RequestMessage
        assertTrue(result instanceof RequestMessage);
        RequestMessage requestMessage = (RequestMessage) result;
        
        // Verify header
        assertEquals("A2A", requestMessage.getHeader().getHeader(ProtocolKey.PROTOCOL_TYPE));
        assertEquals("1.0", requestMessage.getHeader().getHeader(ProtocolKey.PROTOCOL_VERSION));
        
        // Verify body contains A2A message
        String bodyContent = requestMessage.getBody().toMap().get("content").toString();
        A2AProtocolAdaptor.A2AMessage parsedMessage = JsonUtils.parseObject(bodyContent, A2AProtocolAdaptor.A2AMessage.class);
        assertEquals("TASK_REQUEST", parsedMessage.getMessageType());
    }

    @Test
    @DisplayName("Test unsupported protocol transport object")
    void testUnsupportedProtocolTransportObject() {
        // Create an unsupported object
        Object unsupportedObject = new Object();
        
        // Should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            adaptor.toCloudEvent((org.apache.eventmesh.common.protocol.ProtocolTransportObject) unsupportedObject);
        });
    }

    @Test
    @DisplayName("Test unsupported protocol type in CloudEvent")
    void testUnsupportedProtocolTypeInCloudEvent() {
        // Create CloudEvent with unsupported protocol
        CloudEvent.Builder cloudEventBuilder = CloudEvent.newBuilder()
            .setId("test-event-id")
            .setSource("eventmesh")
            .setSpecVersion("1.0")
            .setType("org.apache.eventmesh.protocol.unsupported")
            .putAttributes("protocol", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("UNSUPPORTED").build());
        
        CloudEvent cloudEvent = cloudEventBuilder.build();
        
        // Should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            adaptor.fromCloudEvent(cloudEvent);
        });
    }

    @Test
    @DisplayName("Test message serialization and deserialization")
    void testMessageSerializationAndDeserialization() {
        // Create A2A message
        A2AProtocolAdaptor.A2AMessage originalMessage = new A2AProtocolAdaptor.A2AMessage();
        originalMessage.setMessageType("TEST_MESSAGE");
        
        A2AProtocolAdaptor.AgentInfo agent = new A2AProtocolAdaptor.AgentInfo();
        agent.setAgentId("test-agent");
        agent.setAgentType("test-type");
        originalMessage.setSourceAgent(agent);
        originalMessage.setTargetAgent(agent);
        
        originalMessage.setPayload(Map.of("key", "value"));
        
        // Serialize
        String json = JsonUtils.toJSONString(originalMessage);
        assertNotNull(json);
        assertTrue(json.contains("TEST_MESSAGE"));
        assertTrue(json.contains("test-agent"));
        
        // Deserialize
        A2AProtocolAdaptor.A2AMessage deserializedMessage = JsonUtils.parseObject(json, A2AProtocolAdaptor.A2AMessage.class);
        
        // Verify
        assertEquals(originalMessage.getMessageType(), deserializedMessage.getMessageType());
        assertEquals(originalMessage.getSourceAgent().getAgentId(), deserializedMessage.getSourceAgent().getAgentId());
        assertEquals(originalMessage.getTargetAgent().getAgentId(), deserializedMessage.getTargetAgent().getAgentId());
    }
}
