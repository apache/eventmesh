package org.apache.eventmesh.client.grpc;

import io.cloudevents.SpecVersion;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMeshGrpcProducer implements AutoCloseable {

    private static Logger logger = LoggerFactory.getLogger(EventMeshGrpcProducer.class);

    private final static String PROTOCOL_TYPE = "eventmeshmessage";
    private final static String PROTOCOL_DESC = "grpc";

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
        EventMeshMessage newMessage = enhanceMessage(message);
        Response response = publisherClient.publish(newMessage);
        logger.info("Received response " + response.toString());
        return response;
    }

    public void close() {
        channel.shutdown();
    }

    private EventMeshMessage enhanceMessage(EventMeshMessage message) {
        RequestHeader header = RequestHeader.newBuilder()
            .setEnv(clientConfig.getEnv())
            .setIdc(clientConfig.getIdc())
            .setIp(clientConfig.getIp())
            .setPid(clientConfig.getPid())
            .setSys(clientConfig.getSys())
            .setLanguage(clientConfig.getLanguage())
            .setUsername(clientConfig.getUserName())
            .setPassword(clientConfig.getPassword())
            .setProtocolType(PROTOCOL_TYPE)
            .setProtocolDesc(PROTOCOL_DESC)
            // default CloudEvents version is V1
            .setProtocolVersion(SpecVersion.V1.toString())
            .build();

        EventMeshMessage newMessage = EventMeshMessage.newBuilder(message)
            .setHeader(header)
            .setProducerGroup(clientConfig.getProducerGroup())
            .build();
        return newMessage;
    }
}
