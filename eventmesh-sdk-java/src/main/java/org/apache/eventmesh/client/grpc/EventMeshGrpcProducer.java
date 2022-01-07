package org.apache.eventmesh.client.grpc;

import io.cloudevents.CloudEvent;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.producer.CloudEventProducer;
import org.apache.eventmesh.client.grpc.util.EventMeshClientUtil;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc.PublisherServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class EventMeshGrpcProducer implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(EventMeshGrpcProducer.class);

    private final static String PROTOCOL_TYPE = "eventmeshmessage";

    private final EventMeshGrpcClientConfig clientConfig;

    private ManagedChannel channel;

    private PublisherServiceBlockingStub publisherClient;

    private CloudEventProducer cloudEventProducer;

    public EventMeshGrpcProducer(EventMeshGrpcClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public void init() {
        channel = ManagedChannelBuilder.forAddress(clientConfig.getServerAddr(), clientConfig.getServerPort())
                .usePlaintext().build();
        publisherClient = PublisherServiceGrpc.newBlockingStub(channel);

        cloudEventProducer = new CloudEventProducer(clientConfig, publisherClient);
    }

    public Response publish(CloudEvent cloudEvent) {
        return cloudEventProducer.publish(cloudEvent);
    }

    public Response publish(List<CloudEvent> cloudEventList) {
        return cloudEventProducer.publish(cloudEventList);
    }

    public Response requestReply(CloudEvent cloudEvent, int timeout) {
        return cloudEventProducer.requestReply(cloudEvent, timeout);
    }

    public Response publish(EventMeshMessage message) {
        logger.info("Publish message " + message.toString());

        EventMeshMessage enhancedMessage = EventMeshMessage.newBuilder(message)
            .setHeader(EventMeshClientUtil.buildHeader(clientConfig, PROTOCOL_TYPE))
            .setProducerGroup(clientConfig.getProducerGroup())
            .build();
        try {
            Response response = publisherClient.publish(enhancedMessage);
            logger.info("Received response " + response.toString());
            return response;
        } catch (Exception e) {
            logger.error("Error in publishing message {}, error {}", message, e.getMessage());
            return null;
        }
    }

    public Response requestReply(EventMeshMessage message, int timeout) {
        logger.info("RequestReply message " + message.toString());

        EventMeshMessage enhancedMessage = EventMeshMessage.newBuilder(message)
            .setHeader(EventMeshClientUtil.buildHeader(clientConfig, PROTOCOL_TYPE))
            .setProducerGroup(clientConfig.getProducerGroup())
            .setTtl(String.valueOf(timeout))
            .build();
        try {
            Response response = publisherClient.requestReply(enhancedMessage);
            logger.info("Received response " + response.toString());
            return response;
        } catch (Exception e) {
            logger.error("Error in RequestReply message {}, error {}", message, e.getMessage());
            return null;
        }
    }

    public Response publish(BatchMessage message) {
        logger.info("BatchPublish message " + message.toString());

        BatchMessage enhancedMessage = BatchMessage.newBuilder(message)
            .setHeader(EventMeshClientUtil.buildHeader(clientConfig, PROTOCOL_TYPE))
            .setProducerGroup(clientConfig.getProducerGroup())
            .build();
        try {
            Response response = publisherClient.batchPublish(enhancedMessage);
            logger.info("Received response " + response.toString());
            return response;
        } catch (Exception e) {
            logger.error("Error in BatchPublish message {}, error {}", message, e.getMessage());
            return null;
        }
    }

    public void close() {
        channel.shutdown();
    }
}
