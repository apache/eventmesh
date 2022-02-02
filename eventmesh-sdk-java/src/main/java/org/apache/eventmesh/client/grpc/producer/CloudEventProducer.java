package org.apache.eventmesh.client.grpc.producer;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.util.EventMeshClientUtil;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc.PublisherServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CloudEventProducer {
    private static final Logger logger = LoggerFactory.getLogger(EventMeshGrpcProducer.class);

    private final static String PROTOCOL_TYPE = EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME;

    private final EventMeshGrpcClientConfig clientConfig;

    private final PublisherServiceBlockingStub publisherClient;

    public CloudEventProducer(EventMeshGrpcClientConfig clientConfig, PublisherServiceBlockingStub publisherClient) {
        this.clientConfig = clientConfig;
        this.publisherClient = publisherClient;
    }

    public Response publish(CloudEvent cloudEvent) {
        logger.info("Publish message " + cloudEvent.toString());
        CloudEvent enhanceEvent = enhanceCloudEvent(cloudEvent, null);

        SimpleMessage enhancedMessage = EventMeshClientUtil.buildSimpleMessage(enhanceEvent, clientConfig, PROTOCOL_TYPE);

        try {
            Response response = publisherClient.publish(enhancedMessage);
            logger.info("Received response " + response.toString());
            return response;
        } catch (Exception e) {
            logger.error("Error in publishing message {}, error {}", cloudEvent, e.getMessage());
            return null;
        }
    }

    public CloudEvent requestReply(CloudEvent cloudEvent, int timeout) {
        logger.info("RequestReply message " + cloudEvent.toString());
        CloudEvent enhanceEvent = enhanceCloudEvent(cloudEvent, String.valueOf(timeout));

        SimpleMessage enhancedMessage = EventMeshClientUtil.buildSimpleMessage(enhanceEvent, clientConfig, PROTOCOL_TYPE);
        try {
            SimpleMessage reply = publisherClient.requestReply(enhancedMessage);
            logger.info("Received reply message" + reply.toString());

            Object msg = EventMeshClientUtil.buildMessage(reply, PROTOCOL_TYPE);
            if (msg instanceof CloudEvent) {
                return (CloudEvent) msg;
            } else {
                return null;
            }
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

        BatchMessage enhancedMessage = EventMeshClientUtil.buildBatchMessages(enhancedEvents, clientConfig, PROTOCOL_TYPE);
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
            .withExtension(ProtocolKey.PROTOCOL_DESC, "grpc")
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
}