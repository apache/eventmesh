package org.apache.eventmesh.client.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.util.EventMeshClientUtil;
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMeshGrpcConsumer implements AutoCloseable {

    private static Logger logger = LoggerFactory.getLogger(EventMeshGrpcConsumer.class);

    private EventMeshGrpcClientConfig clientConfig;

    private ManagedChannel channel;

    public EventMeshGrpcConsumer(EventMeshGrpcClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public void init() {
        channel = ManagedChannelBuilder.forAddress(clientConfig.getServerAddr(), clientConfig.getServerPort())
            .usePlaintext().build();
    }

    public Response subscribe(Subscription subscription) {
        logger.info("Subscribe topic " + subscription.toString());
        ConsumerServiceGrpc.ConsumerServiceBlockingStub consumerClient = ConsumerServiceGrpc.newBlockingStub(channel);

        Subscription enhancedSubscription = Subscription.newBuilder(subscription)
            .setHeader(EventMeshClientUtil.buildHeader(clientConfig))
            .setConsumerGroup(clientConfig.getConsumerGroup())
            .build();
        Response response = consumerClient.subscribe(enhancedSubscription);
        logger.info("Received response " + response.toString());
        return response;
    }

    @Override
    public void close() throws Exception {
        channel.shutdown();
    }
}
