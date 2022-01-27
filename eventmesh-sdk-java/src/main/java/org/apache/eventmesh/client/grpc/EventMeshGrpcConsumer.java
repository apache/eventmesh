package org.apache.eventmesh.client.grpc;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.util.EventMeshClientUtil;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc.ConsumerServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat;
import org.apache.eventmesh.common.protocol.grpc.protos.HeartbeatServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.HeartbeatServiceGrpc.HeartbeatServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
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
    private final List<ListenerThread> listenerThreads = new LinkedList<>();
    private final Map<String, String> subscriptionMap = new ConcurrentHashMap<>();
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors(),
        new ThreadFactoryBuilder().setNameFormat("GRPCClientScheduler").setDaemon(true).build());
    private ManagedChannel channel;
    private ConsumerServiceBlockingStub consumerClient;
    private HeartbeatServiceBlockingStub heartbeatClient;
    private ReceiveMsgHook<?> listener;

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

    public Response subscribe(List<SubscriptionItem> subscriptionItems, String url) {
        logger.info("Create subscription: " + subscriptionItems + ", url: " + url);

        addSubscription(subscriptionItems, url);

        Subscription subscription = buildSubscription(subscriptionItems, url);
        try {
            Response response = consumerClient.subscribe(subscription);
            logger.info("Received response " + response.toString());
            return response;
        } catch (Exception e) {
            logger.error("Error in subscribe. error {}", e.getMessage());
            return null;
        }
    }

    public void subscribe(List<SubscriptionItem> subscriptionItems) {
        logger.info("Create streaming subscription: " + subscriptionItems);

        addSubscription(subscriptionItems, "grpc_stream");

        Subscription subscription = buildSubscription(subscriptionItems, null);
        Iterator<SimpleMessage> msgIterator;
        try {
            msgIterator = consumerClient.subscribeStream(subscription);
        } catch (Exception e) {
            logger.error("Error in subscribe. error {}", e.getMessage());
            return;
        }

        ListenerThread listenerThread = new ListenerThread(msgIterator, listener, listener.getProtocolType());
        listenerThreads.add(listenerThread);
        listenerThread.start();
    }

    private void addSubscription(List<SubscriptionItem> subscriptionItems, String url) {
        for (SubscriptionItem item : subscriptionItems) {
            subscriptionMap.put(item.getTopic(), url);
        }
    }

    private void removeSubscription(List<SubscriptionItem> subscriptionItems) {
        for (SubscriptionItem item : subscriptionItems) {
            subscriptionMap.remove(item.getTopic());
        }
    }

    public Response unsubscribe(List<SubscriptionItem> subscriptionItems, String url) {
        logger.info("Removing subscription: " + subscriptionItems + ", url: " + url);

        removeSubscription(subscriptionItems);

        Subscription subscription = buildSubscription(subscriptionItems, url);

        try {
            Response response = consumerClient.unsubscribe(subscription);
            logger.info("Received response " + response.toString());
            return response;
        } catch (Exception e) {
            logger.error("Error in unsubscribe. error {}", e.getMessage());
            return null;
        }
    }

    public Response unsubscribe(List<SubscriptionItem> subscriptionItems) {
        return unsubscribe(subscriptionItems, null);
    }

    private Subscription buildSubscription(List<SubscriptionItem> subscriptionItems, String url) {
        Subscription.Builder builder = Subscription.newBuilder()
            .setHeader(EventMeshClientUtil.buildHeader(clientConfig, EventMeshCommon.EM_MESSAGE_PROTOCOL_NAME))
            .setConsumerGroup(clientConfig.getConsumerGroup());

        if (StringUtils.isNotEmpty(url)) {
            builder.setUrl(url);
        }

        for (SubscriptionItem subscriptionItem : subscriptionItems) {
            Subscription.SubscriptionItem.SubscriptionMode mode;
            if (SubscriptionMode.CLUSTERING.equals(subscriptionItem.getMode())) {
                mode = Subscription.SubscriptionItem.SubscriptionMode.CLUSTERING;
            } else if (SubscriptionMode.BROADCASTING.equals(subscriptionItem.getMode())) {
                mode = Subscription.SubscriptionItem.SubscriptionMode.BROADCASTING;
            } else {
                mode = Subscription.SubscriptionItem.SubscriptionMode.UNRECOGNIZED;
            }

            Subscription.SubscriptionItem.SubscriptionType type;
            if (SubscriptionType.ASYNC.equals(subscriptionItem.getType())) {
                type = Subscription.SubscriptionItem.SubscriptionType.ASYNC;
            } else if (SubscriptionType.SYNC.equals(subscriptionItem.getType())) {
                type = Subscription.SubscriptionItem.SubscriptionType.SYNC;
            } else {
                type = Subscription.SubscriptionItem.SubscriptionType.UNRECOGNIZED;
            }

            Subscription.SubscriptionItem item = Subscription.SubscriptionItem.newBuilder()
                .setTopic(subscriptionItem.getTopic())
                .setMode(mode)
                .setType(type)
                .build();
            builder.addSubscriptionItems(item);
        }
        return builder.build();
    }

    public void registerListener(ReceiveMsgHook<?> listener) {
        if (this.listener == null) {
            this.listener = listener;
        }
    }

    private void heartBeat() {
        RequestHeader header = EventMeshClientUtil.buildHeader(clientConfig, EventMeshCommon.EM_MESSAGE_PROTOCOL_NAME);
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
        }, 10000, EventMeshCommon.HEARTBEAT, TimeUnit.MILLISECONDS);

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

    static class ListenerThread<T> extends Thread {
        private final Iterator<SimpleMessage> msgIterator;

        private final ReceiveMsgHook<T> listener;

        private final String protocolType;

        ListenerThread(Iterator<SimpleMessage> msgIterator, ReceiveMsgHook<T> listener, String protocolType) {
            this.msgIterator = msgIterator;
            this.listener = listener;
            this.protocolType = protocolType;
        }

        public void run() {
            logger.info("start receiving...");
            try {
                while (msgIterator.hasNext()) {
                    logger.info("sdk received message ");

                    SimpleMessage simpleMessage = msgIterator.next();
                    T msg = buildMessage(simpleMessage);
                    if (msg != null) {
                        listener.handle(msg);
                    }
                }
            } catch (Throwable t) {
                logger.warn("Error in handling message. {}", t.getMessage());
            }
        }

        private T buildMessage(SimpleMessage simpleMessage) {
            String seq = simpleMessage.getSeqNum();
            String uniqueId = simpleMessage.getUniqueId();

            // This is GRPC response message, ignore it
            if (StringUtils.isEmpty(seq) && StringUtils.isEmpty(uniqueId)) {
                return null;
            }

            if (EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME.equals(protocolType)) {
                String contentType = simpleMessage.getPropertiesOrDefault(ProtocolKey.CONTENT_TYPE, JsonFormat.CONTENT_TYPE);
                try {
                    CloudEvent cloudEvent = EventFormatProvider.getInstance().resolveFormat(contentType)
                        .deserialize(simpleMessage.getContent().getBytes(StandardCharsets.UTF_8));
                    return (T) cloudEvent;
                } catch (Throwable t) {
                    logger.warn("Error in building message. {}", t.getMessage());
                    return null;
                }
            } else {
                EventMeshMessage eventMeshMessage = EventMeshMessage.builder()
                    .content(simpleMessage.getContent())
                    .topic(simpleMessage.getTopic())
                    .bizSeqNo(seq)
                    .uniqueId(uniqueId)
                    .prop(simpleMessage.getPropertiesMap())
                    .build();
                return (T) eventMeshMessage;
            }
        }
    }
}
