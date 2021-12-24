package org.apache.eventmesh.client.grpc;

import io.cloudevents.SpecVersion;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.util.EventMeshClientUtil;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMeshGrpcProducer implements AutoCloseable {

    private static Logger logger = LoggerFactory.getLogger(EventMeshGrpcProducer.class);

    private EventMeshGrpcClientConfig clientConfig;

    private ManagedChannel channel;

    public EventMeshGrpcProducer(EventMeshGrpcClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public void init() {
        channel = ManagedChannelBuilder.forAddress(clientConfig.getServerAddr(), clientConfig.getServerPort())
                .usePlaintext().build();
    }

    public Response publish(EventMeshMessage message) {
        logger.info("Publish message " + message.toString());
        PublisherServiceGrpc.PublisherServiceBlockingStub publisherClient = PublisherServiceGrpc.newBlockingStub(channel);

        EventMeshMessage enhancedMessage = EventMeshMessage.newBuilder(message)
            .setHeader(EventMeshClientUtil.buildHeader(clientConfig))
            .setProducerGroup(clientConfig.getProducerGroup())
            .build();
        Response response = publisherClient.publish(enhancedMessage);
        logger.info("Received response " + response.toString());
        return response;
    }

    public void close() {
        channel.shutdown();
    }
}
