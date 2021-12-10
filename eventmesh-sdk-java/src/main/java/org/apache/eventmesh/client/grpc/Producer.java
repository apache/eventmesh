package org.apache.eventmesh.client.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.eventmesh.client.grpc.config.ClientConfig;
import org.apache.eventmesh.common.protocol.grpc.protos.Message;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Producer {

    private static Logger logger = LoggerFactory.getLogger(Producer.class);

    private ClientConfig clientConfig;

    private ManagedChannel channel;

    public Producer(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public void start() {
        channel = ManagedChannelBuilder.forAddress(clientConfig.getServerAddr(), clientConfig.getServerPort())
                .usePlaintext().build();
    }

    public boolean publish(String content) {
        PublisherServiceGrpc.PublisherServiceBlockingStub publisherClient = PublisherServiceGrpc.newBlockingStub(channel);

        Message message = Message.newBuilder().setContent(content).build();

        Response response = publisherClient.publish(message);
        logger.info("===========" + response.getRespCode() + " " + response.getRespMsg());
        return true;
    }

    public void stop() {
        channel.shutdown();
    }
}
