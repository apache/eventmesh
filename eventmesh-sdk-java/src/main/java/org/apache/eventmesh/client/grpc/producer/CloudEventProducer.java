package org.apache.eventmesh.client.grpc.producer;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.client.grpc.EventMeshGrpcProducer;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.util.EventMeshClientUtil;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage.MessageItem;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc.PublisherServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class CloudEventProducer {
    private static final Logger logger = LoggerFactory.getLogger(EventMeshGrpcProducer.class);

    private final static String PROTOCOL_TYPE = "cloudevents";

    private final EventMeshGrpcClientConfig clientConfig;

    private final PublisherServiceBlockingStub publisherClient;

    public CloudEventProducer(EventMeshGrpcClientConfig clientConfig, PublisherServiceBlockingStub publisherClient) {
        this.clientConfig = clientConfig;
        this.publisherClient = publisherClient;
    }

    public Response publish(CloudEvent cloudEvent) {
        logger.info("Publish message " + cloudEvent.toString());
        CloudEvent enhanceEvent = enhanceCloudEvent(cloudEvent, null);

        SimpleMessage enhancedMessage = buildSimpleMessage(enhanceEvent);

        try {
            Response response = publisherClient.publish(enhancedMessage);
            logger.info("Received response " + response.toString());
            return response;
        } catch (Exception e) {
            logger.error("Error in publishing message {}, error {}", cloudEvent, e.getMessage());
            return null;
        }
    }

    public Response requestReply(CloudEvent cloudEvent, int timeout) {
        logger.info("RequestReply message " + cloudEvent.toString());
        CloudEvent enhanceEvent = enhanceCloudEvent(cloudEvent, String.valueOf(timeout));

        SimpleMessage enhancedMessage = buildSimpleMessage(enhanceEvent);
        try {
            Response response = publisherClient.requestReply(enhancedMessage);
            logger.info("Received response " + response.toString());
            return response;
        } catch (Exception e) {
            logger.error("Error in RequestReply message {}, error {}", cloudEvent, e.getMessage());
            return null;
        }
    }

    public Response publish(List<CloudEvent> events) {
        logger.info("BatchPublish message, batch size=" + events.size());

        if (events.size() == 0) {
            return null;
        }
        List<CloudEvent> enhancedEvents = events.stream()
            .map(event -> enhanceCloudEvent(event, null))
            .collect(Collectors.toList());

        BatchMessage enhancedMessage = buildBatchMessage(enhancedEvents);
        try {
            Response response = publisherClient.batchPublish(enhancedMessage);
            logger.info("Received response " + response.toString());
            return response;
        } catch (Exception e) {
            logger.error("Error in BatchPublish message {}, error {}", events, e.getMessage());
            return null;
        }
    }

    private CloudEvent enhanceCloudEvent(final CloudEvent cloudEvent, String timeout) {
        CloudEventBuilder builder = CloudEventBuilder.from(cloudEvent)
            .withExtension(ProtocolKey.ENV, clientConfig.getEnv())
            .withExtension(ProtocolKey.IDC, clientConfig.getIdc())
            .withExtension(ProtocolKey.IP, IPUtils.getLocalAddress())
            .withExtension(ProtocolKey.PID, Long.toString(ThreadUtils.getPID()))
            .withExtension(ProtocolKey.SYS, clientConfig.getSys())
            .withExtension(ProtocolKey.LANGUAGE, "JAVA")
            .withExtension(ProtocolKey.PROTOCOL_TYPE, PROTOCOL_TYPE)
            .withExtension(ProtocolKey.PROTOCOL_DESC, cloudEvent.getSpecVersion().name())
            .withExtension(ProtocolKey.PROTOCOL_VERSION, cloudEvent.getSpecVersion().toString())
            .withExtension(ProtocolKey.UNIQUE_ID, RandomStringUtils.generateNum(30))
            .withExtension(ProtocolKey.SEQ_NUM, RandomStringUtils.generateNum(30))
            .withExtension(ProtocolKey.USERNAME, clientConfig.getUserName())
            .withExtension(ProtocolKey.PASSWD, clientConfig.getPassword())
            .withExtension(ProtocolKey.PRODUCERGROUP, clientConfig.getProducerGroup());

        if (timeout != null) {
            builder.withExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL, timeout);
        }
        return builder.build();
    }

    private SimpleMessage buildSimpleMessage(CloudEvent cloudEvent) {
        String contentType = StringUtils.isEmpty(cloudEvent.getDataContentType()) ? "application/cloudevents+json"
            : cloudEvent.getDataContentType();
        byte[] bodyByte = EventFormatProvider.getInstance().resolveFormat(contentType)
            .serialize(cloudEvent);
        String content = new String(bodyByte, StandardCharsets.UTF_8);
        String ttl = cloudEvent.getExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL) == null ? "4000"
            : cloudEvent.getExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL).toString();

        return SimpleMessage.newBuilder()
            .setHeader(EventMeshClientUtil.buildHeader(clientConfig, PROTOCOL_TYPE))
            .setProducerGroup(clientConfig.getProducerGroup())
            .setTopic(cloudEvent.getSubject())
            .setTtl(ttl)
            .setSeqNum(cloudEvent.getExtension(ProtocolKey.SEQ_NUM).toString())
            .setUniqueId(cloudEvent.getExtension(ProtocolKey.UNIQUE_ID).toString())
            .setContent(content)
            .putProperties(ProtocolKey.CONTENT_TYPE, contentType)
            .build();
    }

    private BatchMessage buildBatchMessage(List<CloudEvent> events) {
        BatchMessage.Builder messageBuilder = BatchMessage.newBuilder()
            .setHeader(EventMeshClientUtil.buildHeader(clientConfig, PROTOCOL_TYPE))
            .setProducerGroup(clientConfig.getProducerGroup())
            .setTopic(events.get(0).getSubject());

        for (CloudEvent event: events) {
            String contentType = StringUtils.isEmpty(event.getDataContentType()) ? "application/cloudevents+json"
                : event.getDataContentType();
            byte[] bodyByte = EventFormatProvider.getInstance().resolveFormat(contentType)
                .serialize(event);
            String content = new String(bodyByte, StandardCharsets.UTF_8);

            String ttl = event.getExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL) == null ? "4000"
                : event.getExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL).toString();

            MessageItem messageItem = MessageItem.newBuilder()
                .setContent(content)
                .setTtl(ttl)
                .setSeqNum(event.getExtension(ProtocolKey.SEQ_NUM).toString())
                .setUniqueId(event.getExtension(ProtocolKey.UNIQUE_ID).toString())
                .putProperties(ProtocolKey.CONTENT_TYPE, contentType)
                .build();

            messageBuilder.addMessageItem(messageItem);
        }

        return messageBuilder.build();
    }
}
