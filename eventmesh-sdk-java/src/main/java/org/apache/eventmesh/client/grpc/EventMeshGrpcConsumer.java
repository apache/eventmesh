package org.apache.eventmesh.client.grpc;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.util.EventMeshClientUtil;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc.ConsumerServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat;
import org.apache.eventmesh.common.protocol.grpc.protos.HeartbeatServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.HeartbeatServiceGrpc.HeartbeatServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class EventMeshGrpcConsumer implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(EventMeshGrpcConsumer.class);

    private final EventMeshGrpcClientConfig clientConfig;

    private ManagedChannel channel;

    private ConsumerServiceBlockingStub consumerClient;
    private HeartbeatServiceBlockingStub heartbeatClient;

    private ReceiveMsgHook<EventMeshMessage> listener;

    private final List<ListenerThread> listenerThreads = new LinkedList<>();

    private final Map<String, String> subscriptionMap = new ConcurrentHashMap<>();

    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors(),
        new ThreadFactoryBuilder().setNameFormat("GRPCClientScheduler").setDaemon(true).build());

    public EventMeshGrpcConsumer(EventMeshGrpcClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public void init() {
        channel = ManagedChannelBuilder.forAddress(clientConfig.getServerAddr(), clientConfig.getServerPort())
            .usePlaintext().build();

        consumerClient = ConsumerServiceGrpc.newBlockingStub(channel);
        heartbeatClient = HeartbeatServiceGrpc.newBlockingStub(channel);

        heartBeat();
    }

    public Response subscribe(Subscription subscription) {
        logger.info("Create subscription: " + subscription.toString());

        addSubscription(subscription);

        Subscription enhancedSubscription = Subscription.newBuilder(subscription)
            .setHeader(EventMeshClientUtil.buildHeader(clientConfig))
            .setConsumerGroup(clientConfig.getConsumerGroup())
            .build();
        try {
            Response response = consumerClient.subscribe(enhancedSubscription);
            logger.info("Received response " + response.toString());
            return response;
        } catch (Exception e) {
            logger.error("Error in subscribe. error {}", e.getMessage());
            return null;
        }
    }

    public void subscribeStream(Subscription subscription) {
        logger.info("Create streaming subscription: " + subscription.toString());

        addSubscription(subscription);

        Subscription enhancedSubscription = Subscription.newBuilder(subscription)
            .setHeader(EventMeshClientUtil.buildHeader(clientConfig))
            .setConsumerGroup(clientConfig.getConsumerGroup())
            .build();
        Iterator<EventMeshMessage> msgIterator;
        try {
            msgIterator = consumerClient.subscribeStream(enhancedSubscription);
        } catch (Exception e) {
            logger.error("Error in subscribe. error {}", e.getMessage());
            return;
        }

        ListenerThread listenerThread = new ListenerThread(msgIterator, listener);
        listenerThreads.add(listenerThread);
        listenerThread.start();
    }

    private void addSubscription(Subscription subscription) {
        for (Subscription.SubscriptionItem item : subscription.getSubscriptionItemsList()) {
            String url = StringUtils.isEmpty(subscription.getUrl()) ? "grpc_stream" : subscription.getUrl();
            subscriptionMap.put(item.getTopic(), url);
        }
    }

    private void removeSubscription(Subscription subscription) {
        for (Subscription.SubscriptionItem item : subscription.getSubscriptionItemsList()) {
            subscriptionMap.remove(item.getTopic());
        }
    }

    public Response unsubscribe(Subscription subscription) {
        logger.info("Removing subscription: " + subscription.toString());

        removeSubscription(subscription);

        Subscription enhancedSubscription = Subscription.newBuilder(subscription)
            .setHeader(EventMeshClientUtil.buildHeader(clientConfig))
            .setConsumerGroup(clientConfig.getConsumerGroup())
            .build();

        try {
            Response response = consumerClient.unsubscribe(enhancedSubscription);
            logger.info("Received response " + response.toString());
            return response;
        } catch (Exception e) {
            logger.error("Error in unsubscribe. error {}", e.getMessage());
            return null;
        }
    }

    public void registerListener(ReceiveMsgHook<EventMeshMessage> listener) {
        if (this.listener == null) {
            this.listener = listener;
        }
    }

    private void heartBeat() {
        RequestHeader header = EventMeshClientUtil.buildHeader(clientConfig);
        scheduler.scheduleAtFixedRate(() -> {
            if (subscriptionMap.isEmpty()) {
                return;
            }
            Heartbeat.Builder heartbeatBuilder = Heartbeat.newBuilder()
                .setHeader(header)
                .setConsumerGroup(clientConfig.getConsumerGroup())
                .setClientType(Heartbeat.ClientType.SUB);

            for (Map.Entry<String, String> entry : subscriptionMap.entrySet()) {
                Heartbeat.HeartbeatItem heartbeatItem = Heartbeat.HeartbeatItem
                    .newBuilder()
                    .setTopic(entry.getKey()).setUrl(entry.getValue())
                    .build();
                heartbeatBuilder.addHeartbeatItems(heartbeatItem);
            }
            Heartbeat heartbeat = heartbeatBuilder.build();

            try {
                Response response = heartbeatClient.heartbeat(heartbeat);
                if (logger.isDebugEnabled()) {
                    logger.debug("Grpc Consumer Heartbeat response: {}", response);
                }
            } catch (Exception e) {
                logger.error("Error in sending out heartbeat. error {}", e.getMessage());
            }
        }, EventMeshCommon.HEARTBEAT, EventMeshCommon.HEARTBEAT, TimeUnit.MILLISECONDS);

        logger.info("Grpc Consumer Heartbeat started.");
    }

    @Override
    public void close() {
        for (ListenerThread thread : listenerThreads) {
            thread.stop();
        }
        channel.shutdown();
        scheduler.shutdown();
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
            try {
                while (msgIterator.hasNext()) {
                    logger.info("sdk received message ");
                    listener.handle(msgIterator.next());
                }
            } catch (Throwable t) {
                logger.warn("Error in handling message. {}", t.getMessage());
            }
        }
    }
}
