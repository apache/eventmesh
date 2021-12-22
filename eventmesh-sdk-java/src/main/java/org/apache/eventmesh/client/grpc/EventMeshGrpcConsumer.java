package org.apache.eventmesh.client.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.util.EventMeshClientUtil;
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class EventMeshGrpcConsumer implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(EventMeshGrpcConsumer.class);

    private final EventMeshGrpcClientConfig clientConfig;

    private ManagedChannel channel;

    private ReceiveMsgHook<EventMeshMessage> listener;

    private final List<ListenerThread> listenerThreads = new LinkedList<>();

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

    public void subscribeStream(Subscription subscription) {
        logger.info("Subscribe streaming topic " + subscription.toString());
        ConsumerServiceGrpc.ConsumerServiceBlockingStub consumerClient = ConsumerServiceGrpc.newBlockingStub(channel);

        Subscription enhancedSubscription = Subscription.newBuilder(subscription)
            .setHeader(EventMeshClientUtil.buildHeader(clientConfig))
            .setConsumerGroup(clientConfig.getConsumerGroup())
            .build();
        Iterator<EventMeshMessage> msgIterator = consumerClient.subscribeStream(enhancedSubscription);

        ListenerThread listenerThread = new ListenerThread(msgIterator, listener);
        listenerThreads.add(listenerThread);
        listenerThread.start();
    }

    public void registerListener(ReceiveMsgHook<EventMeshMessage> listener) {
        if (this.listener == null) {
            this.listener = listener;
        }
    }

    @Override
    public void close() {
        for (ListenerThread thread : listenerThreads) {
            thread.stop();
        }
        channel.shutdown();
    }

    static class ListenerThread extends Thread {
        private final Iterator<EventMeshMessage> msgIterator;

        private final ReceiveMsgHook<EventMeshMessage> listener;

        ListenerThread(Iterator<EventMeshMessage> msgIterator, ReceiveMsgHook<EventMeshMessage> listener) {
            this.msgIterator = msgIterator;
            this.listener = listener;
        }

        public void run() {
            logger.info("start receiving...");
            while (msgIterator.hasNext()) {
                logger.info("sdk received message ");
                listener.handle(msgIterator.next());
            }
        }
    }
}
